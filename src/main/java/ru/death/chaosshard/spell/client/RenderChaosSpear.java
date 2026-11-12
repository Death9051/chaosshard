package ru.death.chaosshard.spell.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.spell.EntityChaosSpear;

import javax.annotation.Nullable;

/** Копьё Хаоса: светящийся вытянутый кристалл (фиолетовый ореол и белая сердцевина), смотрит по направлению полёта. */
public class RenderChaosSpear extends Render<EntityChaosSpear> {

    public RenderChaosSpear(RenderManager rm) {
        super(rm);
        shadowSize = 0F;
    }

    @Override
    public void doRender(EntityChaosSpear e, double x, double y, double z, float yaw, float pt) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(e.rotationYaw, 0, 1, 0);
        GlStateManager.rotate(-e.rotationPitch, 1, 0, 0);
        GlStateManager.rotate((e.ticksExisted + pt) * 40F, 0, 0, 1);   // медленно вращается вдоль оси
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        crystal(1.25, 0.95, 0.20, 0.62F, 0.30F, 1.0F, 0.45F);   // ореол
        crystal(1.10, 0.80, 0.11, 0.85F, 0.60F, 1.0F, 0.75F);   // тело
        crystal(0.95, 0.60, 0.05, 1.0F, 1.0F, 1.0F, 0.95F);     // сердцевина
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        super.doRender(e, x, y, z, yaw, pt);
    }

    /** Вытянутый восьмигранник вдоль +Z: острие tip впереди, хвост tail сзади, ширина r в трети от острия. */
    private static void crystal(double tip, double tail, double r, float cr, float cg, float cb, float a) {
        double mid = tip * 0.35;
        double[][] ring = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 4; i++) {
            double[] p = ring[i], q = ring[(i + 1) % 4];
            float k = i % 2 == 0 ? 1F : 0.8F;
            b.pos(0, 0, tip).color(cr, cg, cb, a).endVertex();
            b.pos(p[0], p[1], mid).color(cr * k, cg * k, cb * k, a).endVertex();
            b.pos(q[0], q[1], mid).color(cr * k, cg * k, cb * k, a).endVertex();
            b.pos(0, 0, -tail).color(cr * 0.6F, cg * 0.6F, cb * 0.8F, a * 0.5F).endVertex();
            b.pos(p[0], p[1], mid).color(cr * k, cg * k, cb * k, a).endVertex();
            b.pos(q[0], q[1], mid).color(cr * k, cg * k, cb * k, a).endVertex();
        }
        t.draw();
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityChaosSpear e) {
        return null;
    }
}
