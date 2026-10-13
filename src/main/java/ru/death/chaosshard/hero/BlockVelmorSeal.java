package ru.death.chaosshard.hero;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/** Печать отступления Вельмора: к ней он сбегает с Опоры. Разбить — и бежать ему некуда. */
public class BlockVelmorSeal extends Block {

    public BlockVelmorSeal() {
        super(Material.GLASS);
        setSoundType(SoundType.GLASS);
        setHardness(3.0F);
        setResistance(20.0F);
        setLightLevel(0.8F);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        if (!world.isRemote) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 80, 0.4, 0.4, 0.4, 1.0);
            Pillars.sealBroken((WorldServer) world, pos);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        for (int i = 0; i < 3; i++) {
            world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5 + (rand.nextDouble() - 0.5), pos.getY() + 0.5 + rand.nextDouble(),
                    pos.getZ() + 0.5 + (rand.nextDouble() - 0.5), 0, -0.2, 0);
        }
    }
}
