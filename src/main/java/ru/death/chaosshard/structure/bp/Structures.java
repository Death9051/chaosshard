package ru.death.chaosshard.structure.bp;

import java.util.Random;

/** Каталог построек Лучезарии. */
public final class Structures {

    public static final String[] ALL = {"crystal_spires", "greenhouse", "pavilion", "sanctum", "dome_tower"};

    public static Blueprint build(String name, Random r) {
        if (name.startsWith("pillar_")) {
            return VeilPillar.build(Integer.parseInt(name.substring(7)), r);
        }
        switch (name) {
            case "monastery": return Monastery.build(r);
            case "capital": return Capital.build(r);
            case "velmor_ward": return VelmorWard.build(r);
            case "crystal_spires": return CrystalSpires.build(r);
            case "greenhouse": return Greenhouse.build(r);
            case "pavilion": return Pavilion.build(r);
            case "sanctum": return Sanctum.build(r);
            case "dome_tower": return DomeTower.build(r);
            default: throw new IllegalArgumentException(name);
        }
    }

    private Structures() {
    }
}
