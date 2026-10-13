package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Березовый павильон Ордена: каменное кольцо-основание, восемь светлых колонн
 * с каменными поясами, решётчатая многоярусная кровля и шпиль. В центре — помост с подношением.
 */
public final class Pavilion {

    static final String LOG = "minecraft:log[variant=birch,axis=y]";
    static final String LOG_X = "minecraft:log[variant=birch,axis=x]";
    static final String LOG_Z = "minecraft:log[variant=birch,axis=z]";
    static final String PLANK = "minecraft:planks[variant=birch]";
    static final String FENCE = "minecraft:birch_fence";
    static final String SLAB = "minecraft:wooden_slab[variant=birch,half=bottom]";
    static final String SLAB_TOP = "minecraft:wooden_slab[variant=birch,half=top]";
    static final String COBBLE = "minecraft:cobblestone";
    static final String MOSSY = "minecraft:mossy_cobblestone";
    static final String ANDESITE = "minecraft:stone[variant=andesite]";
    static final String BRICK = "minecraft:stonebrick";
    static final String BRICK_M = "minecraft:stonebrick[variant=mossy_stonebrick]";
    static final String BRICK_C = "minecraft:stonebrick[variant=cracked_stonebrick]";
    static final String DIORITE = "minecraft:stone[variant=smooth_diorite]";
    static final String QUARTZ = "minecraft:quartz_block";

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("pavilion");
        // --- основание: кольцо крупного камня, внутри мох и плитка
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = oct(x, z);
                if (d > 7.2) {
                    continue;
                }
                if (d > 6.2) {
                    b.set(x, -1, z, pick(r, BRICK, BRICK_M, BRICK_C));
                    if (r.nextInt(5) < 2 && (x + z) % 2 == 0) {
                        b.set(x, 0, z, pick(r, BRICK, BRICK, BRICK_M, ANDESITE));
                    }
                } else if (d > 5.2) {
                    b.set(x, -1, z, BRICK);
                    b.set(x, 0, z, "minecraft:stone_slab[variant=smooth_brick,half=bottom]");
                } else {
                    b.set(x, -1, z, r.nextInt(3) == 0 ? MOSSY : (r.nextInt(2) == 0 ? "minecraft:grass" : DIORITE));
                }
            }
        }
        // ступени на юг
        for (int x = -1; x <= 1; x++) {
            b.set(x, 0, 7, "minecraft:stone_brick_stairs[facing=north]");
            b.set(x, 0, 6, "minecraft:stone_slab[variant=smooth_brick,half=bottom]");
        }
        // --- помост в центре
        b.fill(-2, 0, -2, 2, 0, 2, DIORITE);
        b.fill(-1, 1, -1, 1, 1, 1, QUARTZ);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            b.set(c[0], 1, c[1], SLAB);
        }
        b.chest(0, 2, 0, 0, "pavilion");
        b.set(-1, 2, 0, "minecraft:flower_pot");
        b.set(1, 2, 0, "minecraft:flower_pot");

        // --- колонны
        int top = 9;
        int[][] posts = {{4, 2}, {4, -2}, {-4, 2}, {-4, -2}, {2, 4}, {-2, 4}, {2, -4}, {-2, -4}};
        for (int[] p : posts) {
            for (int y = 0; y < top; y++) {
                String s = LOG;
                if (y == 0 || y == 1 || y == 4 || y == 5) {
                    s = y == 0 ? ANDESITE : (r.nextInt(3) == 0 ? MOSSY : COBBLE);
                }
                b.set(p[0], y, p[1], s);
            }
            // каменная «манжета» на нижнем поясе
            String f = Blueprint.outward(p[0], p[1]);
            int ox = f.equals("east") ? 1 : f.equals("west") ? -1 : 0;
            int oz = f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
            b.set(p[0] + ox, 0, p[1] + oz, "minecraft:stone_brick_stairs[facing=" + Blueprint.opposite(f) + "]");
            b.set(p[0] + ox, 1, p[1] + oz, COBBLE);
            b.set(p[0] + ox, 4, p[1] + oz, ANDESITE);
            b.set(p[0], top, p[1], PLANK);
        }
        // --- кровля: решётка «#» из перекрещённых балок, три яруса
        hash(b, top, 2, 5);
        // обвязка по колоннам
        for (int i = -2; i <= 2; i++) {
            b.set(i, top, 4, LOG_X);
            b.set(i, top, -4, LOG_X);
            b.set(4, top, i, LOG_Z);
            b.set(-4, top, i, LOG_Z);
        }
        for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            b.set(c[0], top, c[1], SLAB_TOP);
        }
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            b.set(c[0], top + 1, c[1], FENCE);
        }
        hash(b, top + 2, 1, 3);
        for (int[] c : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            b.set(c[0], top + 3, c[1], FENCE);
        }
        b.fill(-1, top + 4, -1, 1, top + 4, 1, SLAB);
        for (int[] c : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            b.set(c[0], top + 4, c[1], SLAB);
        }
        // шпиль
        for (int y = top + 4; y <= top + 9; y++) {
            b.set(0, y, 0, y == top + 6 || y == top + 7 ? COBBLE : LOG);
        }
        b.set(0, top + 10, 0, FENCE);
        b.set(0, top + 11, 0, FENCE);
        // свисающий фонарь над помостом
        for (int y = top; y <= top + 3; y++) {
            b.setIfEmpty(0, y, 0, FENCE);
        }
        b.set(0, top - 1, 0, "minecraft:glowstone");
        b.footingsUnder(-1, BRICK);
        return b;
    }

    /** Решётка «#»: балки вдоль x на z=±o и вдоль z на x=±o, длиной ±len. */
    static void hash(Blueprint b, int y, int o, int len) {
        for (int i = -len; i <= len; i++) {
            b.set(i, y, o, LOG_X);
            b.set(i, y, -o, LOG_X);
            b.set(o, y, i, LOG_Z);
            b.set(-o, y, i, LOG_Z);
        }
        for (int[] c : new int[][]{{o, o}, {-o, o}, {o, -o}, {-o, -o}}) {
            b.set(c[0], y, c[1], PLANK);
        }
        for (int[] c : new int[][]{{len, o}, {len, -o}, {-len, o}, {-len, -o}, {o, len}, {-o, len}, {o, -len}, {-o, -len}}) {
            b.set(c[0], y, c[1], PLANK);
            b.set(c[0], y - 1, c[1], FENCE);
        }
    }

    /** Квадратная рама полуразмера h на высоте y (брёвна вдоль сторон, углы — доски). */
    static void frame(Blueprint b, int y, int h, int unused) {
        for (int i = -h; i <= h; i++) {
            b.set(i, y, -h, LOG_X);
            b.set(i, y, h, LOG_X);
            b.set(-h, y, i, LOG_Z);
            b.set(h, y, i, LOG_Z);
        }
        for (int[] c : new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
            b.set(c[0], y, c[1], PLANK);
        }
    }

    /** Перила-стойки по углам и серединам между ярусами. */
    static void rails(Blueprint b, int y, int h) {
        for (int[] c : new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}, {0, -h}, {0, h}, {-h, 0}, {h, 0}}) {
            b.set(c[0], y, c[1], FENCE);
        }
    }

    /** «Восьмиугольное» расстояние. */
    static double oct(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        return Math.max(Math.max(ax, az), (ax + az) * 0.75);
    }

    static String pick(Random r, String... s) {
        return s[r.nextInt(s.length)];
    }

    private Pavilion() {
    }
}
