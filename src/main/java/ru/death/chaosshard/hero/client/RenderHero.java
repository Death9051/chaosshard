package ru.death.chaosshard.hero.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.hero.EntityHero;
import ru.death.chaosshard.hero.Lightbearer;

/** Светочи: модель игрока со своим скином, чуть крупнее человека. */
public class RenderHero extends RenderBiped<EntityHero> {

    private static final ResourceLocation[] TEX = new ResourceLocation[Lightbearer.values().length];

    static {
        for (Lightbearer h : Lightbearer.values()) {
            TEX[h.ordinal()] = new ResourceLocation(Tags.MOD_ID, "textures/entity/hero/" + (h.ready() ? h.id() : "kael") + ".png");
        }
    }

    public RenderHero(RenderManager rm) {
        super(rm, new ru.death.chaosshard.hero.client.PoseModel(), 0.6F);
        addLayer(ru.death.chaosshard.hero.client.PoseModel.armorLayer(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityHero e) {
        return TEX[e.hero().ordinal()];
    }

    @Override
    protected void preRenderCallback(EntityHero e, float partialTicks) {
        float k = dissolve(e, partialTicks);
        if (k > 0F) {
            // гибель: тело поднимается и стягивается в точку света у груди (ось Y здесь смотрит вниз)
            float s = Math.max(0.001F, 1F - k * k);
            GlStateManager.translate(0F, -0.8F - k * 0.9F, 0F);
            GlStateManager.scale(s, s, s);
            GlStateManager.translate(0F, 0.8F, 0F);
        }
        GlStateManager.scale(1.15F, 1.15F, 1.15F);
        GlStateManager.translate(0F, PoseModel.drop(e, partialTicks), 0F);
    }

    /** 0 — цел, 1 — растаял. */
    static float dissolve(EntityHero e, float partialTicks) {
        int d = e.dyingTicks();
        if (d <= EntityHero.DISSOLVE_FROM) {
            return 0F;
        }
        float t = Math.min(EntityHero.DYING, d + partialTicks);
        return Math.min(1F, (t - EntityHero.DISSOLVE_FROM) / (EntityHero.DYING - EntityHero.DISSOLVE_FROM));
    }
}
