package ru.death.chaosshard.structure;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

/** Светоч Ордена: бьёт в небо золотым лучом, пока жив Хранитель святилища. Ничего не роняет. */
public class BlockOrderBeacon extends Block implements ITileEntityProvider {

    public BlockOrderBeacon() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(30.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 2);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileOrderBeacon();
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileOrderBeacon && ((TileOrderBeacon) te).lit ? 15 : 4;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }
}
