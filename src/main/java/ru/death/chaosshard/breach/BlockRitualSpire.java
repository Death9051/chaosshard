package ru.death.chaosshard.breach;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;

import java.util.Random;

/**
 * Ритуальный шпиль — двойной блок-колонна с остроконечной верхушкой.
 * Четыре шпиля по диагоналям от алтаря (через клетку) — опоры Прорыва и будущих ритуалов.
 * Ставится и ломается целиком, как дверь.
 */
public class BlockRitualSpire extends ModBlocks.SimpleBlock {

    public enum Half implements IStringSerializable {
        LOWER, UPPER;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    public static final PropertyEnum<Half> HALF = PropertyEnum.create("half", Half.class);
    /** Руны и кристалл горят, пока на алтаре идёт ритуал. */
    public static final net.minecraft.block.properties.PropertyBool LIT = net.minecraft.block.properties.PropertyBool.create("lit");

    private static final AxisAlignedBB LOWER_BOX = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB UPPER_BOX = new AxisAlignedBB(0.125, 0, 0.125, 0.875, 1, 0.875);

    public BlockRitualSpire() {
        super(Material.ROCK, SoundType.STONE, 2.0F, 20.0F, 0.0F);
        setDefaultState(blockState.getBaseState().withProperty(HALF, Half.LOWER).withProperty(LIT, false));
        setTickRandomly(true);
    }

    public static boolean isLower(IBlockState state) {
        return state.getBlock() instanceof BlockRitualSpire && state.getValue(HALF) == Half.LOWER;
    }

    /** Целый шпиль стоит на p (нижняя половина) и p.up() (верхняя). */
    public static boolean isWhole(World world, BlockPos p) {
        IBlockState up = world.getBlockState(p.up());
        return isLower(world.getBlockState(p)) && up.getBlock() instanceof BlockRitualSpire && up.getValue(HALF) == Half.UPPER;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF, LIT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(HALF, (meta & 1) == 1 ? Half.UPPER : Half.LOWER).withProperty(LIT, (meta & 2) == 2);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return (state.getValue(HALF) == Half.UPPER ? 1 : 0) | (state.getValue(LIT) ? 2 : 0);
    }

    // ------------------------------------------------------------------ установка и поломка

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return pos.getY() < world.getHeight() - 1 && super.canPlaceBlockAt(world, pos)
                && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos.up(), getDefaultState().withProperty(HALF, Half.UPPER), 2);
        if (state.getValue(LIT)) {
            world.setBlockState(pos, state.withProperty(LIT, false), 2);
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (state.getValue(HALF) == Half.UPPER) {
            if (!isLower(world.getBlockState(pos.down()))) {
                world.setBlockToAir(pos);
            }
        } else {
            IBlockState up = world.getBlockState(pos.up());
            if (!(up.getBlock() instanceof BlockRitualSpire) || up.getValue(HALF) != Half.UPPER) {
                world.setBlockToAir(pos);
                if (!world.isRemote) {
                    dropBlockAsItem(world, pos, state, 0);
                }
            }
        }
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        // Ломают верх — снимаем низ (он и даёт предмет, если не творческий режим)
        if (state.getValue(HALF) == Half.UPPER && isLower(world.getBlockState(pos.down()))) {
            BlockPos low = pos.down();
            if (player.capabilities.isCreativeMode) {
                world.setBlockToAir(low);
            } else {
                world.destroyBlock(low, true);
            }
        }
    }

    @Override
    public int getLightValue(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
        if (state.getBlock() != this) {
            return 0;
        }
        if (state.getValue(LIT)) {
            return 10;
        }
        return state.getValue(HALF) == Half.UPPER ? 5 : 0;
    }

    /** Зажечь или погасить четыре шпиля вокруг алтаря. */
    public static void setLit(World world, BlockPos altar, boolean lit) {
        for (BlockPos p : BreachRitual.pillars(altar)) {
            for (BlockPos q : new BlockPos[]{p, p.up()}) {
                IBlockState st = world.getBlockState(q);
                if (st.getBlock() instanceof BlockRitualSpire && st.getValue(LIT) != lit) {
                    world.setBlockState(q, st.withProperty(LIT, lit), 2);
                }
            }
        }
    }

    /** Подстраховка: шпиль, забытый зажжённым (сервер перезапущен посреди ритуала), гаснет сам. */
    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote || !state.getValue(LIT)) {
            return;
        }
        BlockPos low = state.getValue(HALF) == Half.UPPER ? pos.down() : pos;
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                BlockPos altar = low.add(dx, 0, dz);
                if (ru.death.chaosshard.ritual.AltarRituals.runningAt(altar) || BreachRitual.runningAt(altar)) {
                    return;
                }
            }
        }
        world.setBlockState(pos, state.withProperty(LIT, false), 2);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return state.getValue(HALF) == Half.UPPER ? Items.AIR : Item.getItemFromBlock(this);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos,
                                  EntityPlayer player) {
        return new ItemStack(this);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    // ------------------------------------------------------------------ форма и вид

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return state.getValue(HALF) == Half.UPPER ? UPPER_BOX : LOWER_BOX;
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
        return face == EnumFacing.DOWN && state.getValue(HALF) == Half.LOWER ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (state.getValue(HALF) == Half.UPPER && rand.nextInt(4) == 0) {
            world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }
}
