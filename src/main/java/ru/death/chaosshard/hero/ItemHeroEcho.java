package ru.death.chaosshard.hero;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Отголосок Светоча: то, что осталось от приёма героя, когда он пал. Метаданные — герой.
 * В Tinkers' Construct — модификатор с его приёмом.
 */
public class ItemHeroEcho extends Item {

    public ItemHeroEcho() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
    }

    public static ItemStack of(Lightbearer h) {
        return new ItemStack(ru.death.chaosshard.init.ModItems.HERO_ECHO, 1, h.ordinal());
    }

    public static Lightbearer hero(ItemStack s) {
        Lightbearer[] all = Lightbearer.values();
        return all[Math.max(0, Math.min(all.length - 1, s.getMetadata()))];
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return "item.chaosshard.hero_echo." + hero(stack).id();
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) {
            for (Lightbearer h : Lightbearer.values()) {
                items.add(of(h));
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GOLD + I18n.translateToLocal("item.chaosshard.hero_echo." + hero(stack).id() + ".desc"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("chaosshard.hero_echo.hint"));
    }
}
