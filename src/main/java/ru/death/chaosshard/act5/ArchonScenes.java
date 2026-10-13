package ru.death.chaosshard.act5;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.cutscene.Cutscene;
import ru.death.chaosshard.cutscene.CutsceneManager;
import ru.death.chaosshard.hero.Pose;

/**
 * Сцены Разлома. Появление Архонта: облёт арены и пилонов → Архонт крупно → Гримуар → в глаза.
 * Финал: Завеса рвётся над ареной, сквозь неё говорит Лорд; потом — домой и титры.
 */
public final class ArchonScenes {

    public static final int INTRO = 260, ENDING = 520;
    static final Vec3d UP = new Vec3d(0, 1, 0);
    static final int PURPLE = 0xC080FF, WHITE = 0xF0F0FF, LORD = 0xFF4060;

    static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : f.normalize();
    }

    public static Cutscene intro(WorldServer w, EntityArchon a, EntityPlayerMP p) {
        Vec3d c = new Vec3d(0.5, RiftDimension.FLOOR + 1, 0.5);
        Vec3d k = a.getPositionVector(), ke = k.add(UP.scale(2.7));
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3d dKP = flat(eye.subtract(k)), side = new Vec3d(-dKP.z, 0, dKP.x);
        Vec3d look = ke.subtract(eye).normalize();
        Cutscene s = new Cutscene("archon", INTRO);
        s.cut(0, c.add(new Vec3d(40, 26, 0)), c)
                .key(40, c.add(new Vec3d(0, 30, 40)), c)
                .key(80, c.add(new Vec3d(-40, 22, 0)), c.add(UP.scale(6)))
                .cut(82, k.add(dKP.scale(5.0)).add(side.scale(1.0)).add(UP.scale(2.2)), ke)
                .key(170, k.add(dKP.scale(3.4)).add(side.scale(0.5)).add(UP.scale(2.6)), ke)
                .cut(172, k.subtract(dKP.scale(2.0)).add(side.scale(1.4)).add(UP.scale(3.0)), eye)
                .key(230, k.subtract(dKP.scale(1.7)).add(side.scale(1.6)).add(UP.scale(2.9)), eye)
                .cut(232, eye.subtract(look.scale(1.4)).add(UP.scale(0.3)), ke)
                .key(INTRO, eye, eye.add(look.scale(5)));
        s.line(86, 168, "entity.chaosshard.archon.name", "chaosshard.archon.intro1", WHITE)
                .line(175, 228, "chaosshard.cutscene.grimoire", "chaosshard.archon.intro2", PURPLE);
        return s;
    }

    public static final class Intro implements CutsceneManager.Script {
        final EntityArchon a;

        public Intro(EntityArchon a) {
            this.a = a;
        }

        @Override
        public void tick(EntityPlayerMP p, int t) {
            if (!a.isEntityAlive()) {
                return;
            }
            WorldServer w = (WorldServer) p.world;
            a.scripted = true;
            a.getLookHelper().setLookPositionWithEntity(p, 30F, 30F);
            if (t < 80 && t % 4 == 0) {
                for (int i = 0; i < 5; i++) {
                    BlockPos py = RiftDimension.pylon(i);
                    w.spawnParticle(EnumParticleTypes.END_ROD, py.getX() + 0.5, py.getY() + 1, py.getZ() + 0.5, 4, 0.1, 1.0, 0.1, 0.05);
                }
            }
            if (t == 86) {
                a.setPose(Pose.RAISE_BOTH, 60);
                w.playSound(null, a.getPosition(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 3.0F, 0.5F);
            }
            if (t > 86 && t < 146 && t % 3 == 0) {
                w.spawnParticle(EnumParticleTypes.END_ROD, a.posX, a.posY + 3.2, a.posZ, 6, 0.6, 0.2, 0.6, 0.03);
            }
        }

        @Override
        public void end(EntityPlayerMP p, boolean skipped, boolean aborted) {
            a.scripted = false;
            a.setPose(Pose.NONE, 0);
        }
    }

    /** Финал: камера над ареной смотрит в небо, где рвётся Завеса. */
    public static Cutscene ending(WorldServer w, EntityPlayerMP p) {
        Vec3d c = new Vec3d(0.5, RiftDimension.FLOOR + 1, 0.5);
        Vec3d sky = c.add(UP.scale(60));
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Cutscene s = new Cutscene("ending", ENDING);
        s.cut(0, c.add(new Vec3d(0, 6, -18)), c.add(UP.scale(4)))
                .key(80, c.add(new Vec3d(0, 10, -24)), c.add(UP.scale(20)))
                .key(200, c.add(new Vec3d(12, 14, -20)), sky)
                .key(360, c.add(new Vec3d(-12, 20, -12)), sky)
                .key(470, c.add(new Vec3d(0, 40, 0)), sky.add(UP.scale(20)))
                .key(ENDING, c.add(new Vec3d(0, 80, 0)), sky.add(new Vec3d(0, 40, 1)));
        s.line(10, 80, "chaosshard.cutscene.grimoire", "chaosshard.ending.1", PURPLE)
                .line(90, 170, "chaosshard.cutscene.grimoire", "chaosshard.ending.2", PURPLE)
                .line(180, 260, "chaosshard.ending.lord", "chaosshard.ending.3", LORD)
                .line(265, 345, "chaosshard.ending.lord", "chaosshard.ending.4", LORD)
                .line(350, 430, "chaosshard.ending.lord", "chaosshard.ending.5", LORD)
                .line(435, 515, "chaosshard.cutscene.grimoire", "chaosshard.ending.6", PURPLE);
        return s;
    }

    public static final class Ending implements CutsceneManager.Script {
        @Override
        public void tick(EntityPlayerMP p, int t) {
            WorldServer w = (WorldServer) p.world;
            if (!RiftDimension.is(w)) {
                return;
            }
            double cy = RiftDimension.FLOOR + 60;
            // Завеса над ареной трескается всё шире
            int n = Math.min(80, 4 + t / 4);
            if (t % 2 == 0) {
                for (int i = 0; i < n; i++) {
                    double a = w.rand.nextDouble() * Math.PI * 2, r = w.rand.nextDouble() * (4 + t / 12.0);
                    w.spawnParticle(EnumParticleTypes.PORTAL, Math.cos(a) * r, cy + (w.rand.nextDouble() - 0.5) * 4, Math.sin(a) * r,
                            1, 0, 0, 0, 0.5);
                }
            }
            if (t > 170 && t % 6 == 0) {
                w.spawnParticle(EnumParticleTypes.DRAGON_BREATH, 0, cy, 0, 60, 6, 3, 6, 0.02);
            }
            if (t == 175 || t == 260 || t == 345 || t == 430) {
                w.addWeatherEffect(new EntityLightningBolt(w, w.rand.nextInt(20) - 10, RiftDimension.FLOOR + 1, w.rand.nextInt(20) - 10, true));
                w.playSound(null, new BlockPos(0, RiftDimension.FLOOR + 20, 0), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 4.0F, 0.4F);
            }
            if (t == 470) {
                w.playSound(null, new BlockPos(0, RiftDimension.FLOOR + 20, 0), SoundEvents.ENTITY_ENDERDRAGON_DEATH, SoundCategory.HOSTILE, 4.0F, 0.6F);
            }
        }

        @Override
        public void end(EntityPlayerMP p, boolean skipped, boolean aborted) {
            if (!aborted) {
                Rift.home(p);
            }
        }
    }

    private ArchonScenes() {
    }
}
