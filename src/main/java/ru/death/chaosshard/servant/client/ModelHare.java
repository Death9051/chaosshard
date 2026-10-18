package ru.death.chaosshard.servant.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Заяц — модель уникального слуги. Сгенерирована по той же геометрии, что развёртка 64×64. */
public class ModelHare extends ModelBase {

    public final ModelRenderer body;
    public final ModelRenderer head;
    public final ModelRenderer ear_l;
    public final ModelRenderer ear_r;
    public final ModelRenderer leg_fl;
    public final ModelRenderer leg_fr;
    public final ModelRenderer leg_bl;
    public final ModelRenderer leg_br;
    public final ModelRenderer tail;

    public ModelHare() {
        textureWidth = 64;
        textureHeight = 64;
        body = new ModelRenderer(this, 0, 0);
        body.addBox(-3.0F, -3.0F, -4.5F, 6, 5, 9);
        body.setRotationPoint(0.0F, 17.0F, 0.0F);
        head = new ModelRenderer(this, 0, 14);
        head.addBox(-2.5F, -4.0F, -3.0F, 5, 5, 5);
        head.setRotationPoint(0.0F, 13.0F, -5.5F);
        // левое ухо обгрызено: осталось 4 пикселя из 7 (развёртка сдвинута на 3 вниз — по текстуре)
        ear_l = new ModelRenderer(this, 20, 17);
        ear_l.addBox(-1.0F, -4.0F, -0.5F, 2, 4, 1);
        ear_l.setRotationPoint(-1.5F, -4.0F, -0.5F);
        ear_l.rotateAngleX = -0.15F;
        ear_r = new ModelRenderer(this, 26, 14);
        ear_r.addBox(-1.0F, -7.0F, -0.5F, 2, 7, 1);
        ear_r.setRotationPoint(1.5F, -4.0F, -0.5F);
        ear_r.rotateAngleX = -0.15F;
        leg_fl = new ModelRenderer(this, 0, 24);
        leg_fl.addBox(-1.0F, 0.0F, -1.0F, 2, 5, 2);
        leg_fl.setRotationPoint(-1.5F, 19.0F, -3.0F);
        leg_fr = new ModelRenderer(this, 8, 24);
        leg_fr.addBox(-1.0F, 0.0F, -1.0F, 2, 5, 2);
        leg_fr.setRotationPoint(1.5F, 19.0F, -3.0F);
        leg_bl = new ModelRenderer(this, 16, 24);
        leg_bl.addBox(-1.0F, 0.0F, -2.0F, 2, 5, 5);
        leg_bl.setRotationPoint(-2.5F, 19.0F, 2.0F);
        leg_br = new ModelRenderer(this, 30, 24);
        leg_br.addBox(-1.0F, 0.0F, -2.0F, 2, 5, 5);
        leg_br.setRotationPoint(2.5F, 19.0F, 2.0F);
        tail = new ModelRenderer(this, 32, 0);
        tail.addBox(-1.5F, -1.0F, -0.5F, 3, 3, 2);
        tail.setRotationPoint(0.0F, 14.0F, 5.0F);
        head.addChild(ear_l);
        head.addChild(ear_r);
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
        tail.render(scale);
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
        tail.rotateAngleY = MathHelper.sin(age * 0.3F) * 0.2F;
        ear_l.rotateAngleX = -0.15F + MathHelper.sin(age * 0.05F) * 0.08F;
        ear_r.rotateAngleX = -0.15F + MathHelper.cos(age * 0.05F) * 0.08F;
    }
}
