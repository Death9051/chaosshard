package ru.death.chaosshard.hero;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.cutscene.Cutscene;
import ru.death.chaosshard.cutscene.CutsceneManager;
import ru.death.chaosshard.structure.OrderStructureData;
import ru.death.chaosshard.structure.bp.VeilPillar;

/**
 * Катсцены Светочей: появление (первый раз, когда подходишь к Опоре) и падение Опоры.
 * Появление: облёт Опоры с лучом → герой крупно и его слова → Гримуар отвечает → камера в глаза.
 * Падение: камера отъезжает, колонна рушится, Гримуар читает проступившую строку памяти.
 */
public final class HeroScenes {

    public static final int INTRO = 220, FALL = 120;
    static final Vec3d UP = new Vec3d(0, 1, 0);
    static final int GOLD = 0xFFD060, PURPLE = 0xC080FF;

    public static final CutsceneManager.Script NONE = new CutsceneManager.Script() {
        @Override
        public void tick(EntityPlayerMP p, int t) {
        }

        @Override
        public void end(EntityPlayerMP p, boolean skipped, boolean aborted) {
        }
    };

    static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : f.normalize();
    }

    static Vec3d rot(Vec3d v, double deg) {
        double a = Math.toRadians(deg);
        return new Vec3d(v.x * Math.cos(a) - v.z * Math.sin(a), 0, v.x * Math.sin(a) + v.z * Math.cos(a));
    }

    static Vec3d origin(WorldServer w, EntityHero hero) {
        OrderStructureData.Entry e = hero.structureId >= 0 ? OrderStructureData.get(w).byId(hero.structureId) : null;
        if (e != null) {
            return new Vec3d(e.x + 0.5, e.y, e.z + 0.5);
        }
        HeroData.Pillar pl = HeroData.get(w).of(hero.hero());
        return pl.state != HeroData.NONE ? new Vec3d(pl.x + 0.5, pl.y, pl.z + 0.5) : hero.getPositionVector();
    }

    /** Появление героя. */
    public static Cutscene intro(WorldServer w, EntityHero hero, EntityPlayerMP p) {
        Vec3d h = hero.getPositionVector(), he = h.add(UP.scale(2.1));
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3d o = origin(w, hero);
        Vec3d top = o.add(UP.scale(VeilPillar.FLOOR + VeilPillar.COLUMN_H));
        Vec3d dHP = flat(eye.subtract(h)), side = new Vec3d(-dHP.z, 0, dHP.x);
        Vec3d dOP = flat(eye.subtract(o));
        Vec3d look = he.subtract(eye).normalize();
        String id = hero.hero().id();
        Cutscene s = new Cutscene("hero_" + id, INTRO);
        s.cut(0, o.add(rot(dOP, 140).scale(30)).add(UP.scale(32)), top)
                .key(70, o.add(rot(dOP, 200).scale(26)).add(UP.scale(20)), o.add(UP.scale(VeilPillar.FLOOR + 14)))
                .cut(72, h.add(dHP.scale(3.4)).add(side.scale(0.7)).add(UP.scale(1.9)), he)
                .key(140, h.add(dHP.scale(2.7)).add(side.scale(0.35)).add(UP.scale(2.0)), he)
                .cut(142, h.subtract(dHP.scale(1.6)).add(side.scale(1.1)).add(UP.scale(2.4)), eye)
                .key(190, h.subtract(dHP.scale(1.3)).add(side.scale(1.3)).add(UP.scale(2.3)), eye)
                .cut(192, eye.subtract(look.scale(1.4)).add(UP.scale(0.3)), he)
                .key(INTRO, eye, eye.add(look.scale(5)));
        s.line(76, 138, hero.hero().nameKey(), "chaosshard.hero." + id + ".intro1", GOLD)
                .line(145, 200, "chaosshard.cutscene.grimoire", "chaosshard.hero." + id + ".intro2", PURPLE);
        return s;
    }

    /** Что делает герой, пока идёт его сцена. */
    public static final class Intro implements CutsceneManager.Script {
        final EntityHero hero;

        public Intro(EntityHero hero) {
            this.hero = hero;
        }

        @Override
        public void tick(EntityPlayerMP p, int t) {
            if (!hero.isEntityAlive()) {
                return;
            }
            WorldServer w = (WorldServer) p.world;
            hero.scripted = true;
            hero.getNavigator().clearPath();
            hero.getLookHelper().setLookPositionWithEntity(p, 30F, 30F);
            switch (hero.hero()) {
                case KAEL:
                    // поднимает клинок к лучу — вспышка
                    if (t == 96) {
                        w.playSound(null, hero.getPosition(), SoundEvents.ENTITY_ILLAGER_PREPARE_BLINDNESS, SoundCategory.HOSTILE, 2.0F, 1.2F);
                    }
                    if (t > 96 && t < 118) {
                        w.spawnParticle(EnumParticleTypes.END_ROD, hero.posX, hero.posY + 2.8, hero.posZ, 4, 0.1, 0.4, 0.1, 0.03);
                    }
                    if (t == 118) {
                        w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, hero.posX, hero.posY + 2.0, hero.posZ, 160, 0.6, 0.6, 0.6, 0.4);
                        w.playSound(null, hero.getPosition(), SoundEvents.ENTITY_FIREWORK_TWINKLE, SoundCategory.HOSTILE, 3.0F, 1.0F);
                    }
                    break;
                case IRENA:
                    // молится — вокруг загорается святая земля
                    hero.setSneaking(t > 72 && t < 140);
                    if (t > 72 && t < 190 && t % 2 == 0) {
                        double a = t * 0.21;
                        for (int k = 0; k < 3; k++) {
                            double r = 4 + k * 3;
                            w.spawnParticle(EnumParticleTypes.END_ROD, hero.posX + Math.cos(a + k) * r, hero.posY + 0.2, hero.posZ + Math.sin(a + k) * r,
                                    1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                    if (t > 72 && t < 140 && t % 16 == 0) {
                        w.playSound(null, hero.getPosition(), SoundEvents.BLOCK_NOTE_BELL, SoundCategory.HOSTILE, 1.5F, 0.6F + (t - 72) / 100F);
                    }
                    break;
                case VELMOR:
                    // открывает и закрывает свои порталы: кольца искр вокруг, вспышки у печатей
                    if (t > 72 && t < 190) {
                        double a = t * 0.35;
                        w.spawnParticle(EnumParticleTypes.PORTAL, hero.posX + Math.cos(a) * 1.4, hero.posY + 1.0 + Math.sin(t * 0.1) * 0.6,
                                hero.posZ + Math.sin(a) * 1.4, 6, 0.05, 0.05, 0.05, 0.4);
                    }
                    if (t == 110 || t == 150) {
                        w.playSound(null, hero.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.5F, 0.8F);
                        for (BlockPos seal : Pillars.intactSeals(w, Lightbearer.VELMOR)) {
                            w.spawnParticle(EnumParticleTypes.PORTAL, seal.getX() + 0.5, seal.getY() + 1, seal.getZ() + 0.5, 120, 0.5, 1.5, 0.5, 1.0);
                        }
                    }
                    break;
                case LIAN:
                    // начинает песнь — ноты и мелодия
                    if (t > 72 && t < 196) {
                        if (t % 3 == 0) {
                            w.spawnParticle(EnumParticleTypes.NOTE, hero.posX + (w.rand.nextDouble() - 0.5) * 2, hero.posY + 2.4,
                                    hero.posZ + (w.rand.nextDouble() - 0.5) * 2, 0, w.rand.nextDouble(), 0, 0, 1.0);
                        }
                        if (t % 8 == 0) {
                            float[] tune = {0.7F, 0.9F, 1.05F, 0.9F, 1.2F, 1.05F, 0.9F, 0.8F};
                            w.playSound(null, hero.getPosition(), SoundEvents.BLOCK_NOTE_HARP, SoundCategory.HOSTILE, 2.0F, tune[(t / 8) % tune.length]);
                        }
                    }
                    break;
                case ELIS:
                    // трубят — и её королевская стража встаёт рядом искрами
                    if (t == 80 || t == 100) {
                        w.playSound(null, hero.getPosition(), SoundEvents.BLOCK_NOTE_BASS, SoundCategory.HOSTILE, 3.0F, t == 80 ? 0.5F : 0.75F);
                    }
                    if (t > 100 && t < 150 && t % 5 == 0) {
                        for (int k = 0; k < 3; k++) {
                            double a = k * Math.PI * 2 / 3 + t * 0.05;
                            w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, hero.posX + Math.cos(a) * 3, hero.posY + 1, hero.posZ + Math.sin(a) * 3,
                                    4, 0.2, 0.5, 0.2, 0.02);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void end(EntityPlayerMP p, boolean skipped, boolean aborted) {
            hero.scripted = false;
            hero.setSneaking(false);
        }
    }

    /** Падение Опоры: камера отъезжает от колонны, пока она рушится. */
    public static Cutscene fall(WorldServer w, BlockPos origin, EntityPlayerMP p, Lightbearer h) {
        Vec3d o = new Vec3d(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3d look = p.getLookVec();
        Vec3d dOP = flat(eye.subtract(o));
        Vec3d mid = o.add(UP.scale(VeilPillar.FLOOR + 18));
        Cutscene s = new Cutscene("fall_" + h.id(), FALL);
        s.cut(0, o.add(rot(dOP, 35).scale(24)).add(UP.scale(8)), mid)
                .key(90, o.add(rot(dOP, 55).scale(34)).add(UP.scale(14)), o.add(UP.scale(VeilPillar.FLOOR + 8)))
                .cut(92, eye.subtract(look.scale(1.5)).add(UP.scale(0.3)), eye.add(look.scale(5)))
                .key(FALL, eye, eye.add(look.scale(5)));
        s.line(34, 116, "chaosshard.cutscene.grimoire", "chaosshard.hero." + h.id() + ".memory", PURPLE);
        return s;
    }

    private HeroScenes() {
    }
}
