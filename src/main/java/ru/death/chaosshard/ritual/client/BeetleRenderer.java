package ru.death.chaosshard.ritual.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.ritual.TilePullBeetle;

/**
 * Клещ Тяги (блок) без текстуры, похож на клеща: плоское брюшко-капля, тёмный щиток у головы, хоботок со щупиками,
 * восемь коленчатых ног, светящиеся фестоны по заднему краю. Чем больше несвёрнутого опыта, тем сильнее брюшко
 * раздувается и наливается зелёным; свернул в Мешочек — снова тощий.
 * Сам по себе неспешно ползает по крышке хранилища (путь — плавная петля, скорость то растёт, то падает почти до
 * остановки), разворачивается по ходу; ноги перебирают в такт, щупики шевелятся. Всё считается от времени мира,
 * на сервере клещ стоит на месте блока.
 */
public class BeetleRenderer extends TileEntitySpecialRenderer<TilePullBeetle> {

    private static final float[] BODY = {0.42F, 0.24F, 0.66F};       // брюшко голодного клеща
    private static final float[] FULL = {0.55F, 0.86F, 0.30F};       // налитое опытом
    private static final float[] SCUTUM = {0.17F, 0.08F, 0.30F};     // спинной щиток
    private static final float[] LEG = {0.24F, 0.14F, 0.36F};
    private static final float[] MOUTH = {0.30F, 0.16F, 0.44F};
    private static final float[] GLOW = {1.0F, 0.55F, 0.95F};
    private static final float[] EYE = {1.0F, 0.85F, 0.40F};
    /** Ноги: смещение от головы к хвосту и разворот (вперёд +, назад −). */
    private static final float[][] LEGS = {{1.7F, 45F}, {0.8F, 18F}, {-0.1F, -12F}, {-1.0F, -40F}};

    /** Смещение по крышке в момент t (у каждого клеща свой сдвиг по фазе). */
    private static double[] at(double t, double seed) {
        double u = t * 0.010 + seed + 0.9 * Math.sin(t * 0.0035 + seed * 2);
        return new double[]{0.14 * Math.sin(u), 0.14 * Math.sin(1.6 * u + seed)};
    }

    @Override
    public void render(TilePullBeetle te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        BlockPos pos = te.getPos();
        double seed = (pos.getX() * 31 + pos.getZ() * 17 + pos.getY() * 7) % 100 / 10.0;
        double t = (te.getWorld() == null ? 0 : te.getWorld().getTotalWorldTime()) + pt;
        double[] p0 = at(t, seed), p1 = at(t + 2, seed);
        double vx = p1[0] - p0[0], vz = p1[1] - p0[1];
        double speed = Math.sqrt(vx * vx + vz * vz);
        float walk = (float) Math.min(1.0, speed * 300) * 0.9F + 0.1F;
        float ry = (float) Math.toDegrees(Math.atan2(vx, vz));
        // посадка на крышку: у сундука она ниже границы блока
        double lid = 1.0;
        if (te.getWorld() != null) {
            lid = te.getWorld().getBlockState(pos.down()).getBoundingBox(te.getWorld(), pos.down()).maxY;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5 + p0[0], y + (lid - 1.0) + 0.01 + Math.sin(t * 0.25) * 0.004, z + 0.5 + p0[1]);
        GlStateManager.rotate(ry, 0, 1, 0);
        GlStateManager.scale(0.8F / 16F, 0.8F / 16F, 0.8F / 16F);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        // насколько налит опытом (0 — голодный, 1 — раздут): брюшко раздувается и зеленеет
        float fill = Math.min(1F, te.storedXp() / 150F);
        float[] body = mix(BODY, FULL, fill * 0.85F);
        double sw = 1 + 0.45 * fill, sh = 1 + 0.9 * fill;

        // восемь ног: бедро вверх к колену, голень вниз на крышку; перебирают попарно в противофазе
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                float ph = (float) Math.sin(t * 0.9F + i * 1.7F + (side > 0 ? Math.PI : 0)) * 14F * walk;
                GlStateManager.pushMatrix();
                GlStateManager.translate(side * 2.1 * sw, 1.5, LEGS[i][0]);
                GlStateManager.rotate(-side * LEGS[i][1] + ph, 0, 1, 0);
                GlStateManager.rotate(side * 28F, 0, 0, 1);
                box(side > 0 ? 0 : -2.8, -0.35, -0.35, side > 0 ? 2.8 : 0, 0.35, 0.35, LEG, 1F);
                GlStateManager.translate(side * 2.7, 0, 0);
                GlStateManager.rotate(-side * 88F, 0, 0, 1);
                box(side > 0 ? 0 : -3.2, -0.28, -0.28, side > 0 ? 3.2 : 0, 0.28, 0.28, LEG, 1F);
                GlStateManager.popMatrix();
            }
        }
        // брюшко: плоская капля, шире к хвосту; ярусы скругляют край
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0.7, -0.6);
        GlStateManager.scale(sw, sh, sw);
        box(-2.6, 0, -3.2, 2.6, 1.4, 2.4, body, 1F);
        box(-3.1, 0.2, -2.6, 3.1, 1.2, 1.6, body, 1F);
        box(-2.0, 0.2, -3.8, 2.0, 1.2, -3.2, body, 1F);
        box(-2.0, 1.4, -2.6, 2.0, 1.9, 1.8, body, 1F);
        // фестоны — светящиеся зубчики по заднему краю
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.15F);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        for (int k = -2; k <= 2; k++) {
            box(k * 1.1 - 0.35, 0.5, -3.85, k * 1.1 + 0.35, 1.0, -3.7, GLOW, pulse);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.popMatrix();
        // спинной щиток у головы — тёмный, с двумя глазками
        box(-1.9, 1.9, 0.6, 1.9, 2.75, 2.8, SCUTUM, 1F);
        box(-1.4, 2.75, 0.9, 1.4, 2.95, 2.5, SCUTUM, 1F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        box(-1.75, 2.2, 2.2, -1.2, 2.6, 2.85, EYE, 1F);
        box(1.2, 2.2, 2.2, 1.75, 2.6, 2.85, EYE, 1F);
        box(-0.5, 2.95, 1.2, 0.5, 3.05, 2.0, GLOW, pulse);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        // головка (хоботок со щупиками) смотрит вперёд и чуть вниз
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 1.7, 2.8);
        GlStateManager.rotate(18F, 1, 0, 0);
        box(-0.9, -0.5, 0, 0.9, 0.5, 1.2, MOUTH, 1F);
        for (int side = -1; side <= 1; side += 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(side * 0.6, 0, 1.1);
            GlStateManager.rotate(side * 10F + (float) Math.sin(t * 0.3F + side) * 6F, 0, 1, 0);
            box(-0.25, -0.3, 0, 0.25, 0.3, 1.3, LEG, 1F);
            GlStateManager.popMatrix();
        }
        box(-0.15, -0.2, 1.1, 0.15, 0.1, 1.9, MOUTH, 1F);
        GlStateManager.popMatrix();

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
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

    private static float[] mix(float[] a, float[] b, float k) {
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static void quad(BufferBuilder b, float[] c, float k, float a, double... v) {
        for (int i = 0; i < 12; i += 3) {
            b.pos(v[i], v[i + 1], v[i + 2]).color(c[0] * k, c[1] * k, c[2] * k, a).endVertex();
        }
    }

}
