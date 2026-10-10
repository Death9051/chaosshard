package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;

import javax.annotation.Nullable;

/** Жнец Сердца — ставится прямо на Сердце Острова. ПКМ — хранилище. */
public class BlockHarvester extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.0625, 0, 0.0625, 0.9375, 0.5, 0.9375);

    public BlockHarvester() {
        super(Material.IRON);
        setSoundType(SoundType.METAL);
        setHardness(3.0F);
        setResistance(20.0F);
        setLightLevel(0.4F);
        setHarvestLevel("pickaxe", 0);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock() == ModBlocks.ISLAND_HEART && super.canPlaceBlockAt(world, pos);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileHarvester();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!world.isRemote) {
            player.openGui(ChaosShard.INSTANCE, HeartGuiHandler.HARVESTER, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileHarvester) {
            TileHarvester h = (TileHarvester) te;
            for (int i = 0; i < TileHarvester.SLOTS; i++) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), h.inventory.getStackInSlot(i));
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && world.getBlockState(pos.down()).getBlock() != ModBlocks.ISLAND_HEART) {
            world.destroyBlock(pos, true);
        }
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

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
