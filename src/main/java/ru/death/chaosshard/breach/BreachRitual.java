package ru.death.chaosshard.breach;

import net.minecraft.block.Block;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ритуал Прорыва на Алтаре Прорыва.
 *
 * Постройка: Алтарь Прорыва, по диагоналям на расстоянии 2 — четыре столба
 * (хаосит снизу, корка сверху). Над алтарём открытое небо.
 * Нужен уровень Нити из конфига (по умолчанию 2) и Книга Прорыва в руке.
 * Ритуал длится 12 секунд; Осколок должен стоять рядом.
 */
public class BreachRitual {

    public static final int DURATION = 240;
    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    private static final class Running {
        final BlockPos altar;
        final int dim;
        int ticks;

        Running(BlockPos altar, int dim) {
            this.altar = altar;
            this.dim = dim;
        }
    }

    /** Позиции столбов относительно алтаря. */
    public static List<BlockPos> pillars(BlockPos altar) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                out.add(altar.add(dx, 0, dz));
            }
        }
        return out;
    }

    /** Проверить всё; вернуть ключ сообщения об ошибке или null. */
    public static String check(World world, BlockPos altar, EntityPlayerMP player) {
        if (!AntechamberDimension.is(world)) {
            return "chaosshard.breach.not_here";
        }
        if (ThreadManager.getLevel(player) < ChaosConfig.breach.threadLevel && !player.isCreative()) {
            return "chaosshard.breach.thin";
        }
        if (!world.canSeeSky(altar.up())) {
            return "chaosshard.breach.no_sky";
        }
        for (BlockPos p : pillars(altar)) {
            if (!BlockRitualSpire.isWhole(world, p)) {
                return "chaosshard.breach.no_pillars";
            }
        }
        return null;
    }

    public static boolean runningAt(BlockPos altar) {
        for (Running r : RUNNING.values()) {
            if (r.altar.equals(altar)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRunning(EntityPlayerMP player) {
        return RUNNING.containsKey(player.getUniqueID());
    }

    public static void start(EntityPlayerMP player, BlockPos altar) {
        RUNNING.put(player.getUniqueID(), new Running(altar, player.dimension));
        BlockRitualSpire.setLit(player.world, altar, true);
        player.sendStatusMessage(Breach.grimoire("chaosshard.breach.start"), true);
        player.world.playSound(null, altar, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.6F, 0.6F);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || RUNNING.isEmpty()) {
            return;
        }
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Running r = RUNNING.get(player.getUniqueID());
        if (r == null) {
            return;
        }
        World world = player.world;
        if (player.dimension != r.dim || player.isDead
                || player.getDistanceSq(r.altar) > 8 * 8
                || world.getBlockState(r.altar).getBlock() != ModBlocks.BREACH_ALTAR) {
            RUNNING.remove(player.getUniqueID());
            BlockRitualSpire.setLit(world, r.altar, false);
            player.sendMessage(Breach.grimoire("chaosshard.breach.broken"));
            return;
        }
        r.ticks++;
        effects((WorldServer) world, r);
        switch (r.ticks) {
            case 50:
                player.sendMessage(Breach.grimoire("chaosshard.breach.step1"));
                break;
            case 110:
                player.sendMessage(Breach.grimoire("chaosshard.breach.step2"));
                world.playSound(null, r.altar, SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.BLOCKS, 0.4F, 0.5F);
                break;
            case 170:
                player.sendMessage(Breach.lord("chaosshard.breach.lord"));
                world.playSound(null, r.altar, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.BLOCKS, 0.5F, 0.4F);
                break;
            default:
                break;
        }
        if (r.ticks >= DURATION) {
            RUNNING.remove(player.getUniqueID());
            BlockRitualSpire.setLit(world, r.altar, false);
            finish(player, r);
        }
    }

    private static void effects(WorldServer world, Running r) {
        double cx = r.altar.getX() + 0.5;
        double cy = r.altar.getY() + 1.2;
        double cz = r.altar.getZ() + 0.5;
        float progress = r.ticks / (float) DURATION;
        // Лучи от столбов к алтарю
        if (r.ticks % 2 == 0) {
            for (BlockPos p : pillars(r.altar)) {
                double t = (r.ticks % 20) / 20.0;
                double x = p.getX() + 0.5 + (cx - p.getX() - 0.5) * t;
                double z = p.getZ() + 0.5 + (cz - p.getZ() - 0.5) * t;
                world.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, p.getY() + 2.0, z, 2, 0.05, 0.05, 0.05, 0.0);
            }
        }
        // Дуги с остриёв шпилей в алтарь
        if (r.ticks % 15 == 0) {
            for (BlockPos p : pillars(r.altar)) {
                ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendToAllAround(
                        new ru.death.chaosshard.network.PacketHeartBolt(p.getX() + 0.5, p.getY() + 1.95, p.getZ() + 0.5,
                                cx, r.altar.getY() + 0.9, cz, 0xB070FF),
                        new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(world.provider.getDimension(), cx, cy, cz, 64));
            }
            world.playSound(null, r.altar, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.BLOCKS, 0.12F, 2.0F);
        }
        // Воронка над алтарём растёт с ходом ритуала
        double angle = r.ticks * 0.35;
        double radius = 0.4 + progress * 1.6;
        world.spawnParticle(EnumParticleTypes.PORTAL, cx + Math.cos(angle) * radius, cy + progress * 3, cz + Math.sin(angle) * radius,
                6, 0.1, 0.1, 0.1, 0.4);
        if (r.ticks % 40 == 0) {
            world.playSound(null, r.altar, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 0.5F, 0.5F + progress);
        }
    }

    private static void finish(EntityPlayerMP player, Running r) {
        WorldServer world = (WorldServer) player.world;
        world.addWeatherEffect(new EntityLightningBolt(world, r.altar.getX() + 0.5, r.altar.getY() + 1, r.altar.getZ() + 0.5, true));
        world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, r.altar.getX() + 0.5, r.altar.getY() + 1.5, r.altar.getZ() + 0.5,
                3, 0.5, 0.5, 0.5, 0.0);
        boolean first = !Breach.hasBreached(player);
        Breach.setBreached(player, true);
        if (first) {
            ThreadManager.addPoints(player, ChaosConfig.breach.threadReward);
            player.sendMessage(Breach.lord("chaosshard.breach.lord_done"));
            player.sendMessage(Breach.grimoire("chaosshard.breach.done"));
            String stage = ChaosConfig.breach.stage;
            if (stage != null && !stage.isEmpty() && player.getServer() != null) {
                player.getServer().getCommandManager().executeCommand(player.getServer(),
                        "gamestage add " + player.getName() + " " + stage);
            }
        }
        Breach.toLuchezariya(player);
    }

    /** Есть ли у игрока Книга Прорыва в одной из рук. */
    public static boolean holdsBook(EntityPlayerMP player) {
        for (ItemStack s : new ItemStack[]{player.getHeldItemMainhand(), player.getHeldItemOffhand()}) {
            if (s.getItem() == ModItems.BREACH_BOOK) {
                return true;
            }
        }
        return false;
    }

    /** Сбросить все ритуалы (при остановке сервера). */
    public static void clear() {
        RUNNING.clear();
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        RUNNING.clear();
    }
}
