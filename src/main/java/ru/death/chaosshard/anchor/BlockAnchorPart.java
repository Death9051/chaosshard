package ru.death.chaosshard.anchor;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;

import java.util.Random;

/**
 * Тело выросшего Якоря: сегменты колонны над основанием и остриё на вершине.
 * Ставится только самим Якорем. ПКМ — как по Якорю; сломанный сегмент Якорь отрастит заново.
 */
public class BlockAnchorPart extends Block {

    public static final PropertyBool TOP = PropertyBool.create("top");
    public static final PropertyBool LIT = PropertyBool.create("lit");
    private static final AxisAlignedBB MID_BOX = new AxisAlignedBB(0.1875, 0, 0.1875, 0.8125, 1, 0.8125);
    private static final AxisAlignedBB TOP_BOX = new AxisAlignedBB(0.25, 0, 0.25, 0.75, 1, 0.75);

    public BlockAnchorPart() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(6.0F);
        setResistance(2000.0F);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(blockState.getBaseState().withProperty(TOP, false).withProperty(LIT, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TOP, LIT);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(LIT, TileAnchor.litAt(world, base(world, pos)));
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TOP, (meta & 1) == 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TOP) ? 1 : 0;
    }

    /** Основание колонны под этим сегментом. */
    public static BlockPos base(IBlockAccess world, BlockPos pos) {
        BlockPos p = pos.down();
        for (int i = 0; i < 16; i++, p = p.down()) {
            Block b = world.getBlockState(p).getBlock();
            if (b == ModBlocks.CHAOS_ANCHOR) {
                return p;
            }
            if (!(b instanceof BlockAnchorPart)) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        BlockPos b = base(world, pos);
        return b != null && ModBlocks.CHAOS_ANCHOR.onBlockActivated(world, b, world.getBlockState(b), player, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (base(world, pos) == null) {
            world.setBlockToAir(pos);
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos,
                                  EntityPlayer player) {
        return new ItemStack(ModBlocks.CHAOS_ANCHOR);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return state.getValue(TOP) ? TOP_BOX : MID_BOX;
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
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(TOP) ? 10 : 4;
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (state.getValue(TOP)) {
            for (int i = 0; i < 2; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 2,
                        pos.getY() + 0.5 + rand.nextDouble(), pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 2,
                        (rand.nextDouble() - 0.5) * 0.3, -0.3, (rand.nextDouble() - 0.5) * 0.3);
            }
        } else if (rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.8,
                    pos.getY() + rand.nextDouble(), pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
    }
}
