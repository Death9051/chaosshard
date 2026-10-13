package ru.death.chaosshard.ritual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.ritual.ItemRitualSphere;
import ru.death.chaosshard.ritual.TileRitualSphere;

/** Поставленная сфера парит, медленно вращается и светится в темноте. */
public class SphereRenderer extends TileEntitySpecialRenderer<TileRitualSphere> {

    @Override
    public void render(TileRitualSphere te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        ItemStack s = ItemRitualSphere.of(te.ritual(), 0);
        float time = te.getAge() + partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.45 + Math.sin(time * 0.08) * 0.08, z + 0.5);
        GlStateManager.rotate(time * 2.0F, 0, 1, 0);
        GlStateManager.scale(0.7F, 0.7F, 0.7F);
        // светится сама по себе
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
