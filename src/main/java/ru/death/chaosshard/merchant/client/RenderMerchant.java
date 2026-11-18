package ru.death.chaosshard.merchant.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.merchant.EntityMerchant;

/** Торговец: модель игрока (64×64, со вторым слоем) и походный рюкзак за спиной. */
public class RenderMerchant extends RenderLiving<EntityMerchant> {

    private static final ResourceLocation SKIN = new ResourceLocation(Tags.MOD_ID, "textures/entity/merchant/merchant.png");
    private static final ResourceLocation PACK = new ResourceLocation(Tags.MOD_ID, "textures/entity/merchant/backpack.png");

    public RenderMerchant(RenderManager rm) {
        super(rm, new ModelPlayer(0.0F, false), 0.5F);
        addLayer(new LayerBackpack());
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityMerchant e) {
        return SKIN;
    }

    @Override
    protected void preRenderCallback(EntityMerchant e, float partialTicks) {
        GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
    }

    /** Имя над головой — когда на торговца смотрят. */
    @Override
    protected boolean canRenderName(EntityMerchant e) {
        return e == renderManager.pointedEntity && !e.isBeingRidden() && net.minecraft.client.Minecraft.isGuiEnabled();
    }

    /** Рюкзак: короб, скатка сверху, карман и котелок сбоку. Поворачивается вместе с телом. */
    static final class ModelBackpack extends ModelBase {
        final ModelRenderer pack, roll, pocket, pot;

        ModelBackpack() {
            textureWidth = 64;
            textureHeight = 32;
            pack = new ModelRenderer(this, 0, 0);
            pack.addBox(-4F, 0.5F, 2F, 8, 10, 4);
            roll = new ModelRenderer(this, 0, 14);
            roll.addBox(-4.5F, -2F, 2.5F, 9, 3, 3);
            pocket = new ModelRenderer(this, 24, 0);
            pocket.addBox(-3F, 5F, 6F, 6, 5, 2);
            pot = new ModelRenderer(this, 40, 0);
            pot.addBox(4F, 6F, 3F, 2, 3, 2);
        }

        void renderAll(float s) {
            pack.render(s);
            roll.render(s);
            pocket.render(s);
            pot.render(s);
        }
    }

    final class LayerBackpack implements LayerRenderer<EntityMerchant> {
        private final ModelBackpack model = new ModelBackpack();

        @Override
        public void doRenderLayer(EntityMerchant e, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
            bindTexture(PACK);
            GlStateManager.pushMatrix();
            ((ModelPlayer) getMainModel()).bipedBody.postRender(scale);
            GlStateManager.color(1F, 1F, 1F, 1F);
            model.renderAll(scale);
            GlStateManager.popMatrix();
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }
}
