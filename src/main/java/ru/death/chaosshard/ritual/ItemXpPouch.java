package ru.death.chaosshard.ritual;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.init.ModItems;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Мешочек опыта: опыт, собранный Тягой (Жучком или сферой над хранилищем) за последний промежуток.
 * Сколько собрано — столько внутри (NBT «Xp»), без верхней границы. ПКМ — забрать опыт, присев — всю стопку.
 */
public class ItemXpPouch extends Item {

    public static final String XP = "Xp";
    /** Как часто Тяга сворачивает накопленный опыт в брюшко. */
    public static final int PACK_TICKS = 20 * 60;

    public ItemXpPouch() {
        setMaxStackSize(16);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    public static ItemStack of(int xp) {
        ItemStack s = new ItemStack(ModItems.XP_POUCH);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger(XP, Math.max(0, xp));
        s.setTagCompound(t);
        return s;
    }

    public static int xp(ItemStack s) {
        return s.hasTagCompound() ? s.getTagCompound().getInteger(XP) : 0;
    }

    /**
     * Свернуть накопленный опыт в брюшко и положить в хранилище. Возвращает, сколько опыта осталось несвёрнутым
     * (0 — всё легло; столько же — хранилищу некуда, копим дальше).
     */
    public static int pack(IItemHandler inv, int xp) {
        if (xp <= 0 || inv == null) {
            return Math.max(0, xp);
        }
        return ItemHandlerHelper.insertItemStacked(inv, of(xp), false).isEmpty() ? 0 : xp;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        int n = player.isSneaking() ? stack.getCount() : 1;
        int total = xp(stack) * n;
        if (!world.isRemote) {
            player.addExperience(total);
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS,
                    0.6F, 0.8F + world.rand.nextFloat() * 0.4F);
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.BLOCK_SLIME_BREAK, SoundCategory.PLAYERS, 0.5F, 1.4F);
        }
        stack.shrink(n);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GREEN + I18n.translateToLocalFormatted("chaosshard.xp_pouch.amount", xp(stack)));
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("chaosshard.xp_pouch.lore"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("chaosshard.xp_pouch.hint"));
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.UNCOMMON;
    }
}
