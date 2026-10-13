package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Опора Завесы: круглая площадь на подпорной стене, в центре — высокая колонна с лучом в небо,
 * по кругу — четыре обелиска. Перед колонной стоит Светоч. Облик — по герою.
 * Опоры не появляются сами: их вызывает ритуал Знамения.
 */
public final class VeilPillar {

    /** Радиус площади, радиус и высота колонны (от пола). По ним Опора рушится. */
    public static final int R = 14, FLOOR = 2, COLUMN_H = 34;
    public static final double COLUMN_R = 2.5;

    /** Облик Опоры. */
    static final class Palette {
        final String floor, floorAlt, ray, wall, wallTop, column, columnAlt, ring, light, crown;

        Palette(String floor, String floorAlt, String ray, String wall, String wallTop, String column, String columnAlt,
                String ring, String light, String crown) {
            this.floor = floor;
            this.floorAlt = floorAlt;
            this.ray = ray;
            this.wall = wall;
            this.wallTop = wallTop;
            this.column = column;
            this.columnAlt = columnAlt;
            this.ring = ring;
            this.light = light;
            this.crown = crown;
        }
    }

    static final String Q = "minecraft:quartz_block";
    static final String Q_CH = "minecraft:quartz_block[variant=chiseled]";
    static final String Q_LN = "minecraft:quartz_block[variant=lines_y]";
    static final String GOLD = "minecraft:gold_block";
    static final String LANTERN = "minecraft:sea_lantern";
    static final String BRICK = "minecraft:stonebrick";
    static final String BRICK_X = "minecraft:stonebrick[variant=chiseled_stonebrick]";

    /** Облик по герою: 0 Каэль, 1 Ирена, 2 Вельмор, 3 Лиан, 4 Элис. */
    static Palette palette(int hero) {
        switch (hero) {
            case 1: // Ирена: белый мрамор, голубая вода, лазурь
                return new Palette(Q, "minecraft:concrete[color=white]", "minecraft:concrete[color=light_blue]", BRICK, Q_CH, Q_LN, Q,
                        "minecraft:lapis_block", LANTERN, LANTERN);
            default:
                return new Palette(Q, "minecraft:stone[variant=smooth_diorite]", GOLD, BRICK, Q_CH, Q_LN, Q, GOLD, LANTERN, "minecraft:glowstone");
        }
    }

    public static Blueprint build(int hero, Random r) {
        Palette p = palette(hero);
        Blueprint b = new Blueprint("pillar_" + hero);
        int floor = FLOOR;

        // --- площадь на подпорной стене
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 0.5) {
                    continue;
                }
                boolean edge = d > R - 1.0;
                for (int y = -1; y < floor; y++) {
                    b.set(x, y, z, edge ? p.wall : BRICK);
                }
                boolean ray = d > COLUMN_R + 1 && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
                boolean ring = Math.abs(d - 7) < 0.55 || Math.abs(d - 11.5) < 0.55;
                b.set(x, floor, z, edge ? p.wall : ray ? p.ray : ring ? p.floorAlt : p.floor);
                if (edge) {
                    b.set(x, floor + 1, z, ((x + z) & 1) == 0 ? p.wallTop : p.wall);
                }
                // над площадью — пусто: Опора встаёт где угодно, холм срезается
                for (int y = floor + (edge ? 2 : 1); y <= floor + 24; y++) {
                    b.setIfEmpty(x, y, z, Blueprint.AIR);
                }
            }
        }
        // --- парадная лестница на юг
        for (int step = 0; step <= floor + 1; step++) {
            int z = R + step;
            int y = floor - step;
            for (int x = -2; x <= 2; x++) {
                for (int yy = -1; yy < y; yy++) {
                    b.set(x, yy, z, BRICK);
                }
                if (y >= 0) {
                    b.set(x, y, z, "minecraft:quartz_stairs[facing=north]");
                }
                for (int yy = Math.max(y + 1, 0); yy <= floor + 3; yy++) {
                    b.clear(x, yy, z);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            b.set(x, floor, R, p.floor);
            b.clear(x, floor + 1, R);
        }

        // --- колонна
        for (int y = floor + 1; y <= floor + COLUMN_H; y++) {
            boolean band = (y - floor) % 8 == 0;
            for (int[] c : Blueprint.shellCells(COLUMN_R, 0)) {
                double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
                boolean outer = d > COLUMN_R - 1.0;
                b.set(c[0], y, c[1], band && outer ? p.ring : outer ? p.column : p.columnAlt);
            }
        }
        // основание колонны пошире
        b.disc(0, floor + 1, 0, COLUMN_R + 1, p.columnAlt);
        b.ring(0, floor + 1, 0, COLUMN_R + 1, Q_CH);
        // венец: золото, свет, луч
        int top = floor + COLUMN_H;
        b.disc(0, top + 1, 0, COLUMN_R + 0.6, p.ring);
        for (int[] c : Blueprint.ringCells(COLUMN_R + 0.6)) {
            if (((c[0] + c[1]) & 1) == 0) {
                b.set(c[0], top + 2, c[1], p.crown);
            }
        }
        b.set(0, top + 1, 0, p.crown);
        b.clear(0, top + 2, 0);
        b.mark(0, top + 2, 0, "beam");

        // --- четыре обелиска по диагоналям
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            int ox = (int) Math.round(Math.cos(a) * 10), oz = (int) Math.round(Math.sin(a) * 10);
            b.set(ox, floor + 1, oz, Q_CH);
            for (int y = floor + 2; y <= floor + 6; y++) {
                b.set(ox, y, oz, p.column);
            }
            b.set(ox, floor + 7, oz, p.light);
            b.set(ox, floor + 8, oz, p.ring);
        }
        // светильники в полу у края
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            b.set((int) Math.round(Math.cos(a) * (R - 2)), floor, (int) Math.round(Math.sin(a) * (R - 2)), p.light);
        }

        // Ирена: Святой камень за колонной — вокруг Опоры святая земля
        if (hero == 1) {
            b.set(0, floor, -6, GOLD);
            b.set(0, floor + 1, -6, "chaosshard:shrine_stone");
            b.mark(0, floor + 1, -6, "shrine");
        }
        // --- герой перед колонной, стража по кругу
        b.mark(0, floor + 1, 6, "hero");
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            b.guard((int) Math.round(Math.cos(a) * 8), floor + 1, (int) Math.round(Math.sin(a) * 8), i == 1 ? "order_captain" : "order_guard");
        }
        b.footingsUnder(-1, BRICK);
        return b;
    }

    private VeilPillar() {
    }
}
