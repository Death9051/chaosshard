package ru.death.chaosshard.enchant;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.heart.HeartGuiHandler;

import javax.annotation.Nullable;
import java.util.Random;

/** Стол Хаоса — стол зачарований без случайности: зачарования выбираются из изученных. */
public class BlockChaosEnchanter extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 0.75, 1);

    public BlockChaosEnchanter() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(5.0F);
        setResistance(1200.0F);
        setLightLevel(0.5F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileChaosEnchanter();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!world.isRemote) {
            player.openGui(ChaosShard.INSTANCE, HeartGuiHandler.ENCHANTER, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileChaosEnchanter) {
            TileChaosEnchanter t = (TileChaosEnchanter) te;
            for (int i = 0; i < t.inventory.getSlots(); i++) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), t.inventory.getStackInSlot(i));
            }
        }
        super.breakBlock(world, pos, state);
    }

    /** Как у ванильного стола: из полок к столу летят знаки. */
    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x > -2 && x < 2 && z == -1) {
                    z = 2;
                }
                if (rand.nextInt(16) != 0) {
                    continue;
                }
                for (int y = 0; y <= 1; y++) {
                    BlockPos shelf = pos.add(x, y, z);
                    if (ForgeHooks.getEnchantPower(world, shelf) <= 0) {
                        continue;
                    }
                    if (!world.isAirBlock(pos.add(x / 2, 0, z / 2))) {
                        break;
                    }
                    world.spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE, pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5,
                            x + rand.nextFloat() - 0.5, y - rand.nextFloat() - 1.0F, z + rand.nextFloat() - 0.5);
                }
            }
        }
        if (rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + rand.nextFloat(), pos.getY() + 0.8, pos.getZ() + rand.nextFloat(),
                    0, 0.15, 0);
        }
    }
}
