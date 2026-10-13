package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Оранжерея Ордена: круглый стеклянный зал под колоколом-куполом с дубовыми рёбрами,
 * фонарь-беседка на макушке, малая пристройка сбоку. Внутри сад и сундук садовницы.
 */
public final class Greenhouse {

    static final String PLANK = "minecraft:planks[variant=oak]";
    static final String LOG = "minecraft:log[variant=oak,axis=y]";
    static final String FENCE = "minecraft:fence";
    static final String GLASS = "minecraft:glass";
    static final String GLASS_W = "minecraft:stained_glass[color=white]";
    static final String GLASS_P = "minecraft:stained_glass[color=pink]";
    static final String SLAB_TOP = "minecraft:wooden_slab[variant=oak,half=top]";
    static final String SLAB = "minecraft:wooden_slab[variant=oak,half=bottom]";
    static final String GRASS = "minecraft:grass";
    static final String PATH = "minecraft:grass_path";
    static final String BRICK = "minecraft:stonebrick";
    static final String LEAVES = "minecraft:leaves[variant=oak,check_decay=false,decayable=false]";

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("greenhouse");
        int R = 7;
        hall(b, 0, 0, R, 7, 10, r, true);
        // пристройка на восток
        int ax = R + 4;
        hall(b, ax, 1, 4, 4, 5, r, false);
        // проход между залами
        for (int y = 0; y <= 3; y++) {
            for (int z = 0; z <= 2; z++) {
                for (int x = R - 1; x <= R + 1; x++) {
                    b.clear(x, y, z);
                }
            }
        }
        for (int x = R - 1; x <= R + 1; x++) {
            for (int z = 0; z <= 2; z++) {
                b.set(x, -1, z, PATH);
                b.set(x, 4, z, PLANK);
            }
            b.set(x, 0, -1, PLANK);
            b.set(x, 0, 3, PLANK);
            for (int y = 1; y <= 3; y++) {
                b.set(x, y, -1, GLASS);
                b.set(x, y, 3, GLASS);
            }
        }
        // тропа к входу с юга и через зал
        for (int z = 0; z <= R + 4; z++) {
            for (int x = -1; x <= 1; x++) {
                if (z > R || x == 0 || r.nextInt(3) > 0) {
                    b.set(x, -1, z, PATH);
                }
            }
        }
        for (int z = R + 1; z <= R + 4; z += 3) {
            b.set(-2, 0, z, FENCE);
            b.set(-2, 1, z, "minecraft:torch");
            b.set(2, 0, z, FENCE);
            b.set(2, 1, z, "minecraft:torch");
        }
        garden(b, R, r);
        // сундук садовницы у северной стены
        b.chest(0, 0, -R + 2, 0, "greenhouse");
        b.set(-1, 0, -R + 2, "minecraft:flower_pot");
        b.set(1, 0, -R + 2, "minecraft:flower_pot");
        b.footingsUnder(-1, BRICK);
        return b;
    }

    /** Круглый зал: стены wallH, купол высотой domeH; door — вход с юга. */
    static void hall(Blueprint b, int cx, int cz, int R, int wallH, int domeH, Random r, boolean big) {
        // пол
        b.disc(cx, -1, cz, R + 0.5, GRASS);
        b.ring(cx, -1, cz, R + 0.5, BRICK);
        // цоколь и стены
        for (int[] c : Blueprint.ringCells(R + 0.5)) {
            int x = cx + c[0], z = cz + c[1];
            b.set(x, 0, z, PLANK);
            double ang = Math.atan2(c[1], c[0]);
            boolean post = isPost(c[0], c[1], R);
            for (int y = 1; y <= wallH; y++) {
                if (post) {
                    b.set(x, y, z, LOG);
                } else {
                    b.set(x, y, z, y == wallH - 2 ? PLANK : (y > wallH - 2 ? GLASS_W : GLASS));
                }
            }
            b.set(x, wallH + 1, z, PLANK);
        }
        // карниз
        for (int[] c : Blueprint.ringCells(R + 1.5)) {
            String f = Blueprint.outward(c[0], c[1]);
            b.set(cx + c[0], wallH + 1, cz + c[1], "minecraft:oak_stairs[facing=" + Blueprint.opposite(f) + ",half=top]");
        }
        // купол-колокол: стекло, дубовые рёбра, пояс посередине
        int y0 = wallH + 2;
        double[] rads = new double[domeH + 1];
        for (int dy = 0; dy <= domeH; dy++) {
            double t = dy / (double) domeH;
            rads[dy] = Math.max(0.5, (R + 0.5) * Math.pow(Math.cos(t * Math.PI / 2), 0.55));
        }
        int belt = domeH / 2;
        for (int dy = 0; dy <= domeH; dy++) {
            double inner = rads[dy];
            if (dy > 0) inner = Math.min(inner, rads[dy - 1]);
            if (dy < domeH) inner = Math.min(inner, rads[dy + 1]);
            for (int[] c : Blueprint.shellCells(rads[dy], dy == domeH ? 0 : inner)) {
                boolean rib = c[0] == 0 || c[1] == 0;
                String g;
                if (rib || dy == domeH) {
                    g = PLANK;
                } else if (big && swirl(c[0], c[1], dy, belt)) {
                    g = PLANK;
                } else {
                    g = (Math.floorMod(c[0] * 5 + c[1] * 3 + dy * 7, 23) == 0) ? GLASS_P : GLASS_W;
                }
                b.set(cx + c[0], y0 + dy, cz + c[1], g);
            }
        }
        if (big) {
            // фонарь-беседка на макушке
            int top = y0 + domeH + 1;
            b.fill(cx - 1, top - 1, cz - 1, cx + 1, top - 1, cz + 1, PLANK);
            for (int[] d : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                b.set(cx + d[0], top, cz + d[1], FENCE);
                b.set(cx + d[0], top + 1, cz + d[1], FENCE);
            }
            for (int[] d : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                b.set(cx + d[0], top, cz + d[1], GLASS);
                b.set(cx + d[0], top + 1, cz + d[1], GLASS);
            }
            b.set(cx, top, cz, "minecraft:glowstone");
            b.fill(cx - 1, top + 2, cz - 1, cx + 1, top + 2, cz + 1, SLAB);
            b.set(cx, top + 2, cz, PLANK);
            b.set(cx, top + 3, cz, FENCE);
            b.set(cx, top + 4, cz, FENCE);
            b.set(cx, top + 5, cz, "minecraft:end_rod[facing=up]");
            // вход: арка с юга
            for (int y = 0; y <= 4; y++) {
                for (int x = -1; x <= 1; x++) {
                    b.clear(cx + x, y, cz + R);
                }
            }
            for (int x = -2; x <= 2; x += 4) {
                for (int y = 0; y <= 5; y++) {
                    b.set(cx + x, y, cz + R, LOG);
                }
            }
            b.fill(cx - 1, 5, cz + R, cx + 1, 5, cz + R, PLANK);
            b.set(cx - 1, 4, cz + R, "minecraft:oak_stairs[facing=east,half=top]");
            b.set(cx + 1, 4, cz + R, "minecraft:oak_stairs[facing=west,half=top]");
        }
        // свет внутри
        b.set(cx, y0 + domeH - 1, cz, "minecraft:glowstone");
    }

    /** Завитки на куполе — дуги из досок, как кованые узоры на референсе. */
    static boolean swirl(int x, int z, int dy, int belt) {
        if (dy <= 0 || dy >= belt) {
            return false;
        }
        double a = Math.atan2(z, x);
        double sector = Math.PI / 2;
        double local = Math.floorMod((int) Math.round(a / sector * 100), 100) / 100.0; // 0..1 внутри сектора
        double want = 0.3 + 0.4 * Math.sin(dy / (double) belt * Math.PI);
        // завиток — спираль, сходящаяся к центру сектора
        return Math.abs(local - want) < 0.045 || (dy == belt / 2 && Math.abs(local - 0.5) < 0.12);
    }

    static boolean isPost(int x, int z, int R) {
        if (x == 0 || z == 0) {
            return true;
        }
        return Math.abs(Math.abs(x) - Math.abs(z)) <= 0 && R > 4;
    }

    /** Сад: клумбы по кругу, кусты, деревце в центре. */
    static void garden(Blueprint b, int R, Random r) {
        String[] flowers = {"minecraft:red_flower[type=poppy]", "minecraft:red_flower[type=allium]", "minecraft:red_flower[type=oxeye_daisy]",
                "minecraft:red_flower[type=blue_orchid]", "minecraft:yellow_flower", "minecraft:red_flower[type=pink_tulip]",
                "minecraft:tallgrass[type=tall_grass]", "minecraft:tallgrass[type=fern]"};
        for (int x = -R + 1; x <= R - 1; x++) {
            for (int z = -R + 1; z <= R - 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R - 0.5 || Math.abs(x) <= 1 && z > 0) {
                    continue;
                }
                if (d > R - 2.5 && r.nextInt(3) == 0) {
                    b.set(x, 0, z, LEAVES);
                    if (r.nextInt(3) == 0) {
                        b.set(x, 1, z, LEAVES);
                    }
                } else if (d > 2.5 && r.nextInt(2) == 0) {
                    b.set(x, 0, z, flowers[r.nextInt(flowers.length)]);
                }
            }
        }
        // кольцо-бордюр вокруг деревца
        b.ring(0, -1, 0, 2.5, BRICK);
        b.ring(0, 0, 0, 2.5, "minecraft:stone_slab[variant=smooth_brick,half=bottom]");
        for (int y = 0; y <= 3; y++) {
            b.set(0, y, 0, LOG);
        }
        for (int dy = 3; dy <= 6; dy++) {
            double rad = dy == 6 ? 1 : (dy == 3 ? 1.6 : 2.3);
            for (int[] c : Blueprint.shellCells(rad, 0)) {
                if (!(c[0] == 0 && c[1] == 0 && dy <= 3)) {
                    b.setIfEmpty(c[0], dy, c[1], LEAVES);
                }
            }
        }
        b.set(0, 4, 0, LOG);
        // вьюнок на стойках
        for (int[] c : new int[][]{{0, -R}, {R, 0}, {-R, 0}}) {
            if (r.nextBoolean()) {
                String f = Blueprint.outward(c[0], c[1]);
                int vx = c[0] - Integer.signum(c[0]), vz = c[1] - Integer.signum(c[1]);
                for (int y = 1; y <= 2 + r.nextInt(4); y++) {
                    b.setIfEmpty(vx, y, vz, "minecraft:vine[" + f + "=true]");
                }
            }
        }
    }

    private Greenhouse() {
    }
}
