package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Купольная башня Ордена: три сужающихся яруса из кварца с пилястрами и колоннадами,
 * карнизы, белый купол с голубыми вставками. Внутри винтовая лестница вокруг оси,
 * на каждом ярусе — стража и сундук, наверху — капитан и казна.
 */
public final class DomeTower {

    static final String QUARTZ = "minecraft:quartz_block";
    static final String Q_PILLAR = "minecraft:quartz_block[variant=lines_y]";
    static final String Q_CHISELED = "minecraft:quartz_block[variant=chiseled]";
    static final String Q_SLAB = "minecraft:stone_slab[variant=quartz,half=bottom]";
    static final String Q_SLAB_TOP = "minecraft:stone_slab[variant=quartz,half=top]";
    static final String GLASS = "minecraft:stained_glass[color=light_blue]";
    static final String PANE = "minecraft:stained_glass_pane[color=light_blue]";
    static final String LANTERN = "minecraft:sea_lantern";
    static final String STAIRS = "minecraft:quartz_stairs";

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("dome_tower");
        int[] radius = {8, 7, 6};
        int[] height = {10, 9, 8};
        int y = 0;
        // фундамент
        b.disc(0, -1, 0, radius[0] + 1.5, QUARTZ);
        b.ring(0, 0, 0, radius[0] + 1.5, Q_SLAB);
        for (int tier = 0; tier < 3; tier++) {
            int R = radius[tier], H = height[tier];
            wall(b, R, y, H, tier, r);
            // пол яруса (кроме первого — там земля) с проёмом под лестницу
            if (tier > 0) {
                floor(b, R, y - 1);
            } else {
                b.disc(0, -1, 0, R - 0.5, "minecraft:stone[variant=smooth_diorite]");
            }
            // карниз над ярусом
            int top = y + H;
            for (int[] c : Blueprint.ringCells(R + 1.5)) {
                String f = Blueprint.outward(c[0], c[1]);
                b.set(c[0], top, c[1], STAIRS + "[facing=" + Blueprint.opposite(f) + ",half=top]");
            }
            b.disc(0, top, 0, R + 0.5, QUARTZ);
            for (int[] c : Blueprint.shellCells(2.9, 0)) {
                b.clear(c[0], top, c[1]);
            }
            if (tier < 2) {
                b.ring(0, top + 1, 0, R + 0.5, Q_SLAB);
            }
            y = top + 1;
        }
        // потолки-полы: последний ярус получает купол
        int domeBase = y - 1;
        double DR = radius[2] + 0.5;
        int domeH = 7;
        double[] rads = new double[domeH + 1];
        for (int dy = 0; dy <= domeH; dy++) {
            rads[dy] = Math.max(0.5, DR * Math.sqrt(Math.max(0, 1 - Math.pow(dy / (double) domeH, 2))));
        }
        for (int dy = 0; dy <= domeH; dy++) {
            double inner = rads[dy];
            if (dy > 0) inner = Math.min(inner, rads[dy - 1]);
            if (dy < domeH) inner = Math.min(inner, rads[dy + 1]);
            for (int[] c : Blueprint.shellCells(rads[dy], dy == domeH ? 0 : inner)) {
                boolean stud = dy % 3 == 1 && Math.floorMod(c[0] * 3 + c[1] * 5, 7) == 0;
                b.set(c[0], domeBase + dy, c[1], stud ? GLASS : (dy % 3 == 0 ? Q_CHISELED : QUARTZ));
            }
        }
        b.set(0, domeBase + domeH + 1, 0, Q_PILLAR);
        b.set(0, domeBase + domeH + 2, 0, "minecraft:end_rod[facing=up]");
        // под куполом — пустота (купол снаружи, зал внутри)
        for (int dy = 0; dy < domeH; dy++) {
            for (int[] c : Blueprint.shellCells(Math.max(0, rads[dy] - 1.2), 0)) {
                b.clear(c[0], domeBase + dy, c[1]);
            }
        }
        b.set(0, domeBase + domeH - 1, 0, LANTERN);

        // винтовая лестница вокруг оси: полуплиты, по полблока за шаг
        int topFloor = height[0] + height[1] + 2;
        for (int y1 = 0; y1 < topFloor; y1++) {
            b.set(0, y1, 0, Q_PILLAR);
        }
        int steps = topFloor * 2;
        for (int k = 0; k < steps; k++) {
            int yy = k / 2;
            String sl = k % 2 == 0 ? Q_SLAB : Q_SLAB_TOP;
            double a0 = Math.floorMod(k, 16) * (2 * Math.PI / 16);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    int ring = Math.max(Math.abs(x), Math.abs(z));
                    if (ring == 0) continue;
                    double ang = Math.atan2(z, x);
                    double diff = Math.abs(Math.IEEEremainder(ang - a0, 2 * Math.PI));
                    if (ring == 2 ? diff <= Math.PI / 16 + 1e-6 : (diff <= Math.PI / 8 + 1e-6 && k % 2 == 1)) {
                        b.set(x, yy, z, sl);
                        for (int h = 1; h <= 3; h++) {
                            b.clear(x, yy + h, z);
                        }
                    }
                }
            }
        }

        // стража и добыча по ярусам
        int[] floors = {0, height[0] + 1, height[0] + height[1] + 2};
        for (int tier = 0; tier < 3; tier++) {
            int fy = floors[tier];
            int R = radius[tier];
            b.chest(-(R - 2), fy, 0, 3, tier == 2 ? "tower_top" : "tower");
            if (tier < 2) {
                b.guard(R - 3, fy, 2, "order_guard");
                b.guard(R - 3, fy, -2, "order_guard");
            } else {
                b.guard(3, fy, 0, "order_captain");
            }
        }
        b.footingsUnder(-1, QUARTZ);
        return b;
    }

    /** Стена яруса: пилястры, окна-арки; на первом — вход с юга. */
    static void wall(Blueprint b, int R, int y0, int H, int tier, Random r) {
        for (int[] c : Blueprint.ringCells(R + 0.5)) {
            int x = c[0], z = c[1];
            double ang = Math.atan2(z, x);
            int sector = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * (tier == 0 ? 16 : 12) + 0.5);
            double frac = (ang + Math.PI) / (2 * Math.PI) * (tier == 0 ? 16 : 12) + 0.5 - sector;
            boolean pilaster = frac < 0.2 || frac > 0.8;
            for (int dy = 0; dy < H; dy++) {
                int y = y0 + dy;
                String s;
                if (pilaster) {
                    s = Q_PILLAR;
                } else if (tier == 0) {
                    // первый ярус: глухая стена, ряд маленьких окон
                    s = (dy == H - 4) ? GLASS : (dy == H - 3 || dy == 1 ? Q_CHISELED : QUARTZ);
                } else {
                    // колоннада: высокие окна-арки
                    if (dy == 0) s = QUARTZ;
                    else if (dy >= H - 2) s = dy == H - 2 ? Q_SLAB_TOP : QUARTZ;
                    else s = Blueprint.AIR;
                }
                b.set(x, y, z, s);
            }
        }
        // выступающие пилястры снаружи
        int n = tier == 0 ? 16 : 12;
        for (int i = 0; i < n; i++) {
            double a = (i + 0.5) * 2 * Math.PI / n - Math.PI;
            int x = (int) Math.round(Math.cos(a) * (R + 1)), z = (int) Math.round(Math.sin(a) * (R + 1));
            for (int dy = 0; dy < H; dy++) {
                b.set(x, y0 + dy, z, dy == H - 1 ? Q_CHISELED : Q_PILLAR);
            }
            if (tier == 0) {
                b.set(x, y0 + H - 4, z, LANTERN);
            }
        }
        // пустота внутри
        for (int dy = 0; dy < H; dy++) {
            for (int[] c : Blueprint.shellCells(R - 0.5, 0)) {
                b.clear(c[0], y0 + dy, c[1]);
            }
        }
        // проёмы колоннады: глубина в блок, за ними — голубое стекло
        if (tier > 0) {
            for (int[] c : Blueprint.ringCells(R + 0.5)) {
                for (int dy = 1; dy < H - 2; dy++) {
                    if (Blueprint.AIR.equals(b.get(c[0], y0 + dy, c[1]))) {
                        double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
                        int ix = (int) Math.round(c[0] * (d - 1) / d), iz = (int) Math.round(c[1] * (d - 1) / d);
                        b.set(ix, y0 + dy, iz, dy == H - 3 ? QUARTZ : GLASS);
                    }
                }
            }
        }
        if (tier == 0) {
            // вход: арка 3×5 с юга
            for (int dy = 0; dy < 5; dy++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = R - 1; z <= R + 1; z++) {
                        b.clear(x, y0 + dy, z);
                    }
                }
            }
            b.set(-1, y0 + 4, R, STAIRS + "[facing=east,half=top]");
            b.set(1, y0 + 4, R, STAIRS + "[facing=west,half=top]");
            b.set(0, y0 + 5, R + 1, LANTERN);
            for (int x = -2; x <= 2; x += 4) {
                for (int dy = 0; dy <= 5; dy++) {
                    b.set(x, y0 + dy, R + 1, Q_PILLAR);
                }
            }
        }
    }

    /** Пол яруса с проёмом радиуса 3 под лестницу и светом по кругу. */
    static void floor(Blueprint b, int R, int y) {
        for (int[] c : Blueprint.shellCells(R - 0.5, 0)) {
            double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
            if (d < 3.0) {
                continue;
            }
            b.set(c[0], y, c[1], (Math.abs(d - (R - 2)) < 0.5 && Math.floorMod(c[0] + c[1], 4) == 0) ? LANTERN : QUARTZ);
        }
    }

    private DomeTower() {
    }
}
