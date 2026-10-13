package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Разрушенное святилище Ордена: каменная площадь на подпорной стене, кольцо обломанных колонн,
 * парящие над ними глыбы, утопленный сад и два световых столба над тайником. Здесь сторожит Хранитель.
 */
public final class Sanctum {

    static final String STONE = "minecraft:stone";
    static final String ANDESITE = "minecraft:stone[variant=andesite]";
    static final String ANDESITE_S = "minecraft:stone[variant=smooth_andesite]";
    static final String COBBLE = "minecraft:cobblestone";
    static final String MOSSY = "minecraft:mossy_cobblestone";
    static final String BRICK = "minecraft:stonebrick";
    static final String BRICK_M = "minecraft:stonebrick[variant=mossy_stonebrick]";
    static final String BRICK_C = "minecraft:stonebrick[variant=cracked_stonebrick]";
    static final String BRICK_X = "minecraft:stonebrick[variant=chiseled_stonebrick]";
    static final String GRAVEL = "minecraft:gravel";
    static final String SLAB = "minecraft:stone_slab[variant=smooth_brick,half=bottom]";
    static final String STAIRS = "minecraft:stone_brick_stairs";

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("sanctum");
        int R = 11;
        int floor = 2; // уровень покрытия площади

        // --- тело площади и подпорная стена
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z) + noise(x, z) * 0.9;
                if (d > R + 0.5) {
                    continue;
                }
                boolean edge = d > R - 1.0;
                for (int y = -1; y < floor; y++) {
                    b.set(x, y, z, edge ? pick(r, BRICK, BRICK_M, BRICK_C, ANDESITE, COBBLE) : STONE);
                }
                b.set(x, floor, z, edge ? pick(r, BRICK, BRICK_C, ANDESITE_S) : paving(r, x, z));
                if (edge && r.nextInt(3) == 0) {
                    // зубцы стены
                    b.set(x, floor + 1, z, pick(r, BRICK, BRICK_M, COBBLE));
                }
            }
        }
        // контрфорсы у подножия стены
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + r.nextDouble() * 0.2;
            int x = (int) Math.round(Math.cos(a) * (R + 1)), z = (int) Math.round(Math.sin(a) * (R + 1));
            if (z > R - 2 && Math.abs(x) <= 4) {
                continue; // не загораживать лестницу
            }
            int hgt = 1 + r.nextInt(floor + 1);
            for (int y = -1; y <= hgt; y++) {
                b.set(x, y, z, pick(r, ANDESITE, BRICK, COBBLE));
            }
        }
        // --- парадная лестница на юг
        for (int step = 0; step <= floor + 1; step++) {
            int z = R - 1 + step;
            int y = floor - step;
            for (int x = -3; x <= 3; x++) {
                for (int yy = -1; yy < y; yy++) {
                    b.set(x, yy, z, STONE);
                }
                if (y >= 0) {
                    b.set(x, y, z, STAIRS + "[facing=north]");
                }
                for (int yy = y + 1; yy <= floor + 2; yy++) {
                    b.clear(x, yy, z);
                }
            }
            for (int s = -4; s <= 4; s += 8) {
                for (int yy = -1; yy <= y; yy++) {
                    b.set(s, yy, z, BRICK);
                }
            }
        }

        // --- утопленный сад в центре
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean rim = Math.abs(x) == 4 || Math.abs(z) == 4;
                if (rim) {
                    b.set(x, floor, z, BRICK);
                    b.set(x, floor + 1, z, (x + z) % 2 == 0 ? SLAB : BRICK_M);
                } else {
                    b.set(x, floor, z, "minecraft:grass");
                    int k = r.nextInt(6);
                    if (k == 0) b.set(x, floor + 1, z, "minecraft:tallgrass[type=tall_grass]");
                    else if (k == 1) b.set(x, floor + 1, z, "minecraft:red_flower[type=oxeye_daisy]");
                    else if (k == 2) b.set(x, floor + 1, z, "minecraft:tallgrass[type=fern]");
                }
            }
        }
        // проход в сад с юга
        b.clear(0, floor + 1, 4);
        b.set(0, floor, 4, BRICK_X);
        // помост с тайником
        b.fill(-1, floor, -1, 1, floor, 1, BRICK_X);
        b.fill(-1, floor + 1, -1, 1, floor + 1, 1, SLAB);
        b.chest(0, floor + 1, 0, 0, "sanctum");
        // излучатели лучей
        for (int sx = -2; sx <= 2; sx += 4) {
            b.set(sx, floor, -2, BRICK_X);
            b.clear(sx, floor + 1, -2);
            b.mark(sx, floor + 1, -2, "beam");
        }
        b.mark(0, floor + 1, 2, "keeper");

        // --- кольцо обломанных колонн
        int clumps = 9 + r.nextInt(3);
        for (int i = 0; i < clumps; i++) {
            double a = i * Math.PI * 2 / clumps + (r.nextDouble() - 0.5) * 0.3;
            double rad = R - 2.5;
            int cx = (int) Math.round(Math.cos(a) * rad), cz = (int) Math.round(Math.sin(a) * rad);
            if (cz > 6 && Math.abs(cx) <= 4) {
                continue;
            }
            int n = 1 + r.nextInt(3);
            for (int k = 0; k < n; k++) {
                int px = cx + r.nextInt(3) - 1, pz = cz + r.nextInt(3) - 1;
                int h = 3 + r.nextInt(7);
                for (int y = floor + 1; y <= floor + h; y++) {
                    b.set(px, y, pz, pick(r, BRICK, BRICK, ANDESITE, COBBLE, BRICK_C, STONE));
                }
                // обломанная вершина — ступенька
                String f = Blueprint.outward(px, pz);
                if (r.nextBoolean()) {
                    b.set(px, floor + h + 1, pz, STAIRS + "[facing=" + f + "]");
                }
                // осыпь у основания
                if (r.nextBoolean()) {
                    b.setIfEmpty(px + r.nextInt(3) - 1, floor + 1, pz + r.nextInt(3) - 1, pick(r, COBBLE, MOSSY, SLAB));
                }
            }
            // парящая глыба над колонной
            if (r.nextInt(3) > 0) {
                rock(b, cx, floor + 13 + r.nextInt(8), cz, r);
            }
        }
        // стража на площади
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            b.guard((int) Math.round(Math.cos(a) * 6.5), floor + 1, (int) Math.round(Math.sin(a) * 6.5), "order_guard");
        }
        b.footingsUnder(-1, STONE);
        return b;
    }

    /** Парящая глыба: неровный кусок камня и пара обломков под ним. */
    static void rock(Blueprint b, int cx, int cy, int cz, Random r) {
        int w = 1 + r.nextInt(2), h = 2 + r.nextInt(3);
        for (int x = -w; x <= w; x++) {
            for (int z = -w; z <= w; z++) {
                for (int y = 0; y <= h; y++) {
                    double e = Math.abs(x) + Math.abs(z) * 0.8 + Math.abs(y - h / 2.0) * 0.6;
                    if (e <= w + 0.8 + r.nextDouble() * 0.6) {
                        b.set(cx + x, cy + y, cz + z, pick(r, STONE, ANDESITE, COBBLE, BRICK_C, STONE));
                    }
                }
            }
        }
        // «капли» под глыбой
        for (int i = 0; i < 2 + r.nextInt(3); i++) {
            b.set(cx + r.nextInt(3) - 1, cy - 2 - r.nextInt(4), cz + r.nextInt(3) - 1, pick(r, STONE, COBBLE, ANDESITE));
        }
    }

    static String paving(Random r, int x, int z) {
        int k = r.nextInt(10);
        if (k < 4) return "minecraft:stone[variant=smooth_andesite]";
        if (k < 6) return COBBLE;
        if (k < 7) return GRAVEL;
        if (k < 8) return MOSSY;
        return STONE;
    }

    static double noise(int x, int z) {
        long h = x * 73856093L ^ z * 19349663L;
        h = (h ^ (h >>> 13)) * 0x5bd1e995L;
        return ((h >>> 8) & 0xFF) / 255.0 - 0.5;
    }

    static String pick(Random r, String... s) {
        return s[r.nextInt(s.length)];
    }

    private Sanctum() {
    }
}
