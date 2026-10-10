package ru.death.chaosshard.heart;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.nbt.NBTTagCompound;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.init.ModBlocks;

/**
 * Ставит Сердце Острова в Преддверии один раз на мир — и в новых мирах, и в уже существующих.
 */
public final class HeartPlacer {

    public static BlockPos heartPos() {
        return new BlockPos(0, ChaosConfig.antechamber.islandSurfaceY + 1, 5);
    }

    public static void ensure(World world) {
        if (world.isRemote || !AntechamberDimension.is(world) || !ChaosConfig.heart.enabled) {
            return;
        }
        Data data = Data.get(world);
        if (data.placed) {
            return;
        }
        place(world);
        data.placed = true;
        data.markDirty();
    }

    public static void place(World world) {
        BlockPos p = heartPos();
        world.setBlockState(p.down(), ModBlocks.CHAOS_STONE.getDefaultState());
        world.setBlockState(p, ModBlocks.ISLAND_HEART.getDefaultState());
        ChaosShard.LOG.info("Сердце Острова поставлено в {}", p);
    }

    public static class Data extends WorldSavedData {
        static final String NAME = "chaosshard_heart";
        boolean placed;

        public Data(String name) {
            super(name);
        }

        static Data get(World world) {
            Data d = (Data) world.getPerWorldStorage().getOrLoadData(Data.class, NAME);
            if (d == null) {
                d = new Data(NAME);
                world.getPerWorldStorage().setData(NAME, d);
            }
            return d;
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            placed = nbt.getBoolean("placed");
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            nbt.setBoolean("placed", placed);
            return nbt;
        }
    }

    private HeartPlacer() {
    }
}
