package ru.death.chaosshard.act5.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.hero.client.PoseModel;

/** Боссы Акта 5 (Король, Архонт): модель игрока с позами, свой скин и размер. */
public class RenderBoss<T extends EntityLiving> extends RenderBiped<T> {

    private final ResourceLocation tex;
    private final float scale;

    public RenderBoss(RenderManager rm, String skin, float scale) {
        super(rm, new PoseModel(), 0.6F * scale);
        this.tex = new ResourceLocation(Tags.MOD_ID, "textures/entity/act5/" + skin + ".png");
        this.scale = scale;
        addLayer(PoseModel.armorLayer(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(T e) {
        return tex;
    }

    @Override
    protected void preRenderCallback(T e, float partialTicks) {
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(0F, PoseModel.drop(e, partialTicks), 0F);
    }
}
