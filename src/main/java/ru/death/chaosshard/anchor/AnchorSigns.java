package ru.death.chaosshard.anchor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.anchor.ItemAnchorSeal.Type;
import ru.death.chaosshard.servant.Servants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Знаки Якоря: Шипы, Искажение, Кровавый дождь, Шёпот — и их сочетания.
 * Силовые печати того же Якоря усиливают знаки числами: Ярость — сила, Охват — дальность, Родник — частота,
 * Кровь — длительность, Нить — опыт Якорю за срабатывание.
 */
public final class AnchorSigns {

    public static final String TAG_MAD = "chaosshard_mad";
    private static final String TAG_PROJ = "chaosshard_signed";
    /** Опыт за срабатывания знаков в эту минуту: Якорь → [минута, сколько]. */
    private static final Map<Long, long[]> SIGN_XP = new HashMap<>();

    /** Числа знаков у Якоря. */
    public static final class Mods {
        public double power, reach, freq, dur, thread;
        public int range;

        public static Mods of(TileAnchor a) {
            Mods m = new Mods();
            m.power = 1 + 0.2 * a.power(Type.FURY);
            m.reach = a.power(Type.REACH);
            m.freq = 1 + 0.2 * a.power(Type.WELL);
            m.dur = 1 + 0.25 * a.power(Type.BLOOD);
            m.thread = a.power(Type.HARVEST);
            m.range = a.radius() + (int) Math.round(4 * m.reach);
            return m;
        }
    }

    private static int iv(int base, double freq) {
        return Math.max(5, (int) Math.round(base / freq));
    }

    private static boolean due(long wt, int ph, int interval) {
        return wt % interval == ph % interval;
    }

    public static void tick(TileAnchor a) {
        World w = a.getWorld();
        if (!(w instanceof WorldServer) || !a.isCaptured()) {
            return;
        }
        double th = a.power(Type.THORNS), di = a.power(Type.DISTORT), ra = a.power(Type.RAIN), wh = a.power(Type.WHISPER);
        if (th + di + ra + wh <= 0) {
            return;
        }
        long wt = w.getTotalWorldTime();
        int ph = (int) (a.getPos().toLong() & 63);
        Mods m = Mods.of(a);
        boolean rain = ra > 0 && raining(a, wt, m);
        if (di > 0 && due(wt, ph, 5)) {
            projectiles(a, m, di, th > 0, rain);
        }
        if (di > 0 && due(wt, ph, 20)) {
            for (EntityLivingBase e : ours(a, m)) {
                e.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 60, 0, true, false));
            }
        }
        if (th > 0 && due(wt, ph, iv(40, m.freq))) {
            thorns(a, m, th, rain, wh);
        }
        if (rain && due(wt, ph, 20)) {
            rain(a, m, ra, di > 0);
        }
        if (wh > 0 && due(wt, ph, iv(60, m.freq))) {
            whisper(a, m, wh);
        }
        if (wh > 0 && di > 0 && due(wt, ph, 40)) {
            shift(a, m, Math.min(wh, di));
        }
    }

    // ------------------------------------------------------------------ кто где

    private static AxisAlignedBB box(TileAnchor a, int range) {
        BlockPos p = a.getPos();
        return new AxisAlignedBB(p.getX() - range, p.getY() - 32, p.getZ() - range, p.getX() + range + 1, p.getY() + 64, p.getZ() + range + 1);
    }

    private static boolean inRange(TileAnchor a, Entity e, int range) {
        double dx = e.posX - a.getPos().getX() - 0.5, dz = e.posZ - a.getPos().getZ() - 0.5;
        return dx * dx + dz * dz <= (double) range * range;
    }

    public static List<EntityLivingBase> enemies(TileAnchor a, Mods m) {
        UUID o = a.getOwner();
        return a.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, box(a, m.range),
                x -> inRange(a, x, m.range) && AnchorSpecs.enemy(x, o));
    }

    public static List<EntityLivingBase> ours(TileAnchor a, Mods m) {
        UUID o = a.getOwner();
        return a.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, box(a, m.range),
                x -> o != null && x.isEntityAlive() && inRange(a, x, m.range) && (o.equals(x.getUniqueID()) || Servants.isServantOf(x, o)));
    }

    /** Нить: срабатывание знака даёт Якорю 1 опыт (до 30 в минуту). */
    private static void signXp(TileAnchor a, Mods m) {
        if (m.thread <= 0) {
            return;
        }
        long minute = a.getWorld().getTotalWorldTime() / 1200;
        long[] c = SIGN_XP.computeIfAbsent(a.getPos().toLong(), k -> new long[2]);
        if (c[0] != minute) {
            c[0] = minute;
            c[1] = 0;
        }
        if (c[1] < 30) {
            c[1]++;
            a.addXp(1, false);
        }
    }

    // ------------------------------------------------------------------ Шипы

    public static int thorns(TileAnchor a, Mods m, double th, boolean rain, double wh) {
        int hit = 0;
        for (EntityLivingBase e : enemies(a, m)) {
            e.attackEntityFrom(DamageSource.MAGIC, (float) (2 * m.power * th));
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, (int) (60 * m.dur), 0));
            if (a.power(Type.RAIN) > 0) {
                // Кровотечение: под дождём вдвое
                e.addPotionEffect(new PotionEffect(MobEffects.WITHER, (int) (60 * m.dur), rain ? 1 : 0));
            }
            if (wh > 0 && a.getWorld().rand.nextDouble() < 0.2 * Math.min(th, wh)) {
                madden(e, m);   // Безумная боль
            }
            ((WorldServer) a.getWorld()).spawnParticle(EnumParticleTypes.CRIT, e.posX, e.posY + e.height * 0.5, e.posZ, 6, 0.3, 0.3, 0.3, 0.1);
            hit++;
        }
        if (hit > 0) {
            signXp(a, m);
        }
        return hit;
    }

    // ------------------------------------------------------------------ Искажение

    private static Entity shooter(Entity p) {
        if (p instanceof EntityArrow) {
            return ((EntityArrow) p).shootingEntity;
        }
        if (p instanceof EntityThrowable) {
            return ((EntityThrowable) p).getThrower();
        }
        if (p instanceof EntityFireball) {
            return ((EntityFireball) p).shootingEntity;
        }
        return null;
    }

    public static int projectiles(TileAnchor a, Mods m, double di, boolean ricochet, boolean rain) {
        UUID o = a.getOwner();
        int n = 0;
        for (Entity p : a.getWorld().getEntitiesWithinAABB(Entity.class, box(a, m.range),
                x -> x instanceof IProjectile || x instanceof EntityFireball)) {
            if (p.getEntityData().getBoolean(TAG_PROJ) || !inRange(a, p, m.range)) {
                continue;
            }
            Entity s = shooter(p);
            if (s == null || !AnchorSpecs.enemy(s, o)) {
                continue;
            }
            p.getEntityData().setBoolean(TAG_PROJ, true);
            if (ricochet) {
                // Рикошет: обратно в стрелка
                p.motionX = -p.motionX * 0.9;
                p.motionY = -p.motionY * 0.9 + 0.05;
                p.motionZ = -p.motionZ * 0.9;
                if (p instanceof EntityArrow) {
                    ((EntityArrow) p).shootingEntity = null;
                }
            } else if (rain) {
                // Вязкая кровь: снаряд падает
                p.motionX *= 0.15;
                p.motionZ *= 0.15;
            } else {
                double k = 0.35 * di * m.power;
                p.motionX += (a.getWorld().rand.nextDouble() - 0.5) * 2 * k;
                p.motionZ += (a.getWorld().rand.nextDouble() - 0.5) * 2 * k;
                p.motionY += (a.getWorld().rand.nextDouble() - 0.5) * k;
            }
            p.velocityChanged = true;
            ((WorldServer) a.getWorld()).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY, p.posZ, 8, 0.2, 0.2, 0.2, 0.3);
            n++;
        }
        if (n > 0) {
            signXp(a, m);
        }
        return n;
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !AnchorNet.isNetWorld(e.world)) {
            return;
        }
        TileAnchor a = TileAnchor.covering(e.world, e.getPosition());
        if (a == null || a.getOwner() == null || !a.isCaptured()) {
            return;
        }
        double di = a.power(Type.DISTORT);
        if (di > 0 && (a.getOwner().equals(e.getUniqueID()) || Servants.isServantOf(e, a.getOwner()))) {
            event.setDamageMultiplier((float) Math.max(0, event.getDamageMultiplier() * (1 - di)));
        }
    }

    // ------------------------------------------------------------------ Кровавый дождь

    /** Дождь идёт: раз в 3 минуты (Родник — чаще) на 30 секунд (Кровь — дольше). */
    public static boolean raining(TileAnchor a, long wt, Mods m) {
        int period = iv(3600, m.freq);
        int active = (int) Math.min(period - 20, 600 * m.dur);
        long phase = (wt + (a.getPos().toLong() & 1023)) % period;
        return phase < active;
    }

    public static void rain(TileAnchor a, Mods m, double ra, boolean viscous) {
        WorldServer w = (WorldServer) a.getWorld();
        float amt = (float) (1 * m.power * ra);
        for (EntityLivingBase e : ours(a, m)) {
            e.heal(amt);
            w.spawnParticle(EnumParticleTypes.REDSTONE, e.posX, e.posY + 3, e.posZ, 25, 2.5, 0.5, 2.5, 0.0);
        }
        int hit = 0;
        for (EntityLivingBase e : enemies(a, m)) {
            e.attackEntityFrom(DamageSource.MAGIC, amt);
            if (viscous) {
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 1));
                e.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 40, 1));
            }
            hit++;
        }
        // под кровавым дождём в баке Якоря копится кровь (EvilCraft)
        if (FluidRegistry.isFluidRegistered("evilcraftblood")) {
            a.blood.fillInternal(new FluidStack(FluidRegistry.getFluid("evilcraftblood"), 25), true);
        }
        if (hit > 0) {
            signXp(a, m);
        }
    }

    // ------------------------------------------------------------------ Шёпот

    public static boolean mad(Entity e) {
        return e.getEntityData().getLong(TAG_MAD) > e.world.getTotalWorldTime();
    }

    public static void madden(EntityLivingBase e, Mods m) {
        e.getEntityData().setLong(TAG_MAD, e.world.getTotalWorldTime() + (long) (100 * m.dur));
        e.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, (int) (100 * m.dur), 0));
        if (e instanceof EntityLiving) {
            EntityLivingBase other = null;
            double bd = 144;
            for (EntityLivingBase x : e.world.getEntitiesWithinAABB(EntityLivingBase.class, e.getEntityBoundingBox().grow(12),
                    x -> x != e && x.isEntityAlive() && !(x instanceof EntityPlayer) && !Servants.isServant(x))) {
                double d = x.getDistanceSq(e);
                if (d < bd) {
                    bd = d;
                    other = x;
                }
            }
            ((EntityLiving) e).setAttackTarget(other);
            if (other instanceof EntityLiving) {
                ((EntityLiving) other).setAttackTarget(e);
            }
        }
        ((WorldServer) e.world).spawnParticle(EnumParticleTypes.SPELL_MOB, e.posX, e.posY + e.height, e.posZ, 12, 0.3, 0.3, 0.3, 0.0);
    }

    public static int whisper(TileAnchor a, Mods m, double wh) {
        int n = 0;
        for (EntityLivingBase e : enemies(a, m)) {
            if (!mad(e) && a.getWorld().rand.nextDouble() < 0.25 * m.power * wh) {
                madden(e, m);
                n++;
            }
        }
        if (n > 0) {
            signXp(a, m);
        }
        return n;
    }

    /** Сдвиг: обезумевших перебрасывает на несколько блоков. */
    public static void shift(TileAnchor a, Mods m, double s) {
        java.util.Random r = a.getWorld().rand;
        for (EntityLivingBase e : enemies(a, m)) {
            if (!mad(e) || r.nextDouble() > s) {
                continue;
            }
            double ang = r.nextDouble() * Math.PI * 2, d = 3 + r.nextDouble() * 3;
            BlockPos to = a.getWorld().getHeight(new BlockPos(e.posX + Math.cos(ang) * d, 0, e.posZ + Math.sin(ang) * d));
            ((WorldServer) a.getWorld()).spawnParticle(EnumParticleTypes.PORTAL, e.posX, e.posY + 1, e.posZ, 15, 0.3, 0.5, 0.3, 0.3);
            e.setPositionAndUpdate(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
    }

    /** Кровавая месса: враг, убитый в безумии под дождём и Шёпотом, лечит своих и даёт опыт Якорю. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !mad(e) || !AnchorNet.isNetWorld(e.world)) {
            return;
        }
        TileAnchor a = TileAnchor.covering(e.world, e.getPosition());
        if (a == null || !a.isCaptured() || a.power(Type.RAIN) <= 0 || a.power(Type.WHISPER) <= 0) {
            return;
        }
        Mods m = Mods.of(a);
        for (EntityLivingBase x : ours(a, m)) {
            x.heal(4.0F);
        }
        a.addXp(5, false);
        ((WorldServer) e.world).spawnParticle(EnumParticleTypes.HEART, e.posX, e.posY + 1, e.posZ, 6, 0.4, 0.4, 0.4, 0.0);
    }

    private AnchorSigns() {
    }

    public static AnchorSigns events() {
        return new AnchorSigns();
    }
}
