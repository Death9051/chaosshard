package ru.death.chaosshard.act5;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Разлом: вечные сумерки цвета Завесы, без погоды, без возрождения. */
public class WorldProviderRift extends WorldProvider {

    @Override
    protected void init() {
        this.biomeProvider = new BiomeProviderSingle(Biomes.SKY);
        this.hasSkyLight = true;
    }

    @Override
    public DimensionType getDimensionType() {
        return RiftDimension.TYPE;
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new ChunkGeneratorRift(this.world);
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        return 0.5F;
    }

    @Override
    public float getSunBrightnessFactor(float partialTicks) {
        return 0.9F;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3d getFogColor(float angle, float partialTicks) {
        return new Vec3d(0.22, 0.12, 0.32);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3d getSkyColor(net.minecraft.entity.Entity camera, float partialTicks) {
        return new Vec3d(0.18, 0.08, 0.28);
    }

    @Override
    public boolean isSurfaceWorld() {
        return false;
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public BlockPos getSpawnPoint() {
        return RiftDimension.arrival();
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    @Override
    public void updateWeather() {
        if (world != null) {
            world.prevRainingStrength = 0.0F;
            world.rainingStrength = 0.0F;
            world.prevThunderingStrength = 0.0F;
            world.thunderingStrength = 0.0F;
        }
    }

    @Override
    public float getCloudHeight() {
        return 8.0F;
    }

    @Override
    public boolean doesXZShowFog(int x, int z) {
        return true;
    }
}
