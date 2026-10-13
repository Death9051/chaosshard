package ru.death.chaosshard.structure.bp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чертёж постройки без зависимостей от Minecraft: блоки — строки вида
 * {@code minecraft:quartz_block[variant=lines_y]}. Тот же код ставит постройку в мир
 * и рисует превью вне игры, поэтому превью совпадает с игрой.
 *
 * Координаты относительные: (0, 0, 0) — центр на уровне земли (первый слой над грунтом).
 */
public class Blueprint {

    public static final String AIR = "minecraft:air";

    public final String name;
    public final Map<Long, String> blocks = new LinkedHashMap<>();
    /** Сундуки: x, y, z, сторона (0 юг, 1 запад, 2 север, 3 восток) + таблица добычи. */
    public final List<Mark> chests = new ArrayList<>();
    /** Стража: x, y, z + вид. */
    public final List<Mark> guards = new ArrayList<>();
    /** Особые точки (лучи, страж-босс и т. п.). */
    public final List<Mark> marks = new ArrayList<>();
    /** Столбы фундамента: под этими точками в мире добивается опора до грунта. */
    public final Map<Long, String> footings = new LinkedHashMap<>();

    public static final class Mark {
        public final int x, y, z, facing;
        public final String kind;

        Mark(int x, int y, int z, int facing, String kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
            this.kind = kind;
        }
    }

    public Blueprint(String name) {
        this.name = name;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (long) (z + 512);
    }

    public static int kx(long k) {
        return (int) ((k >> 40) & 0xFFFFF) - 512;
    }

    public static int ky(long k) {
        return (int) ((k >> 20) & 0xFFFFF) - 512;
    }

    public static int kz(long k) {
        return (int) (k & 0xFFFFF) - 512;
    }

    // ------------------------------------------------------------------ запись

    public void set(int x, int y, int z, String id) {
        blocks.put(key(x, y, z), id);
    }

    public void setIfEmpty(int x, int y, int z, String id) {
        blocks.putIfAbsent(key(x, y, z), id);
    }

    public String get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        String s = get(x, y, z);
        return s != null && !s.equals(AIR);
    }

    public void clear(int x, int y, int z) {
        set(x, y, z, AIR);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, id);
                }
            }
        }
    }

    /** Диск (круг) радиуса r на высоте y. */
    public void disc(int cx, int y, int cz, double r, String id) {
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (x * x + z * z <= r * r + 0.01) {
                    set(cx + x, y, cz + z, id);
                }
            }
        }
    }

    /** Кольцо толщиной в блок: клетки на расстоянии (r-1, r]. */
    public void ring(int cx, int y, int cz, double r, String id) {
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r + 0.01 && d > r - 1.0) {
                    set(cx + x, y, cz + z, id);
                }
            }
        }
    }

    /** Клетки кольца, по которым удобно ставить что-то поштучно. */
    public static List<int[]> ringCells(double r) {
        List<int[]> out = new ArrayList<>();
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r + 0.01 && d > r - 1.0) {
                    out.add(new int[]{x, z});
                }
            }
        }
        return out;
    }

    /** Клетки оболочки: outer >= d > inner - 1 (без дыр между слоями разного радиуса). */
    public static List<int[]> shellCells(double outer, double inner) {
        List<int[]> out = new ArrayList<>();
        int R = (int) Math.ceil(outer);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= outer + 0.01 && d > inner - 1.0) {
                    out.add(new int[]{x, z});
                }
            }
        }
        return out;
    }

    public void chest(int x, int y, int z, int facing, String loot) {
        set(x, y, z, "minecraft:chest[facing=" + face(facing) + "]");
        chests.add(new Mark(x, y, z, facing, loot));
    }

    public void guard(int x, int y, int z, String kind) {
        guards.add(new Mark(x, y, z, 0, kind));
    }

    public void mark(int x, int y, int z, String kind) {
        marks.add(new Mark(x, y, z, 0, kind));
    }

    public void footing(int x, int z, String id) {
        footings.put(key(x, 0, z), id);
    }

    /** Под всеми клетками нижнего слоя (y = yMin) — опора из id. */
    public void footingsUnder(int yMin, String id) {
        for (long k : blocks.keySet()) {
            if (ky(k) == yMin && !blocks.get(k).equals(AIR)) {
                footings.put(key(kx(k), yMin, kz(k)), id);
            }
        }
    }

    public static String face(int f) {
        switch (f & 3) {
            case 0: return "south";
            case 1: return "west";
            case 2: return "north";
            default: return "east";
        }
    }

    /** Сторона «наружу» для клетки (x, z) относительно центра. */
    public static String outward(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x >= 0 ? "east" : "west";
        }
        return z >= 0 ? "south" : "north";
    }

    public static String opposite(String f) {
        switch (f) {
            case "east": return "west";
            case "west": return "east";
            case "north": return "south";
            default: return "north";
        }
    }

    public int[] bounds() {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long k : blocks.keySet()) {
            int x = kx(k), y = ky(k), z = kz(k);
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x);
            b[4] = Math.max(b[4], y);
            b[5] = Math.max(b[5], z);
        }
        return b;
    }
}
