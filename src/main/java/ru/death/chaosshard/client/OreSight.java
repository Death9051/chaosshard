package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * «Взгляд сквозь камень»: рамки вокруг руд (и сундуков/спавнеров), видимые сквозь блоки.
 * Цвет по ценности: 0 — обычная руда, 1 — средняя, 2 — дорогая, 3 — сундук/спавнер, 4 — без разбора.
 */
public class OreSight {

    private static long[] pos = new long[0];
    private static byte[] tier = new byte[0];
    private static long until;

    private static final float[][] COLOR = {
            {0.75F, 0.75F, 0.75F}, {1.0F, 0.75F, 0.2F}, {0.3F, 1.0F, 0.5F}, {0.8F, 0.4F, 1.0F}, {0.7F, 0.5F, 1.0F}};

    public static void show(long[] p, byte[] t, int ticks) {
        pos = p;
        tier = t;
        Minecraft mc = Minecraft.getMinecraft();
        until = mc.world == null ? 0 : mc.world.getTotalWorldTime() + ticks;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || pos.length == 0 || mc.world.getTotalWorldTime() > until) {
            return;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0F);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buf = tes.getBuffer();
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < pos.length; i++) {
            BlockPos b = BlockPos.fromLong(pos[i]);
            float[] c = COLOR[Math.max(0, Math.min(tier[i], COLOR.length - 1))];
            AxisAlignedBB box = new AxisAlignedBB(b).grow(0.002).offset(-vx, -vy, -vz);
            RenderGlobal.drawBoundingBox(buf, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, c[0], c[1], c[2], 0.9F);
        }
        tes.draw();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
