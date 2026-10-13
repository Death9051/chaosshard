package ru.death.chaosshard.structure.bp;

import java.util.Random;

/**
 * Бледный павильон Ордена: квадратное кольцо из крупного камня, моховой пол с крестом дорожек,
 * восемь тонких бледных колонн с каменными вставками, решётка балок «в клетку» в два яруса
 * и тонкий шпиль. В центре — ступенчатый помост с подношением.
 */
public final class Pavilion {

    static final String BONE = "minecraft:bone_block[axis=y]";
    static final String BONE_X = "minecraft:bone_block[axis=x]";
    static final String BONE_Z = "minecraft:bone_block[axis=z]";
    static final String COBBLE = "minecraft:cobblestone";
    static final String MOSSY = "minecraft:mossy_cobblestone";
    static final String BRICK = "minecraft:stonebrick";
    static final String BRICK_M = "minecraft:stonebrick[variant=mossy_stonebrick]";
    static final String BRICK_C = "minecraft:stonebrick[variant=cracked_stonebrick]";
    static final String ANDESITE = "minecraft:stone[variant=smooth_andesite]";
    static final String GRASS = "minecraft:grass";
    static final String SLAB = "minecraft:stone_slab[variant=smooth_brick,half=bottom]";
    static final String Q_SLAB = "minecraft:stone_slab[variant=quartz,half=bottom]";
    static final String DIORITE = "minecraft:stone[variant=smooth_diorite]";
    static final String FENCE = "minecraft:birch_fence";

    /** Полуразмер основания; колонны стоят на кольце P. */
    static final int B = 7, P = 4;
    /** Высота колонн (верх — уровень нижней рамы). */
    static final int H = 8;

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("pavilion");
        base(b, r);
        dais(b);
        int[][] posts = {{P, 2}, {P, -2}, {-P, 2}, {-P, -2}, {2, P}, {-2, P}, {2, -P}, {-2, -P}};
        for (int[] p : posts) {
            pillar(b, p[0], p[1], r);
        }
        roof(b);
        b.footingsUnder(-1, BRICK);
        return b;
    }

    /** Основание: кольцо из крупных блоков с зазорами, внутри мох и крест дорожек. */
    static void base(Blueprint b, Random r) {
        for (int x = -B; x <= B; x++) {
            for (int z = -B; z <= B; z++) {
                int ax = Math.abs(x), az = Math.abs(z);
                if (ax == B && az == B) {
                    continue; // срезанные углы
                }
                boolean rim = ax == B || az == B || (ax == B - 1 && az == B - 1);
                if (rim) {
                    b.set(x, -1, z, BRICK);
                    // крупные камни кольца — тройками, между ними полуплита
                    int along = ax == B ? z : x;
                    int seg = Math.floorMod(along + B, 4);
                    b.set(x, 0, z, seg != 3 ? pick(r, BRICK, BRICK, BRICK_M, BRICK_C) : SLAB);
                    continue;
                }
                boolean path = ax <= 1 || az <= 1;
                if (path) {
                    b.set(x, -1, z, (x + z) % 2 == 0 ? BRICK : ANDESITE);
                } else {
                    int k = r.nextInt(10);
                    b.set(x, -1, z, k < 4 ? GRASS : k < 7 ? MOSSY : k < 9 ? BRICK_M : COBBLE);
                    if (k < 4 && r.nextInt(3) == 0) {
                        b.set(x, 0, z, "minecraft:tallgrass[type=tall_grass]");
                    }
                }
            }
        }
        // вход с юга: проём в кольце
        for (int x = -1; x <= 1; x++) {
            b.set(x, -1, B, ANDESITE);
            b.clear(x, 0, B);
        }
    }

    /** Ступенчатый помост с сундуком-подношением. */
    static void dais(Blueprint b) {
        b.fill(-2, 0, -2, 2, 0, 2, SLAB);
        b.fill(-1, 0, -1, 1, 0, 1, DIORITE);
        b.fill(-1, 1, -1, 1, 1, 1, Q_SLAB);
        b.set(0, 1, 0, DIORITE);
        b.chest(0, 2, 0, 0, "pavilion");
    }

    /** Тонкая колонна: каменный низ, бледный ствол, каменная вставка посередине. */
    static void pillar(Blueprint b, int x, int z, Random r) {
        for (int y = 0; y < H; y++) {
            String s;
            if (y <= 1) {
                s = y == 0 ? BRICK : pick(r, COBBLE, COBBLE, MOSSY);
            } else if (y == 4 || y == 5) {
                s = pick(r, COBBLE, COBBLE, MOSSY);
            } else {
                s = BONE;
            }
            b.set(x, y, z, s);
        }
        // каменная «манжета» снаружи у основания
        String f = Blueprint.outward(x, z);
        int ox = f.equals("east") ? 1 : f.equals("west") ? -1 : 0;
        int oz = f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
        b.set(x + ox, 0, z + oz, "minecraft:stone_brick_stairs[facing=" + Blueprint.opposite(f) + "]");
        b.set(x + ox, 1, z + oz, pick(r, COBBLE, MOSSY));
        // наросты посередине: снаружи и сбоку, вразнобой
        b.set(x + ox, 4, z + oz, pick(r, COBBLE, COBBLE, MOSSY));
        int sx = oz != 0 ? Integer.signum(x) : 0, sz = ox != 0 ? Integer.signum(z) : 0;
        b.set(x + sx, 5, z + sz, COBBLE);
        b.set(x + ox, 5, z + oz, "minecraft:stone_stairs[facing=" + Blueprint.opposite(f) + ",half=top]");
    }

    /** Кровля: нижняя рама по колоннам, «клетка» балок, верхняя рама, шпиль. */
    static void roof(Blueprint b) {
        int y = H;
        // нижняя рама по колоннам, концы балок выступают
        for (int i = -P - 1; i <= P + 1; i++) {
            if (Math.abs(i) <= 2 || Math.abs(i) == P + 1) {
                b.set(i, y, P, BONE_X);
                b.set(i, y, -P, BONE_X);
                b.set(P, y, i, BONE_Z);
                b.set(-P, y, i, BONE_Z);
            }
        }
        // косые связи на углах рамы
        for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            b.set(c[0], y, c[1], BONE);
        }
        // «клетка»: балки вдоль x на z=±2 и вдоль z на x=±2, ярусом выше и длиннее рамы
        y = H + 1;
        for (int i = -P - 2; i <= P + 2; i++) {
            b.set(i, y, 2, BONE_X);
            b.set(i, y, -2, BONE_X);
            b.set(2, y, i, BONE_Z);
            b.set(-2, y, i, BONE_Z);
        }
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            b.set(c[0], y, c[1], BONE);
        }
        // столбики на концах балок «клетки»
        for (int s = -1; s <= 1; s += 2) {
            for (int o = -2; o <= 2; o += 4) {
                b.set(s * (P + 2), y + 1, o, FENCE);
                b.set(o, y + 1, s * (P + 2), FENCE);
            }
        }
        // стойки и верхняя рама вокруг шпиля
        y = H + 2;
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            b.set(c[0], y, c[1], FENCE);
        }
        y = H + 3;
        for (int i = -2; i <= 2; i++) {
            b.set(i, y, 2, BONE_X);
            b.set(i, y, -2, BONE_X);
            b.set(2, y, i, BONE_Z);
            b.set(-2, y, i, BONE_Z);
        }
        for (int i = -1; i <= 1; i++) {
            b.set(i, y + 1, 1, Q_SLAB);
            b.set(i, y + 1, -1, Q_SLAB);
            b.set(1, y + 1, i, Q_SLAB);
            b.set(-1, y + 1, i, Q_SLAB);
        }
        // шпиль: бледный ствол с каменной вставкой
        for (int yy = H + 1; yy <= H + 9; yy++) {
            b.set(0, yy, 0, yy == H + 5 || yy == H + 6 ? COBBLE : BONE);
        }
        b.set(0, H + 10, 0, FENCE);
        b.set(0, H + 11, 0, FENCE);
        // свисающий светильник над помостом
        b.set(0, H, 0, FENCE);
        b.set(0, H - 1, 0, FENCE);
        b.set(0, H - 2, 0, "minecraft:glowstone");
    }

    static String pick(Random r, String... s) {
        return s[r.nextInt(s.length)];
    }

    private Pavilion() {
    }
}
