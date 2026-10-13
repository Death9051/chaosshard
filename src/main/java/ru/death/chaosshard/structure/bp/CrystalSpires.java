package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Хрустальные шпили Ордена: белые витые колонны, бирюзовые узлы,
 * капитель с хрустальной главой и подвесками. Обычно стоят у воды по 1–3.
 * Внутри главы большого шпиля — тайник.
 */
public final class CrystalSpires {

    static final String QUARTZ = "minecraft:quartz_block";
    static final String Q_CHISELED = "minecraft:quartz_block[variant=chiseled]";
    static final String Q_PILLAR = "minecraft:quartz_block[variant=lines_y]";
    static final String Q_SLAB = "minecraft:stone_slab[variant=quartz,half=bottom]";
    static final String Q_SLAB_TOP = "minecraft:stone_slab[variant=quartz,half=top]";
    static final String PRISM = "minecraft:prismarine[variant=prismarine_bricks]";
    static final String PRISM_DARK = "minecraft:prismarine[variant=dark_prismarine]";
    static final String LANTERN = "minecraft:sea_lantern";
    static final String GLASS_A = "minecraft:stained_glass[color=light_blue]";
    static final String GLASS_B = "minecraft:stained_glass[color=cyan]";
    static final String GLASS_W = "minecraft:stained_glass[color=white]";
    static final String PANE = "minecraft:stained_glass_pane[color=light_blue]";
    static final String PANE_C = "minecraft:stained_glass_pane[color=cyan]";

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("crystal_spires");
        int count = 1 + r.nextInt(3);
        // Большой шпиль в центре, малые — рядом
        spire(b, 0, 0, 30 + r.nextInt(6), 1.0, r, true);
        if (count >= 2) {
            spire(b, -9, 3 + r.nextInt(3), 20 + r.nextInt(5), 0.8, r, false);
        }
        if (count >= 3) {
            spire(b, 9, -2 - r.nextInt(3), 21 + r.nextInt(5), 0.8, r, false);
        }
        return b;
    }

    /** Один шпиль; h — высота ствола до капители. */
    static void spire(Blueprint b, int cx, int cz, int h, double scale, Random r, boolean big) {
        // --- подножие: ступенчатая база
        b.disc(cx, -1, cz, 3.6 * scale + 0.4, QUARTZ);
        b.ring(cx, 0, cz, 3.4 * scale + 0.4, Q_SLAB);
        b.disc(cx, 0, cz, 2.5, QUARTZ);
        b.ring(cx, 1, cz, 2.5, Q_SLAB);
        for (int[] c : Blueprint.ringCells(3.6 * scale + 0.4)) {
            b.footing(cx + c[0], cz + c[1], QUARTZ);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                b.footing(cx + dx, cz + dz, QUARTZ);
            }
        }

        // --- ствол: бирюзовое ядро, четыре белых стойки, две спирали
        for (int y = 1; y <= h; y++) {
            b.set(cx, y, cz, y % 6 == 3 ? LANTERN : PRISM_DARK);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    b.set(cx + sx, y, cz + sz, Q_PILLAR);
                }
            }
            // боковые грани ядра — стекло, сквозь него светит фонарь
            b.set(cx + 1, y, cz, y % 6 == 3 ? GLASS_A : QUARTZ);
            b.set(cx - 1, y, cz, y % 6 == 3 ? GLASS_A : QUARTZ);
            b.set(cx, y, cz + 1, y % 6 == 3 ? GLASS_A : QUARTZ);
            b.set(cx, y, cz - 1, y % 6 == 3 ? GLASS_A : QUARTZ);
        }
        // спирали: два витка навстречу друг другу, шаг 12
        double R = 2.2;
        for (int y = 2; y <= h - 2; y++) {
            for (int arm = 0; arm < 2; arm++) {
                double a = y * (2 * Math.PI / 12.0) + arm * Math.PI;
                int x = (int) Math.round(Math.cos(a) * R);
                int z = (int) Math.round(Math.sin(a) * R);
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;
                }
                b.set(cx + x, y, cz + z, QUARTZ);
                // гладкий переход полуплитой на следующем уровне
                double a2 = (y + 0.5) * (2 * Math.PI / 12.0) + arm * Math.PI;
                int x2 = (int) Math.round(Math.cos(a2) * R);
                int z2 = (int) Math.round(Math.sin(a2) * R);
                if ((x2 != x || z2 != z) && (Math.abs(x2) > 1 || Math.abs(z2) > 1)) {
                    b.setIfEmpty(cx + x2, y, cz + z2, Q_SLAB_TOP);
                }
            }
        }
        // бирюзовые узлы: кубы на гранях через каждые 9 блоков
        for (int y = 5; y < h - 3; y += 9) {
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                b.set(cx + d[0], y, cz + d[1], PRISM);
                b.set(cx + d[0], y + 1, cz + d[1], PRISM);
                b.set(cx + d[0] * 3 / 2, y + 2, cz + d[1] * 3 / 2, PRISM);
            }
        }

        // --- капитель: расширяется ступенями
        int y0 = h + 1;
        b.disc(cx, y0, cz, 2.3, QUARTZ);
        b.disc(cx, y0 + 1, cz, 3.3, QUARTZ);
        b.ring(cx, y0 + 1, cz, 3.3, Q_CHISELED);
        b.disc(cx, y0 + 2, cz, 4.4, QUARTZ);
        b.ring(cx, y0 + 2, cz, 4.4, Q_SLAB_TOP);
        b.set(cx, y0, cz, LANTERN);
        // подвески: нити хрусталя с краёв капители
        int[][] hang = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] d : hang) {
            int len = 3 + r.nextInt(4);
            for (int i = 1; i <= len; i++) {
                b.set(cx + d[0], y0 + 2 - i, cz + d[1], i == len ? GLASS_B : (i % 2 == 0 ? PANE_C : PANE));
            }
        }

        // --- хрустальная глава: луковица из стекла с белыми рёбрами
        int base = y0 + 3;
        double rx = 4.0 * scale + 0.6;
        int domeH = (int) Math.round(8 * scale) + 1;
        double[] rads = new double[domeH + 1];
        for (int dy = 0; dy <= domeH; dy++) {
            double t = dy / (double) domeH;
            // «медуза»: свес шире капители, потом плавно к острию
            double k = t < 0.18 ? 1.12 : Math.pow(Math.cos((t - 0.18) / 0.82 * Math.PI / 2), 0.7) * 1.12;
            rads[dy] = Math.max(0.6, rx * k);
        }
        for (int dy = 0; dy <= domeH; dy++) {
            double inner = rads[dy];
            if (dy > 0) inner = Math.min(inner, rads[dy - 1]);
            if (dy < domeH) inner = Math.min(inner, rads[dy + 1]);
            for (int[] c : Blueprint.shellCells(rads[dy], dy == domeH ? 0 : inner)) {
                boolean rib = c[0] == 0 || c[1] == 0 || Math.abs(c[0]) == Math.abs(c[1]);
                String g = rib ? GLASS_W : (Math.floorMod(c[0] * 7 + c[1] * 3 + dy, 3) == 0 ? GLASS_B : GLASS_A);
                b.set(cx + c[0], base + dy, cz + c[1], g);
            }
        }
        // нижний край — белые «лепестки» через клетку
        for (int[] c : Blueprint.ringCells(rads[0] + 1)) {
            if (Math.floorMod(c[0] + c[1], 2) == 0) {
                b.set(cx + c[0], base, cz + c[1], GLASS_W);
                b.set(cx + c[0], base - 1, cz + c[1], PANE);
            }
        }
        // внутри — бирюзовое ядро со светом
        for (int dy = 0; dy < domeH - 1; dy++) {
            b.set(cx, base + dy, cz, dy % 3 == 1 ? LANTERN : PRISM);
            if (dy < domeH - 3) {
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    b.set(cx + d[0], base + dy, cz + d[1], GLASS_B);
                }
            }
        }
        // шпиль на макушке
        for (int i = 1; i <= 3; i++) {
            b.set(cx, base + domeH + i, cz, i == 3 ? "minecraft:end_rod[facing=up]" : GLASS_A);
        }
        if (big) {
            // тайник в главе: пол из кварца, сундук у ядра
            b.disc(cx, base, cz, rx - 1.0, QUARTZ);
            b.set(cx, base, cz, LANTERN);
            for (int dy = 1; dy <= 2; dy++) {
                for (int[] c : Blueprint.ringCells(1.5)) {
                    b.clear(cx + c[0], base + dy, cz + c[1]);
                }
            }
            b.set(cx, base + 1, cz, LANTERN);
            b.chest(cx + 1, base + 1, cz, 3, "spire");
        }
    }

    private CrystalSpires() {
    }
}
