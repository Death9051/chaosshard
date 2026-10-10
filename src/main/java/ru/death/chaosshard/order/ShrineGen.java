package ru.death.chaosshard.order;

import net.minecraft.block.BlockQuartz;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.IWorldGenerator;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;

import java.util.Random;

/** Святыни Ордена в Лучезарии: площадка, четыре колонны, Святой камень на золоте. */
public class ShrineGen implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator gen, IChunkProvider provider) {
        if (world.provider.getDimension() != Breach.luchezariyaDim() || ChaosConfig.order.shrineRarity <= 0
                || random.nextInt(ChaosConfig.order.shrineRarity) != 0) {
            return;
        }
        int x = chunkX * 16 + 11 + random.nextInt(4);
        int z = chunkZ * 16 + 11 + random.nextInt(4);
        BlockPos top = world.getHeight(new BlockPos(x, 0, z));
        Biome biome = world.getBiome(top);
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)) {
            return;
        }
        // Площадка должна быть ровной
        int min = 255, max = 0;
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                int y = world.getHeight(new BlockPos(x + dx, 0, z + dz)).getY();
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        if (max - min > 3 || world.getBlockState(top.down()).getMaterial().isLiquid()) {
            return;
        }
        build(world, new BlockPos(x, min, z), random);
    }

    /** Построить святыню; center — уровень пола. */
    public static void build(World world, BlockPos center, Random random) {
        IBlockState brick = Blocks.STONEBRICK.getDefaultState();
        IBlockState chiseled = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CHISELED);
        IBlockState pillar = Blocks.QUARTZ_BLOCK.getDefaultState().withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_Y);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos p = center.add(dx, 0, dz);
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                world.setBlockState(p.down(), corner ? chiseled : brick, 2);
                for (int dy = -2; dy >= -4; dy--) {
                    BlockPos f = p.add(0, dy, 0);
                    if (world.isAirBlock(f) || world.getBlockState(f).getMaterial().isReplaceable()) {
                        world.setBlockState(f, Blocks.STONE.getDefaultState(), 2);
                    }
                }
                for (int dy = 0; dy <= 4; dy++) {
                    world.setBlockToAir(p.up(dy));
                }
                if (corner) {
                    for (int dy = 0; dy < 3; dy++) {
                        world.setBlockState(p.up(dy), pillar, 2);
                    }
                    world.setBlockState(p.up(3), Blocks.TORCH.getDefaultState(), 2);
                } else if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && random.nextInt(3) == 0) {
                    world.setBlockState(p, Blocks.RED_FLOWER.getStateFromMeta(8), 2);
                }
            }
        }
        world.setBlockState(center, Blocks.GOLD_BLOCK.getDefaultState(), 2);
        world.setBlockState(center.up(), ModBlocks.SHRINE_STONE.getDefaultState(), 3);
        Shrines.register(world, center.up());
        // Ступени к святыне
        for (EnumFacing f : EnumFacing.HORIZONTALS) {
            world.setBlockState(center.offset(f, 4).down(), brick, 2);
        }
    }
}
