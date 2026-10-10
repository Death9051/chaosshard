package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.init.ModItems;

import java.util.Random;

/** Жила Завесы: в ней застряли осколки барьера. Растёт на Сердце с модулем «Богатство». */
public class BlockVeilVein extends Block {

    public BlockVeilVein() {
        super(Material.ROCK);
        setSoundType(SoundType.GLASS);
        setHardness(2.5F);
        setResistance(10.0F);
        setLightLevel(0.5F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return ModItems.VEIL_SHARD;
    }

    @Override
    public int quantityDropped(Random random) {
        return 1 + random.nextInt(2);
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        return quantityDropped(random) + (fortune > 0 ? random.nextInt(fortune + 1) : 0);
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        return MathHelper.getInt(new Random(), 2, 5);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.END_ROD, pos.getX() + rand.nextDouble(), pos.getY() + rand.nextDouble(),
                    pos.getZ() + rand.nextDouble(), 0, 0.01, 0);
        }
    }
}
