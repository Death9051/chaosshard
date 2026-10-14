package ru.death.chaosshard.compat.tinkers;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Set;

import static ru.death.chaosshard.compat.tinkers.ChaosMaterials.*;

/** Подсказка инструмента: уровни материалов Хаоса и запас кристалла. Только клиент. */
public class MaterialTooltips {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack s = event.getItemStack();
        Set<String> mats = materials(s);
        if (mats.isEmpty()) {
            return;
        }
        for (String m : mats) {
            int xp = xp(s, m);
            int l = levelOf(xp);
            String next = l >= 5 ? "max" : xp + "/" + XP[l];
            event.getToolTip().add(TextFormatting.LIGHT_PURPLE + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                    "chaosshard.material.level", net.minecraft.util.text.translation.I18n.translateToLocal("material." + m + ".name"), l, next));
        }
        if (level(s, CRYSTAL) > 0) {
            event.getToolTip().add(TextFormatting.AQUA + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                    "chaosshard.material.store", store(s), storeCap(s)));
        }
    }
}
