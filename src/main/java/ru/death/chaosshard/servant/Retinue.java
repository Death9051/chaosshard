package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.breach.Breach;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Свита в Гримуаре: учёт обычных слуг (не зверей, не гарнизона, не Гнезда), где они и что делают,
 * и управление ими — позвать к себе и временно отозвать (слуга уходит в Нить целиком, со снаряжением, сумкой
 * и заданием, а потом возвращается туда, откуда ушёл; попутчик — ко мне).
 * <p>
 * Учёт пишется в мир: слуга, оставшийся в непрогруженном чанке или в другом мире, всё равно виден
 * в Гримуаре и его можно позвать — чанк догрузится. Сами в порталы слуги не заходят: между мирами их переносит
 * только {@link ServantTravel} вместе со мной (раньше слуга мог сам шагнуть в портал и потеряться).
 */
public class Retinue extends WorldSavedData {

    public static final String NAME = "chaosshard_retinue";

    /** Что делает слуга (для Гримуара). */
    public static final int FOLLOW = 0, GUARD = 1, CHEST = 2, STASHED = 3, FAR = 4;

    /** Одна запись: где слуга был в последний раз; у отозванного — он сам целиком. */
    public static class Rec {
        public String kind = "";
        public String name = "";
        public int dim;
        public BlockPos pos = BlockPos.ORIGIN;
        /** Отозванный слуга целиком (NBT сущности), иначе null. */
        public NBTTagCompound stash;
        /** Отозван, когда шёл за мной: вернётся ко мне. Иначе — на прежнее место. */
        public boolean follower;
    }

    private final Map<UUID, Map<UUID, Rec>> data = new LinkedHashMap<>();

    public Retinue() {
        super(NAME);
    }

    public Retinue(String name) {
        super(name);
    }

    public static Retinue get() {
        World w = DimensionManager.getWorld(0);
        Retinue d = (Retinue) w.getMapStorage().getOrLoadData(Retinue.class, NAME);
        if (d == null) {
            d = new Retinue();
            w.getMapStorage().setData(NAME, d);
        }
        return d;
    }

    public Map<UUID, Rec> of(UUID owner) {
        return data.computeIfAbsent(owner, k -> new LinkedHashMap<>());
    }

    /** Сколько слуг игрока сейчас отозвано (считаются в предел свиты). */
    public static int stashed(UUID owner) {
        if (DimensionManager.getWorld(0) == null) {
            return 0;
        }
        int n = 0;
        for (Rec r : get().of(owner).values()) {
            if (r.stash != null) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        data.clear();
        NBTTagList owners = tag.getTagList("owners", 10);
        for (int i = 0; i < owners.tagCount(); i++) {
            NBTTagCompound o = owners.getCompoundTagAt(i);
            Map<UUID, Rec> m = of(o.getUniqueId("id"));
            NBTTagList list = o.getTagList("list", 10);
            for (int j = 0; j < list.tagCount(); j++) {
                NBTTagCompound t = list.getCompoundTagAt(j);
                Rec r = new Rec();
                r.kind = t.getString("kind");
                r.name = t.getString("name");
                r.dim = t.getInteger("dim");
                r.pos = BlockPos.fromLong(t.getLong("pos"));
                r.follower = t.getBoolean("follower");
                if (t.hasKey("stash")) {
                    r.stash = t.getCompoundTag("stash");
                }
                m.put(t.getUniqueId("id"), r);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList owners = new NBTTagList();
        for (Map.Entry<UUID, Map<UUID, Rec>> o : data.entrySet()) {
            if (o.getValue().isEmpty()) {
                continue;
            }
            NBTTagCompound ot = new NBTTagCompound();
            ot.setUniqueId("id", o.getKey());
            NBTTagList list = new NBTTagList();
            for (Map.Entry<UUID, Rec> e : o.getValue().entrySet()) {
                Rec r = e.getValue();
                NBTTagCompound t = new NBTTagCompound();
                t.setUniqueId("id", e.getKey());
                t.setString("kind", r.kind);
                t.setString("name", r.name);
                t.setInteger("dim", r.dim);
                t.setLong("pos", r.pos.toLong());
                t.setBoolean("follower", r.follower);
                if (r.stash != null) {
                    t.setTag("stash", r.stash);
                }
                list.appendTag(t);
            }
            ot.setTag("list", list);
            owners.appendTag(ot);
        }
        tag.setTag("owners", owners);
        return tag;
    }

    // ------------------------------------------------------------------ учёт

    /** Слуга свиты: обычный, не зверь, не гарнизон, не Гнездо. */
    public static boolean counted(Entity e) {
        return e instanceof EntityCreature && Servants.isServant(e) && !(e instanceof EntityChaosBeast) && !Servants.isNest(e)
                && !e.getEntityData().hasKey(Servants.TAG_ANCHOR) && Servants.kindOf(e) != null;
    }

    private static void note(Entity e) {
        UUID owner = e.getEntityData().getUniqueId(Servants.TAG_OWNER);
        Map<UUID, Rec> m = get().of(owner);
        if (!counted(e)) {
            if (m.remove(e.getUniqueID()) != null) {
                get().markDirty();
            }
            return;
        }
        Rec r = m.get(e.getUniqueID());
        if (r == null) {
            r = new Rec();
            m.put(e.getUniqueID(), r);
        }
        r.kind = Servants.kindOf(e).name();
        r.name = e.hasCustomName() ? e.getCustomNameTag() : "";
        r.dim = e.dimension;
        r.pos = e.getPosition();
        r.stash = null;
        get().markDirty();
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent event) {
        Entity e = event.getEntityLiving();
        if (e.world.isRemote || !Servants.isServant(e) || e instanceof EntityChaosBeast || (e.ticksExisted + e.getEntityId()) % 40 != 0) {
            return;
        }
        note(e);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        Entity e = event.getEntityLiving();
        if (!e.world.isRemote && Servants.isServant(e) && !(e instanceof EntityChaosBeast)) {
            if (get().of(e.getEntityData().getUniqueId(Servants.TAG_OWNER)).remove(e.getUniqueID()) != null) {
                get().markDirty();
            }
        }
    }

    /** Сами слуги в порталы не ходят: переносит их только перенос свиты вместе с хозяином. */
    @SubscribeEvent
    public void onTravel(EntityTravelToDimensionEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof EntityPlayer) && Servants.isServant(e) && !ServantTravel.moving) {
            event.setCanceled(true);
            e.timeUntilPortal = e.getPortalCooldown();
        }
    }

    // ------------------------------------------------------------------ поиск

    /** Найти слугу: в загруженных мирах, иначе догрузить его мир и чанк по последней записи. */
    public static Entity find(net.minecraft.server.MinecraftServer s, UUID id, Rec r, boolean load) {
        for (WorldServer w : s.worlds) {
            Entity e = w.getEntityFromUuid(id);
            if (e != null && e.isEntityAlive()) {
                return e;
            }
        }
        if (!load || r == null) {
            return null;
        }
        WorldServer w = DimensionManager.getWorld(r.dim);
        if (w == null) {
            DimensionManager.initDimension(r.dim);
            w = DimensionManager.getWorld(r.dim);
        }
        if (w == null) {
            return null;
        }
        w.getChunkProvider().loadChunk(r.pos.getX() >> 4, r.pos.getZ() >> 4);
        Entity e = w.getEntityFromUuid(id);
        return e != null && e.isEntityAlive() ? e : null;
    }

    /** Что делает живой слуга. */
    public static int state(Entity e) {
        if (e.getEntityData().hasKey(Servants.TAG_CHEST)) {
            return CHEST;
        }
        if (e.getEntityData().hasKey(Servants.TAG_GUARD)) {
            return GUARD;
        }
        return FOLLOW;
    }

    // ------------------------------------------------------------------ для Гримуара

    /** Список свиты для Гримуара. Заодно вычёркивает тех, чей чанк загружен, а их самих там нет (погибли или пропали). */
    public static NBTTagList view(EntityPlayerMP p) {
        net.minecraft.server.MinecraftServer s = p.getServer();
        Map<UUID, Rec> m = get().of(p.getUniqueID());
        // слуги, которых ещё нет в учёте (только что призваны)
        for (WorldServer w : p.getServer().worlds) {
            for (Entity e : new ArrayList<>(w.loadedEntityList)) {
                if (Servants.isServantOf(e, p.getUniqueID()) && counted(e) && e.isEntityAlive() && !m.containsKey(e.getUniqueID())) {
                    note(e);
                }
            }
        }
        NBTTagList out = new NBTTagList();
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Rec> en : m.entrySet()) {
            Rec r = en.getValue();
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("id", en.getKey());
            t.setString("kind", r.kind);
            t.setString("name", r.name);
            if (r.stash != null) {
                t.setInteger("state", STASHED);
                t.setBoolean("follower", r.follower);
                out.appendTag(t);
                continue;
            }
            Entity e = find(s, en.getKey(), r, false);
            if (e == null) {
                WorldServer w = DimensionManager.getWorld(r.dim);
                if (w != null && w.isBlockLoaded(r.pos) && w.getEntityFromUuid(en.getKey()) == null) {
                    gone.add(en.getKey());
                    continue;
                }
                t.setInteger("state", FAR);
                t.setInteger("dim", r.dim);
                t.setString("world", worldName(r.dim));
                t.setInteger("dist", r.dim == p.dimension ? (int) Math.sqrt(r.pos.distanceSq(p.getPosition())) : -1);
                out.appendTag(t);
                continue;
            }
            if (!counted(e)) {
                gone.add(en.getKey());
                continue;
            }
            t.setInteger("state", state(e));
            t.setInteger("dim", e.dimension);
            t.setString("world", worldName(e.dimension));
            t.setInteger("dist", e.world == p.world ? (int) e.getDistance(p) : -1);
            if (e instanceof EntityLiving) {
                t.setFloat("hp", ((EntityLiving) e).getHealth());
                t.setFloat("max", ((EntityLiving) e).getMaxHealth());
            }
            out.appendTag(t);
        }
        if (!gone.isEmpty()) {
            gone.forEach(m::remove);
            get().markDirty();
        }
        return out;
    }

    static String worldName(int dim) {
        try {
            return net.minecraftforge.common.DimensionManager.getProviderType(dim).getName();
        } catch (RuntimeException ex) {
            return "dim " + dim;
        }
    }

    // ------------------------------------------------------------------ действия

    /** Позвать к себе: слуга встаёт в свиту (снимается с поста и с сундука) и переносится ко мне. */
    public static boolean call(EntityPlayerMP p, UUID id) {
        Rec r = get().of(p.getUniqueID()).get(id);
        if (r == null || r.stash != null || !ServantTravel.allowed(p)) {
            return false;
        }
        Entity e = find(p.getServer(), id, r, true);
        if (!(e instanceof EntityCreature) || !Servants.isServantOf(e, p.getUniqueID())) {
            lost(p, id, r);
            return false;
        }
        EntityCreature m = (EntityCreature) e;
        m.getEntityData().removeTag(Servants.TAG_GUARD);
        if (m.getEntityData().hasKey(Servants.TAG_CHEST)) {
            ru.death.chaosshard.servant.work.Work.unbind(m);
        }
        m.detachHome();
        m.setAttackTarget(null);
        m.getNavigator().clearPath();
        if (m.world == p.world) {
            BlockPos at = Servants.safeSpot(p.world, behind(p), p);
            m.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            p.getServerWorld().spawnParticle(EnumParticleTypes.PORTAL, m.posX, m.posY + 1, m.posZ, 40, 0.3, 0.6, 0.3, 0.6);
            Resync.later(m, 3);
        } else {
            ServantTravel.bring(m, p, 0);
        }
        p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.NEUTRAL, 0.6F, 1.1F);
        return true;
    }

    /** Отозвать: слуга целиком уходит в Нить и ждёт, пока его вернут. */
    public static boolean stash(EntityPlayerMP p, UUID id) {
        Rec r = get().of(p.getUniqueID()).get(id);
        if (r == null || r.stash != null) {
            return false;
        }
        Entity e = find(p.getServer(), id, r, true);
        if (!(e instanceof EntityCreature) || !Servants.isServantOf(e, p.getUniqueID())) {
            lost(p, id, r);
            return false;
        }
        e.dismountRidingEntity();
        e.removePassengers();
        NBTTagCompound t = new NBTTagCompound();
        if (!e.writeToNBTOptional(t)) {
            return false;
        }
        r.kind = Servants.kindOf(e).name();
        r.name = e.hasCustomName() ? e.getCustomNameTag() : "";
        r.dim = e.dimension;
        r.pos = e.getPosition();
        r.follower = state(e) == FOLLOW;
        r.stash = t;
        get().markDirty();
        ((WorldServer) e.world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, e.posX, e.posY + 1, e.posZ, 40, 0.3, 0.6, 0.3, 0.03);
        e.world.playSound(null, e.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.NEUTRAL, 0.6F, 0.7F);
        e.setDead();
        return true;
    }

    /** Вернуть отозванного: попутчика — ко мне, остальных — туда, откуда отозваны (их мир догрузится). */
    public static boolean unstash(EntityPlayerMP p, UUID id) {
        Rec r = get().of(p.getUniqueID()).get(id);
        if (r == null || r.stash == null) {
            return false;
        }
        boolean toMe = r.follower;
        WorldServer w;
        double x, y, z;
        if (toMe) {
            if (!ServantTravel.allowed(p)) {
                p.sendStatusMessage(Breach.grimoire("chaosshard.retinue.not_here"), true);
                return false;
            }
            w = p.getServerWorld();
            BlockPos at = Servants.safeSpot(w, behind(p), p);
            x = at.getX() + 0.5;
            y = at.getY();
            z = at.getZ() + 0.5;
        } else {
            w = DimensionManager.getWorld(r.dim);
            if (w == null) {
                DimensionManager.initDimension(r.dim);
                w = DimensionManager.getWorld(r.dim);
            }
            if (w == null) {
                return false;
            }
            w.getChunkProvider().loadChunk(r.pos.getX() >> 4, r.pos.getZ() >> 4);
            x = r.pos.getX() + 0.5;
            y = r.pos.getY();
            z = r.pos.getZ() + 0.5;
        }
        Entity e = EntityList.createEntityFromNBT(r.stash, w);
        if (e == null) {
            return false;
        }
        e.setLocationAndAngles(x, y, z, p.rotationYaw, 0);
        e.motionX = e.motionY = e.motionZ = 0;
        e.fallDistance = 0;
        e.timeUntilPortal = e.getPortalCooldown();
        if (!w.spawnEntity(e)) {
            return false;
        }
        r.stash = null;
        r.dim = w.provider.getDimension();
        r.pos = e.getPosition();
        get().markDirty();
        w.spawnParticle(EnumParticleTypes.DRAGON_BREATH, e.posX, e.posY + 1, e.posZ, 40, 0.3, 0.6, 0.3, 0.03);
        w.playSound(null, e.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.NEUTRAL, 0.6F, 1.3F);
        Resync.later(e, 3);
        return true;
    }

    /** Точка в двух с половиной блоках за спиной. */
    private static net.minecraft.util.math.Vec3d behind(EntityPlayer p) {
        double ang = Math.toRadians(p.renderYawOffset + 180);
        return new net.minecraft.util.math.Vec3d(p.posX - Math.sin(ang) * 2.5, p.posY, p.posZ + Math.cos(ang) * 2.5);
    }

    /** Слугу не нашли даже в догруженном чанке — вычеркнуть. */
    private static void lost(EntityPlayerMP p, UUID id, Rec r) {
        get().of(p.getUniqueID()).remove(id);
        get().markDirty();
        p.sendStatusMessage(Breach.grimoire("chaosshard.retinue.lost",
                new TextComponentTranslation("chaosshard.servant.kind." + r.kind.toLowerCase(java.util.Locale.ROOT))), true);
    }

    /** Действие из Гримуара: 0 — позвать, 1 — отозвать, 2 — вернуть, 3 — отозвать всех, 4 — вернуть всех. */
    public static void act(EntityPlayerMP p, int action, UUID id) {
        if (action == 3 || action == 4) {
            for (UUID x : new ArrayList<>(get().of(p.getUniqueID()).keySet())) {
                if (action == 3) {
                    stash(p, x);
                } else {
                    unstash(p, x);
                }
            }
        } else if (id != null) {
            if (action == 0) {
                call(p, id);
            } else if (action == 1) {
                stash(p, id);
            } else if (action == 2) {
                unstash(p, id);
            }
        }
    }
}
