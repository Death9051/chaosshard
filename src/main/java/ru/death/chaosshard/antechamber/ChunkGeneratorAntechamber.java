package ru.death.chaosshard.antechamber;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.init.ModBlocks;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Генератор Преддверия: пустота и один остров-«ложе» вокруг точки (0, 0).
 *
 * Остров — перевёрнутый конус из хаосита с рваным краем. Сверху тонкий слой
 * «Звёздной пыли Преддверия» (корка). Никакой руды: Преддверие бедно материей.
 */
public class ChunkGeneratorAntechamber implements IChunkGenerator {

    private final World world;
    private final long seed;

    public ChunkGeneratorAntechamber(World world) {
        this.world = world;
        this.seed = world.getSeed();
    }

    @Override
    public Chunk generateChunk(int chunkX, int chunkZ) {
        ChunkPrimer primer = new ChunkPrimer();
        int radius = ChaosConfig.antechamber.islandRadius;
        int surface = ChaosConfig.antechamber.islandSurfaceY;
        int maxDepth = Math.max(6, radius / 2);

        // Чанк целиком вне острова — оставляем пустым.
        int minX = chunkX * 16, minZ = chunkZ * 16;
        int nearestX = MathHelper.clamp(0, minX, minX + 15);
        int nearestZ = MathHelper.clamp(0, minZ, minZ + 15);
        boolean touches = nearestX * nearestX + nearestZ * nearestZ <= (radius + 4) * (radius + 4);

        if (touches) {
            IBlockState stone = ModBlocks.CHAOS_STONE.getDefaultState();
            IBlockState crust = ModBlocks.CHAOS_CRUST.getDefaultState();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = minX + lx, wz = minZ + lz;
                    double edge = radius + edgeNoise(wx, wz) * 3.0;
                    double dist = Math.sqrt((double) wx * wx + (double) wz * wz);
                    if (dist >= edge) {
                        continue;
                    }
                    double k = 1.0 - (dist / edge);                    // 1 в центре, 0 на краю
                    int depth = 1 + (int) Math.round(Math.pow(k, 0.6) * maxDepth + edgeNoise(wz, wx) * 2.0);
                    int bottom = Math.max(1, surface - depth);
                    for (int y = bottom; y < surface; y++) {
                        primer.setBlockState(lx, y, lz, stone);
                    }
                    primer.setBlockState(lx, surface, lz, crust);
                }
            }
        }

        Chunk chunk = new Chunk(this.world, primer, chunkX, chunkZ);
        byte[] biomes = chunk.getBiomeArray();
        byte sky = (byte) Biome.getIdForBiome(Biomes.SKY);
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = sky;
        }
        chunk.generateSkylightMap();
        return chunk;
    }

    /** Детерминированный шум в диапазоне [-1, 1] для рваного края. */
    private double edgeNoise(int x, int z) {
        long h = this.seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        // Случайная часть даёт рваный край, синусоида — крупные «заливы» и «мысы»
        double v = ((h >>> 11) & 0xFFFF) / 32767.5 - 1.0;
        return MathHelper.clamp(v, -1.0, 1.0) * 0.6 + Math.sin((x * 0.21) + (z * 0.17)) * 0.4;
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
        // В Преддверии никто не появляется сам: только то, что призовёт Осколок.
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
