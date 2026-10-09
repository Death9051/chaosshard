package ru.death.chaosshard.antechamber;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;

/**
 * Регистрация Преддверия — кармана у самого края Плана Хаоса.
 */
public final class AntechamberDimension {

    public static DimensionType TYPE;

    public static void register() {
        int id = id();
        if (DimensionManager.isDimensionRegistered(id)) {
            ChaosShard.LOG.error("ID измерения {} уже занят другим модом. Поменяйте antechamber.dimensionId в chaosshard.cfg.", id);
            throw new IllegalStateException("Chaos Shard: dimension id " + id + " is already registered");
        }
        TYPE = DimensionType.register("chaosshard_antechamber", "_chaosshard_antechamber", id, WorldProviderAntechamber.class, false);
        DimensionManager.registerDimension(id, TYPE);
    }

    public static int id() {
        return ChaosConfig.antechamber.dimensionId;
    }

    public static boolean is(World world) {
        return world != null && world.provider.getDimension() == id();
    }

    public static boolean is(int dimension) {
        return dimension == id();
    }

    /** Точка появления: центр острова, на блок выше поверхности. */
    public static BlockPos spawnPoint() {
        return new BlockPos(0, ChaosConfig.antechamber.islandSurfaceY + 1, 0);
    }

    private AntechamberDimension() {
    }
}
