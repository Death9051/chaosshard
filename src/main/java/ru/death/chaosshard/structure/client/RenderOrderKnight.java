package ru.death.chaosshard.structure.client;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.structure.EntityOrderKnight;

/** Стража Ордена: модель игрока (со вторым слоем скина), Хранитель — чуть крупнее. */
public class RenderOrderKnight extends RenderBiped<EntityOrderKnight> {

    private static final ResourceLocation[] TEX = {
            new ResourceLocation(Tags.MOD_ID, "textures/entity/order/guard.png"),
            new ResourceLocation(Tags.MOD_ID, "textures/entity/order/captain.png"),
            new ResourceLocation(Tags.MOD_ID, "textures/entity/order/keeper.png")};

    public RenderOrderKnight(RenderManager rm) {
        super(rm, new ModelPlayer(0.0F, false), 0.5F);
        addLayer(new LayerBipedArmor(this));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityOrderKnight e) {
        return TEX[Math.max(0, Math.min(2, e.variant()))];
    }

    @Override
    protected void preRenderCallback(EntityOrderKnight e, float partialTicks) {
        float s = e.variant() == EntityOrderKnight.KEEPER ? 1.2F : 0.9375F;
        GlStateManager.scale(s, s, s);
    }
}
