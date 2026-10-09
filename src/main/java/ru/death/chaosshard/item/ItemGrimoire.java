package ru.death.chaosshard.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosShard;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Гримуар Осколка. ПКМ — открыть книгу квестов Better Questing.
 */
public class ItemGrimoire extends Item {

    public ItemGrimoire() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            if (Loader.isModLoaded("betterquesting")) {
                ChaosShard.proxy.openGrimoire();
            } else {
                player.sendMessage(new TextComponentTranslation("chaosshard.grimoire.no_bq"));
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    /** Гримуар — катализатор Преобразования: в верстаке он не тратится. */
    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.format("item.chaosshard.grimoire.lore"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.grimoire.use"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.grimoire.craft"));
    }
}
