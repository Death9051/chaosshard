package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Монастырь Ордена (Акт 4, Ирена): каменная церковь с двускатной крышей, колокольня над входом,
 * скамьи, алтарь со Святым камнем. Пока камень горит, Ирену на её Опоре лечит молитва.
 * Сам по миру не встаёт — его вызывает Знамение Ирены.
 */
public final class Monastery {

    static final String BRICK = "minecraft:stonebrick";
    static final String BRICK_M = "minecraft:stonebrick[variant=mossy_stonebrick]";
    static final String BRICK_X = "minecraft:stonebrick[variant=chiseled_stonebrick]";
    static final String COBBLE = "minecraft:cobblestone";
    static final String Q = "minecraft:quartz_block";
    static final String Q_LN = "minecraft:quartz_block[variant=lines_y]";
    static final String Q_SLAB = "minecraft:stone_slab[variant=quartz,half=bottom]";
    static final String ROOF = "minecraft:stone_brick_stairs";
    static final String ROOF_SLAB = "minecraft:stone_slab[variant=smooth_brick,half=bottom]";
    static final String GLASS = "minecraft:stained_glass_pane[color=light_blue]";
    static final String PEW = "minecraft:spruce_stairs[facing=north]";

    /** Половина ширины нефа (стены на x = ±W), передняя и задняя стены. */
    static final int W = 4, FRONT = -6, BACK = 7;

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("monastery");
        // --- основание и пол
        for (int x = -W - 1; x <= W + 1; x++) {
            for (int z = FRONT - 4; z <= BACK + 1; z++) {
                b.set(x, -1, z, COBBLE);
                b.set(x, 0, z, Math.abs(x) == W + 1 || z == BACK + 1 || z == FRONT - 4 ? BRICK_X : r.nextInt(6) == 0 ? BRICK_M : BRICK);
            }
        }
        // проход: ковёр к алтарю
        for (int z = FRONT + 1; z <= BACK - 3; z++) {
            b.set(0, 1, z, "minecraft:carpet[color=red]");
        }
        // --- стены нефа
        for (int z = FRONT; z <= BACK; z++) {
            for (int y = 1; y <= 5; y++) {
                for (int sx = -W; sx <= W; sx += 2 * W) {
                    boolean pillar = z == FRONT || z == BACK || (z - FRONT) % 3 == 0;
                    boolean window = !pillar && (y == 2 || y == 3) && (z == -3 || z == 0 || z == 3);
                    b.set(sx, y, z, pillar ? Q_LN : window ? GLASS : r.nextInt(8) == 0 ? BRICK_M : BRICK);
                }
            }
        }
        for (int x = -W + 1; x <= W - 1; x++) {
            for (int y = 1; y <= 5; y++) {
                b.set(x, y, BACK, BRICK);
                boolean door = Math.abs(x) <= 0 && y <= 2;
                if (door) {
                    b.clear(x, y, FRONT);
                } else {
                    b.set(x, y, FRONT, BRICK);
                }
            }
        }
        // круглое окно над алтарём
        b.set(0, 4, BACK, GLASS);
        b.set(-1, 4, BACK, GLASS);
        b.set(1, 4, BACK, GLASS);
        b.set(0, 5, BACK, GLASS);
        b.set(0, 3, BACK, GLASS);
        // --- двускатная крыша вдоль оси z, с фронтонами
        for (int k = 0; k <= W; k++) {
            int y = 5 + k;
            for (int z = FRONT - 1; z <= BACK + 1; z++) {
                b.set(-(W + 1 - k), y, z, ROOF + "[facing=east]");
                b.set(W + 1 - k, y, z, ROOF + "[facing=west]");
            }
            // фронтоны
            for (int x = -(W - k); x <= W - k; x++) {
                if (k > 0) {
                    b.set(x, y, FRONT, BRICK);
                    b.set(x, y, BACK, BRICK);
                }
            }
        }
        for (int z = FRONT - 1; z <= BACK + 1; z++) {
            b.set(0, 5 + W + 1, z, ROOF_SLAB);
        }
        b.set(0, 7, FRONT, GLASS);
        // --- колокольня над входом
        for (int y = 1; y <= 14; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = FRONT - 4; z <= FRONT - 1; z++) {
                    boolean shell = Math.abs(x) == 2 || z == FRONT - 4 || z == FRONT - 1;
                    if (!shell) {
                        b.clear(x, y, z);
                        continue;
                    }
                    boolean corner = Math.abs(x) == 2 && (z == FRONT - 4 || z == FRONT - 1);
                    boolean belfry = y >= 10 && y <= 11 && !corner;
                    b.set(x, y, z, corner ? Q_LN : belfry ? "minecraft:iron_bars" : BRICK);
                }
            }
        }
        // проход сквозь колокольню
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                b.clear(x, y, FRONT - 4);
                b.clear(x, y, FRONT - 1);
            }
        }
        b.set(0, 4, FRONT - 4, BRICK_X);
        // колокол: золото на цепи
        b.set(0, 12, FRONT - 3, "minecraft:iron_bars");
        b.set(0, 11, FRONT - 3, "minecraft:gold_block");
        // шатёр колокольни
        for (int k = 0; k <= 2; k++) {
            for (int x = -2 + k; x <= 2 - k; x++) {
                for (int z = FRONT - 4 + k; z <= FRONT - 1 - k; z++) {
                    b.set(x, 15 + k, z, k == 2 ? ROOF_SLAB : BRICK);
                }
            }
        }
        b.set(0, 18, FRONT - 3, "minecraft:end_rod[facing=up]");
        // --- скамьи
        for (int z = FRONT + 2; z <= BACK - 4; z += 2) {
            for (int x = 1; x <= W - 1; x++) {
                b.set(x, 1, z, PEW);
                b.set(-x, 1, z, PEW);
            }
        }
        // --- алтарь со Святым камнем
        b.fill(-2, 1, BACK - 2, 2, 1, BACK - 1, Q_SLAB);
        b.set(0, 1, BACK - 1, "minecraft:gold_block");
        b.set(0, 2, BACK - 1, "chaosshard:shrine_stone");
        b.mark(0, 2, BACK - 1, "shrine");
        for (int sx = -2; sx <= 2; sx += 4) {
            b.set(sx, 1, BACK - 1, Q);
            b.set(sx, 2, BACK - 1, "minecraft:end_rod[facing=up]");
        }
        b.chest(W - 1, 1, BACK - 1, 1, "monastery");
        // люстры
        for (int z : new int[]{-2, 3}) {
            b.set(0, 9, z, "minecraft:fence");
            b.set(0, 8, z, "minecraft:glowstone");
        }
        // --- стража
        b.guard(-2, 1, FRONT + 1, "order_guard");
        b.guard(2, 1, FRONT + 1, "order_guard");
        b.guard(3, 1, FRONT - 3, "order_captain");
        b.footingsUnder(-1, COBBLE);
        return b;
    }

    private Monastery() {
    }
}
