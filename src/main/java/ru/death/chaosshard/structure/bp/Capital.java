package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Столица Лучезарии (Акт 5): город за стеной. Ворота с юга (z < 0), четыре угловые башни с маяками Света,
 * улица к дворцу, дома по сторонам, дворец с тронным залом на севере. Вход во дворец закрыт барьером Света,
 * пока горит хоть один маяк. Ставит её только Знамение Короны.
 *
 * Метки: beam — маяк на башне; barrier — клетка барьера; king — где ждёт Король; rift — где откроется Разлом.
 */
public final class Capital {

    /** Полуразмер города: стена на |x| = H или |z| = H. */
    public static final int H = 28;
    static final int WALL_H = 8, TOWER_R = 3, TOWER_H = 16;
    /** Дворец: x ∈ [-PW, PW], z ∈ [PZ0, PZ1]. */
    static final int PW = 11, PZ0 = 4, PZ1 = 24, PALACE_H = 12;

    static final String BRICK = "minecraft:stonebrick";
    static final String BRICK_M = "minecraft:stonebrick[variant=mossy_stonebrick]";
    static final String BRICK_C = "minecraft:stonebrick[variant=cracked_stonebrick]";
    static final String BRICK_X = "minecraft:stonebrick[variant=chiseled_stonebrick]";
    static final String COBBLE = "minecraft:cobblestone";
    static final String Q = "minecraft:quartz_block";
    static final String Q_LN = "minecraft:quartz_block[variant=lines_y]";
    static final String Q_CH = "minecraft:quartz_block[variant=chiseled]";
    static final String Q_SLAB = "minecraft:stone_slab[variant=quartz,half=bottom]";
    static final String SMOOTH = "minecraft:stone[variant=smooth_andesite]";
    static final String GOLD = "minecraft:gold_block";
    static final String GLASS = "minecraft:stained_glass_pane[color=yellow]";
    static final String PLANKS = "minecraft:planks[variant=spruce]";
    static final String LOG = "minecraft:log[variant=spruce,axis=y]";
    static final String ROOF = "minecraft:dark_oak_stairs";
    static final String ROOF_SLAB = "minecraft:wooden_slab[variant=dark_oak,half=bottom]";
    static final String BARRIER = "chaosshard:capital_barrier";

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("capital");
        ground(b, r);
        walls(b, r);
        gate(b);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                tower(b, sx * H, sz * H, r);
            }
        }
        avenue(b);
        int[][] houses = {{-12, -20}, {12, -20}, {-12, -10}, {12, -10}, {-20, -2}, {20, -2}, {-21, 14}, {21, 14}};
        for (int i = 0; i < houses.length; i++) {
            house(b, houses[i][0], houses[i][1], houses[i][0] < 0, r, i);
        }
        palace(b, r);
        // стража на улицах
        int[][] posts = {{-3, -24}, {3, -24}, {-6, -14}, {6, -14}, {-6, -4}, {6, -4}, {-14, 6}, {14, 6}};
        for (int[] p : posts) {
            b.guard(p[0], 1, p[1], "order_guard");
        }
        b.guard(0, 1, -22, "order_captain");
        b.footingsUnder(-1, COBBLE);
        return b;
    }

    /** Мощёная площадь, воздух над ней. */
    static void ground(Blueprint b, Random r) {
        for (int x = -H; x <= H; x++) {
            for (int z = -H; z <= H; z++) {
                b.set(x, -1, z, COBBLE);
                int k = r.nextInt(10);
                b.set(x, 0, z, k == 0 ? BRICK_M : k == 1 ? BRICK_C : (x + z & 7) == 0 ? SMOOTH : BRICK);
                for (int y = 1; y <= 12; y++) {
                    b.setIfEmpty(x, y, z, Blueprint.AIR);
                }
            }
        }
    }

    static boolean onWall(int x, int z) {
        return Math.abs(x) >= H - 1 || Math.abs(z) >= H - 1;
    }

    static void walls(Blueprint b, Random r) {
        for (int x = -H; x <= H; x++) {
            for (int z = -H; z <= H; z++) {
                if (!onWall(x, z)) {
                    continue;
                }
                boolean outer = Math.abs(x) == H || Math.abs(z) == H;
                for (int y = 1; y <= WALL_H; y++) {
                    b.set(x, y, z, y == WALL_H ? Q : r.nextInt(9) == 0 ? BRICK_M : BRICK);
                }
                // зубцы по внешнему краю, через клетку
                if (outer && ((x + z) & 1) == 0) {
                    b.set(x, WALL_H + 1, z, BRICK);
                }
            }
        }
        // факелы на стене изнутри
        for (int t = -H + 4; t <= H - 4; t += 6) {
            b.set(t, WALL_H + 1, -H + 1, "minecraft:torch");
            b.set(t, WALL_H + 1, H - 1, "minecraft:torch");
            b.set(-H + 1, WALL_H + 1, t, "minecraft:torch");
            b.set(H - 1, WALL_H + 1, t, "minecraft:torch");
        }
    }

    static void gate(Blueprint b) {
        // проём ворот
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 5; y++) {
                b.clear(x, y, -H);
                b.clear(x, y, -H + 1);
            }
        }
        for (int x = -3; x <= 3; x++) {
            b.set(x, 6, -H, Q_CH);
        }
        // надвратные башни
        for (int sx = -1; sx <= 1; sx += 2) {
            int cx = sx * 5;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = -H - 1; z <= -H + 3; z++) {
                    for (int y = 1; y <= 12; y++) {
                        boolean shell = Math.abs(x - cx) == 2 || z == -H - 1 || z == -H + 3;
                        if (shell) {
                            b.set(x, y, z, Math.abs(x - cx) == 2 && (z == -H - 1 || z == -H + 3) ? Q_LN : BRICK);
                        } else if (y < 12) {
                            b.clear(x, y, z);
                        }
                    }
                    b.set(x, 12, z, Q);
                    if (((x + z) & 1) == 0 && (Math.abs(x - cx) == 2 || z == -H - 1 || z == -H + 3)) {
                        b.set(x, 13, z, BRICK);
                    }
                }
            }
            // знамя Ордена над воротами
            b.set(cx, 10, -H - 2, "minecraft:wall_banner[facing=north]");
        }
    }

    /** Угловая башня с маяком Света наверху и лестницей внутри. */
    static void tower(Blueprint b, int cx, int cz, Random r) {
        for (int x = cx - TOWER_R; x <= cx + TOWER_R; x++) {
            for (int z = cz - TOWER_R; z <= cz + TOWER_R; z++) {
                boolean shell = Math.abs(x - cx) == TOWER_R || Math.abs(z - cz) == TOWER_R;
                boolean corner = Math.abs(x - cx) == TOWER_R && Math.abs(z - cz) == TOWER_R;
                for (int y = 0; y <= TOWER_H; y++) {
                    if (y == 0 || y == TOWER_H) {
                        b.set(x, y, z, y == TOWER_H ? Q : BRICK);
                    } else if (shell) {
                        boolean window = !corner && (y % 5 == 3) && (Math.abs(x - cx) == 0 || Math.abs(z - cz) == 0);
                        b.set(x, y, z, corner ? Q_LN : window ? GLASS : r.nextInt(9) == 0 ? BRICK_M : BRICK);
                    } else {
                        b.clear(x, y, z);
                    }
                }
                if (shell && ((x + z) & 1) == 0) {
                    b.set(x, TOWER_H + 1, z, BRICK);
                }
            }
        }
        // дверь в башню — со стороны города
        int dx = -Integer.signum(cx), dz = -Integer.signum(cz);
        int doorX = cx + dx * TOWER_R, doorZ = cz;
        b.clear(doorX, 1, doorZ);
        b.clear(doorX, 2, doorZ);
        // проход сквозь толщу стены к двери
        b.clear(doorX + dx, 1, doorZ);
        b.clear(doorX + dx, 2, doorZ);
        // лестница вдоль внутренней стены и люк на крышу
        int lx = cx - dx * (TOWER_R - 1), lz = cz;
        for (int y = 1; y < TOWER_H; y++) {
            b.set(lx, y, lz, "minecraft:ladder[facing=" + (dx > 0 ? "east" : "west") + "]");
        }
        b.clear(lx, TOWER_H, lz);
        b.set(lx, TOWER_H, lz, "minecraft:trapdoor[facing=" + (dx > 0 ? "east" : "west") + ",half=top,open=false]");
        // маяк Света на крыше
        b.set(cx, TOWER_H + 1, cz, Q_CH);
        b.mark(cx, TOWER_H + 2, cz, "beam");
        b.guard(cx + 1, TOWER_H + 1, cz + 1, "order_guard");
        b.guard(cx - 1, TOWER_H + 1, cz - 1, "order_guard");
        // факелы внутри
        b.set(cx + dx, 6, cz + (TOWER_R - 1) * dz, "minecraft:torch");
        b.set(cx + dx, 12, cz + (TOWER_R - 1) * dz, "minecraft:torch");
    }

    /** Главная улица от ворот к дворцу, фонари. */
    static void avenue(Blueprint b) {
        for (int z = -H + 2; z < PZ0; z++) {
            for (int x = -2; x <= 2; x++) {
                b.set(x, 0, z, Math.abs(x) == 2 ? Q_LN.replace("lines_y", "lines_z") : Q);
            }
            if ((z + H) % 6 == 3) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    b.set(sx * 4, 1, z, "minecraft:cobblestone_wall");
                    b.set(sx * 4, 2, z, "minecraft:cobblestone_wall");
                    b.set(sx * 4, 3, z, "minecraft:glowstone");
                }
            }
        }
        // фонтан на площади перед дворцом
        b.disc(0, 0, -1, 2.5, Q);
        b.ring(0, 1, -1, 2.5, Q_SLAB);
        b.set(0, 1, -1, "minecraft:water");
        b.set(0, 2, -1, Q_LN);
        b.set(0, 3, -1, GOLD);
    }

    /** Дом горожанина: каменный цоколь, деревянные стены, двускатная крыша, сундук. */
    static void house(Blueprint b, int cx, int cz, boolean doorEast, Random r, int i) {
        int w = 3, d = 3;
        for (int x = cx - w; x <= cx + w; x++) {
            for (int z = cz - d; z <= cz + d; z++) {
                b.set(x, 0, z, PLANKS);
                boolean shell = Math.abs(x - cx) == w || Math.abs(z - cz) == d;
                boolean corner = Math.abs(x - cx) == w && Math.abs(z - cz) == d;
                for (int y = 1; y <= 4; y++) {
                    if (!shell) {
                        b.clear(x, y, z);
                    } else if (corner) {
                        b.set(x, y, z, LOG);
                    } else if (y == 1) {
                        b.set(x, y, z, COBBLE);
                    } else {
                        boolean window = y == 2 && (x == cx || z == cz);
                        b.set(x, y, z, window ? "minecraft:glass_pane" : PLANKS);
                    }
                }
            }
        }
        int doorX = cx + (doorEast ? w : -w);
        b.set(doorX, 1, cz, "minecraft:spruce_door[facing=" + (doorEast ? "east" : "west") + ",half=lower,hinge=left]");
        b.set(doorX, 2, cz, "minecraft:spruce_door[facing=" + (doorEast ? "east" : "west") + ",half=upper,hinge=left]");
        // крыша вдоль оси x
        for (int k = 0; k <= d + 1; k++) {
            int y = 5 + k;
            for (int x = cx - w - 1; x <= cx + w + 1; x++) {
                if (d + 1 - k >= 0) {
                    b.set(x, y, cz - (d + 1 - k), ROOF + "[facing=south]");
                    b.set(x, y, cz + (d + 1 - k), ROOF + "[facing=north]");
                }
            }
            for (int z = cz - (d - k); z <= cz + (d - k); z++) {
                if (k > 0 && d - k >= 0) {
                    b.set(cx - w, y, z, PLANKS);
                    b.set(cx + w, y, z, PLANKS);
                }
            }
        }
        for (int x = cx - w - 1; x <= cx + w + 1; x++) {
            b.set(x, 5 + d + 1, cz, ROOF_SLAB);
        }
        b.set(cx, 4, cz, "minecraft:glowstone");
        b.chest(cx + (doorEast ? -w + 1 : w - 1), 1, cz + d - 1, doorEast ? 3 : 1, "capital_house");
        b.set(cx, 1, cz - d + 1, "minecraft:crafting_table");
    }

    /** Дворец: тронный зал, барьер Света во входе, трон, место Разлома за троном. */
    static void palace(Blueprint b, Random r) {
        for (int x = -PW; x <= PW; x++) {
            for (int z = PZ0; z <= PZ1; z++) {
                b.set(x, 0, z, ((x + z) & 1) == 0 ? Q : "minecraft:quartz_block[variant=chiseled]");
                boolean shell = Math.abs(x) == PW || z == PZ0 || z == PZ1;
                boolean pillar = shell && (Math.abs(x) == PW && (z - PZ0) % 4 == 0 || (z == PZ0 || z == PZ1) && x % 4 == 0);
                for (int y = 1; y <= PALACE_H; y++) {
                    if (!shell) {
                        b.clear(x, y, z);
                        continue;
                    }
                    boolean window = !pillar && y >= 3 && y <= 7 && (Math.abs(x) == PW ? (z - PZ0) % 4 == 2 : Math.abs(x) % 4 == 2);
                    b.set(x, y, z, pillar ? Q_LN : window ? GLASS : y == PALACE_H ? Q : BRICK);
                }
                b.set(x, PALACE_H + 1, z, Q);
                if (shell && ((x + z) & 1) == 0) {
                    b.set(x, PALACE_H + 2, z, Q_SLAB);
                }
            }
        }
        // купол над тронным залом
        for (int k = 0; k <= 5; k++) {
            b.ring(0, PALACE_H + 2 + k, PZ1 - 6, 6.5 - k, k == 5 ? GOLD : Q);
        }
        b.set(0, PALACE_H + 8, PZ1 - 6, "minecraft:end_rod[facing=up]");
        // колонны внутри
        for (int z = PZ0 + 3; z <= PZ1 - 4; z += 4) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int y = 1; y <= PALACE_H - 1; y++) {
                    b.set(sx * 6, y, z, Q_LN);
                }
                b.set(sx * 6, PALACE_H - 2, z - 1, "minecraft:glowstone");
            }
        }
        // ковёр к трону
        for (int z = PZ0 + 1; z <= PZ1 - 4; z++) {
            for (int x = -1; x <= 1; x++) {
                b.set(x, 1, z, "minecraft:carpet[color=red]");
            }
        }
        // вход: проём, закрытый барьером Света
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 5; y++) {
                b.set(x, y, PZ0, BARRIER);
                b.mark(x, y, PZ0, "barrier");
            }
        }
        b.set(0, 6, PZ0, Q_CH);
        // трон: золотое возвышение, кварцевое кресло лицом ко входу
        b.fill(-3, 1, PZ1 - 3, 3, 1, PZ1 - 1, GOLD);
        b.fill(-2, 2, PZ1 - 2, 2, 2, PZ1 - 1, Q);
        b.set(0, 3, PZ1 - 2, "minecraft:quartz_stairs[facing=south]");
        b.set(-1, 3, PZ1 - 2, Q_LN);
        b.set(1, 3, PZ1 - 2, Q_LN);
        b.set(0, 3, PZ1 - 1, Q_LN);
        b.set(0, 4, PZ1 - 1, Q_LN);
        b.set(0, 5, PZ1 - 1, GOLD);
        b.mark(0, 1, PZ1 - 5, "king");
        b.mark(0, 1, PZ1 + 2, "rift");
        // королевская стража в зале
        b.guard(-4, 1, PZ0 + 4, "order_captain");
        b.guard(4, 1, PZ0 + 4, "order_captain");
        b.chest(PW - 2, 1, PZ1 - 1, 2, "capital_treasury");
        b.chest(-PW + 2, 1, PZ1 - 1, 2, "capital_treasury");
    }

    private Capital() {
    }
}
