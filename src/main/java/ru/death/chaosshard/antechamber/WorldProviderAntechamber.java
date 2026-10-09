package ru.death.chaosshard.antechamber;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

/**
 * Преддверие: вечная звёздная ночь, без погоды, остров висит в пустоте.
 *
 * Время в Преддверии общее с верхним миром (так устроены измерения в 1.12),
 * но небо всегда показывает полночь: угол небесной сферы зафиксирован.
 */
public class WorldProviderAntechamber extends WorldProvider {

    @Override
    protected void init() {
        // Биом «Небо» (как в Энде): без дождя и снега.
        this.biomeProvider = new BiomeProviderSingle(Biomes.SKY);
        // Базовый init() включает небесный свет; без этого остров был бы тёмным, как Незер
        this.hasSkyLight = true;
    }

    @Override
    public DimensionType getDimensionType() {
        return AntechamberDimension.TYPE;
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new ChunkGeneratorAntechamber(this.world);
    }

    /** 0.5 — полночь. Звёзды горят всегда. */
    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        return 0.5F;
    }

    /**
     * Свет Преддверия. Небо — вечная ночь со звёздами, но земля освещена, как днём:
     * будто остров подсвечен невидимыми источниками. Для света блоков (рост растений, спавн мобов)
     * мир считается дневным, а для отрисовки — освещённым почти полностью.
     */
    @Override
    public float getSunBrightnessFactor(float partialTicks) {
        return ru.death.chaosshard.ChaosConfig.antechamber.ambientLight ? 1.0F : super.getSunBrightnessFactor(partialTicks);
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public float getSunBrightness(float partialTicks) {
        return ru.death.chaosshard.ChaosConfig.antechamber.ambientLight ? 0.85F : super.getSunBrightness(partialTicks);
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public boolean canRespawnHere() {
        return true;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        return true;
    }

    @Override
    public BlockPos getSpawnPoint() {
        return AntechamberDimension.spawnPoint();
    }

    @Override
    public BlockPos getRandomizedSpawnPoint() {
        return AntechamberDimension.spawnPoint();
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    /**
     * В Преддверии нет погоды. Мир делит данные с верхним миром, поэтому
     * дождь сверху мог бы «протечь» сюда и мешать телескопам Astral Sorcery.
     */
    @Override
    public void updateWeather() {
        clearWeather();
    }

    @Override
    public void calculateInitialWeather() {
        clearWeather();
    }

    private void clearWeather() {
        if (this.world != null) {
            this.world.setRainStrength(0.0F);
            this.world.setThunderStrength(0.0F);
        }
    }

    @Override
    public float getCloudHeight() {
        // Облака ниже острова: сверху видно только звёзды.
        return 40.0F;
    }

    @Override
    public boolean doesXZShowFog(int x, int z) {
        return false;
    }
}
