package ru.death.chaosshard.ritual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.ritual.TileRitualBowl;

/** Подношения кружат над Чашей; когда ритуал идёт — быстрее и выше. */
public class BowlRenderer extends TileEntitySpecialRenderer<TileRitualBowl> {

    @Override
    public void render(TileRitualBowl te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        int count = 0;
        for (ItemStack s : te.getItems()) {
            if (!s.isEmpty()) {
                count++;
            }
        }
        boolean running = te.getActive() != null;
        if (count == 0 && !running) {
            return;
        }
        float time = te.getAge() + partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + (running ? 1.3 : 0.9), z + 0.5);
        RenderHelper.enableStandardItemLighting();
        int k = 0;
        for (ItemStack s : te.getItems()) {
            if (s.isEmpty()) {
                continue;
            }
            double a = Math.toRadians(time * (running ? 6 : 1.5) + k * 360.0 / count);
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(a) * 0.4, Math.sin(time * 0.1 + k) * 0.05, Math.sin(a) * 0.4);
            GlStateManager.rotate(time * 3, 0, 1, 0);
            GlStateManager.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
            k++;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
