package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosShard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Свита переходит со мной между мирами.
 * <ul>
 * <li>Переход (портал, книга связи, ритуал): слуги, что ходят за мной, записываются поимённо в тот же миг, пока их мир
 * ещё загружен, и переносятся в конце тика. Если кого-то не нашли (мир успел выгрузиться — Mystcraft выгружает пустой
 * мир через секунду), его мир и чанк догружаются, и попытка повторяется ещё несколько раз.</li>
 * <li>Смерть и возрождение в другом мире: свита из мира гибели идёт за мной так же.</li>
 * <li>Подстраховка: раз в 5 секунд слуги-попутчики из загруженных миров, где меня нет, переносятся ко мне.</li>
 * </ul>
 * Ничего не пишет в сохранение: список ожидания живёт только в памяти.
 */
public final class ServantTravel {

    private static final class Pending {
        final UUID owner, id;
        final int dim;
        final BlockPos last;
        int tries, wait;

        Pending(UUID owner, UUID id, int dim, BlockPos last) {
            this.owner = owner;
            this.id = id;
            this.dim = dim;
            this.last = last;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Integer> DEATH_DIM = new HashMap<>();
    private static final int TRIES = 6, RETRY = 20, SWEEP = 100;
    private int ticks;

    /** Слуга-попутчик хозяина (не стережёт, не у сундука, не в гарнизоне, ни на ком не едет). */
    static boolean travels(Entity e, UUID owner) {
        return e.isEntityAlive() && Servants.isServantOf(e, owner) && Servants.isFollower(e) && !e.isRiding() && !e.isBeingRidden();
    }

    /** Пускают ли свиту туда, где я сейчас. В Испытания свита не ходит — ждёт у входа (кроме арены Вызова). */
    static boolean allowed(EntityPlayer p) {
        return !(ru.death.chaosshard.trial.TrialDimension.is(p.world) && !ru.death.chaosshard.challenge.ChallengeManager.inArena(p));
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.player.world.isRemote) {
            queue(event.player, event.fromDim);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            DEATH_DIM.put(event.getEntityLiving().getUniqueID(), event.getEntityLiving().dimension);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player.world.isRemote || event.isEndConquered()) {
            return;
        }
        Integer d = DEATH_DIM.remove(event.player.getUniqueID());
        if (d != null && d != event.player.dimension) {
            queue(event.player, d);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        try {
            process(server);
            if (++ticks % SWEEP == 0) {
                for (EntityPlayerMP p : new ArrayList<>(server.getPlayerList().getPlayers())) {
                    sweep(p);
                }
            }
        } catch (RuntimeException e) {
            ChaosShard.LOG.warn("Перенос свиты не удался", e);
        }
    }

    // ------------------------------------------------------------------ перенос

    /** Записать попутчиков из мира fromDim, пока он загружен. */
    public static int queue(EntityPlayer p, int fromDim) {
        WorldServer from = DimensionManager.getWorld(fromDim);
        if (from == null) {
            return 0;
        }
        int n = 0;
        for (Entity e : new ArrayList<>(from.loadedEntityList)) {
            if (travels(e, p.getUniqueID())) {
                PENDING.add(new Pending(p.getUniqueID(), e.getUniqueID(), fromDim, e.getPosition()));
                n++;
            }
        }
        return n;
    }

    /** Обработать список ожидания (вызывается в конце тика). */
    public static void process(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Map<UUID, Integer> placed = new HashMap<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending q = it.next();
            if (q.wait > 0) {
                q.wait--;
                continue;
            }
            EntityPlayerMP p = ru.death.chaosshard.debug.TestHooks.player(server, q.owner);
            if (p == null || p.isDead || p.dimension == q.dim || !allowed(p)) {
                it.remove();
                continue;
            }
            if (p.getServerWorld().getEntityFromUuid(q.id) != null) {
                it.remove();            // уже здесь
                continue;
            }
            WorldServer from = DimensionManager.getWorld(q.dim);
            if (from == null) {
                DimensionManager.initDimension(q.dim);
                from = DimensionManager.getWorld(q.dim);
            }
            Entity e = from == null ? null : from.getEntityFromUuid(q.id);
            if (e == null && from != null) {
                from.getChunkProvider().loadChunk(q.last.getX() >> 4, q.last.getZ() >> 4);
                e = from.getEntityFromUuid(q.id);
            }
            if (e != null && travels(e, q.owner)) {
                int n = placed.merge(q.owner, 1, Integer::sum) - 1;
                bring(e, p, n);
                it.remove();
            } else if (e != null || ++q.tries >= TRIES) {
                it.remove();
            } else {
                q.wait = RETRY;
            }
        }
    }

    /** Подстраховка: попутчики из других загруженных миров — ко мне. Возвращает, сколько перенесено. */
    public static int sweep(EntityPlayerMP p) {
        if (p.isDead || !allowed(p)) {
            return 0;
        }
        int n = 0;
        for (WorldServer w : p.getServer().worlds) {
            if (w == p.world) {
                continue;
            }
            for (Entity e : new ArrayList<>(w.loadedEntityList)) {
                if (travels(e, p.getUniqueID())) {
                    bring(e, p, n++);
                }
            }
        }
        return n;
    }

    /** Перенести слугу ко мне: на свободное место рядом, не вплотную. */
    static void bring(Entity e, EntityPlayerMP p, int n) {
        double ang = Math.toRadians(p.renderYawOffset + 180 + (n % 6 - 2.5) * 40);
        double r = 2.2 + (n / 6) * 1.4;
        net.minecraft.util.math.Vec3d want = new net.minecraft.util.math.Vec3d(p.posX - Math.sin(ang) * r, p.posY, p.posZ + Math.cos(ang) * r);
        Entity moved = e.changeDimension(p.dimension, (world, entity, yaw) -> {
            BlockPos at = Servants.safeSpot(world, want, p);
            entity.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.rotationYaw, 0);
            entity.motionX = entity.motionY = entity.motionZ = 0;
        });
        if (moved != null) {
            p.getServerWorld().spawnParticle(EnumParticleTypes.PORTAL, moved.posX, moved.posY + 1, moved.posZ, 30, 0.3, 0.6, 0.3, 0.5);
        }
    }

    public static void clearState() {
        PENDING.clear();
        DEATH_DIM.clear();
    }
}
