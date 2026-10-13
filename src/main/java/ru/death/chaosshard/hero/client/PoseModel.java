package ru.death.chaosshard.hero.client;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import ru.death.chaosshard.hero.Pose;

/** Модель игрока, которая умеет вставать в позы боссов (руки, ноги, корпус, голова). */
public class PoseModel extends ModelPlayer {

    public PoseModel() {
        super(0.0F, false);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, float scale, Entity e) {
        super.setRotationAngles(limbSwing, limbSwingAmount, age, headYaw, headPitch, scale, e);
        int pose = e instanceof Pose.Posed ? ((Pose.Posed) e).pose() : Pose.NONE;
        if (pose == Pose.NONE) {
            return;
        }
        float t = age * 0.15F;
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
        // второй слой скина повторяет первый
        copyModelAngles(bipedRightArm, bipedRightArmwear);
        copyModelAngles(bipedLeftArm, bipedLeftArmwear);
        copyModelAngles(bipedRightLeg, bipedRightLegwear);
        copyModelAngles(bipedLeftLeg, bipedLeftLegwear);
        copyModelAngles(bipedBody, bipedBodyWear);
        copyModelAngles(bipedHead, bipedHeadwear);
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
