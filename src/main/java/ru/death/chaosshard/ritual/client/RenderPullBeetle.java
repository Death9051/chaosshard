package ru.death.chaosshard.ritual.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.ritual.EntityPullBeetle;

import javax.annotation.Nullable;

/**
 * Жучок Тяги без текстуры: цветные брусочки (панцирь, голова, шесть лапок, усики) со светящимися пятнами.
 * Лапки перебирают, пока жучок ползёт, и чуть подрагивают на месте; усики шевелятся.
 */
public class RenderPullBeetle extends Render<EntityPullBeetle> {

    private static final float[] SHELL = {0.52F, 0.28F, 0.90F};
    private static final float[] SHELL_DARK = {0.30F, 0.14F, 0.55F};
    private static final float[] BODY = {0.16F, 0.10F, 0.24F};
    private static final float[] GLOW = {1.0F, 0.55F, 0.95F};
    private static final float[] EYE = {1.0F, 0.85F, 0.40F};

    public RenderPullBeetle(RenderManager rm) {
        super(rm);
        shadowSize = 0.15F;
    }

    @Override
    public void doRender(EntityPullBeetle e, double x, double y, double z, float yaw, float pt) {
        float t = e.ticksExisted + pt;
        double moved = Math.sqrt((e.posX - e.prevPosX) * (e.posX - e.prevPosX) + (e.posZ - e.prevPosZ) * (e.posZ - e.prevPosZ));
        float walk = moved > 0.002 ? 1F : 0.15F;
        float ry = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + 0.02 + Math.sin(t * 0.25) * 0.004, z);
        GlStateManager.rotate(-ry, 0, 1, 0);
        GlStateManager.scale(1 / 16F, 1 / 16F, 1 / 16F);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        // лапки: по три с боку, перебирают в противофазе
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float ph = (float) Math.sin(t * 0.9F + i * 2.1F + (side > 0 ? Math.PI : 0)) * 25F * walk;
                GlStateManager.pushMatrix();
                GlStateManager.translate(side * 2.6, 1.4, -2 + i * 2);
                GlStateManager.rotate(ph, 0, 1, 0);
                GlStateManager.rotate(side * 35F, 0, 0, 1);
                box(side > 0 ? 0 : -3.2, -0.4, -0.4, side > 0 ? 3.2 : 0, 0.4, 0.4, BODY, 1F);
                GlStateManager.popMatrix();
            }
        }
        // брюшко и панцирь
        box(-2.6, 0.6, -3.6, 2.6, 2.2, 3.0, BODY, 1F);
        box(-3.0, 1.6, -4.0, 3.0, 4.0, 2.6, SHELL, 1F);
        box(-0.25, 3.98, -4.0, 0.25, 4.05, 2.6, SHELL_DARK, 1F);   // шов надкрылий
        // голова, глаза, жвалы
        box(-1.8, 1.0, 2.6, 1.8, 3.0, 4.6, BODY, 1F);
        // усики шевелятся
        for (int side = -1; side <= 1; side += 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(side * 0.9, 2.8, 4.4);
            GlStateManager.rotate(-30F + (float) Math.sin(t * 0.3F + side) * 12F, 1, 0, 0);
            GlStateManager.rotate(side * (15F + (float) Math.sin(t * 0.21F) * 8F), 0, 1, 0);
            box(-0.2, -0.2, 0, 0.2, 0.2, 3.2, BODY, 1F);
            GlStateManager.popMatrix();
        }
        // светящиеся пятна и глаза — полная яркость
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.15F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        box(-2.2, 4.0, -2.6, -1.0, 4.2, -1.4, GLOW, pulse);
        box(1.0, 4.0, -0.6, 2.2, 4.2, 0.6, GLOW, pulse);
        box(-1.8, 4.0, 1.0, -0.8, 4.2, 2.0, GLOW, pulse);
        box(1.2, 4.0, -3.2, 2.0, 4.2, -2.4, GLOW, pulse);
        box(-1.4, 2.0, 4.6, -0.6, 2.7, 4.75, EYE, 1F);
        box(0.6, 2.0, 4.6, 1.4, 2.7, 4.75, EYE, 1F);

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        super.doRender(e, x, y, z, yaw, pt);
    }

    /** Брусок с простым светом граней: верх ярче, бока темнее, низ темнее всего. */
    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, float[] c, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        quad(b, c, 1.0F, a, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);    // верх
        quad(b, c, 0.5F, a, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);    // низ
        quad(b, c, 0.8F, a, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);    // перед
        quad(b, c, 0.7F, a, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);    // зад
        quad(b, c, 0.75F, a, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);   // лево
        quad(b, c, 0.65F, a, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);   // право
        t.draw();
    }

    private static void quad(BufferBuilder b, float[] c, float k, float a, double... v) {
        for (int i = 0; i < 12; i += 3) {
            b.pos(v[i], v[i + 1], v[i + 2]).color(c[0] * k, c[1] * k, c[2] * k, a).endVertex();
        }
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityPullBeetle e) {
        return null;
    }
}
