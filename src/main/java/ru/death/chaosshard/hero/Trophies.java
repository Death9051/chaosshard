package ru.death.chaosshard.hero;

import net.minecraft.item.ItemStack;
import ru.death.chaosshard.init.ModItems;

/** Трофеи Светочей — падают к ногам павшего героя. */
public final class Trophies {

    public static ItemStack of(Lightbearer h) {
        switch (h) {
            case KAEL: return ModItems.KAEL_BLADE == null ? ItemStack.EMPTY : new ItemStack(ModItems.KAEL_BLADE);
            case LIAN: return ModItems.LIAN_LUTE == null ? ItemStack.EMPTY : new ItemStack(ModItems.LIAN_LUTE);
            case ELIS: return ModItems.ELIS_SIGNET == null ? ItemStack.EMPTY : new ItemStack(ModItems.ELIS_SIGNET);
            case VELMOR: return ModItems.VELMOR_STAFF == null ? ItemStack.EMPTY : new ItemStack(ModItems.VELMOR_STAFF);
            case IRENA: return ModItems.IRENA_ROSARY == null ? ItemStack.EMPTY : new ItemStack(ModItems.IRENA_ROSARY);
            default: return ItemStack.EMPTY;
        }
    }

    /** Трофей предмета (или null). */
    public static Lightbearer heroOf(net.minecraft.item.Item item) {
        for (Lightbearer h : Lightbearer.values()) {
            ItemStack s = of(h);
            if (!s.isEmpty() && s.getItem() == item) {
                return h;
            }
        }
        return null;
    }

    /** Shift+ПКМ трофеем по блоку — поставить на подставку. */
    public static net.minecraft.util.EnumActionResult place(net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world,
                                                            net.minecraft.util.math.BlockPos pos, net.minecraft.util.EnumFacing facing,
                                                            net.minecraft.util.EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        Lightbearer h = heroOf(stack.getItem());
        if (h == null || !player.isSneaking()) {
            return net.minecraft.util.EnumActionResult.PASS;
        }
        net.minecraft.util.math.BlockPos at = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(facing);
        if (!world.isAirBlock(at) && !world.getBlockState(at).getBlock().isReplaceable(world, at)
                || !player.canPlayerEdit(at, facing, stack)
                || !world.getBlockState(at.down()).isSideSolid(world, at.down(), net.minecraft.util.EnumFacing.UP)) {
            return net.minecraft.util.EnumActionResult.FAIL;
        }
        if (world.isRemote) {
            return net.minecraft.util.EnumActionResult.SUCCESS;
        }
        world.setBlockState(at, ru.death.chaosshard.init.ModBlocks.TROPHY.getDefaultState().withProperty(BlockTrophy.HERO, h.ordinal()), 3);
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(at);
        if (te instanceof TileTrophy) {
            ((TileTrophy) te).setOwner(player.getUniqueID());
            boolean ok = ((TileTrophy) te).checkLair();
            player.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire(ok ? "chaosshard.trophy.placed" : "chaosshard.trophy.no_lair"), true);
            if (ok && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ru.death.chaosshard.quest.Milestones.grant((net.minecraft.entity.player.EntityPlayerMP) player, ru.death.chaosshard.quest.Milestones.TROPHY_HALL);
            }
        }
        world.playSound(null, at, net.minecraft.init.SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, net.minecraft.util.SoundCategory.BLOCKS, 0.8F, 0.8F);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return net.minecraft.util.EnumActionResult.SUCCESS;
    }

    private Trophies() {
    }
}
