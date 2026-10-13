package ru.death.chaosshard.structure.bp;

import java.util.Random;

/** Печать отступления Вельмора: круглая площадка, четыре пурпурных столбика с огнями, на постаменте — печать. */
public final class VelmorWard {

    public static Blueprint build(Random r) {
        Blueprint b = new Blueprint("velmor_ward");
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 3.4) {
                    continue;
                }
                b.set(x, -1, z, "minecraft:stonebrick");
                b.set(x, 0, z, d > 2.5 ? "minecraft:purpur_block" : (x + z & 1) == 0 ? "minecraft:end_bricks" : "minecraft:quartz_block");
                for (int y = 1; y <= 4; y++) {
                    b.clear(x, y, z);
                }
            }
        }
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            for (int y = 1; y <= 2; y++) {
                b.set(c[0], y, c[1], "minecraft:purpur_pillar[axis=y]");
            }
            b.set(c[0], 3, c[1], "minecraft:end_rod[facing=up]");
        }
        b.set(0, 1, 0, "minecraft:quartz_block[variant=chiseled]");
        b.set(0, 2, 0, "chaosshard:velmor_seal");
        b.mark(0, 2, 0, "seal");
        b.footingsUnder(-1, "minecraft:stonebrick");
        return b;
    }

    private VelmorWard() {
    }
}
