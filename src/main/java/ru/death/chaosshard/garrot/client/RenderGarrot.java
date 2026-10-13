package ru.death.chaosshard.garrot.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.garrot.EntityGarrot;

/** Гаррот (крупнее человека), его тени (полупрозрачные, тёмные) и солдаты-вассалы. */
public class RenderGarrot extends RenderBiped<EntityGarrot> {

    private static final ResourceLocation LORD = new ResourceLocation(Tags.MOD_ID, "textures/entity/garrot/lord.png");
    private static final ResourceLocation SOLDIER = new ResourceLocation(Tags.MOD_ID, "textures/entity/garrot/soldier.png");
    private static final ResourceLocation SHIELD = new ResourceLocation(Tags.MOD_ID, "textures/entity/garrot/shieldbearer.png");

    public RenderGarrot(RenderManager rm) {
        super(rm, new ru.death.chaosshard.hero.client.PoseModel(), 0.55F);
        addLayer(new LayerBipedArmor(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityGarrot e) {
        switch (e.variant()) {
            case EntityGarrot.VASSAL: return (e.getEntityId() & 1) == 0 ? SOLDIER : SHIELD;
            default: return LORD;
        }
    }

    @Override
    protected void preRenderCallback(EntityGarrot e, float partialTicks) {
        float s = e.variant() == EntityGarrot.LORD ? 1.15F : 0.9375F;
        GlStateManager.scale(s, s, s);
        GlStateManager.translate(0F, ru.death.chaosshard.hero.client.PoseModel.kneelDrop(e.pose()), 0F);
        if (e.variant() == EntityGarrot.SHADOW) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(0.25F, 0.15F, 0.3F, 0.6F);
        }
    }

    @Override
    public void doRender(EntityGarrot e, double x, double y, double z, float yaw, float partialTicks) {
        super.doRender(e, x, y, z, yaw, partialTicks);
        if (e.variant() == EntityGarrot.SHADOW) {
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.disableBlend();
        }
    }
}
