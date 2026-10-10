package ru.death.chaosshard.order;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;

import java.util.Random;

/** Святой камень Ордена — сердце святыни. Не ломается; оскверняется Эссенцией Хаоса. */
public class BlockShrineStone extends Block {

    public BlockShrineStone() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(1.0F);
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            Shrines.register(world, pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            Shrines.unregister(world, pos);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == ModItems.CHAOS_ESSENCE) {
            Shrines.desecrate(world, pos, (EntityPlayerMP) player, held);
        } else {
            player.sendMessage(Breach.grimoire("chaosshard.shrine.info"));
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        for (int i = 0; i < 2; i++) {
            world.spawnParticle(EnumParticleTypes.END_ROD, pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.8,
                    pos.getY() + 1.0 + rand.nextDouble() * 0.5, pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.8, 0, 0.03, 0);
        }
    }
}
