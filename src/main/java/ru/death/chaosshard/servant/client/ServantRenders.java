package ru.death.chaosshard.servant.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderVindicator;
import net.minecraft.client.renderer.entity.RenderWitherSkeleton;
import net.minecraft.client.renderer.entity.RenderZombie;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;

/** Свита Хаоса: ванильные модели, свои шкуры. */
public final class ServantRenders {

    private ServantRenders() {
    }

    static ResourceLocation tex(String name) {
        return new ResourceLocation(Tags.MOD_ID, "textures/entity/servant/" + name + ".png");
    }

    public static class Husk extends RenderZombie {
        private final ResourceLocation tex;

        public Husk(RenderManager rm, String name) {
            super(rm);
            this.tex = tex(name);
        }

        @Override
        protected void preRenderCallback(EntityZombie e, float partial) {
            GlStateManager.scale(1.0625F, 1.0625F, 1.0625F);
            super.preRenderCallback(e, partial);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityZombie e) {
            return tex;
        }
    }

    public static class Bone extends RenderWitherSkeleton {
        private static final ResourceLocation TEX = tex("bone");

        public Bone(RenderManager rm) {
            super(rm);
        }

        @Override
        protected ResourceLocation getEntityTexture(AbstractSkeleton e) {
            return TEX;
        }
    }

    public static class Executioner extends RenderVindicator {
        private static final ResourceLocation TEX = tex("executioner");

        public Executioner(RenderManager rm) {
            super(rm);
        }

        @Override
        protected ResourceLocation getEntityTexture(net.minecraft.entity.monster.EntityMob e) {
            return TEX;
        }
    }
}
