package ru.death.chaosshard.village;

import net.minecraft.init.Biomes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.BiomeDictionary;
import ru.death.chaosshard.ChaosShard;

import java.util.Random;

/**
 * Деревня у входа в Лучезарию: Акт 3 завязан на деревне, поэтому у точки первого прихода она должна быть.
 * Если рядом уже есть настоящая — ничего не строим; иначе ставим обычную деревню в 40–70 блоках от точки появления.
 */
public final class EntranceVillage {

    private static final String NAME = "chaosshard_entrance_village";

    public static class Flag extends WorldSavedData {
        public boolean done;
        public long x, z;

        public Flag() {
            super(NAME);
        }

        public Flag(String n) {
            super(n);
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            done = t.getBoolean("done");
            x = t.getLong("x");
            z = t.getLong("z");
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound t) {
            t.setBoolean("done", done);
            t.setLong("x", x);
            t.setLong("z", z);
            return t;
        }
    }

    private static Flag flag(WorldServer w) {
        Flag f = (Flag) w.getPerWorldStorage().getOrLoadData(Flag.class, NAME);
        if (f == null) {
            f = new Flag();
            w.getPerWorldStorage().setData(NAME, f);
        }
        return f;
    }

    /** Один раз на мир: убедиться, что у точки появления есть деревня. */
    public static void ensure(WorldServer w) {
        Flag f = flag(w);
        if (f.done) {
            return;
        }
        BlockPos spawn = w.getSpawnPoint();
        try {
            BlockPos near = w.findNearestStructure("Village", spawn, false);
            if (near != null && near.distanceSq(spawn.getX(), near.getY(), spawn.getZ()) < 160 * 160) {
                mark(w, f, near);
                return;
            }
        } catch (Exception ignored) {
            // у этого мира нет деревень в генераторе — построим сами
        }
        BlockPos at = build(w, spawn, new Random(w.getSeed() ^ 0x5EEDL));
        if (at != null) {
            mark(w, f, at);
        }
    }

    /** Поставить деревню рядом с точкой (для отладки — у игрока). Возвращает её центр или null. */
    public static BlockPos build(WorldServer w, BlockPos around, Random rnd) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            int d = 40 + rnd.nextInt(31);
            BlockPos c = around.add((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            Biome b = w.getBiome(c);
            if (BiomeDictionary.hasType(b, BiomeDictionary.Type.OCEAN) || BiomeDictionary.hasType(b, BiomeDictionary.Type.RIVER)
                    || BiomeDictionary.hasType(b, BiomeDictionary.Type.BEACH) || b == Biomes.DEEP_OCEAN) {
                continue;
            }
            // ровность: разброс высот по девяти точкам
            int min = 256, max = 0;
            for (int dx = -16; dx <= 16; dx += 16) {
                for (int dz = -16; dz <= 16; dz += 16) {
                    int h = w.getHeight(c.add(dx, 0, dz)).getY();
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                }
            }
            double score = (max - min) + (w.getHeight(c).getY() < w.getSeaLevel() ? 100 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null) {
            best = around.add(48, 0, 0);
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            MapGenVillage.Start start = new MapGenVillage.Start(w, rnd, best.getX() >> 4, best.getZ() >> 4, 0);
            if (!start.isSizeableStructure()) {
                continue;
            }
            StructureBoundingBox bb = start.getBoundingBox();
            for (int cx = bb.minX >> 4; cx <= bb.maxX >> 4; cx++) {
                for (int cz = bb.minZ >> 4; cz <= bb.maxZ >> 4; cz++) {
                    w.getChunk(cx, cz);
                    start.generateStructure(w, rnd, new StructureBoundingBox(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15));
                }
            }
            ChaosShard.LOG.info("Деревня у входа в Лучезарию: {}", best);
            return best;
        }
        return null;
    }

    private static void mark(WorldServer w, Flag f, BlockPos p) {
        f.done = true;
        f.x = p.getX();
        f.z = p.getZ();
        f.markDirty();
    }

    private EntranceVillage() {
    }
}
