package ru.death.chaosshard.servant.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.servant.EntityChaosBeast;

import javax.annotation.Nullable;

/** Отрисовка уникальных слуг. Текстуры: textures/entity/servant_<имя>.png (по развёрткам). */
public class RenderChaosBeast<T extends EntityChaosBeast> extends RenderLiving<T> {

    private final ResourceLocation texture;

    public RenderChaosBeast(RenderManager rm, ModelBase model, float shadow, String name) {
        super(rm, model, shadow);
        this.texture = new ResourceLocation(Tags.MOD_ID, "textures/entity/servant_" + name + ".png");
    }

    @Override
    protected void preRenderCallback(T entity, float partialTicks) {
        float g = entity.growth();
        net.minecraft.client.renderer.GlStateManager.scale(g, g, g);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return texture;
    }
}
