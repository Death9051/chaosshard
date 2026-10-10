package ru.death.chaosshard.anchor;

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
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import javax.annotation.Nullable;
import java.util.Random;

/** Якорь Хаоса: вбивается в чужой мир, от него ползёт Скверна. ПКМ — состояние края. */
public class BlockAnchor extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.25, 0, 0.25, 0.75, 1.0, 0.75);

    public BlockAnchor() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(6.0F);
        setResistance(2000.0F);
        setLightLevel(0.6F);
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
        return new TileAnchor();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) placer;
        String error = null;
        if (AntechamberDimension.is(world)) {
            error = "chaosshard.anchor.not_here";
        } else if (!player.isCreative() && ThreadManager.getLevel(player) < ChaosConfig.anchor.threadLevel) {
            error = "chaosshard.anchor.thin";
        } else {
            BlockPos near = AnchorData.get(world).nearest(pos, pos);
            if (near != null && near.distanceSq(pos) < (double) ChaosConfig.anchor.minDistance * ChaosConfig.anchor.minDistance) {
                error = "chaosshard.anchor.too_close";
                player.sendMessage(Breach.grimoire(error, near.getX(), near.getY(), near.getZ(), ChaosConfig.anchor.minDistance));
                error = "";
            }
        }
        if (error != null) {
            if (!error.isEmpty()) {
                player.sendMessage(Breach.grimoire(error, ChaosConfig.anchor.threadLevel));
            }
            world.setBlockToAir(pos);
            if (!player.isCreative()) {
                ItemStack back = stack.copy();
                back.setCount(1);
                if (!player.inventory.addItemStackToInventory(back)) {
                    player.dropItem(back, false);
                }
            }
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAnchor) {
            ((TileAnchor) te).setOwner(player);
        }
        AnchorData data = AnchorData.get(world);
        data.anchors.add(pos);
        data.markDirty();
        player.sendMessage(Breach.grimoire("chaosshard.anchor.placed"));
        ru.death.chaosshard.quest.Milestones.grant(player, ru.death.chaosshard.quest.Milestones.ANCHOR);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            AnchorData data = AnchorData.get(world);
            if (data.anchors.remove(pos)) {
                data.markDirty();
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileAnchor) {
                TileAnchor a = (TileAnchor) te;
                player.sendMessage(Breach.grimoire("chaosshard.anchor.info", a.getOwnerName(), a.radius(), a.getCorrupted(),
                        ChaosConfig.anchor.captureThreshold));
                player.sendMessage(Breach.grimoire(a.isCaptured() ? "chaosshard.anchor.info_captured" : "chaosshard.anchor.info_open"));
            }
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        for (int i = 0; i < 4; i++) {
            world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 3,
                    pos.getY() + rand.nextDouble() * 2.5, pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 3,
                    (rand.nextDouble() - 0.5) * 0.3, -0.3, (rand.nextDouble() - 0.5) * 0.3);
        }
        world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.05, 0);
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
        return face == EnumFacing.DOWN ? BlockFaceShape.CENTER : BlockFaceShape.UNDEFINED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
