package ru.death.chaosshard.garrot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.anchor.AnchorClusters;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.cutscene.Cutscene;
import ru.death.chaosshard.cutscene.CutsceneManager;
import ru.death.chaosshard.hero.HeroData;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Дуэль с Гарротом (Акт 4). Когда падёт достаточно Опор, Гаррот приходит сам — туда, где я стою
 * на своей земле. Явление — катсцена: барабаны, портал, Гаррот выходит и говорит. Потом — круг дуэли:
 * один на один, слуги в стороне, из круга не выйти. На 8% здоровья Гаррот склоняется:
 * набегов больше нет, Знамёна Мрачной армии превращаются в его солдат для гарнизонов.
 */
public class GarrotDuel {

    public static final int WARN = 60, SCENE = 420, YIELD_LEN = 220;
    public static final int STATE_WARN = 0, STATE_SCENE = 1, STATE_FIGHT = 2, STATE_YIELD = 3;

    public static final class Duel {
        public final UUID player;
        public final int dim;
        public final Vec3d center, dir, side;
        public int state = STATE_WARN, t;
        public UUID lord;

        Duel(UUID player, int dim, Vec3d center, Vec3d dir) {
            this.player = player;
            this.dim = dim;
            this.center = center;
            this.dir = dir;
            this.side = new Vec3d(-dir.z, 0, dir.x);
        }

        Vec3d stop() {
            return center.add(dir.scale(6));
        }
    }

    private static final Map<UUID, Duel> DUELS = new HashMap<>();

    public static Duel of(EntityPlayer p) {
        return DUELS.get(p.getUniqueID());
    }

    /** Гаррот принадлежит идущей дуэли (иначе — сирота после перезапуска, уходит сам). */
    public static boolean owns(EntityGarrot g) {
        for (Duel d : DUELS.values()) {
            if (g.getUniqueID().equals(d.lord)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ когда приходит

    /** Пора ли Гарроту прийти к этому игроку. */
    public static boolean ready(EntityPlayerMP p) {
        World w = p.world;
        if (w.provider.getDimension() != Breach.luchezariyaDim() || DUELS.containsKey(p.getUniqueID()) || CutsceneManager.playing(p)
                || ru.death.chaosshard.challenge.ChallengeManager.of(p) != null || p.isCreative() || p.isSpectator()) {
            return false;
        }
        GarrotData gd = GarrotData.get(w);
        if (gd.submitted || w.getTotalWorldTime() - gd.lastDuel < ChaosConfig.order.duelCooldown) {
            return false;
        }
        if (HeroData.get(w).fallenCount() < ChaosConfig.order.duelAfterPillars) {
            return false;
        }
        return onOwnLand(p) && p.onGround
                && w.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(48), e -> e.getTags().contains(GarrotRaids.TAG_ARMY)).isEmpty();
    }

    static boolean onOwnLand(EntityPlayerMP p) {
        BlockPos near = AnchorData.get(p.world).nearest(p.getPosition(), null);
        if (near == null) {
            return false;
        }
        TileAnchor a = AnchorClusters.at(p.world, near);
        return a != null && p.getUniqueID().equals(a.getOwner()) && a.isCaptured()
                && p.getDistanceSq(near) <= (double) a.radius() * a.radius();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.world.isRemote
                || event.player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        if (DUELS.containsKey(p.getUniqueID())) {
            tick(p);
        } else if (p.ticksExisted % 100 == 37 && ready(p)) {
            start(p);
        }
    }

    // ------------------------------------------------------------------ ход дуэли

    /** Начать: барабаны, через три секунды — явление. */
    public static Duel start(EntityPlayerMP p) {
        Vec3d look = p.getLookVec();
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        dir = dir.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : dir.normalize();
        Duel d = new Duel(p.getUniqueID(), p.dimension, new Vec3d(p.posX, p.posY, p.posZ), dir);
        DUELS.put(p.getUniqueID(), d);
        GarrotData gd = GarrotData.get(p.world);
        gd.lastDuel = p.world.getTotalWorldTime();
        gd.duels++;
        gd.markDirty();
        p.sendMessage(Breach.grimoire("chaosshard.garrot.duel.warn"));
        ChaosShard.LOG.info("Гаррот идёт на дуэль к {} у {}", p.getName(), p.getPosition());
        return d;
    }

    /** Один тик дуэли игрока (вынесено для автотеста). */
    public static void tick(EntityPlayerMP p) {
        Duel d = DUELS.get(p.getUniqueID());
        if (d == null) {
            return;
        }
        WorldServer w = (WorldServer) p.world;
        d.t++;
        switch (d.state) {
            case STATE_WARN:
                if (d.t % 20 == 0) {
                    w.playSound(null, p.getPosition(), SoundEvents.BLOCK_NOTE_BASEDRUM, SoundCategory.HOSTILE, 4.0F, 0.5F);
                }
                if (p.dimension != d.dim || p.isDead) {
                    DUELS.remove(d.player);
                    return;
                }
                if (d.t >= WARN) {
                    d.state = STATE_SCENE;
                    d.t = 0;
                    CutsceneManager.play(p, scene(w, d, p), new Script(d));
                }
                break;
            case STATE_SCENE:
                // сценой правит Script
                break;
            case STATE_FIGHT:
                fight(p, w, d);
                break;
            default:
                yieldTick(p, w, d);
                break;
        }
    }

    static EntityGarrot lord(WorldServer w, Duel d) {
        if (d.lord == null) {
            return null;
        }
        Entity e = w.getEntityFromUuid(d.lord);
        return e instanceof EntityGarrot && e.isEntityAlive() ? (EntityGarrot) e : null;
    }

    static void fight(EntityPlayerMP p, WorldServer w, Duel d) {
        EntityGarrot g = lord(w, d);
        if (g == null) {
            end(w, d, false);
            return;
        }
        if (p.isDead || p.dimension != d.dim) {
            defeat(w, d, g, p.isDead ? "chaosshard.garrot.duel.won_by_him" : "chaosshard.garrot.duel.fled");
            return;
        }
        double r = ChaosConfig.order.duelRadius;
        double dx = p.posX - d.center.x, dz = p.posZ - d.center.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > r + 12) {
            defeat(w, d, g, "chaosshard.garrot.duel.fled");
            return;
        }
        if (dist > r) {
            // круг не выпускает: толчок к центру
            p.motionX = -dx / dist * 0.8;
            p.motionZ = -dz / dist * 0.8;
            p.motionY = 0.3;
            p.velocityChanged = true;
            p.sendStatusMessage(Breach.grimoire("chaosshard.garrot.duel.ring"), true);
        }
        if (g.getAttackTarget() != p) {
            g.setAttackTarget(p);
        }
        // слуги в стороне
        if (d.t % 10 == 0) {
            for (EntityLiving m : w.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(d.center.x, d.center.y, d.center.z,
                    d.center.x, d.center.y, d.center.z).grow(r + 16, 16, r + 16), x -> Servants.isServantOf(x, p.getUniqueID()))) {
                if (m.getAttackTarget() instanceof EntityGarrot) {
                    m.setAttackTarget(null);
                }
            }
            ring(w, d, 24);
        }
    }

    /** Круг дуэли: тёмное пламя по краю. */
    static void ring(WorldServer w, Duel d, int n) {
        double r = ChaosConfig.order.duelRadius;
        for (int i = 0; i < n; i++) {
            double a = w.rand.nextDouble() * Math.PI * 2;
            double x = d.center.x + Math.cos(a) * r, z = d.center.z + Math.sin(a) * r;
            BlockPos top = w.getHeight(new BlockPos(x, 0, z));
            w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, top.getY() + 0.3, z, 1, 0.1, 0.4, 0.1, 0.01);
            w.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, top.getY() + 0.8, z, 2, 0.1, 0.5, 0.1, 0.0);
        }
    }

    /** Гаррот склонился (зовёт EntityGarrot на 8% здоровья). */
    public static void yielded(WorldServer w, EntityGarrot g) {
        g.yielded = true;
        g.kneel(true);
        g.showBar(false);
        g.setAttackTarget(null);
        g.getNavigator().clearPath();
        for (Duel d : DUELS.values()) {
            if (g.getUniqueID().equals(d.lord) && d.state != STATE_YIELD) {
                d.state = STATE_YIELD;
                d.t = 0;
            }
        }
        for (EntityGarrot s : w.getEntitiesWithinAABB(EntityGarrot.class, g.getEntityBoundingBox().grow(40), x -> x.variant() == EntityGarrot.SHADOW)) {
            w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, s.posX, s.posY + 1, s.posZ, 20, 0.3, 0.6, 0.3, 0.02);
            s.setDead();
        }
        w.playSound(null, g.getPosition(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 0.6F, 1.6F);
    }

    static void yieldTick(EntityPlayerMP p, WorldServer w, Duel d) {
        EntityGarrot g = lord(w, d);
        switch (d.t) {
            case 20: line(w, g, "chaosshard.garrot.duel.yield1"); break;
            case 80: line(w, g, "chaosshard.garrot.duel.yield2"); break;
            case 130: p.sendMessage(Breach.grimoire("chaosshard.garrot.duel.yield3")); break;
            case 160: reward(p, w, d, g); break;
            default: break;
        }
        if (d.t >= YIELD_LEN) {
            end(w, d, true);
        }
    }

    static void reward(EntityPlayerMP p, WorldServer w, Duel d, EntityGarrot g) {
        GarrotData gd = GarrotData.get(w);
        gd.submitted = true;
        gd.markDirty();
        GarrotRaids.retreat(w);
        Vec3d at = g != null ? g.getPositionVector() : d.stop();
        EntityItem banners = new EntityItem(w, at.x, at.y + 0.5, at.z, new ItemStack(ModItems.GARROT_BANNER, 6));
        banners.setNoDespawn();
        w.spawnEntity(banners);
        ThreadManager.addPoints(p, ChaosConfig.order.duelReward);
        p.sendMessage(Breach.grimoire("chaosshard.garrot.duel.sworn", ChaosConfig.order.duelReward));
        Milestones.grant(p, "garrot_duel");
        ChaosShard.LOG.info("Гаррот склонился перед {}", p.getName());
    }

    static void defeat(WorldServer w, Duel d, EntityGarrot g, String key) {
        line(w, g, key);
        end(w, d, false);
    }

    /** Конец дуэли: Гаррот уходит в дым, тени исчезают. */
    static void end(WorldServer w, Duel d, boolean sworn) {
        DUELS.remove(d.player);
        EntityGarrot g = lord(w, d);
        if (g != null) {
            w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, g.posX, g.posY + 1, g.posZ, 60, 0.4, 1.0, 0.4, 0.03);
            w.playSound(null, g.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.4F);
            for (EntityGarrot s : w.getEntitiesWithinAABB(EntityGarrot.class, g.getEntityBoundingBox().grow(40), x -> x.variant() == EntityGarrot.SHADOW)) {
                s.setDead();
            }
            g.setDead();
        }
        if (!sworn) {
            GarrotData gd = GarrotData.get(w);
            gd.lastDuel = w.getTotalWorldTime();
            gd.markDirty();
        }
    }

    /** Реплика Гаррота всем рядом. */
    public static void line(WorldServer w, EntityGarrot g, String key) {
        if (g == null) {
            return;
        }
        for (EntityPlayer pl : w.getEntitiesWithinAABB(EntityPlayer.class, g.getEntityBoundingBox().grow(48))) {
            GarrotRaids.say(pl, key);
        }
    }

    /** Пал в дуэли — Гаррот победил (смерть настоящая: это не арена). */
    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || DUELS.isEmpty()) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.getEntityLiving();
        Duel d = DUELS.get(p.getUniqueID());
        if (d == null) {
            return;
        }
        WorldServer w = (WorldServer) p.world;
        if (d.state == STATE_SCENE) {
            CutsceneManager.abort(p);
        }
        defeat(w, d, lord(w, d), "chaosshard.garrot.duel.won_by_him");
    }

    // ------------------------------------------------------------------ явление (катсцена)

    static Vec3d rot(Vec3d v, double deg) {
        double a = Math.toRadians(deg);
        return new Vec3d(v.x * Math.cos(a) - v.z * Math.sin(a), 0, v.x * Math.sin(a) + v.z * Math.cos(a));
    }

    static Vec3d portal(WorldServer w, Duel d) {
        Vec3d p = d.center.add(d.dir.scale(14));
        BlockPos top = ru.death.chaosshard.structure.OrderStructures.ground(w, (int) Math.floor(p.x), (int) Math.floor(p.z));
        return new Vec3d(p.x, top.getY(), p.z);
    }

    /** Сцена явления для этой дуэли (для проверок). */
    public static Cutscene sceneFor(WorldServer w, Duel d, EntityPlayerMP p) {
        return scene(w, d, p);
    }

    static Cutscene scene(WorldServer w, Duel d, EntityPlayerMP p) {
        Vec3d c = d.center, dir = d.dir, side = d.side;
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d eye = c.add(up.scale(p.getEyeHeight()));
        Vec3d pt = portal(w, d);
        Vec3d g = d.stop(), gh = g.add(up.scale(1.75));
        Cutscene s = new Cutscene("garrot_duel", SCENE);
        s.cut(0, c.add(rot(dir, 200).scale(22)).add(up.scale(11)), c.add(up))
                .key(70, c.add(rot(dir, 260).scale(22)).add(up.scale(9)), pt.add(up.scale(1.5)))
                .cut(72, eye.subtract(dir.scale(2.6)).add(side.scale(1.1)).add(up.scale(0.5)), pt.add(up.scale(1.6)))
                .key(150, eye.subtract(dir.scale(2.2)).add(side.scale(1.0)).add(up.scale(0.4)), gh)
                .cut(158, c.add(dir.scale(3.6)).add(side.scale(0.7)).add(up.scale(1.75)), gh)
                .key(215, c.add(dir.scale(4.0)).add(side.scale(0.4)).add(up.scale(1.75)), gh)
                .cut(218, c.add(dir.scale(4.5)).subtract(side.scale(2.5)).add(up.scale(1.0)), gh.add(up.scale(0.3)))
                .key(275, c.add(dir.scale(4.2)).subtract(side.scale(2.2)).add(up.scale(1.1)), gh)
                .cut(278, c.add(dir.scale(2.6)).subtract(side.scale(0.6)).add(up.scale(1.7)), eye)
                .key(318, c.add(dir.scale(2.4)).subtract(side.scale(0.5)).add(up.scale(1.68)), eye)
                .cut(320, c.add(dir.scale(3.8)).subtract(side.scale(0.6)).add(up.scale(1.8)), gh)
                .key(372, c.add(dir.scale(4.1)).subtract(side.scale(0.3)).add(up.scale(1.8)), gh)
                .cut(374, c.add(side.scale(9)).add(dir.scale(3)).add(up.scale(3.5)), c.add(dir.scale(3)).add(up))
                .key(398, c.subtract(dir.scale(7)).add(side.scale(5)).add(up.scale(4)), c.add(dir.scale(3)).add(up))
                .key(SCENE, eye.subtract(dir.scale(0.05)), eye.add(dir.scale(5)));
        int red = 0xC0302A, purple = 0xC080FF;
        String gar = "entity.chaosshard.garrot.name", grim = "chaosshard.cutscene.grimoire";
        s.line(160, 215, gar, "chaosshard.garrot.duel.l1", red)
                .line(218, 275, gar, "chaosshard.garrot.duel.l2", red)
                .line(278, 318, grim, "chaosshard.garrot.duel.l3", purple)
                .line(320, 372, gar, "chaosshard.garrot.duel.l4", red)
                .line(374, 412, grim, "chaosshard.garrot.duel.l5", purple);
        return s;
    }

    /** Что делается в мире, пока идёт катсцена. */
    static final class Script implements CutsceneManager.Script {
        final Duel d;

        Script(Duel d) {
            this.d = d;
        }

        @Override
        public void tick(EntityPlayerMP p, int t) {
            WorldServer w = (WorldServer) p.world;
            Vec3d pt = portal(w, d);
            if (t <= 80 && t % 20 == 0) {
                w.playSound(null, p.getPosition(), SoundEvents.BLOCK_NOTE_BASEDRUM, SoundCategory.HOSTILE, 4.0F, 0.5F);
            }
            if (t >= 60 && t <= 100) {
                w.spawnParticle(EnumParticleTypes.PORTAL, pt.x, pt.y + 1.2, pt.z, 30, 0.6, 1.2, 0.6, 0.6);
                w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pt.x, pt.y + 0.4, pt.z, 6, 0.8, 0.2, 0.8, 0.02);
            }
            if (t == 90) {
                spawnLord(w, d, pt, p);
            }
            EntityGarrot g = lord(w, d);
            if (g != null) {
                g.getLookHelper().setLookPositionWithEntity(p, 30F, 30F);
                Vec3d stop = d.stop();
                if (t == 95) {
                    g.getNavigator().tryMoveToXYZ(stop.x, stop.y, stop.z, 0.9);
                }
                if (t == 152 && g.getDistanceSq(stop.x, g.posY, stop.z) > 2.0) {
                    g.setPositionAndUpdate(stop.x, ru.death.chaosshard.structure.OrderStructures.ground(w, (int) Math.floor(stop.x), (int) Math.floor(stop.z)).getY(), stop.z);
                }
            }
            if (t >= 376 && t % 4 == 0) {
                ring(w, d, 40);
            }
        }

        @Override
        public void end(EntityPlayerMP p, boolean skipped, boolean aborted) {
            WorldServer w = (WorldServer) p.world;
            if (aborted) {
                Duel dd = DUELS.get(d.player);
                if (dd != null) {
                    end(w, dd, false);
                }
                return;
            }
            EntityGarrot g = lord(w, d);
            Vec3d stop = d.stop();
            if (g == null) {
                g = spawnLord(w, d, new Vec3d(stop.x, ru.death.chaosshard.structure.OrderStructures.ground(w, (int) Math.floor(stop.x),
                        (int) Math.floor(stop.z)).getY(), stop.z), p);
            }
            begin(w, d, g, p);
        }

        private static void end(WorldServer w, Duel d, boolean sworn) {
            GarrotDuel.end(w, d, sworn);
        }
    }

    static EntityGarrot spawnLord(WorldServer w, Duel d, Vec3d at, EntityPlayerMP p) {
        EntityGarrot g = new EntityGarrot(w);
        g.setup(EntityGarrot.LORD);
        g.scripted = true;
        g.duelist = p.getUniqueID();
        g.showBar(false);
        g.setLocationAndAngles(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(d.dir.x, -d.dir.z)), 0);
        w.spawnEntity(g);
        d.lord = g.getUniqueID();
        w.addWeatherEffect(new EntityLightningBolt(w, at.x, at.y, at.z, true));
        w.playSound(null, new BlockPos(at), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.5F, 1.2F);
        w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, at.x, at.y + 1, at.z, 80, 0.5, 1.0, 0.5, 0.05);
        return g;
    }

    /** Катсцена кончилась — дуэль. */
    static void begin(WorldServer w, Duel d, EntityGarrot g, EntityPlayerMP p) {
        g.scripted = false;
        g.showBar(true);
        g.setHomePosAndDistance(new BlockPos(d.center), ChaosConfig.order.duelRadius + 2);
        g.setAttackTarget(p);
        d.state = STATE_FIGHT;
        d.t = 0;
        p.sendMessage(Breach.grimoire("chaosshard.garrot.duel.rules"));
        Milestones.grant(p, "garrot_duel_start");
    }

    // ------------------------------------------------------------------ вассалы: Знамя на Якорь

    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickBlock event) {
        World w = event.getWorld();
        EntityPlayer p = event.getEntityPlayer();
        ItemStack held = p.getHeldItem(event.getHand());
        if (w.isRemote || held.getItem() != ModItems.GARROT_BANNER || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        if (w.getBlockState(pos).getBlock() instanceof ru.death.chaosshard.anchor.BlockAnchorPart) {
            pos = ru.death.chaosshard.anchor.BlockAnchorPart.base(w, pos);
        }
        TileAnchor a = pos == null ? null : AnchorClusters.at(w, pos);
        if (a == null) {
            return;
        }
        event.setCanceled(true);
        if (!GarrotData.get(w).submitted) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.garrot.vassal.not_yet"), true);
            return;
        }
        if (!p.getUniqueID().equals(a.getOwner())) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.garrison.not_yours"), true);
            return;
        }
        int n = vassals((WorldServer) w, a, p, 2);
        if (n == 0) {
            List<TileAnchor> c = AnchorClusters.cluster(w, a);
            p.sendMessage(Breach.grimoire("chaosshard.garrison.full", AnchorClusters.garrison((WorldServer) w, c).size(), AnchorClusters.garrisonLimit(c)));
            return;
        }
        if (!p.isCreative()) {
            held.shrink(1);
        }
        p.sendMessage(Breach.grimoire("chaosshard.garrot.vassal.came", n));
        if (p instanceof EntityPlayerMP) {
            Milestones.grant((EntityPlayerMP) p, "garrot_vassal");
        }
    }

    /** Поставить до n солдат Гаррота в гарнизон Якоря (сколько влезет). */
    public static int vassals(WorldServer w, TileAnchor a, EntityPlayer owner, int n) {
        List<TileAnchor> c = AnchorClusters.cluster(w, a);
        int room = AnchorClusters.garrisonLimit(c) - AnchorClusters.garrison(w, c).size();
        if (owner.isCreative()) {
            room = Math.max(room, n);
        }
        int made = 0;
        for (int i = 0; i < Math.min(n, room); i++) {
            BlockPos at = w.getHeight(a.getPos().add(w.rand.nextInt(7) - 3, 0, w.rand.nextInt(7) - 3));
            EntityGarrot v = new EntityGarrot(w);
            v.setup(EntityGarrot.VASSAL);
            v.getEntityData().setUniqueId(Servants.TAG_OWNER, owner.getUniqueID());
            v.getEntityData().setLong(Servants.TAG_ANCHOR, a.getPos().toLong());
            v.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.rand.nextFloat() * 360, 0);
            v.setHomePosAndDistance(a.getPos(), 16);
            w.spawnEntity(v);
            w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, v.posX, v.posY + 1, v.posZ, 30, 0.3, 0.8, 0.3, 0.02);
            made++;
        }
        return made;
    }

    // ------------------------------------------------------------------ отладка

    /** Сбросить все дуэли (команда). */
    public static void clear(WorldServer w) {
        for (Duel d : DUELS.values().toArray(new Duel[0])) {
            end(w, d, false);
        }
        DUELS.clear();
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        DUELS.clear();
    }
}
