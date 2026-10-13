package ru.death.chaosshard.ritual;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Поставленная сфера ритуала: парит над землёй, светится. Сквозь неё можно пройти.
 * Shift+ПКМ пустой рукой или удар — сфера возвращается в руки с остатком времени.
 */
public class BlockRitualSphere extends Block implements ITileEntityProvider {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.25, 0.2, 0.25, 0.75, 0.8, 0.75);

    public BlockRitualSphere() {
        super(Material.GLASS);
        setSoundType(SoundType.GLASS);
        setHardness(0.3F);
        setLightLevel(0.75F);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileRitualSphere();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
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
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking() || !player.getHeldItem(hand).isEmpty()) {
            return false;
        }
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileRitualSphere) {
                ItemHandlerHelper.giveItemToPlayer(player, ((TileRitualSphere) te).toItem());
            }
            world.removeTileEntity(pos);
            world.setBlockToAir(pos);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRitualSphere && ((TileRitualSphere) te).ticksLeft() > 0) {
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 0.3, pos.getZ(), ((TileRitualSphere) te).toItem());
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileRitualSphere ? ((TileRitualSphere) te).toItem() : ItemStack.EMPTY;
    }
}
