package ru.death.chaosshard.anchor;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.init.ModBlocks;

import java.util.Random;

/** Основание Альфы-Якоря 3×3: восемь плит вокруг Якоря. Ставится только эволюцией; ПКМ — как по Якорю. */
public class BlockAnchorRim extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 0.375, 1);

    public BlockAnchorRim() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(6.0F);
        setResistance(2000.0F);
        setHarvestLevel("pickaxe", 1);
    }

    /** Якорь, вокруг которого плита. */
    public static BlockPos center(IBlockAccess w, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = pos.add(dx, 0, dz);
                if (w.getBlockState(p).getBlock() == ModBlocks.CHAOS_ANCHOR) {
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        BlockPos c = center(world, pos);
        return c != null && ModBlocks.CHAOS_ANCHOR.onBlockActivated(world, c, world.getBlockState(c), player, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (center(world, pos) == null) {
            world.setBlockToAir(pos);
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(ModBlocks.CHAOS_ANCHOR);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
