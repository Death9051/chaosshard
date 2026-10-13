package ru.death.chaosshard.hero.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import ru.death.chaosshard.hero.Pose;

/**
 * Модель игрока, которая умеет вставать в позы боссов (руки, ноги, корпус, голова).
 * Позы сменяются плавно: в момент смены запоминается, насколько части тела сейчас отклонены
 * от обычной анимации, и за несколько тиков это отклонение перетекает в отклонение новой позы.
 */
public class PoseModel extends ModelPlayer {

    private static final int N = 18;
    private static final Map<Entity, Blend> BLENDS = new WeakHashMap<>();

    /** Состояние перехода одной сущности (только на клиенте). */
    static final class Blend {
        int pose;
        float start = -1000F, dur = 1F;
        final float[] from = new float[N], last = new float[N];
        float dropFrom, dropLast;
    }

    private final ModelRenderer[] parts;
    private final float[] base = new float[N], target = new float[N];

    public PoseModel() {
        super(0.0F, false);
        parts = new ModelRenderer[] {bipedHead, bipedBody, bipedRightArm, bipedLeftArm, bipedRightLeg, bipedLeftLeg};
    }

    /**
     * Сколько тиков длится переход from → to (20 тиков = 1 с).
     * Удары резкие; телеграфы атак быстрые, чтобы не съедать окно; долгие позы и колени неспешные.
     */
    static float duration(int from, int to) {
        switch (to) {
            case Pose.CHOP: return 3F;
            case Pose.DASH: return 4F;
            case Pose.WINDUP:
            case Pose.CAST:
            case Pose.CAST_DOWN:
            case Pose.RAISE:
            case Pose.STAGGER: return 6F;
            case Pose.KNEEL:
            case Pose.KNEEL_PRAY: return 20F;
            case Pose.NONE: return from == Pose.KNEEL || from == Pose.KNEEL_PRAY ? 20F : 10F;
            default: return 10F;
        }
    }

    static float ease(float x) {
        x = MathHelper.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** Найти или завести переход; при смене позы стартует новый от текущего положения. */
    static Blend blend(Entity e, int pose, float age) {
        Blend b = BLENDS.get(e);
        if (b == null) {
            b = new Blend();
            b.pose = pose;
            BLENDS.put(e, b);
            return b;
        }
        if (b.pose != pose) {
            System.arraycopy(b.last, 0, b.from, 0, N);
            b.dropFrom = b.dropLast;
            b.dur = duration(b.pose, pose);
            b.start = age;
            b.pose = pose;
        }
        return b;
    }

    static int poseOf(Entity e) {
        return e instanceof Pose.Posed ? ((Pose.Posed) e).pose() : Pose.NONE;
    }

    /** Плавное опускание модели на колени (вызывается из preRenderCallback). */
    public static float drop(Entity e, float partialTicks) {
        int pose = poseOf(e);
        Blend b = blend(e, pose, e.ticksExisted + partialTicks);
        float f = ease((e.ticksExisted + partialTicks - b.start) / b.dur);
        b.dropLast = b.dropFrom + (kneelDrop(pose) - b.dropFrom) * f;
        return b.dropLast;
    }

    /**
     * Броня повторяет позу: модель брони сама считает обычную анимацию, а потом получает то же отклонение,
     * что основная модель посчитала в этом кадре (основная рисуется раньше слоёв).
     */
    public static void applyTo(net.minecraft.client.model.ModelBiped m, Entity e) {
        Blend b = BLENDS.get(e);
        if (b == null) {
            return;
        }
        ModelRenderer[] ps = {m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg, m.bipedLeftLeg};
        for (int i = 0; i < ps.length; i++) {
            ps[i].rotateAngleX += b.last[i * 3];
            ps[i].rotateAngleY += b.last[i * 3 + 1];
            ps[i].rotateAngleZ += b.last[i * 3 + 2];
        }
        net.minecraft.client.model.ModelBase.copyModelAngles(m.bipedHead, m.bipedHeadwear);
    }

    /** Модель брони, которая встаёт в позу вместе с телом. */
    public static class Armor extends net.minecraft.client.model.ModelBiped {
        public Armor(float scale) {
            super(scale);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, float scale, Entity e) {
            super.setRotationAngles(limbSwing, limbSwingAmount, age, headYaw, headPitch, scale, e);
            applyTo(this, e);
        }
    }

    /** Слой брони с моделями, которые повторяют позу. */
    public static net.minecraft.client.renderer.entity.layers.LayerBipedArmor armorLayer(net.minecraft.client.renderer.entity.RenderLivingBase<?> r) {
        return new net.minecraft.client.renderer.entity.layers.LayerBipedArmor(r) {
            @Override
            protected void initArmor() {
                this.modelLeggings = new Armor(0.5F);
                this.modelArmor = new Armor(1.0F);
            }
        };
    }

    private void read(float[] out) {
        for (int i = 0; i < parts.length; i++) {
            out[i * 3] = parts[i].rotateAngleX;
            out[i * 3 + 1] = parts[i].rotateAngleY;
            out[i * 3 + 2] = parts[i].rotateAngleZ;
        }
    }

    private void write(float[] in) {
        for (int i = 0; i < parts.length; i++) {
            parts[i].rotateAngleX = in[i * 3];
            parts[i].rotateAngleY = in[i * 3 + 1];
            parts[i].rotateAngleZ = in[i * 3 + 2];
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, float scale, Entity e) {
        super.setRotationAngles(limbSwing, limbSwingAmount, age, headYaw, headPitch, scale, e);
        int pose = poseOf(e);
        Blend b = blend(e, pose, age);
        read(base);
        apply(pose, age * 0.15F);
        read(target);
        float f = ease((age - b.start) / b.dur);
        for (int i = 0; i < N; i++) {
            // отклонение от обычной анимации: из запомненного к отклонению новой позы
            float off = b.from[i] + (target[i] - base[i] - b.from[i]) * f;
            b.last[i] = off;
            target[i] = base[i] + off;
        }
        write(target);
        // второй слой скина повторяет первый
        copyModelAngles(bipedRightArm, bipedRightArmwear);
        copyModelAngles(bipedLeftArm, bipedLeftArmwear);
        copyModelAngles(bipedRightLeg, bipedRightLegwear);
        copyModelAngles(bipedLeftLeg, bipedLeftLegwear);
        copyModelAngles(bipedBody, bipedBodyWear);
        copyModelAngles(bipedHead, bipedHeadwear);
    }

    /** Выставить углы позы поверх обычной анимации. */
    private void apply(int pose, float t) {
        switch (pose) {
            case Pose.RAISE:
                bipedRightArm.rotateAngleX = -2.9F;
                bipedRightArm.rotateAngleY = 0;
                bipedRightArm.rotateAngleZ = 0.1F;
                bipedHead.rotateAngleX = -0.35F;
                break;
            case Pose.DASH:
                bipedBody.rotateAngleX = 0.2F;
                bipedRightArm.rotateAngleX = -1.5F;
                bipedLeftArm.rotateAngleX = -1.3F;
                bipedRightArm.rotateAngleY = -0.2F;
                bipedHead.rotateAngleX = -0.3F;
                bipedRightLeg.rotateAngleX = -0.6F;
                bipedLeftLeg.rotateAngleX = 0.5F;
                break;
            case Pose.KNEEL_PRAY:
                kneel();
                bipedRightArm.rotateAngleX = -1.1F;
                bipedLeftArm.rotateAngleX = -1.1F;
                bipedRightArm.rotateAngleY = -0.45F;
                bipedLeftArm.rotateAngleY = 0.45F;
                bipedHead.rotateAngleX = 0.45F;
                break;
            case Pose.CAST:
                bipedRightArm.rotateAngleX = -1.6F;
                bipedRightArm.rotateAngleY = -0.1F;
                bipedLeftArm.rotateAngleX = -0.3F;
                break;
            case Pose.STAGGER:
                bipedRightArm.rotateAngleX = 0.25F;
                bipedLeftArm.rotateAngleX = 0.25F;
                bipedRightArm.rotateAngleZ = 0.15F;
                bipedLeftArm.rotateAngleZ = -0.15F;
                bipedBody.rotateAngleX = 0.12F;
                bipedHead.rotateAngleX = 0.6F + MathHelper.sin(t) * 0.05F;
                break;
            case Pose.WINDUP:
                bipedRightArm.rotateAngleX = -3.0F;
                bipedLeftArm.rotateAngleX = -3.0F;
                bipedRightArm.rotateAngleZ = -0.25F;
                bipedLeftArm.rotateAngleZ = 0.25F;
                bipedBody.rotateAngleX = -0.08F;
                bipedHead.rotateAngleX = -0.2F;
                break;
            case Pose.CHOP:
                bipedRightArm.rotateAngleX = -0.7F;
                bipedLeftArm.rotateAngleX = -0.7F;
                bipedRightArm.rotateAngleZ = 0.2F;
                bipedLeftArm.rotateAngleZ = -0.2F;
                bipedBody.rotateAngleX = 0.2F;
                bipedHead.rotateAngleX = 0.3F;
                break;
            case Pose.RAISE_BOTH:
                bipedRightArm.rotateAngleX = -2.7F;
                bipedLeftArm.rotateAngleX = -2.7F;
                bipedRightArm.rotateAngleZ = 0.45F;
                bipedLeftArm.rotateAngleZ = -0.45F;
                bipedHead.rotateAngleX = -0.5F;
                break;
            case Pose.LUTE:
                // левая держит гриф, правая перебирает струны
                bipedLeftArm.rotateAngleX = -1.0F;
                bipedLeftArm.rotateAngleY = 0.6F;
                bipedRightArm.rotateAngleX = -0.75F + MathHelper.sin(t * 3) * 0.15F;
                bipedRightArm.rotateAngleY = -0.7F;
                bipedHead.rotateAngleZ = MathHelper.sin(t) * 0.12F;
                bipedHead.rotateAngleX = -0.15F;
                break;
            case Pose.CHOKE:
                bipedRightArm.rotateAngleX = -2.2F;
                bipedRightArm.rotateAngleY = -0.6F;
                bipedRightArm.rotateAngleZ = 0.5F;
                bipedLeftArm.rotateAngleX = 0.2F;
                bipedHead.rotateAngleX = -0.4F;
                bipedBody.rotateAngleX = 0.08F;
                break;
            case Pose.PRAY:
                bipedRightArm.rotateAngleX = -1.2F;
                bipedLeftArm.rotateAngleX = -1.2F;
                bipedRightArm.rotateAngleY = -0.5F;
                bipedLeftArm.rotateAngleY = 0.5F;
                bipedHead.rotateAngleX = 0.3F;
                break;
            case Pose.KNEEL:
                kneel();
                bipedRightArm.rotateAngleX = -0.3F;
                bipedLeftArm.rotateAngleX = 0.1F;
                bipedHead.rotateAngleX = 0.7F;
                bipedBody.rotateAngleX = 0.15F;
                break;
            case Pose.CAST_DOWN:
                bipedRightArm.rotateAngleX = -0.9F;
                bipedLeftArm.rotateAngleX = -0.9F;
                bipedRightArm.rotateAngleZ = 0.3F;
                bipedLeftArm.rotateAngleZ = -0.3F;
                bipedHead.rotateAngleX = 0.5F;
                break;
            default:
                break;
        }
    }

    /** На коленях: голени назад. Модель целиком опускается в рендере (kneelDrop). */
    private void kneel() {
        bipedRightLeg.rotateAngleX = 1.25F;
        bipedRightLeg.rotateAngleY = 0.08F;
        bipedLeftLeg.rotateAngleX = 1.25F;
        bipedLeftLeg.rotateAngleY = -0.08F;
    }

    /** На сколько опустить модель в позе на коленях (в блоках модели). */
    public static float kneelDrop(int pose) {
        return pose == Pose.KNEEL_PRAY || pose == Pose.KNEEL ? 0.47F : 0F;
    }
}
