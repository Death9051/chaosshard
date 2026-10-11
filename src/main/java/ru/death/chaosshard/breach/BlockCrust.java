package ru.death.chaosshard.breach;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;

import java.util.Random;

/**
 * Корка Преддверия.
 *  - Природная корка (сгенерированная или наросшая сама) светится и хранит Осколки Завесы.
 *  - Корка, поставленная игроком, — «свежая»: тусклая, без осколков.
 *  - Любая корка медленно расползается на соседний хаосит под открытым воздухом, как трава на землю;
 *    наросшая так корка — природная. Это и есть постоянный источник корки и осколков.
 */
public class BlockCrust extends ModBlocks.SimpleBlock {

    /** true — поставлена игроком (мета 1); false — природная (мета 0, как в старых мирах). */
    public static final PropertyBool FRESH = PropertyBool.create("fresh");

    public BlockCrust() {
        super(Material.GROUND, SoundType.STONE, 0.8F, 8.0F, 0.0F);
        setDefaultState(blockState.getBaseState().withProperty(FRESH, false));
        setTickRandomly(true);
    }

    public static boolean isNatural(IBlockState state) {
        return state.getBlock() instanceof BlockCrust && !state.getValue(FRESH);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FRESH);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FRESH, (meta & 1) == 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FRESH) ? 1 : 0;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FRESH, true);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(this);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos,
                                  net.minecraft.entity.player.EntityPlayer player) {
        return new ItemStack(this);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(FRESH) ? 0 : 3;
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote || !world.isAreaLoaded(pos, 2)) {
            return;
        }
        // Две попытки за тик, как у травы: хаосит рядом (±1 по высоте) с воздухом над ним обрастает коркой
        for (int i = 0; i < 2; i++) {
            BlockPos p = pos.add(rand.nextInt(3) - 1, rand.nextInt(3) - 1, rand.nextInt(3) - 1);
            if (world.getBlockState(p).getBlock() != ModBlocks.CHAOS_STONE) {
                continue;
            }
            IBlockState above = world.getBlockState(p.up());
            if (above.isOpaqueCube() || above.getMaterial().isLiquid()) {
                continue;
            }
            world.setBlockState(p, getDefaultState());
        }
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (!state.getValue(FRESH) && rand.nextInt(12) == 0 && !world.getBlockState(pos.up()).isOpaqueCube()) {
            world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + rand.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + rand.nextDouble(), 0, 0.02, 0);
        }
    }
}
