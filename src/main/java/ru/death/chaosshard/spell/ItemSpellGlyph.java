package ru.death.chaosshard.spell;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Глиф Хаоса: модификатор заклинания. Метаданные — вид глифа ({@link SpellGlyph}). */
public class ItemSpellGlyph extends Item {

    public ItemSpellGlyph() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    public static ItemStack of(SpellGlyph g) {
        return new ItemStack(ru.death.chaosshard.init.ModItems.SPELL_GLYPH, 1, g.ordinal());
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        SpellGlyph g = SpellGlyph.byMeta(stack.getMetadata());
        return "item.chaosshard.spell_glyph." + (g == null ? "blank" : g.id());
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) {
            for (SpellGlyph g : SpellGlyph.values()) {
                items.add(of(g));
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        SpellGlyph g = SpellGlyph.byMeta(stack.getMetadata());
        if (g != null) {
            tooltip.add("§7" + I18n.format(g.key() + ".desc"));
        }
        tooltip.add("§8" + I18n.format("chaosshard.glyph.howto"));
    }
}
