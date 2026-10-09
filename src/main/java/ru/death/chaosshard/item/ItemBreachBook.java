package ru.death.chaosshard.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.breach.Breach;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Книга Прорыва. Первый раз её читают на Алтаре Прорыва (ритуал).
 * После прорыва ПКМ — переход между Преддверием и Лучезарией.
 */
public class ItemBreachBook extends Item {

    private static final int COOLDOWN = 100;

    public ItemBreachBook() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (!Breach.hasBreached(mp)) {
            mp.sendStatusMessage(Breach.grimoire("chaosshard.breach_book.sealed"), true);
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        mp.getCooldownTracker().setCooldown(this, COOLDOWN);
        Breach.travel(mp);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
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
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.format("chaosshard.breach_book.lore"));
        tooltip.add(TextFormatting.GRAY + I18n.format("chaosshard.breach_book.use1"));
        tooltip.add(TextFormatting.GRAY + I18n.format("chaosshard.breach_book.use2"));
    }
}
