package ru.death.chaosshard.village;

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
import net.minecraft.village.Village;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.quest.Milestones;

import javax.annotation.Nullable;
import java.util.Random;

/** Знак покровителя: ставится в деревне. Деревня платит дань и Скверна её не трогает. */
public class BlockPatronSigil extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.25, 0, 0.25, 0.75, 1.0, 0.75);

    public BlockPatronSigil() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(3.0F);
        setResistance(20.0F);
        setLightLevel(0.6F);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TilePatronSigil();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) placer;
        Village v = world.getVillageCollection().getNearestVillage(pos, 32);
        VillageData data = VillageData.get(world);
        String error = null;
        if (v == null || !v.isBlockPosWithinSqVillageRadius(pos)) {
            error = "chaosshard.village.not_village";
        } else if (data.isFallen(v)) {
            error = "chaosshard.village.already_fallen";
        } else if (data.isPatron(v)) {
            error = "chaosshard.village.already_patron";
        }
        if (error != null) {
            player.sendMessage(Breach.grimoire(error));
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
        if (te instanceof TilePatronSigil) {
            ((TilePatronSigil) te).setOwner(player);
        }
        data.sigils.add(pos.toImmutable());
        data.markDirty();
        player.sendMessage(Breach.grimoire("chaosshard.village.patron", v.getNumVillageDoors()));
        Milestones.grant(player, Milestones.VILLAGE_PATRON);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TilePatronSigil) {
                ((TilePatronSigil) te).dropAll();
            }
            VillageData data = VillageData.get(world);
            if (data.sigils.remove(pos)) {
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
            if (te instanceof TilePatronSigil) {
                TilePatronSigil sg = (TilePatronSigil) te;
                if (player.isSneaking()) {
                    int next = sg.nextThreshold();
                    player.sendMessage(Breach.grimoire("chaosshard.village.info", sg.level(), TilePatronSigil.maxLevel(), sg.getXp(),
                            next < 0 ? "—" : String.valueOf(next), TilePatronSigil.shrink(sg.level()), sg.getBorder()));
                    if (sg.isEvolved()) {
                        player.sendMessage(Breach.grimoire("chaosshard.village.info_evolved"));
                    }
                } else {
                    sg.giveTo(player);
                }
            }
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0, 0);
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
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
