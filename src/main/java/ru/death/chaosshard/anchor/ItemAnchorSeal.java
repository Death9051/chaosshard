package ru.death.chaosshard.anchor;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Печать Якоря — модификатор, который навешивают на Якорь Хаоса (по одной печати на уровень Якоря). */
public class ItemAnchorSeal extends Item {

    public enum Type {
        /** Ярость: Скверна ползёт быстрее (+50% попыток за печать). */
        FURY(0xFF5050),
        /** Охват: +6 к потолку радиуса за печать. */
        REACH(0x60A0FF),
        /** Жатва: край питает Нить вдвое чаще за печать. */
        HARVEST(0xFFD040),
        /** Родник: энергия Хаоса на этой земле восстанавливается ещё быстрее. */
        WELL(0xC070FF),
        /** Кровь: смерти тварей в краю питают Скверну (+3 к осквернению за смерть за печать). */
        BLOOD(0xB01020),
        /** Двигатель: Якорь питается RF/FE; пока есть ток, каждая печать удваивает попытки Скверны. */
        ENGINE(0x40E0C0);

        public final int color;

        Type(int color) {
            this.color = color;
        }
    }

    public final Type type;

    public ItemAnchorSeal(Type type) {
        this.type = type;
        setMaxStackSize(16);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format("chaosshard.anchor_seal." + type.name().toLowerCase() + ".desc"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("chaosshard.anchor_seal.hint"));
    }
}
