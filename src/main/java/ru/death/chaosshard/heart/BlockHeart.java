package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
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

    public BlockHeart() {
        super(Material.ROCK);
        setSoundType(SoundType.GLASS);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(0.8F);
        setCreativeTab(CreativeTabs.MISC);
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
