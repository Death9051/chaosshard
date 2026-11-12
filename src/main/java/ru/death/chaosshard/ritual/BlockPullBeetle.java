package ru.death.chaosshard.ritual;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nullable;

/**
 * Жучок Тяги — блок на крышке хранилища (сундук, бочка, ящик): сфера Тяги, поставленная на хранилище.
 * Без срока стягивает вещи и сферы опыта в {@link TilePullBeetle#RADIUS} блоках и складывает вещи в хранилище под собой.
 * Открывать сундук не мешает. Сломать — вернётся сфера Тяги; без хранилища под собой жучок сам становится сферой.
 * Рисуется {@code BeetleRenderer}: ползает по крышке и перебирает лапками.
 */
public class BlockPullBeetle extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.2, 0, 0.2, 0.8, 0.32, 0.8);

    public BlockPullBeetle() {
        super(Material.CIRCUITS);
        setHardness(0.3F);
        setSoundType(SoundType.SLIME);
        setLightLevel(0.25F);
    }

    /** Есть ли под позицией хранилище, в которое можно класть сверху. */
    public static boolean hasInventory(IBlockAccess world, BlockPos below) {
        TileEntity te = world.getTileEntity(below);
        return te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TilePullBeetle();
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

    /** Крышку сундука жучок не держит. */
    @Override
    public boolean doesSideBlockChestOpening(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && hasInventory(world, pos.down());
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && !hasInventory(world, pos.down())) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(ItemRitualSphere.of(Ritual.PULL, 0));
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return ItemRitualSphere.of(Ritual.PULL, 0);
    }
}
