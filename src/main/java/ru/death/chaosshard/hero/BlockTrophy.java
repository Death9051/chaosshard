package ru.death.chaosshard.hero;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Подставка с трофеем Светоча. В стенах Логова хозяина даёт ему пассивку героя. Ломается — трофей выпадает. */
public class BlockTrophy extends Block {

    public static final PropertyInteger HERO = PropertyInteger.create("hero", 0, 4);
    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.125, 0, 0.125, 0.875, 0.8125, 0.875);

    public BlockTrophy() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
        setLightLevel(0.4F);
        setDefaultState(blockState.getBaseState().withProperty(HERO, 0));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HERO);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(HERO, Math.max(0, Math.min(meta, 4)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(HERO);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileTrophy();
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

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ItemStack s = Trophies.of(Lightbearer.values()[state.getValue(HERO)]);
        if (!s.isEmpty()) {
            drops.add(s);
        }
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return Trophies.of(Lightbearer.values()[state.getValue(HERO)]);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileTrophy && ((TileTrophy) te).getOwner() != null) {
            TrophyData.get(world).set(((TileTrophy) te).getOwner(), Lightbearer.values()[state.getValue(HERO)], false);
        }
        super.breakBlock(world, pos, state);
    }
}
