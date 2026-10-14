package ru.death.chaosshard.anchor.client;

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
import ru.death.chaosshard.anchor.ItemAnchorSeal;
import ru.death.chaosshard.anchor.TileAnchor;

import java.util.List;

/** Печати Якоря — светящиеся сферы, кружащие у вершины колонны. */
public class AnchorRenderer extends TileEntitySpecialRenderer<TileAnchor> {

    private static final ResourceLocation ORB = new ResourceLocation(Tags.MOD_ID, "textures/misc/heart_orb.png");

    @Override
    public void render(TileAnchor te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        List<ItemAnchorSeal.Type> seals = te.getSeals();
        if (te.getWorld() == null || (seals.isEmpty() && !te.isAlpha())) {
            return;
        }
        float time = te.getWorld().getTotalWorldTime() + partialTicks;
        double h = te.columnHeight() + 0.6;
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + h, z + 0.5);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        bindTexture(ORB);
        if (te.isAlpha()) {
            // Альфа: парящий кристалл над вершиной
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 1.6 + Math.sin(time * 0.05F) * 0.25, 0);
            GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
            GlStateManager.rotate(rm.playerViewX, 1, 0, 0);
            float pulse = 0.85F + 0.15F * (float) Math.sin(time * 0.12F);
            quad(0.75F * pulse, new float[]{0.75F, 0.35F, 1F}, 0.9F);
            quad(0.35F, new float[]{1F, 0.85F, 1F}, 0.95F);
            GlStateManager.popMatrix();
        }
        int n = seals.size();
        for (int i = 0; i < n; i++) {
            ItemAnchorSeal.Type t = seals.get(i);
            double a = Math.toRadians(time * 1.6F + i * 360.0 / n);
            float bob = (float) Math.sin(time * 0.06F + i * 1.7F) * 0.2F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(a) * 1.1, bob, Math.sin(a) * 1.1);
            GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
            GlStateManager.rotate(rm.playerViewX, 1, 0, 0);
            float[] c = {((t.color >> 16) & 255) / 255F, ((t.color >> 8) & 255) / 255F, (t.color & 255) / 255F};
            quad(0.2F, c, 0.95F);
            quad(0.09F, new float[]{1F, 1F, 1F}, 0.9F);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void quad(float s, float[] c, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        b.pos(-s, -s, 0).tex(0, 1).color(c[0], c[1], c[2], a).endVertex();
        b.pos(s, -s, 0).tex(1, 1).color(c[0], c[1], c[2], a).endVertex();
        b.pos(s, s, 0).tex(1, 0).color(c[0], c[1], c[2], a).endVertex();
        b.pos(-s, s, 0).tex(0, 0).color(c[0], c[1], c[2], a).endVertex();
        t.draw();
    }
}
