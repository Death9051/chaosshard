package ru.death.chaosshard.breach.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.breach.TileBreachAltar;

/** Подношения медленно кружат над Алтарём Прорыва. */
public class AltarRenderer extends TileEntitySpecialRenderer<TileBreachAltar> {

    @Override
    public void render(TileBreachAltar te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        int count = 0;
        for (ItemStack s : te.getItems()) {
            if (!s.isEmpty()) {
                count++;
            }
        }
        if (count == 0) {
            return;
        }
        float time = te.getAge() + partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 1.25, z + 0.5);
        RenderHelper.enableStandardItemLighting();
        int k = 0;
        for (ItemStack s : te.getItems()) {
            if (s.isEmpty()) {
                continue;
            }
            double a = Math.toRadians(time * 1.2 + k * 360.0 / count);
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(a) * 0.55, Math.sin(time * 0.08 + k) * 0.06, Math.sin(a) * 0.55);
            GlStateManager.rotate(time * 2.5F, 0, 1, 0);
            GlStateManager.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
            k++;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
