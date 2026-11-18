package ru.death.chaosshard.merchant;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.anchor.TileAnchor;

import javax.annotation.Nullable;
import java.util.List;

/** Колокольчик торговца: ПКМ по своему Якорю — торговец приходит к нему. Многоразовый, с перезарядкой. */
public class ItemMerchantBell extends Item {

    private static final String TAG_NEXT = "chaosshard_bell_next";

    public ItemMerchantBell() {
        setMaxStackSize(1);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        BlockPos anchor = Merchant.anchorAt(world, pos);
        if (anchor == null) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        ItemStack stack = player.getHeldItem(hand);
        TileEntity te = world.getTileEntity(anchor);
        if (!(te instanceof TileAnchor) || ((TileAnchor) te).getOwner() == null || !((TileAnchor) te).getOwner().equals(player.getUniqueID())) {
            player.sendStatusMessage(new TextComponentTranslation("chaosshard.merchant.bell.not_yours"), true);
            return EnumActionResult.SUCCESS;
        }
        long now = Merchant.overworldTime(world);
        long next = stack.hasTagCompound() ? stack.getTagCompound().getLong(TAG_NEXT) : 0;
        if (now < next && !player.capabilities.isCreativeMode) {
            int min = (int) Math.ceil((next - now) / 1200.0);
            player.sendStatusMessage(new TextComponentTranslation("chaosshard.merchant.bell.cooldown", min), true);
            return EnumActionResult.SUCCESS;
        }
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BELL, SoundCategory.PLAYERS, 1.0F, 0.6F);
        EntityMerchant m = Merchant.summon((WorldServer) world, anchor, player.getUniqueID());
        if (m == null) {
            player.sendStatusMessage(new TextComponentTranslation("chaosshard.merchant.bell.no_room"), true);
            return EnumActionResult.SUCCESS;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setLong(TAG_NEXT, now + (long) (ChaosConfig.merchant.bellCooldownDays * Merchant.DAY));
        player.sendStatusMessage(new TextComponentTranslation("chaosshard.merchant.bell.done"), true);
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("\u00A75\u00A7o" + I18n.format("chaosshard.merchant_bell.lore"));
        tooltip.add("\u00A77" + I18n.format("chaosshard.merchant_bell.hint"));
        if (world != null && stack.hasTagCompound()) {
            long left = stack.getTagCompound().getLong(TAG_NEXT) - world.getTotalWorldTime();
            if (left > 0 && world.provider.getDimension() == 0) {
                tooltip.add("§6" + I18n.format("chaosshard.merchant.bell.cooldown", (int) Math.ceil(left / 1200.0)));
            }
        }
    }
}
