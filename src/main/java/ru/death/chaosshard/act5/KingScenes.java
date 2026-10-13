package ru.death.chaosshard.act5;

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
 * Сцены Короля: появление (камера над столицей → тронный зал → Король встаёт → Гримуар → в глаза)
 * и падение (камера над троном, Гримуар читает строку о Разломе).
 */
public final class KingScenes {

    public static final int INTRO = 240, FALL = 120;
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

    public static Cutscene intro(WorldServer w, EntityKing king, EntityPlayerMP p) {
        CapitalData d = CapitalData.get(w);
        Vec3d c = new Vec3d(d.x + 0.5, d.y, d.z + 0.5);
        Vec3d k = king.getPositionVector(), ke = k.add(UP.scale(2.1));
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3d dKP = flat(eye.subtract(k)), side = new Vec3d(-dKP.z, 0, dKP.x);
        Vec3d look = ke.subtract(eye).normalize();
        Cutscene s = new Cutscene("king", INTRO);
        // над городом: от ворот к куполу
        s.cut(0, c.add(dKP.scale(40)).add(UP.scale(30)), c.add(UP.scale(8)))
                .key(70, c.add(dKP.scale(22)).add(side.scale(10)).add(UP.scale(22)), k.add(UP.scale(6)))
                // тронный зал: Король встаёт
                .cut(72, k.add(dKP.scale(4.0)).add(side.scale(0.8)).add(UP.scale(1.6)), ke)
                .key(150, k.add(dKP.scale(2.8)).add(side.scale(0.4)).add(UP.scale(2.0)), ke)
                // из-за плеча Короля — на меня
                .cut(152, k.subtract(dKP.scale(1.6)).add(side.scale(1.1)).add(UP.scale(2.4)), eye)
                .key(210, k.subtract(dKP.scale(1.3)).add(side.scale(1.3)).add(UP.scale(2.3)), eye)
                .cut(212, eye.subtract(look.scale(1.4)).add(UP.scale(0.3)), ke)
                .key(INTRO, eye, eye.add(look.scale(5)));
        s.line(76, 148, "entity.chaosshard.king.name", "chaosshard.king.intro1", GOLD)
                .line(155, 208, "chaosshard.cutscene.grimoire", "chaosshard.king.intro2", PURPLE);
        return s;
    }

    /** Что делает Король во время своей сцены. */
    public static final class Intro implements CutsceneManager.Script {
        final EntityKing king;

        public Intro(EntityKing king) {
            this.king = king;
        }

        @Override
        public void tick(EntityPlayerMP p, int t) {
            if (!king.isEntityAlive()) {
                return;
            }
            WorldServer w = (WorldServer) p.world;
            king.scripted = true;
            king.getLookHelper().setLookPositionWithEntity(p, 30F, 30F);
            if (t == 1) {
                king.setPose(Pose.KNEEL, 60);
            }
            if (t == 76) {
                king.setPose(Pose.RAISE, 40);
                w.playSound(null, king.getPosition(), SoundEvents.BLOCK_NOTE_BASS, SoundCategory.HOSTILE, 3.0F, 0.5F);
            }
            if (t > 76 && t < 116 && t % 3 == 0) {
                w.spawnParticle(EnumParticleTypes.END_ROD, king.posX, king.posY + 2.8, king.posZ, 3, 0.2, 0.4, 0.2, 0.03);
            }
            if (t == 116) {
                w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, king.posX, king.posY + 2, king.posZ, 120, 0.8, 0.8, 0.8, 0.3);
                w.playSound(null, king.getPosition(), SoundEvents.ENTITY_FIREWORK_TWINKLE, SoundCategory.HOSTILE, 3.0F, 0.8F);
            }
        }

        @Override
        public void end(EntityPlayerMP p, boolean skipped, boolean aborted) {
            king.scripted = false;
            king.setPose(Pose.NONE, 0);
        }
    }

    public static Cutscene fall(WorldServer w, BlockPos at, EntityPlayerMP p) {
        CapitalData d = CapitalData.get(w);
        Vec3d o = new Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        Vec3d rift = d.rift != null ? new Vec3d(d.rift.getX() + 0.5, d.rift.getY() + 1.5, d.rift.getZ() + 0.5) : o.add(UP.scale(2));
        Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3d look = p.getLookVec();
        Vec3d dOE = flat(eye.subtract(o));
        Cutscene s = new Cutscene("king_fall", FALL);
        s.cut(0, o.add(dOE.scale(6)).add(UP.scale(4)), rift)
                .key(90, o.add(dOE.scale(3.5)).add(UP.scale(2.5)), rift)
                .cut(92, eye.subtract(look.scale(1.5)).add(UP.scale(0.3)), eye.add(look.scale(5)))
                .key(FALL, eye, eye.add(look.scale(5)));
        s.line(20, 116, "chaosshard.cutscene.grimoire", "chaosshard.king.memory", PURPLE);
        return s;
    }

    private KingScenes() {
    }
}
