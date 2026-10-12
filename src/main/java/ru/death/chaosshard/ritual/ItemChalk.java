package ru.death.chaosshard.ritual;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;

/** Мел Хаоса: ПКМ по верху блока — рисует знак. 64 знака на кусок. */
public class ItemChalk extends Item {

    public ItemChalk() {
        setMaxStackSize(1);
        setMaxDamage(63);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.FAIL;
        }
        BlockPos at = pos.up();
        if (!world.isAirBlock(at) || !ModBlocks.CHALK.canPlaceBlockAt(world, at)) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote) {
            world.setBlockState(at, ModBlocks.CHALK.getDefaultState());
            world.playSound(null, at, net.minecraft.init.SoundEvents.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 0.6F, 1.4F);
            ItemStack stack = player.getHeldItem(hand);
            if (!player.isCreative()) {
                stack.damageItem(1, player);
            }
        }
        return EnumActionResult.SUCCESS;
    }
}
