package ru.death.chaosshard.spell;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * Застывший Хаос — плиты Тропы Хаоса. Держат, сколько отмерено, потом рассыпаются.
 * Если плита легла на воду или лаву, при рассыпании возвращает источник обратно (ORIGIN: 0 — воздух, 1 — вода, 2 — лава).
 * Ничего не роняет.
 */
public class BlockChaosTrail extends Block {

    public static final PropertyInteger ORIGIN = PropertyInteger.create("origin", 0, 2);

    public BlockChaosTrail() {
        super(Material.GLASS);
        setSoundType(SoundType.GLASS);
        setHardness(0.3F);
        setResistance(1.0F);
        setLightLevel(0.45F);
        setDefaultState(blockState.getBaseState().withProperty(ORIGIN, 0));
    }

    /** Положить плиту на life тиков. Только в воздух или в жидкость. Возвращает true, если легла. */
    public static boolean place(World w, BlockPos q, int life) {
        Block trail = ru.death.chaosshard.init.ModBlocks.CHAOS_TRAIL;
        IBlockState st = w.getBlockState(q);
        if (st.getBlock() == trail) {
            w.scheduleUpdate(q, trail, life);     // продлить
            return true;
        }
        int origin;
        if (w.isAirBlock(q) || st.getBlock().isReplaceable(w, q) && !st.getMaterial().isLiquid()) {
            origin = 0;
        } else if (st.getBlock() == Blocks.WATER || st.getBlock() == Blocks.FLOWING_WATER) {
            origin = st.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0 ? 1 : 0;
        } else if (st.getBlock() == Blocks.LAVA || st.getBlock() == Blocks.FLOWING_LAVA) {
            origin = st.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0 ? 2 : 0;
        } else {
            return false;
        }
        if (q.getY() < 1 || q.getY() > 254) {
            return false;
        }
        w.setBlockState(q, trail.getDefaultState().withProperty(ORIGIN, origin), 3);
        w.scheduleUpdate(q, trail, life);
        return true;
    }

    @Override
    public void updateTick(World w, BlockPos pos, IBlockState state, Random rand) {
        if (w.isRemote) {
            return;
        }
        int o = state.getValue(ORIGIN);
        w.setBlockState(pos, o == 1 ? Blocks.WATER.getDefaultState() : o == 2 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
        if (w instanceof WorldServer) {
            ((WorldServer) w).spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.2);
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ORIGIN);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(ORIGIN, Math.max(0, Math.min(2, meta)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ORIGIN);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        IBlockState other = world.getBlockState(pos.offset(side));
        return other.getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
    }
}
