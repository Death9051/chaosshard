package ru.death.chaosshard.act5;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import ru.death.chaosshard.init.ModBlocks;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Генератор Разлома: пустота, круглая арена из обломков Завесы (кварц, хаосит, корка),
 * пять пилонов по кольцу — у каждого ядро в цвет героя, — и редкие висящие осколки вокруг.
 */
public class ChunkGeneratorRift implements IChunkGenerator {

    private final World world;

    public ChunkGeneratorRift(World world) {
        this.world = world;
    }

    /** Цвет стекла пилона героя i (Каэль золото, Ирена голубой, Вельмор пурпур, Лиан оранж, Элис красный). */
    static final int[] GLASS = {4, 3, 10, 1, 14};

    public static IBlockState floorAt(int x, int z, Random r) {
        int k = r.nextInt(12);
        if (k == 0) {
            return ModBlocks.CHAOS_CRUST.getDefaultState();
        }
        if (k < 3) {
            return ModBlocks.CHAOS_STONE.getDefaultState();
        }
        return ((x * 7 + z * 13) & 7) == 0 ? Blocks.QUARTZ_BLOCK.getStateFromMeta(1) : Blocks.QUARTZ_BLOCK.getDefaultState();
    }

    @Override
    public Chunk generateChunk(int cx, int cz) {
        ChunkPrimer pr = new ChunkPrimer();
        int fl = RiftDimension.FLOOR, R = RiftDimension.ARENA_R;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                Random r = new Random((long) x * 341873128712L + (long) z * 132897987541L);
                double d = Math.sqrt(x * x + z * z);
                double edge = R + (r.nextDouble() - 0.5) * 2;
                if (d <= edge) {
                    pr.setBlockState(lx, fl, lz, floorAt(x, z, r));
                    // чаша снизу: чем ближе к краю, тем тоньше
                    int depth = 2 + (int) ((1 - d / edge) * 10);
                    for (int y = fl - 1; y >= fl - depth; y--) {
                        pr.setBlockState(lx, y, lz, ModBlocks.CHAOS_STONE.getDefaultState());
                    }
                    if (d > R - 1.5 && r.nextInt(3) == 0) {
                        pr.setBlockState(lx, fl + 1, lz, Blocks.QUARTZ_BLOCK.getStateFromMeta(2));
                    }
                } else if (d < 90 && r.nextInt(900) == 0) {
                    // висящий осколок
                    int y0 = fl - 20 + r.nextInt(50);
                    int h = 2 + r.nextInt(4);
                    for (int y = y0; y < y0 + h; y++) {
                        pr.setBlockState(lx, y, lz, r.nextBoolean() ? ModBlocks.CHAOS_STONE.getDefaultState() : Blocks.QUARTZ_BLOCK.getDefaultState());
                    }
                }
            }
        }
        // пилоны
        for (int i = 0; i < 5; i++) {
            BlockPos core = RiftDimension.pylon(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = core.getX() + dx, z = core.getZ() + dz;
                    if (x >> 4 != cx || z >> 4 != cz) {
                        continue;
                    }
                    boolean mid = dx == 0 && dz == 0;
                    for (int y = fl + 1; y < core.getY(); y++) {
                        IBlockState s = mid ? Blocks.QUARTZ_BLOCK.getStateFromMeta(2)
                                : Math.abs(dx) + Math.abs(dz) == 2 ? Blocks.QUARTZ_BLOCK.getDefaultState()
                                : Blocks.STAINED_GLASS.getStateFromMeta(GLASS[i]);
                        if (y < fl + 3 || mid) {
                            pr.setBlockState(x & 15, y, z & 15, s);
                        } else if (Math.abs(dx) + Math.abs(dz) == 2) {
                            pr.setBlockState(x & 15, y, z & 15, s);
                        }
                    }
                    if (mid) {
                        pr.setBlockState(x & 15, core.getY(), z & 15, ModBlocks.RIFT_PYLON.getStateFromMeta(i));
                    }
                }
            }
        }
        Chunk c = new Chunk(world, pr, cx, cz);
        byte[] biomes = c.getBiomeArray();
        java.util.Arrays.fill(biomes, (byte) Biome.getIdForBiome(net.minecraft.init.Biomes.SKY));
        c.generateSkylightMap();
        return c;
    }

    @Override
    public void populate(int x, int z) {
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        return null;
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) {
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        return false;
    }
}
