package ru.death.chaosshard.pump;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.Breach;

import javax.annotation.Nullable;

/** Звёздный насос. ПКМ ведром — набрать жидкий свет, ПКМ пустой рукой — сведения. */
public class BlockStarPump extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.0625, 0, 0.0625, 0.9375, 1, 0.9375);

    public BlockStarPump() {
        super(Material.IRON);
        setSoundType(SoundType.METAL);
        setHardness(3.5F);
        setResistance(20F);
        setLightLevel(0.5F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileStarPump();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileStarPump && placer instanceof EntityPlayerMP) {
            ((TileStarPump) te).setOwner((EntityPlayerMP) placer);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileStarPump)) {
            return true;
        }
        TileStarPump pump = (TileStarPump) te;
        if (!player.getHeldItem(hand).isEmpty()) {
            return FluidUtil.interactWithFluidHandler(player, hand, world, pos, facing) || true;
        }
        if (!world.isRemote) {
            TileAnchor a = pump.anchorHere();
            int mb = pump.getTank().getFluidAmount();
            if (TileStarPump.starlight() == null) {
                player.sendMessage(Breach.grimoire("chaosshard.pump.no_astral"));
            } else if (a == null) {
                player.sendMessage(Breach.grimoire("chaosshard.pump.no_land", mb, TileStarPump.CAPACITY));
            } else {
                player.sendMessage(Breach.grimoire("chaosshard.pump.info", a.level(), pump.getRate() > 0 ? pump.getRate() : 5 * a.level(),
                        mb, TileStarPump.CAPACITY));
            }
        }
        return true;
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
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
}
