package ru.death.chaosshard.heart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.network.PacketHeartBolt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Тонкие ломаные дуги в духе виспов Thaumcraft: от Сердца к месту нароста.
 * Живут полсекунды, каждые пару тиков «перещёлкиваются» в новую форму и гаснут.
 */
public class HeartBolts {

    private static final int LIFE = 10;
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final Random RAND = new Random();

    private static final class Bolt {
        final double[] a, b;
        final float r, g, bl;
        int age;
        double[][] main, branch;

        Bolt(PacketHeartBolt p) {
            a = new double[]{p.x1, p.y1, p.z1};
            b = new double[]{p.x2, p.y2, p.z2};
            r = ((p.color >> 16) & 255) / 255F;
            g = ((p.color >> 8) & 255) / 255F;
            bl = (p.color & 255) / 255F;
            reshape();
        }

        void reshape() {
            double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            main = path(a, b, Math.max(4, (int) (len * 3)), 0.18);
            // короткое ответвление из середины
            double[] mid = main[main.length / 2];
            double[] end = {mid[0] + (RAND.nextDouble() - 0.5) * 0.9, mid[1] + (RAND.nextDouble() - 0.5) * 0.9,
                    mid[2] + (RAND.nextDouble() - 0.5) * 0.9};
            branch = path(mid, end, 3, 0.12);
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double[][] path(double[] a, double[] b, int segs, double jitter) {
        double[][] pts = new double[segs + 1][];
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / segs;
            double j = (i == 0 || i == segs) ? 0 : jitter;
            pts[i] = new double[]{
                    a[0] + (b[0] - a[0]) * t + (RAND.nextDouble() - 0.5) * 2 * j,
                    a[1] + (b[1] - a[1]) * t + (RAND.nextDouble() - 0.5) * 2 * j,
                    a[2] + (b[2] - a[2]) * t + (RAND.nextDouble() - 0.5) * 2 * j};
        }
        return pts;
    }

    public static void add(PacketHeartBolt p) {
        synchronized (BOLTS) {
            if (BOLTS.size() < 32) {
                BOLTS.add(new Bolt(p));
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getMinecraft().isGamePaused()) {
            return;
        }
        synchronized (BOLTS) {
            Iterator<Bolt> it = BOLTS.iterator();
            while (it.hasNext()) {
                Bolt b = it.next();
                if (++b.age >= LIFE) {
                    it.remove();
                } else if (b.age % 2 == 0) {
                    b.reshape();
                }
            }
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        synchronized (BOLTS) {
            if (BOLTS.isEmpty()) {
                return;
            }
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            if (view == null) {
                return;
            }
            float pt = event.getPartialTicks();
            double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
            double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
            double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

            GlStateManager.pushMatrix();
            GlStateManager.translate(-cx, -cy, -cz);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

            for (Bolt b : BOLTS) {
                float fade = 1F - (b.age + pt) / LIFE;
                if (fade <= 0) {
                    continue;
                }
                // ореол
                GL11.glLineWidth(5F);
                strip(b.main, b.r, b.g, b.bl, 0.35F * fade);
                GL11.glLineWidth(3F);
                strip(b.branch, b.r, b.g, b.bl, 0.25F * fade);
                // яркая сердцевина
                GL11.glLineWidth(1.5F);
                strip(b.main, 1F, 0.9F + 0.1F * b.g, 1F, 0.9F * fade);
                strip(b.branch, 1F, 0.9F, 1F, 0.6F * fade);
            }

            GL11.glLineWidth(1F);
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }

    private static void strip(double[][] pts, float r, float g, float b, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (double[] p : pts) {
            buf.pos(p[0], p[1], p[2]).color(r, g, b, a).endVertex();
        }
        t.draw();
    }
}
