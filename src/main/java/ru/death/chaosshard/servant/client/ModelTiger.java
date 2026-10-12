package ru.death.chaosshard.servant.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Тигр — модель уникального слуги. Сгенерирована по той же геометрии, что развёртка 128×64. */
public class ModelTiger extends ModelBase {

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

    public ModelTiger() {
        textureWidth = 128;
        textureHeight = 64;
        body = new ModelRenderer(this, 0, 0);
        body.addBox(-5.0F, -4.0F, -11.0F, 10, 9, 22);
        body.setRotationPoint(0.0F, 10.0F, 0.0F);
        head = new ModelRenderer(this, 0, 32);
        head.addBox(-4.5F, -5.0F, -7.0F, 9, 8, 8);
        head.setRotationPoint(0.0F, 8.0F, -11.0F);
        snout = new ModelRenderer(this, 34, 32);
        snout.addBox(-2.5F, -1.0F, -10.0F, 5, 3, 3);
        snout.setRotationPoint(0.0F, 0.0F, 0.0F);
        ear_l = new ModelRenderer(this, 50, 32);
        ear_l.addBox(-4.5F, -7.0F, -3.0F, 3, 3, 1);
        ear_l.setRotationPoint(0.0F, 0.0F, 0.0F);
        ear_r = new ModelRenderer(this, 58, 32);
        ear_r.addBox(1.5F, -7.0F, -3.0F, 3, 3, 1);
        ear_r.setRotationPoint(0.0F, 0.0F, 0.0F);
        leg_fl = new ModelRenderer(this, 0, 48);
        leg_fl.addBox(-2.0F, 0.0F, -2.0F, 4, 9, 4);
        leg_fl.setRotationPoint(-3.0F, 15.0F, -7.0F);
        leg_fr = new ModelRenderer(this, 16, 48);
        leg_fr.addBox(-2.0F, 0.0F, -2.0F, 4, 9, 4);
        leg_fr.setRotationPoint(3.0F, 15.0F, -7.0F);
        leg_bl = new ModelRenderer(this, 32, 48);
        leg_bl.addBox(-2.0F, 0.0F, -2.0F, 4, 9, 4);
        leg_bl.setRotationPoint(-3.0F, 15.0F, 8.0F);
        leg_br = new ModelRenderer(this, 48, 48);
        leg_br.addBox(-2.0F, 0.0F, -2.0F, 4, 9, 4);
        leg_br.setRotationPoint(3.0F, 15.0F, 8.0F);
        tail1 = new ModelRenderer(this, 64, 0);
        tail1.addBox(-1.0F, -1.0F, 0.0F, 2, 2, 10);
        tail1.setRotationPoint(0.0F, 7.0F, 11.0F);
        tail1.rotateAngleX = -0.9F;
        tail2 = new ModelRenderer(this, 64, 12);
        tail2.addBox(-1.0F, -1.0F, 0.0F, 2, 2, 8);
        tail2.setRotationPoint(0.0F, 0.0F, 10.0F);
        tail2.rotateAngleX = 1.4F;
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
        tail1.rotateAngleX = -0.9F + MathHelper.cos(age * 0.06F) * 0.08F;
    }
}
