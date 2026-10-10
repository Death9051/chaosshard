package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Сердце Острова. Неразрушимо. ПКМ — окно: ступень, выбор руды, модули, пробуждение.
 */
public class BlockHeart extends Block {

    public static final PropertyInteger TIER = PropertyInteger.create("tier", 1, 4);
    public static final PropertyBool PULSE = PropertyBool.create("pulse");
    public static final PropertyBool CAPACITY = PropertyBool.create("capacity");
    public static final PropertyBool RICH = PropertyBool.create("rich");
    public static final PropertyBool FERTILE = PropertyBool.create("fertile");
    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.1875, 0, 0.1875, 0.8125, 0.875, 0.8125);

    public BlockHeart() {
        super(Material.ROCK);
        setSoundType(SoundType.GLASS);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(0.8F);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(TIER, 1).withProperty(PULSE, false)
                .withProperty(CAPACITY, false).withProperty(RICH, false).withProperty(FERTILE, false));
    }

    // ------------------------------------------------------------------ вид: ступень и модули

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TIER, PULSE, CAPACITY, RICH, FERTILE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileHeart)) {
            return state;
        }
        TileHeart h = (TileHeart) te;
        return state.withProperty(TIER, Math.max(1, Math.min(4, h.getTier())))
                .withProperty(PULSE, h.count(ItemHeartModule.Type.PULSE) > 0)
                .withProperty(CAPACITY, h.count(ItemHeartModule.Type.CAPACITY) > 0)
                .withProperty(RICH, h.count(ItemHeartModule.Type.RICH) > 0)
                .withProperty(FERTILE, h.count(ItemHeartModule.Type.FERTILE) > 0);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileHeart ? 8 + 2 * Math.min(3, ((TileHeart) te).getTier() - 1) : 10;
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
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileHeart();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        // С Жнецом в руке — не открывать окно, а дать поставить его на Сердце
        if (player.getHeldItem(hand).getItem() == net.minecraft.item.Item.getItemFromBlock(ru.death.chaosshard.init.ModBlocks.HEART_HARVESTER)) {
            return false;
        }
        if (!world.isRemote) {
            player.openGui(ru.death.chaosshard.ChaosShard.INSTANCE, HeartGuiHandler.HEART, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        for (int i = 0; i < 3; i++) {
            world.spawnParticle(EnumParticleTypes.PORTAL,
                    pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 2, pos.getY() + rand.nextDouble() * 1.5,
                    pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 2,
                    (rand.nextDouble() - 0.5) * 0.5, -rand.nextDouble() * 0.5, (rand.nextDouble() - 0.5) * 0.5);
        }
    }
}
