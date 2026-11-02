package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.anchor.AnchorClusters;
import ru.death.chaosshard.anchor.AnchorSpecs;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.garrot.GarrotRaids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Гарнизон Якоря: посты, обход, тревога по всей земле, криперы уводятся подальше от Якоря.
 * <ul>
 * <li>Роль по виду: Хаски обходят землю по кругу (около половины радиуса), Костяные и прочие стоят на постах в 7–12
 * блоках и смотрят наружу, Палачи стерегут колонну в 3–4 блоках.</li>
 * <li>Раз в секунду Якорь осматривает свою землю: на каждого врага идут 1–3 ближайших свободных бойца (Палачи — только
 * если враг подошёл к колонне). Если бьют хозяина на этой земле — на помощь идут двое ближайших.</li>
 * <li>Крипер на земле: ближайший свободный боец подманивает его и уводит прочь от Якоря, а там убивает. Остальные
 * бойцы гарнизона криперов не трогают.</li>
 * <li>Набег: гарнизон всей сети встаёт цепью на стороне, откуда идёт армия, Палачи — у колонны.</li>
 * <li>После боя — на свой пост; без боя дольше 10 секунд на своей земле понемногу лечатся.</li>
 * </ul>
 * Ничего не пишет в сохранение: роли считаются на лету, подманивание и цепь набега живут в памяти.
 */
public final class Garrison {

    public enum Role { PATROL, POST, CORE }

    private static final class Lure {
        final UUID creeper;
        final long start;
        Vec3d safe;

        Lure(UUID creeper, long start) {
            this.creeper = creeper;
            this.start = start;
        }
    }

    private static final class Raid {
        final long anchor;
        final Vec3d spot;
        final long start;

        Raid(long anchor, Vec3d spot, long start) {
            this.anchor = anchor;
            this.spot = spot;
            this.start = start;
        }
    }

    /** Боец → крипер, которого он уводит. */
    private static final Map<UUID, Lure> LURES = new HashMap<>();
    /** Боец → место в цепи на время набега. */
    private static final Map<UUID, Raid> RAIDS = new HashMap<>();
    /** Последний бой бойца (для лечения). */
    private static final Map<UUID, Long> FOUGHT = new HashMap<>();

    static final int RAID_MAX = 6000, LURE_MAX = 1200;

    private Garrison() {
    }

    public static boolean isGarrison(Entity m) {
        return Servants.isServant(m) && m.getEntityData().hasKey(Servants.TAG_ANCHOR) && !Servants.isNest(m)
                && !(m instanceof EntityChaosBeast);
    }

    /** Якорь, у которого боец сейчас несёт службу: свой, а во время набега — тот, на который идёт армия. */
    public static long post(Entity m) {
        Raid r = RAIDS.get(m.getUniqueID());
        return r != null ? r.anchor : m.getEntityData().getLong(Servants.TAG_ANCHOR);
    }

    public static Role role(Entity m) {
        Servants.Kind k = Servants.kindOf(m);
        if (k == Servants.Kind.HUSK) {
            return Role.PATROL;
        }
        if (k == Servants.Kind.EXECUTIONER) {
            return Role.CORE;
        }
        return Role.POST;
    }

    public static boolean isLuring(Entity m) {
        return LURES.containsKey(m.getUniqueID());
    }

    public static boolean inRaid(Entity m) {
        return RAIDS.containsKey(m.getUniqueID());
    }

    /** Радиус «дома» бойца гарнизона: вся земля Якоря и ещё немного. */
    public static int homeRadius(TileAnchor a) {
        return a == null ? 16 : Math.max(16, a.radius() + 8);
    }

    /** На каком удалении от колонны можно взрывать крипера. */
    public static double safeDist(TileAnchor a) {
        return Math.max(24, Math.min(40, a.radius() * 0.7));
    }

    static boolean enemy(Entity e) {
        return e instanceof EntityLiving && e.isEntityAlive() && !Servants.isServant(e)
                && (e instanceof IMob || e.getTags().contains(GarrotRaids.TAG_ARMY));
    }

    /** Цель, которую боец гарнизона сам не выбирает: крипер (его уводят). */
    public static boolean avoids(EntityLiving m, Entity t) {
        return t instanceof EntityCreeper && isGarrison(m);
    }

    /** Бойцы, несущие службу у этого Якоря (загруженные). */
    public static List<EntityLiving> of(TileAnchor a) {
        long key = a.getPos().toLong();
        int r = homeRadius(a) + 24;
        return a.getWorld().getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(a.getPos()).grow(r, 64, r),
                x -> x.isEntityAlive() && isGarrison(x) && post(x) == key);
    }

    static void fought(Entity m) {
        FOUGHT.put(m.getUniqueID(), m.world.getTotalWorldTime());
    }

    /** Давно ли боец дрался (в тиках). */
    static long sinceFight(Entity m) {
        Long t = FOUGHT.get(m.getUniqueID());
        return t == null ? Long.MAX_VALUE : m.world.getTotalWorldTime() - t;
    }

    // ------------------------------------------------------------------ места

    /** Место бойца в покое (для обхода — текущая точка маршрута). */
    static Vec3d spot(EntityLiving m, TileAnchor a, int patrolStep) {
        Raid raid = RAIDS.get(m.getUniqueID());
        if (raid != null) {
            return raid.spot;
        }
        BlockPos c = a.getPos();
        Role role = role(m);
        List<EntityLiving> same = new ArrayList<>();
        for (EntityLiving x : of(a)) {
            if (role(x) == role && !inRaid(x)) {
                same.add(x);
            }
        }
        same.sort(Comparator.comparingInt(Entity::getEntityId));
        int i = Math.max(0, same.indexOf(m));
        int n = Math.max(1, same.size());
        double r, ang;
        switch (role) {
            case CORE:
                r = 3.5;
                ang = i * 2 * Math.PI / n;
                break;
            case PATROL:
                r = Math.max(8, Math.min(30, a.radius() * 0.5));
                // 6 точек на круге; каждый патрульный начинает со своей
                ang = ((i * 6 / n) + patrolStep) % 6 * Math.PI / 3;
                break;
            default:
                r = Math.max(7, Math.min(12, a.radius() * 0.3));
                ang = i * 2 * Math.PI / Math.max(4, n) + Math.PI / 4;
                break;
        }
        return ground(a.getWorld(), c.getX() + 0.5 + Math.cos(ang) * r, c.getY(), c.getZ() + 0.5 + Math.sin(ang) * r);
    }

    private static Vec3d ground(World w, double x, double y, double z) {
        BlockPos p = new BlockPos(x, y, z);
        if (w.isBlockLoaded(p)) {
            BlockPos top = w.getHeight(p);
            // не на кроне, не на крыше и не на дне обрыва: если поверхность сильно выше или ниже колонны — высота колонны
            if (top.getY() <= y + 6 && top.getY() >= y - 8) {
                p = top;
            }
        }
        return new Vec3d(x, p.getY(), z);
    }

    /** Идти к точке; если путь целиком не находится (далеко), шагнуть к ней на 10 блоков. */
    static void go(EntityCreature m, double x, double y, double z, double speed) {
        if (m.getNavigator().tryMoveToXYZ(x, y, z, speed)) {
            return;
        }
        double dx = x - m.posX, dz = z - m.posZ, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) {
            return;
        }
        Vec3d mid = ground(m.world, m.posX + dx / len * Math.min(10, len), m.posY, m.posZ + dz / len * Math.min(10, len));
        m.getNavigator().tryMoveToXYZ(mid.x, mid.y, mid.z, speed);
    }

    // ------------------------------------------------------------------ разум бойца на службе

    /**
     * Служба: идти на свой пост, по маршруту обхода, в цепь набега, к далёкой цели тревоги или уводить крипера.
     * Ближний бой ведёт обычный EntityAIAttackMelee (он выше по приоритету и перебивает службу, как только дойдёт).
     */
    static class Duty extends EntityAIBase {
        private final EntityCreature m;
        private Vec3d spot;
        private int repath, wait, step;

        Duty(EntityCreature m) {
            this.m = m;
            setMutexBits(1);
            step = m.getRNG().nextInt(6);
        }

        private TileAnchor anchor() {
            if (!isGarrison(m)) {
                return null;
            }
            EntityLivingBase t = m.getAttackTarget();
            if (t != null && !isLuring(m) && m.getDistanceSq(t) < 10 * 10) {
                return null;   // цель рядом — бой ведёт ближний бой
            }
            return AnchorClusters.at(m.world, BlockPos.fromLong(post(m)));
        }

        @Override
        public boolean shouldExecute() {
            return anchor() != null;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return anchor() != null;
        }

        @Override
        public void startExecuting() {
            repath = 0;
            spot = null;
        }

        @Override
        public void resetTask() {
            m.getNavigator().clearPath();
        }

        @Override
        public void updateTask() {
            TileAnchor a = anchor();
            if (a == null) {
                return;
            }
            Raid raid = RAIDS.get(m.getUniqueID());
            if (raid != null && m.world.getTotalWorldTime() - raid.start > RAID_MAX) {
                endRaid(m);
                raid = null;
            }
            Lure l = LURES.get(m.getUniqueID());
            if (l != null) {
                lead(l);
                return;
            }
            EntityLivingBase t = m.getAttackTarget();
            if (t != null) {
                // цель тревоги далеко: идти к ней (путь ищется не дальше обзора — шагаем по пути)
                if (--repath <= 0) {
                    repath = 10;
                    if (!m.getNavigator().tryMoveToEntityLiving(t, 1.2)) {
                        go(m, t.posX, t.posY, t.posZ, 1.2);
                    }
                }
                return;
            }
            if (spot == null || --repath <= 0) {
                repath = 20;
                spot = spot(m, a, step);
                if (raid == null && role(m) == Role.PATROL && m.getDistanceSq(spot.x, spot.y, spot.z) < 2.0 * 2.0) {
                    if (--wait <= 0) {
                        wait = 3 + m.getRNG().nextInt(4);   // постоять 3–6 секунд и дальше по кругу
                        step = (step + 1) % 6;
                        spot = spot(m, a, step);
                    }
                }
                if (m.getDistanceSq(spot.x, spot.y, spot.z) > 1.5 * 1.5) {
                    go(m, spot.x, spot.y, spot.z, role(m) == Role.PATROL && raid == null ? 0.8 : 1.0);
                }
            }
            if (m.getNavigator().noPath() && (role(m) != Role.PATROL || raid != null)) {
                // на посту — смотреть наружу, от колонны
                BlockPos c = a.getPos();
                double dx = m.posX - (c.getX() + 0.5), dz = m.posZ - (c.getZ() + 0.5);
                m.getLookHelper().setLookPosition(m.posX + dx * 4, m.posY + m.getEyeHeight(), m.posZ + dz * 4, 10.0F, 40.0F);
            }
        }

        /** Уводим крипера: к безопасной точке, не отрываясь от него и не подпуская вплотную. */
        private void lead(Lure l) {
            Entity cr = ((WorldServer) m.world).getEntityFromUuid(l.creeper);
            if (l.safe == null || cr == null) {
                return;
            }
            double d = m.getDistance(cr);
            if (d > 9) {
                // крипер отстал — подождать, глядя на него
                if (!m.getNavigator().noPath()) {
                    m.getNavigator().clearPath();
                }
                m.getLookHelper().setLookPositionWithEntity(cr, 30.0F, 30.0F);
                repath = 0;
                return;
            }
            if (--repath <= 0 || m.getNavigator().noPath()) {
                repath = 10;
                go(m, l.safe.x, l.safe.y, l.safe.z, d < 4 ? 1.4 : 1.1);
            }
        }
    }

    // ------------------------------------------------------------------ Якорь распоряжается гарнизоном (раз в секунду)

    public static void tick(TileAnchor a) {
        World w = a.getWorld();
        if (!(w instanceof WorldServer) || a.getOwner() == null) {
            return;
        }
        long key = a.getPos().toLong();
        long now = w.getTotalWorldTime();
        // набег кончился: армии у этого Якоря больше нет
        if (RAIDS.values().stream().anyMatch(r -> r.anchor == key)) {
            int r = homeRadius(a) + 32;
            boolean army = !w.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(a.getPos()).grow(r, 48, r),
                    x -> x.isEntityAlive() && x.getTags().contains(GarrotRaids.TAG_ARMY)).isEmpty();
            if (!army) {
                RAIDS.entrySet().removeIf(en -> en.getValue().anchor == key && now - en.getValue().start > 400);
            }
        }
        List<EntityLiving> crew = of(a);
        if (crew.isEmpty()) {
            return;
        }
        BlockPos c = a.getPos();
        int home = homeRadius(a);
        for (EntityLiving f : crew) {
            if (f instanceof EntityCreature && !inRaid(f) && (((EntityCreature) f).getMaximumHomeDistance() != home
                    || !((EntityCreature) f).getHomePosition().equals(c))) {
                ((EntityCreature) f).setHomePosAndDistance(c, home);
            }
            if (f.getAttackTarget() != null && !isLuring(f)) {
                fought(f);
            }
        }
        List<EntityLiving> threats = new ArrayList<>();
        List<EntityCreeper> creepers = new ArrayList<>();
        for (EntityLiving e : w.getEntitiesWithinAABB(EntityLiving.class, AnchorSpecs.land(a), x -> enemy(x) && AnchorSpecs.inLand(a, x))) {
            if (e instanceof EntityCreeper) {
                creepers.add((EntityCreeper) e);
            } else {
                threats.add(e);
            }
        }
        // хозяина бьют на этой земле
        EntityPlayer o = ru.death.chaosshard.debug.TestHooks.player(w.getMinecraftServer(), a.getOwner());
        EntityLiving hitter = null;
        if (o != null && o.world == w && AnchorSpecs.inLand(a, o)) {
            EntityLivingBase t = o.getRevengeTarget();
            if (t instanceof EntityLiving && t.isEntityAlive() && !(t instanceof EntityCreeper) && !Servants.isServant(t)
                    && AnchorSpecs.inLand(a, t) && o.ticksExisted - o.getRevengeTimer() < 100) {
                hitter = (EntityLiving) t;
            }
        }
        updateLures(a, crew, now);
        // кто уже кем занят
        Map<Entity, Integer> engaged = new HashMap<>();
        for (EntityLiving f : crew) {
            if (f.getAttackTarget() != null) {
                engaged.merge(f.getAttackTarget(), 1, Integer::sum);
            }
        }
        threats.sort(Comparator.comparingDouble(e -> e.getDistanceSq(c)));
        if (hitter != null) {
            threats.remove(hitter);
            threats.add(0, hitter);
        }
        for (EntityLiving t : threats) {
            boolean isArmy = t.getTags().contains(GarrotRaids.TAG_ARMY);
            boolean atCore = t.getDistanceSq(c) < 8 * 8;
            int want = (t == hitter ? 2 : isArmy ? 3 : t.getMaxHealth() > 30 ? 2 : 1) - engaged.getOrDefault(t, 0);
            if (want <= 0) {
                continue;
            }
            List<EntityLiving> free = new ArrayList<>();
            for (EntityLiving f : crew) {
                if (f.getAttackTarget() == null && !isLuring(f) && (role(f) != Role.CORE || atCore)) {
                    free.add(f);
                }
            }
            free.sort(Comparator.comparingDouble(f -> f.getDistanceSq(t)));
            for (int i = 0; i < Math.min(want, free.size()); i++) {
                free.get(i).setAttackTarget(t);
                fought(free.get(i));
                engaged.merge(t, 1, Integer::sum);
            }
        }
        // криперы: на каждого — один, кто уведёт
        for (EntityCreeper cr : creepers) {
            boolean taken = false;
            for (Lure l : LURES.values()) {
                taken |= l.creeper.equals(cr.getUniqueID());
            }
            for (EntityLiving f : crew) {
                taken |= f.getAttackTarget() == cr;
            }
            if (taken) {
                continue;
            }
            EntityLiving best = null;
            double bd = Double.MAX_VALUE;
            for (EntityLiving f : crew) {
                if (f.getAttackTarget() == null && !isLuring(f) && role(f) != Role.CORE) {
                    double d = f.getDistanceSq(cr);
                    if (d < bd) {
                        bd = d;
                        best = f;
                    }
                }
            }
            if (best != null) {
                LURES.put(best.getUniqueID(), new Lure(cr.getUniqueID(), now));
            }
        }
        updateLures(a, crew, now);
        // у колонны крипера не бить (если кто-то взялся сам — отменить)
        double near = safeDist(a) - 8;
        for (EntityLiving f : crew) {
            if (f.getAttackTarget() instanceof EntityCreeper && !isLuring(f) && f.getAttackTarget().getDistanceSq(c) < near * near) {
                f.setAttackTarget(null);
            }
        }
    }

    private static void updateLures(TileAnchor a, List<EntityLiving> crew, long now) {
        WorldServer w = (WorldServer) a.getWorld();
        BlockPos c = a.getPos();
        double safe = safeDist(a);
        for (EntityLiving f : crew) {
            Lure l = LURES.get(f.getUniqueID());
            if (l == null) {
                continue;
            }
            Entity e = w.getEntityFromUuid(l.creeper);
            if (!(e instanceof EntityCreeper) || !e.isEntityAlive() || !f.isEntityAlive()) {
                LURES.remove(f.getUniqueID());
                continue;
            }
            EntityCreeper cr = (EntityCreeper) e;
            if (l.safe == null) {
                // прочь и от колонны, и от крипера: боец не бежит мимо крипера, а уводит его за собой
                double ax = f.posX - (c.getX() + 0.5), az = f.posZ - (c.getZ() + 0.5);
                double bx = f.posX - cr.posX, bz = f.posZ - cr.posZ;
                double la = Math.sqrt(ax * ax + az * az), lb = Math.sqrt(bx * bx + bz * bz);
                double dx = (la > 0.5 ? ax / la : 0) + (lb > 0.5 ? bx / lb : 0), dz = (la > 0.5 ? az / la : 0) + (lb > 0.5 ? bz / lb : 0);
                if (dx * dx + dz * dz < 0.04) {
                    dx = cr.posX - (c.getX() + 0.5);
                    dz = cr.posZ - (c.getZ() + 0.5);
                }
                if (dx * dx + dz * dz < 0.04) {
                    dx = 1;
                    dz = 0;
                }
                double len = Math.sqrt(dx * dx + dz * dz);
                l.safe = ground(w, c.getX() + 0.5 + dx / len * (safe + 4), c.getY(), c.getZ() + 0.5 + dz / len * (safe + 4));
            }
            double fd = Math.sqrt(f.getDistanceSq(c)), cd = Math.sqrt(cr.getDistanceSq(c));
            boolean arrived = f.getDistanceSq(l.safe.x, l.safe.y, l.safe.z) < 3 * 3 || fd >= safe + 2;
            boolean far = cd >= safe - 6;
            if ((arrived && far) || now - l.start > LURE_MAX) {
                // увели — теперь можно
                LURES.remove(f.getUniqueID());
                f.setAttackTarget(cr);
                fought(f);
                w.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, f.posX, f.posY + f.height + 0.3, f.posZ, 2, 0.2, 0.1, 0.2, 0.0);
            } else {
                cr.setAttackTarget(f);    // крипер идёт за бойцом
                if (f.getAttackTarget() == cr) {
                    f.setAttackTarget(null);
                }
            }
        }
    }

    // ------------------------------------------------------------------ набег

    /**
     * Армия идёт с направления angle (0 — восток, π/2 — юг): гарнизон сети встаёт цепью в 10 блоках от Якоря на этой
     * стороне, Палачи — у колонны. Кто далеко — переносится, кто рядом — идёт сам.
     */
    public static void deploy(WorldServer w, TileAnchor target, double angle, List<EntityLiving> garrison) {
        BlockPos c = target.getPos();
        long key = c.toLong(), now = w.getTotalWorldTime();
        double fx = Math.cos(angle), fz = Math.sin(angle);
        double px = -fz, pz = fx;
        List<EntityLiving> line = new ArrayList<>();
        int core = 0;
        for (EntityLiving g : garrison) {
            if (!isGarrison(g)) {
                continue;
            }
            LURES.remove(g.getUniqueID());
            if (role(g) == Role.CORE) {
                double a = core++ * 1.7;
                send(w, target, g, ground(w, c.getX() + 0.5 + Math.cos(a) * 3, c.getY(), c.getZ() + 0.5 + Math.sin(a) * 3), key, now);
            } else {
                line.add(g);
            }
        }
        line.sort(Comparator.comparingInt(Entity::getEntityId));
        for (int i = 0; i < line.size(); i++) {
            double off = (i - (line.size() - 1) / 2.0) * 2.2;
            send(w, target, line.get(i), ground(w, c.getX() + 0.5 + fx * 10 + px * off, c.getY(), c.getZ() + 0.5 + fz * 10 + pz * off), key, now);
        }
    }

    private static void send(WorldServer w, TileAnchor target, EntityLiving g, Vec3d spot, long key, long now) {
        RAIDS.put(g.getUniqueID(), new Raid(key, spot, now));
        if (g instanceof EntityCreature) {
            ((EntityCreature) g).setHomePosAndDistance(target.getPos(), homeRadius(target));
        }
        if (g.getDistanceSq(spot.x, spot.y, spot.z) < 16 * 16) {
            return;
        }
        w.spawnParticle(EnumParticleTypes.PORTAL, g.posX, g.posY + 1, g.posZ, 20, 0.3, 0.5, 0.3, 0.5);
        g.getNavigator().clearPath();
        g.setPositionAndUpdate(spot.x, spot.y, spot.z);
        w.spawnParticle(EnumParticleTypes.SPELL_WITCH, g.posX, g.posY + 1, g.posZ, 20, 0.3, 0.5, 0.3, 0.05);
    }

    static void endRaid(Entity m) {
        RAIDS.remove(m.getUniqueID());
    }

    public static void clearState() {
        LURES.clear();
        RAIDS.clear();
        FOUGHT.clear();
    }

    /** Для тестов: кого уводит боец. */
    public static UUID luring(Entity m) {
        Lure l = LURES.get(m.getUniqueID());
        return l == null ? null : l.creeper;
    }

    /** Для тестов: место в цепи набега. */
    public static Vec3d raidSpot(Entity m) {
        Raid r = RAIDS.get(m.getUniqueID());
        return r == null ? null : r.spot;
    }
}
