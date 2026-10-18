package ru.death.chaosshard.ritual;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Знак мела Хаоса — плоская руна на земле. Рисуется мелом, стирается ударом.
 * Знаков девять; какой нарисован — решает место (не хранится): восемь мест вокруг Чаши всегда дают восемь разных знаков.
 */
public class BlockChalk extends Block {

    public static final PropertyInteger RUNE = PropertyInteger.create("rune", 0, 8);

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 1.0 / 32, 1);

    public BlockChalk() {
        super(Material.CIRCUITS);
        setSoundType(SoundType.SAND);
        setHardness(0.0F);
        setLightLevel(0.3F);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, RUNE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    /** Знак по месту: (3x + z) mod 9 — у восьми мест кольца вокруг Чаши (шаг 2) все остатки разные. */
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(RUNE, Math.floorMod(3 * pos.getX() + pos.getZ(), 9));
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
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSideSolid(world, pos.down(), EnumFacing.UP);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!canPlaceBlockAt(world, pos)) {
            world.setBlockToAir(pos);
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
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
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (rand.nextInt(6) == 0) {
            world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + rand.nextDouble(), pos.getY() + 0.1,
                    pos.getZ() + rand.nextDouble(), 0, 0, 0);
        }
    }
}
