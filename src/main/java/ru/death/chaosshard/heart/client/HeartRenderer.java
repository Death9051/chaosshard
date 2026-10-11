package ru.death.chaosshard.heart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.heart.ItemHeartModule;
import ru.death.chaosshard.heart.TileHeart;

/**
 * Сферы вокруг Сердца: по одной на каждый вставленный модуль, цвет — по типу модуля.
 * Чем больше модулей, тем быстрее они кружат. Без модулей ничего не рисуется.
 */
public class HeartRenderer extends TileEntitySpecialRenderer<TileHeart> {

    private static final ResourceLocation ORB = new ResourceLocation(Tags.MOD_ID, "textures/misc/heart_orb.png");
    /** Цвета модулей: Пульс, Ёмкость, Богатство, Плодовитость. */
    private static final float[][] COLORS = {
            {1.0F, 0.35F, 0.35F}, {0.4F, 0.6F, 1.0F}, {1.0F, 0.85F, 0.3F}, {0.4F, 1.0F, 0.5F}
    };

    @Override
    public void render(TileHeart te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        ItemHeartModule.Type[] types = ItemHeartModule.Type.values();
        int total = 0;
        for (ItemHeartModule.Type t : types) {
            total += te.count(t);
        }
        if (total <= 0) {
            return;
        }
        float time = te.getWorld().getTotalWorldTime() + partialTicks;
        float speed = 1.0F + 0.35F * total;          // больше модулей — быстрее
        int orbs = Math.min(total, 16);

        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.55, z + 0.5);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        bindTexture(ORB);

        int k = 0;
        for (int ti = 0; ti < types.length; ti++) {
            int n = te.count(types[ti]);
            for (int j = 0; j < n && k < orbs; j++, k++) {
                double a = Math.toRadians(time * 2.2F * speed + k * 360.0 / orbs);
                float radius = 0.75F + 0.1F * (k % 2);
                float bob = (float) Math.sin(time * 0.07F + k * 1.3F) * 0.12F;
                float pulse = 0.85F + 0.15F * (float) Math.sin(time * 0.2F + k);
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(a) * radius, bob, Math.sin(a) * radius);
                // всегда лицом к камере
                GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
                GlStateManager.rotate(rm.playerViewX, 1, 0, 0);
                float s = 0.16F * pulse;
                float[] c = COLORS[ti % COLORS.length];
                quad(s, c, 0.95F);
                quad(s * 0.45F, new float[]{1F, 1F, 1F}, 0.9F); // яркое ядро
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void quad(float s, float[] c, float a) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        b.pos(-s, -s, 0).tex(0, 1).color(c[0], c[1], c[2], a).endVertex();
        b.pos(s, -s, 0).tex(1, 1).color(c[0], c[1], c[2], a).endVertex();
        b.pos(s, s, 0).tex(1, 0).color(c[0], c[1], c[2], a).endVertex();
        b.pos(-s, s, 0).tex(0, 0).color(c[0], c[1], c[2], a).endVertex();
        tess.draw();
    }

    @Override
    public boolean isGlobalRenderer(TileHeart te) {
        return true;
    }
}
