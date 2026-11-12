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
 * Жучок Тяги (блок) без текстуры: цветные брусочки (панцирь, голова, шесть лапок, усики) со светящимися пятнами.
 * Сам по себе неспешно ползает по крышке хранилища (путь — плавная петля, скорость то растёт, то падает почти до
 * остановки), разворачивается по ходу; лапки перебирают в такт, усики шевелятся. Всё считается от времени мира,
 * на сервере жучок стоит на месте блока.
 */
public class BeetleRenderer extends TileEntitySpecialRenderer<TilePullBeetle> {

    private static final float[] SHELL = {0.52F, 0.28F, 0.90F};
    private static final float[] SHELL_DARK = {0.30F, 0.14F, 0.55F};
    private static final float[] BODY = {0.16F, 0.10F, 0.24F};
    private static final float[] LEG = {0.36F, 0.24F, 0.52F};
    private static final float[] GLOW = {1.0F, 0.55F, 0.95F};
    private static final float[] EYE = {1.0F, 0.85F, 0.40F};

    /** Смещение по крышке в момент t (у каждого жучка свой сдвиг по фазе). */
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

        // лапки: по три с боку, перебирают в противофазе
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float ph = (float) Math.sin(t * 0.9F + i * 2.1F + (side > 0 ? Math.PI : 0)) * 25F * walk;
                GlStateManager.pushMatrix();
                GlStateManager.translate(side * 2.6, 1.4, -2 + i * 2);
                GlStateManager.rotate(ph, 0, 1, 0);
                GlStateManager.rotate(side * 35F, 0, 0, 1);
                box(side > 0 ? 0 : -3.4, -0.45, -0.45, side > 0 ? 3.4 : 0, 0.45, 0.45, LEG, 1F);
                GlStateManager.popMatrix();
            }
        }
        // брюшко и панцирь
        box(-2.6, 0.6, -3.6, 2.6, 2.2, 3.0, BODY, 1F);
        // панцирь округлый: нижний широкий ярус, верхний уже, «нос» и «хвост» скошены ступеньками
        box(-3.0, 1.6, -3.4, 3.0, 3.0, 2.2, SHELL, 1F);
        box(-2.4, 1.6, -4.2, 2.4, 2.8, -3.4, SHELL, 1F);
        box(-2.4, 1.6, 2.2, 2.4, 2.8, 2.9, SHELL, 1F);
        box(-2.4, 3.0, -3.0, 2.4, 3.8, 1.8, SHELL, 1F);
        box(-1.6, 3.8, -2.2, 1.6, 4.3, 1.0, SHELL, 1F);
        box(-0.2, 3.0, -4.2, 0.2, 4.35, 2.9, SHELL_DARK, 1F);   // шов надкрылий
        // голова, глаза, жвалы
        box(-1.8, 1.0, 2.6, 1.8, 3.0, 4.6, BODY, 1F);
        // усики шевелятся
        for (int side = -1; side <= 1; side += 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(side * 0.9, 2.8, 4.4);
            GlStateManager.rotate(-30F + (float) Math.sin(t * 0.3F + side) * 12F, 1, 0, 0);
            GlStateManager.rotate(side * (15F + (float) Math.sin(t * 0.21F) * 8F), 0, 1, 0);
            box(-0.25, -0.25, 0, 0.25, 0.25, 3.4, LEG, 1F);
            GlStateManager.popMatrix();
        }
        // светящиеся пятна и глаза — полная яркость
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.15F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        box(-1.4, 4.3, -1.8, -0.5, 4.45, -0.9, GLOW, pulse);
        box(0.5, 4.3, -0.4, 1.4, 4.45, 0.5, GLOW, pulse);
        box(-2.3, 3.8, 0.6, -1.4, 3.95, 1.5, GLOW, pulse);
        box(1.4, 3.8, -2.6, 2.3, 3.95, -1.7, GLOW, pulse);
        box(-1.4, 2.0, 4.6, -0.6, 2.7, 4.75, EYE, 1F);
        box(0.6, 2.0, 4.6, 1.4, 2.7, 4.75, EYE, 1F);

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

    private static void quad(BufferBuilder b, float[] c, float k, float a, double... v) {
        for (int i = 0; i < 12; i += 3) {
            b.pos(v[i], v[i + 1], v[i + 2]).color(c[0] * k, c[1] * k, c[2] * k, a).endVertex();
        }
    }

}
