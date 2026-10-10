package ru.death.chaosshard.heart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.heart.ItemHeartModule;
import ru.death.chaosshard.heart.TileHeart;
import ru.death.chaosshard.init.ModItems;

/**
 * Живое Сердце: вокруг ядра кружат Осколки Завесы (их число растёт со ступенью),
 * а по внешнему кольцу — вставленные модули. «Пульс» ускоряет вращение.
 */
public class HeartRenderer extends TileEntitySpecialRenderer<TileHeart> {

    @Override
    public void render(TileHeart te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        float time = te.getWorld().getTotalWorldTime() + partialTicks;
        int tier = Math.max(1, te.getTier());
        float speed = 1.0F + 0.25F * te.count(ItemHeartModule.Type.PULSE);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        RenderHelper.enableStandardItemLighting();
        // полная яркость, как у светящегося предмета
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        // Внутреннее кольцо: осколки
        ItemStack shard = new ItemStack(ModItems.VEIL_SHARD);
        int shards = tier * 2;
        float radius = 0.55F + 0.08F * tier;
        for (int i = 0; i < shards; i++) {
            float a = (float) (time * 2.0F * speed + i * 360.0 / shards);
            double rad = Math.toRadians(a);
            float bob = (float) Math.sin((time * 0.08F * speed) + i) * 0.06F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(rad) * radius, 0.55 + 0.05 * tier + bob, Math.sin(rad) * radius);
            GlStateManager.rotate(-a + 90, 0, 1, 0);
            GlStateManager.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getMinecraft().getRenderItem().renderItem(shard, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }

        // Внешнее кольцо: модули (по одному значку на тип, крупнее, если их больше)
        ItemHeartModule.Type[] types = ItemHeartModule.Type.values();
        int present = 0;
        for (ItemHeartModule.Type t : types) {
            if (te.count(t) > 0) {
                present++;
            }
        }
        int k = 0;
        for (ItemHeartModule.Type t : types) {
            int n = te.count(t);
            if (n <= 0) {
                continue;
            }
            Item item = Item.getByNameOrId("chaosshard:heart_module_" + t.name().toLowerCase());
            if (item == null) {
                continue;
            }
            float a = (float) (-time * 0.8F * speed + k * 360.0 / present);
            double rad = Math.toRadians(a);
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(rad) * 1.15, 0.95 + Math.sin(time * 0.05F + k) * 0.08, Math.sin(rad) * 1.15);
            GlStateManager.rotate(time * 3F, 0, 1, 0);
            float sc = 0.3F + 0.04F * Math.min(n, 16);
            GlStateManager.scale(sc, sc, sc);
            Minecraft.getMinecraft().getRenderItem().renderItem(new ItemStack(item), ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
            k++;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(TileHeart te) {
        return true;
    }
}
