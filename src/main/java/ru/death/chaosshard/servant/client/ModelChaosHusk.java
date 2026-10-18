package ru.death.chaosshard.servant.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;

/**
 * Хаск — своё существо: сгорбленная высохшая оболочка, выеденная Хаосом, из пасти — четыре членистые лапки.
 * Геометрия сгенерирована pack/tools/husk_model.py (там же текстура и .bbmodel) — правки геометрии делать там.
 * Наследует ModelBiped ради предмета в руке (LayerHeldItem); броня на эту модель не надевается.
 */
public class ModelChaosHusk extends ModelBiped {

    public final ModelRenderer torso;
    public final ModelRenderer head;
    public final ModelRenderer jaw;
    public final ModelRenderer mr1;
    public final ModelRenderer mr2;
    public final ModelRenderer mr3;
    public final ModelRenderer ml1;
    public final ModelRenderer ml2;
    public final ModelRenderer ml3;
    public final ModelRenderer lr1;
    public final ModelRenderer lr2;
    public final ModelRenderer lr3;
    public final ModelRenderer ll1;
    public final ModelRenderer ll2;
    public final ModelRenderer ll3;
    public final ModelRenderer cr1;
    public final ModelRenderer cr2;
    public final ModelRenderer cr3;
    public final ModelRenderer cr4;
    public final ModelRenderer armR;
    public final ModelRenderer armL;
    public final ModelRenderer legR;
    public final ModelRenderer legL;
    /** Лапки из пасти: [лапка][членик]. */
    private final ModelRenderer[][] maw;
    private final float[][] rest;

    public ModelChaosHusk() {
        super(0.0F, 0.0F, 64, 64);
        torso = new ModelRenderer(this, 16, 16);
        torso.addBox(-4.0F, -12.0F, -2.0F, 8, 12, 4);
        torso.setRotationPoint(0.0F, 12.0F, 0.5F);
        head = new ModelRenderer(this, 0, 0);
        head.addBox(-4.0F, -6.0F, -6.0F, 8, 6, 8);
        head.setRotationPoint(0.0F, -12.0F, -0.5F);
        jaw = new ModelRenderer(this, 32, 0);
        jaw.addBox(-3.5F, 0.0F, -6.5F, 7, 2, 7);
        jaw.setRotationPoint(0.0F, 0.0F, 0.5F);
        mr1 = new ModelRenderer(this, 16, 32);
        mr1.addBox(-0.5F, -0.5F, -4.0F, 1, 1, 4);
        mr1.setRotationPoint(-1.3F, -0.4F, -5.6F);
        mr2 = new ModelRenderer(this, 26, 32);
        mr2.addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3);
        mr2.setRotationPoint(0.0F, 0.0F, -4.0F);
        mr3 = new ModelRenderer(this, 34, 32);
        mr3.addBox(-0.5F, -0.5F, -2.0F, 1, 1, 2);
        mr3.setRotationPoint(0.0F, 0.0F, -3.0F);
        ml1 = new ModelRenderer(this, 16, 32);
        ml1.addBox(-0.5F, -0.5F, -4.0F, 1, 1, 4);
        ml1.setRotationPoint(1.3F, -0.4F, -5.6F);
        ml2 = new ModelRenderer(this, 26, 32);
        ml2.addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3);
        ml2.setRotationPoint(0.0F, 0.0F, -4.0F);
        ml3 = new ModelRenderer(this, 34, 32);
        ml3.addBox(-0.5F, -0.5F, -2.0F, 1, 1, 2);
        ml3.setRotationPoint(0.0F, 0.0F, -3.0F);
        lr1 = new ModelRenderer(this, 16, 32);
        lr1.addBox(-0.5F, -0.5F, -4.0F, 1, 1, 4);
        lr1.setRotationPoint(-1.6F, 0.6F, -5.8F);
        lr2 = new ModelRenderer(this, 26, 32);
        lr2.addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3);
        lr2.setRotationPoint(0.0F, 0.0F, -4.0F);
        lr3 = new ModelRenderer(this, 34, 32);
        lr3.addBox(-0.5F, -0.5F, -2.0F, 1, 1, 2);
        lr3.setRotationPoint(0.0F, 0.0F, -3.0F);
        ll1 = new ModelRenderer(this, 16, 32);
        ll1.addBox(-0.5F, -0.5F, -4.0F, 1, 1, 4);
        ll1.setRotationPoint(1.6F, 0.6F, -5.8F);
        ll2 = new ModelRenderer(this, 26, 32);
        ll2.addBox(-0.5F, -0.5F, -3.0F, 1, 1, 3);
        ll2.setRotationPoint(0.0F, 0.0F, -4.0F);
        ll3 = new ModelRenderer(this, 34, 32);
        ll3.addBox(-0.5F, -0.5F, -2.0F, 1, 1, 2);
        ll3.setRotationPoint(0.0F, 0.0F, -3.0F);
        cr1 = new ModelRenderer(this, 16, 40);
        cr1.addBox(-1.0F, -5.0F, -1.0F, 2, 5, 2);
        cr1.setRotationPoint(-1.5F, -9.0F, 2.0F);
        cr2 = new ModelRenderer(this, 24, 40);
        cr2.addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1);
        cr2.setRotationPoint(2.0F, -7.0F, 2.0F);
        cr3 = new ModelRenderer(this, 28, 40);
        cr3.addBox(-1.0F, -3.0F, -1.0F, 2, 3, 2);
        cr3.setRotationPoint(3.5F, -11.5F, 0.5F);
        cr4 = new ModelRenderer(this, 24, 40);
        cr4.addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1);
        cr4.setRotationPoint(-2.5F, -5.5F, -1.0F);
        armR = new ModelRenderer(this, 40, 16);
        armR.addBox(-1.5F, -1.0F, -1.5F, 3, 15, 3);
        armR.setRotationPoint(-5.5F, 3.0F, -4.0F);
        armL = new ModelRenderer(this, 40, 34);
        armL.addBox(-1.5F, -1.0F, -1.5F, 3, 15, 3);
        armL.setRotationPoint(5.5F, 3.0F, -4.0F);
        legR = new ModelRenderer(this, 0, 16);
        legR.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
        legR.setRotationPoint(-2.1F, 12.0F, 0.5F);
        legL = new ModelRenderer(this, 0, 32);
        legL.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4);
        legL.setRotationPoint(2.1F, 12.0F, 0.5F);
        torso.addChild(head);
        head.addChild(jaw);
        head.addChild(mr1);
        mr1.addChild(mr2);
        mr2.addChild(mr3);
        head.addChild(ml1);
        ml1.addChild(ml2);
        ml2.addChild(ml3);
        jaw.addChild(lr1);
        lr1.addChild(lr2);
        lr2.addChild(lr3);
        jaw.addChild(ll1);
        ll1.addChild(ll2);
        ll2.addChild(ll3);
        torso.addChild(cr1);
        torso.addChild(cr2);
        torso.addChild(cr3);
        head.addChild(cr4);
        bipedRightArm = armR;
        bipedLeftArm = armL;
        bipedRightLeg = legR;
        bipedLeftLeg = legL;
        bipedHead = head;
        bipedBody = torso;
        maw = new ModelRenderer[][]{{mr1, mr2, mr3}, {ml1, ml2, ml3}, {lr1, lr2, lr3}, {ll1, ll2, ll3}};
        rest = new float[][]{
                {0.42F, 0.0F, 0.0F},   // torso
                {-0.25F, 0.0F, 0.0F},   // head
                {0.35F, 0.0F, 0.0F},   // jaw
                {-0.45F, -0.55F, 0.0F},   // mr1
                {0.9F, 0.3F, 0.0F},   // mr2
                {0.8F, 0.0F, 0.0F},   // mr3
                {-0.45F, 0.55F, 0.0F},   // ml1
                {0.9F, -0.3F, 0.0F},   // ml2
                {0.8F, 0.0F, 0.0F},   // ml3
                {0.3F, -0.75F, 0.0F},   // lr1
                {-0.8F, 0.4F, 0.0F},   // lr2
                {-0.7F, 0.0F, 0.0F},   // lr3
                {0.3F, 0.75F, 0.0F},   // ll1
                {-0.8F, -0.4F, 0.0F},   // ll2
                {-0.7F, 0.0F, 0.0F},   // ll3
                {-0.7F, 0.0F, 0.35F},   // cr1
                {-0.9F, 0.0F, -0.4F},   // cr2
                {-0.2F, 0.0F, -0.9F},   // cr3
                {-0.5F, 0.0F, 0.5F},   // cr4
                {-0.15F, 0.0F, 0.1F},   // armR
                {-0.15F, 0.0F, -0.1F},   // armL
                {0.0F, 0.0F, 0.0F},   // legR
                {0.0F, 0.0F, 0.0F},   // legL
        };
    }

    private void pose(ModelRenderer r, int i) {
        r.rotateAngleX = rest[i][0];
        r.rotateAngleY = rest[i][1];
        r.rotateAngleZ = rest[i][2];
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, age, netHeadYaw, headPitch, scale, entity);
        torso.render(scale);
        armR.render(scale);
        armL.render(scale);
        legR.render(scale);
        legL.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, float scale, Entity entity) {
        pose(torso, 0);
        pose(head, 1);
        pose(jaw, 2);
        pose(mr1, 3);
        pose(mr2, 4);
        pose(mr3, 5);
        pose(ml1, 6);
        pose(ml2, 7);
        pose(ml3, 8);
        pose(lr1, 9);
        pose(lr2, 10);
        pose(lr3, 11);
        pose(ll1, 12);
        pose(ll2, 13);
        pose(ll3, 14);
        pose(cr1, 15);
        pose(cr2, 16);
        pose(cr3, 17);
        pose(cr4, 18);
        pose(armR, 19);
        pose(armL, 20);
        pose(legR, 21);
        pose(legL, 22);
        float walk = Math.min(1.0F, limbSwingAmount);
        float step = limbSwing * 0.6662F;
        float attack = swingProgress > 0 ? MathHelper.sin(swingProgress * (float) Math.PI) : 0.0F;
        // шаркающая походка: правая нога шагает, левая подволакивается
        legR.rotateAngleX = MathHelper.cos(step) * 1.1F * walk;
        legL.rotateAngleX = MathHelper.cos(step + (float) Math.PI) * 0.5F * walk + 0.12F * walk;
        legL.rotateAngleZ = -0.06F * walk;
        // дыхание и раскачка сгорбленного тела
        torso.rotateAngleX += MathHelper.sin(age * 0.06F) * 0.025F + walk * 0.08F;
        torso.rotateAngleZ = MathHelper.cos(step) * 0.06F * walk;
        // голова: взгляд и редкие резкие подёргивания
        head.rotateAngleY = netHeadYaw * 0.017453292F * 0.7F;
        head.rotateAngleX += headPitch * 0.017453292F * 0.6F;
        float twitch = MathHelper.sin(age * 0.13F) * MathHelper.sin(age * 0.071F + 1.0F);
        if (twitch > 0.82F) {
            head.rotateAngleZ = (twitch - 0.82F) * 3.5F * MathHelper.sin(age * 2.6F);
        }
        // пасть приоткрыта, на ударе распахивается
        jaw.rotateAngleX += MathHelper.sin(age * 0.09F) * 0.06F + attack * 0.6F;
        // руки болтаются; на ударе — выброс вперёд
        armR.rotateAngleX += MathHelper.cos(step + (float) Math.PI) * 0.45F * walk - attack * 1.5F;
        armL.rotateAngleX += MathHelper.cos(step) * 0.45F * walk - attack * 1.3F;
        armR.rotateAngleZ += MathHelper.sin(age * 0.05F) * 0.03F;
        armL.rotateAngleZ -= MathHelper.sin(age * 0.05F) * 0.03F;
        // лапки из пасти: шевелятся сами по себе, иногда ощупывают воздух, на ударе выстреливают вперёд
        float probe = Math.max(0.0F, MathHelper.sin(age * 0.045F) * MathHelper.sin(age * 0.029F + 2.0F) - 0.5F) * 2.0F;
        float reach = Math.max(attack, probe * 0.6F);
        for (int i = 0; i < 4; i++) {
            float ph = i * 1.7F;
            ModelRenderer[] l = maw[i];
            l[0].rotateAngleX = (l[0].rotateAngleX + MathHelper.sin(age * 0.3F + ph) * 0.18F) * (1.0F - reach);
            l[0].rotateAngleY = (l[0].rotateAngleY + MathHelper.cos(age * 0.23F + ph) * 0.15F) * (1.0F - reach * 0.7F);
            l[1].rotateAngleX = (l[1].rotateAngleX + MathHelper.sin(age * 0.4F + ph) * 0.25F) * (1.0F - reach);
            l[2].rotateAngleX = (l[2].rotateAngleX + MathHelper.sin(age * 0.5F + ph) * 0.3F) * (1.0F - reach * 0.8F);
        }
    }

    /** Предмет в руке — в кисти длинной руки, а не у локтя. */
    @Override
    public void postRenderArm(float scale, EnumHandSide side) {
        super.postRenderArm(scale, side);
        GlStateManager.translate(0.0F, 0.42F, 0.0F);
    }
}
