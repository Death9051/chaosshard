package ru.death.chaosshard.servant.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Кошка — модель уникального слуги. Сгенерирована по той же геометрии, что развёртка 64×64. */
public class ModelCat extends ModelBase {

    public final ModelRenderer body;
    public final ModelRenderer head;
    public final ModelRenderer snout;
    public final ModelRenderer ear_l;
    public final ModelRenderer ear_r;
    public final ModelRenderer leg_fl;
    public final ModelRenderer leg_fr;
    public final ModelRenderer leg_bl;
    public final ModelRenderer leg_br;
    public final ModelRenderer tail1;
    public final ModelRenderer tail2;

    public ModelCat() {
        textureWidth = 64;
        textureHeight = 64;
        body = new ModelRenderer(this, 0, 0);
        body.addBox(-2.0F, -2.0F, -6.0F, 4, 5, 12);
        body.setRotationPoint(0.0F, 14.0F, 0.0F);
        head = new ModelRenderer(this, 0, 17);
        head.addBox(-2.5F, -2.0F, -4.0F, 5, 4, 5);
        head.setRotationPoint(0.0F, 10.0F, -6.0F);
        snout = new ModelRenderer(this, 20, 17);
        snout.addBox(-1.5F, 0.0F, -5.0F, 3, 2, 1);
        snout.setRotationPoint(0.0F, 0.0F, 0.0F);
        ear_l = new ModelRenderer(this, 28, 17);
        ear_l.addBox(-2.0F, -4.0F, -2.0F, 1, 2, 2);
        ear_l.setRotationPoint(0.0F, 0.0F, 0.0F);
        ear_r = new ModelRenderer(this, 34, 17);
        ear_r.addBox(1.0F, -4.0F, -2.0F, 1, 2, 2);
        ear_r.setRotationPoint(0.0F, 0.0F, 0.0F);
        leg_fl = new ModelRenderer(this, 0, 26);
        leg_fl.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2);
        leg_fl.setRotationPoint(-1.0F, 17.0F, -4.0F);
        leg_fr = new ModelRenderer(this, 8, 26);
        leg_fr.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2);
        leg_fr.setRotationPoint(1.0F, 17.0F, -4.0F);
        leg_bl = new ModelRenderer(this, 16, 26);
        leg_bl.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2);
        leg_bl.setRotationPoint(-1.0F, 17.0F, 4.0F);
        leg_br = new ModelRenderer(this, 24, 26);
        leg_br.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2);
        leg_br.setRotationPoint(1.0F, 17.0F, 4.0F);
        tail1 = new ModelRenderer(this, 32, 0);
        tail1.addBox(-0.5F, -0.5F, 0.0F, 1, 1, 8);
        tail1.setRotationPoint(0.0F, 12.5F, 6.0F);
        tail1.rotateAngleX = 0.9F;
        tail2 = new ModelRenderer(this, 32, 9);
        tail2.addBox(-0.5F, -0.5F, 0.0F, 1, 1, 6);
        tail2.setRotationPoint(0.0F, 0.0F, 8.0F);
        tail2.rotateAngleX = -0.7F;
        head.addChild(snout);
        head.addChild(ear_l);
        head.addChild(ear_r);
        tail1.addChild(tail2);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, age, netHeadYaw, headPitch, scale, entity);
        body.render(scale);
        head.render(scale);
        leg_fl.render(scale);
        leg_fr.render(scale);
        leg_bl.render(scale);
        leg_br.render(scale);
        tail1.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, float scale, Entity entity) {
        head.rotateAngleY = netHeadYaw * 0.017453292F;
        head.rotateAngleX = headPitch * 0.017453292F;
        float a = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leg_fl.rotateAngleX = a;
        leg_br.rotateAngleX = a;
        leg_fr.rotateAngleX = -a;
        leg_bl.rotateAngleX = -a;
        tail1.rotateAngleY = MathHelper.sin(age * 0.08F) * 0.35F;
        tail1.rotateAngleX = 0.9F + MathHelper.cos(age * 0.06F) * 0.08F;
    }
}
