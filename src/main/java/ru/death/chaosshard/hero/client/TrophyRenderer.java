package ru.death.chaosshard.hero.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.hero.TileTrophy;
import ru.death.chaosshard.hero.Trophies;

/** Трофей парит над подставкой и медленно вращается (заглушка до настоящих моделей). */
public class TrophyRenderer extends TileEntitySpecialRenderer<TileTrophy> {

    @Override
    public void render(TileTrophy te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        ItemStack stack = Trophies.of(te.hero());
        if (stack.isEmpty()) {
            return;
        }
        float t = te.getWorld().getTotalWorldTime() + pt;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 1.15 + Math.sin(t / 20.0) * 0.05, z + 0.5);
        GlStateManager.rotate(t * 1.5F, 0, 1, 0);
        GlStateManager.scale(0.75F, 0.75F, 0.75F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
