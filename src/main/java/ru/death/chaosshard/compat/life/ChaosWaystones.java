package ru.death.chaosshard.compat.life;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.death.chaosshard.ChaosShard;

/**
 * Путевые камни Waystones у Якорей и Опор. Мод не нужен для сборки — блок и имя ставятся по названиям,
 * без Waystones ничего не происходит.
 */
public final class ChaosWaystones {

    static Block block() {
        return Block.getBlockFromName("waystones:waystone");
    }

    public static boolean available() {
        return block() != null;
    }

    /** Поставить камень у точки (на первой ровной клетке рядом). true — поставлен. */
    public static boolean place(World w, BlockPos near, String name) {
        return place(w, near, name, 2, 5);
    }

    /** Поставить камень на кольце от rMin до rMax блоков от точки. */
    public static boolean place(World w, BlockPos near, String name, int rMin, int rMax) {
        Block b = block();
        if (b == null) {
            return false;
        }
        for (int r = rMin; r <= rMax; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    BlockPos p = w.getHeight(near.add(dx, 0, dz));
                    if (Math.abs(p.getY() - near.getY()) > 6 || !w.getBlockState(p.down()).isSideSolid(w, p.down(), net.minecraft.util.EnumFacing.UP)
                            || !w.isAirBlock(p) || !w.isAirBlock(p.up())) {
                        continue;
                    }
                    if (put(w, b, p, name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Есть ли уже путевой камень в радиусе. */
    public static boolean nearby(World w, BlockPos at, int r) {
        Block b = block();
        if (b == null) {
            return false;
        }
        for (BlockPos p : BlockPos.getAllInBox(at.add(-r, -4, -r), at.add(r, 6, r))) {
            if (w.getBlockState(p).getBlock() == b) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean put(World w, Block b, BlockPos p, String name) {
        IBlockState lower = b.getDefaultState(), upper = b.getDefaultState();
        for (IProperty prop : lower.getPropertyKeys()) {
            if ("base".equals(prop.getName())) {
                lower = lower.withProperty(prop, Boolean.TRUE);
                upper = upper.withProperty(prop, Boolean.FALSE);
            }
        }
        w.setBlockState(p, lower, 3);
        w.setBlockState(p.up(), upper, 3);
        TileEntity te = w.getTileEntity(p);
        if (te == null) {
            return false;
        }
        try {
            te.getClass().getMethod("setWaystoneName", String.class).invoke(te, name);
        } catch (Exception ex) {
            ChaosShard.LOG.warn("Waystones: не удалось назвать камень: {}", ex.toString());
        }
        try {
            te.getClass().getMethod("setWasGenerated", boolean.class).invoke(te, true);
        } catch (Exception ignored) {
            // в этой версии метода нет — не страшно
        }
        te.markDirty();
        return true;
    }

    private ChaosWaystones() {
    }
}
