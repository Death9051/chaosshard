package ru.death.chaosshard.servant.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import ru.death.chaosshard.servant.entity.ServantEntities;

/** Хаск: своя модель, оружие в руке, трещины, пасть и кристаллы светятся в темноте и «дышат». */
public class RenderChaosHusk extends RenderLiving<ServantEntities.Husk> {

    private static final ResourceLocation TEX = ServantRenders.tex("husk");
    private static final ResourceLocation GLOW = ServantRenders.tex("husk_glow");

    public RenderChaosHusk(RenderManager rm) {
        super(rm, new ModelChaosHusk(), 0.5F);
        addLayer(new LayerHeldItem(this));
        addLayer(new Glow(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(ServantEntities.Husk e) {
        return TEX;
    }

    /** Свечение поверх модели: сложение цветов, полная яркость, мерцание по времени. */
    static final class Glow implements LayerRenderer<ServantEntities.Husk> {
        private final RenderChaosHusk render;

        Glow(RenderChaosHusk render) {
            this.render = render;
        }

        @Override
        public void doRenderLayer(ServantEntities.Husk e, float limbSwing, float limbSwingAmount, float partialTicks,
                                  float age, float netHeadYaw, float headPitch, float scale) {
            render.bindTexture(GLOW);
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(!e.isInvisible());
            int light = 61680;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
            float pulse = 0.7F + 0.3F * MathHelper.sin(age * 0.08F + e.getEntityId());
            GlStateManager.color(pulse, pulse, pulse, 1.0F);
            render.getMainModel().render(e, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch, scale);
            int b = e.getBrightnessForRender();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, b % 65536, b / 65536);
            render.setLightmap(e);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }
}
