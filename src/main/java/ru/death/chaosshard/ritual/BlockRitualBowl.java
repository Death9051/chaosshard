package ru.death.chaosshard.ritual;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModItems;

import javax.annotation.Nullable;

/**
 * Чаша ритуала. ПКМ предметом — положить подношение (Shift — всю стопку),
 * ПКМ пустой рукой — забрать последнее, ПКМ Гримуаром — начать ритуал.
 */
public class BlockRitualBowl extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.1875, 0, 0.1875, 0.8125, 0.53125, 0.8125);

    public BlockRitualBowl() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(2.0F);
        setResistance(10.0F);
        setLightLevel(0.4F);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRitualBowl();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRitualBowl)) {
            return true;
        }
        TileRitualBowl bowl = (TileRitualBowl) te;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == ModItems.GRIMOIRE) {
            bowl.tryStart((EntityPlayerMP) player);
        } else if (held.isEmpty()) {
            bowl.takeLast(player);
        } else {
            bowl.insert(player, held, player.isSneaking());
        }
        return true;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos from) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileRitualBowl) {
                ((TileRitualBowl) te).redstone(world.isBlockPowered(pos));
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRitualBowl) {
            ((TileRitualBowl) te).dropAll();
        }
        super.breakBlock(world, pos, state);
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
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
