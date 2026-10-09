package ru.death.chaosshard.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Простой предмет с подсказкой-описанием. */
public class ItemLore extends Item {

    private final String loreKey;
    private final boolean shiny;

    public ItemLore(String loreKey, boolean shiny) {
        this.loreKey = loreKey;
        this.shiny = shiny;
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return shiny;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return shiny ? EnumRarity.RARE : EnumRarity.UNCOMMON;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.format(loreKey));
    }
}
