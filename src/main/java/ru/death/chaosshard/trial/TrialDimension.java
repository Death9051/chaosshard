package ru.death.chaosshard.trial;

import net.minecraft.init.Biomes;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.DimensionManager;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** Архив обломков — пустое измерение, где Испытания строятся по ключу. */
public final class TrialDimension {

    public static DimensionType TYPE;

    public static void register() {
        int id = id();
        if (DimensionManager.isDimensionRegistered(id)) {
            ChaosShard.LOG.error("ID измерения {} уже занят. Поменяйте trials.dimensionId в chaosshard.cfg.", id);
            return;
        }
        TYPE = DimensionType.register("chaosshard_archive", "_chaosshard_archive", id, Provider.class, false);
        DimensionManager.registerDimension(id, TYPE);
    }

    public static int id() {
        return ChaosConfig.trials.dimensionId;
    }

    public static boolean is(World world) {
        return world != null && world.provider.getDimension() == id();
    }

    public static class Provider extends WorldProvider {
        @Override
        protected void init() {
            this.biomeProvider = new BiomeProviderSingle(Biomes.VOID);
            this.hasSkyLight = false;
        }

        @Override
        public DimensionType getDimensionType() {
            return TYPE;
        }

        @Override
        public IChunkGenerator createChunkGenerator() {
            return new Void(world);
        }

        @Override
        public boolean canRespawnHere() {
            return false;
        }

        @Override
        public boolean isSurfaceWorld() {
            return false;
        }

        @Override
        public float calculateCelestialAngle(long worldTime, float partialTicks) {
            return 0.5F;
        }

        @Override
        public boolean canDoRainSnowIce(Chunk chunk) {
            return false;
        }
    }

    /** Пустота. */
    public static class Void implements IChunkGenerator {
        private final World world;

        Void(World world) {
            this.world = world;
        }

        @Override
        public Chunk generateChunk(int x, int z) {
            Chunk c = new Chunk(world, new ChunkPrimer(), x, z);
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

    private TrialDimension() {
    }
}
