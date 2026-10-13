package ru.death.chaosshard.act5;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;

/** Разлом — узел Завесы, где ждёт Архонт. Свой мир: обломки над пустотой и арена с пятью пилонами. */
public final class RiftDimension {

    public static DimensionType TYPE;
    /** Высота пола арены, её радиус, радиус кольца пилонов, высота пилона. */
    public static final int FLOOR = 64, ARENA_R = 30, PYLON_RING = 20, PYLON_H = 8;

    public static void register() {
        int id = id();
        if (DimensionManager.isDimensionRegistered(id)) {
            ChaosShard.LOG.error("ID измерения {} уже занят другим модом. Поменяйте order.riftDimensionId в chaosshard.cfg.", id);
            throw new IllegalStateException("Chaos Shard: dimension id " + id + " is already registered");
        }
        TYPE = DimensionType.register("chaosshard_rift", "_chaosshard_rift", id, WorldProviderRift.class, false);
        DimensionManager.registerDimension(id, TYPE);
    }

    public static int id() {
        return ChaosConfig.order.riftDimensionId;
    }

    public static boolean is(World world) {
        return world != null && world.provider.getDimension() == id();
    }

    /** Куда встаёт пришедший: южный край арены, лицом к центру. */
    public static BlockPos arrival() {
        return new BlockPos(0, FLOOR + 1, -(ARENA_R - 4));
    }

    public static BlockPos center() {
        return new BlockPos(0, FLOOR + 1, 0);
    }

    /** Ядро пилона героя i (0 Каэль … 4 Элис): на кольце, вершина колонны. */
    public static BlockPos pylon(int i) {
        double a = Math.toRadians(90 + 72 * i);
        return new BlockPos((int) Math.round(Math.cos(a) * PYLON_RING), FLOOR + PYLON_H + 1, (int) Math.round(Math.sin(a) * PYLON_RING));
    }

    private RiftDimension() {
    }
}
