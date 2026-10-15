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

    /** Высота модели трофея в пикселях модели — чтобы все стояли на подставке одного роста. */
    private static float scaleOf(ru.death.chaosshard.hero.Lightbearer h) {
        switch (h) {
            case KAEL: return 0.56F;
            case VELMOR: return 0.44F;
            case LIAN: return 0.54F;
            case ELIS: return 0.72F;
            default: return 0.64F;
        }
    }

    @Override
    public void render(TileTrophy te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        ItemStack stack = Trophies.of(te.hero());
        if (stack.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.block.model.IBakedModel model = mc.getRenderItem().getItemModelMesher().getModelManager()
                .getModel(new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                        ru.death.chaosshard.Tags.MOD_ID + ":trophy/" + te.hero().id(), "inventory"));
        boolean full = model != mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel();
        float t = te.getWorld().getTotalWorldTime() + pt;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 1.3 + Math.sin(t / 20.0) * 0.05, z + 0.5);
        GlStateManager.rotate(t * 1.5F, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        if (full) {
            // объёмная модель трофея
            float s = scaleOf(te.hero());
            GlStateManager.scale(s, s, s);
            bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            mc.getRenderItem().renderItem(stack, model);
        } else {
            GlStateManager.translate(0, -0.15, 0);
            GlStateManager.scale(0.75F, 0.75F, 0.75F);
            mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        }
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
