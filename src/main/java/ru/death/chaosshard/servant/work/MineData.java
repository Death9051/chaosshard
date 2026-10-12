package ru.death.chaosshard.servant.work;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Шахты у сундуков. План один на сундук, и все Рудокопы, закреплённые за ним, берут из него участки:
 * винтовую лестницу (один рудокоп), штреки на уровнях руды (по одному на уровень), ответвления (сколько угодно).
 * Хранится в мире: погибший рудокоп не теряет сделанного.
 */
public class MineData extends WorldSavedData {

    public static final String NAME = "chaosshard_mines";
    /** Сколько тиков участок считается занятым без вестей от рудокопа. */
    public static final long CLAIM_TTL = 1200;

    public static final int SPIRAL = 0, MAIN = 1, BRANCH = 2;

    public static class Job {
        public int type, ty, j, side, k;
        public UUID claim;
        public long seen;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("type", type);
            t.setInteger("ty", ty);
            t.setInteger("j", j);
            t.setInteger("side", side);
            t.setInteger("k", k);
            return t;
        }

        static Job read(NBTTagCompound t) {
            Job j = new Job();
            j.type = t.getInteger("type");
            j.ty = t.getInteger("ty");
            j.j = t.getInteger("j");
            j.side = t.getInteger("side");
            j.k = t.getInteger("k");
            return j;
        }

        boolean free(long now, UUID who) {
            return claim == null || claim.equals(who) || now - seen > CLAIM_TTL;
        }
    }

    public static class Mine {
        public int dir;
        /** Следующий шаг лестницы и индекс следующего уровня руды. */
        public int spiralK, level;
        public boolean spiralDone, announced;
        public final Job spiral = new Job();
        public final List<Job> jobs = new ArrayList<>();

        public boolean done() {
            return spiralDone && jobs.isEmpty();
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("dir", dir);
            t.setInteger("sk", spiralK);
            t.setInteger("lvl", level);
            t.setBoolean("sd", spiralDone);
            t.setBoolean("ann", announced);
            NBTTagList l = new NBTTagList();
            for (Job j : jobs) {
                l.appendTag(j.write());
            }
            t.setTag("jobs", l);
            return t;
        }

        static Mine read(NBTTagCompound t) {
            Mine m = new Mine();
            m.dir = t.getInteger("dir");
            m.spiralK = t.getInteger("sk");
            m.level = t.getInteger("lvl");
            m.spiralDone = t.getBoolean("sd");
            m.announced = t.getBoolean("ann");
            NBTTagList l = t.getTagList("jobs", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                m.jobs.add(Job.read(l.getCompoundTagAt(i)));
            }
            return m;
        }

        /** Взять работу: ответвления, потом штреки, потом лестница. */
        public Job claim(long now, UUID who) {
            Job mine = null;
            for (Job j : jobs) {
                if (who.equals(j.claim) && now - j.seen <= CLAIM_TTL) {
                    mine = j;
                }
            }
            if (mine == null && !spiralDone && who.equals(spiral.claim) && now - spiral.seen <= CLAIM_TTL) {
                mine = spiral;
            }
            if (mine == null) {
                for (int type : new int[]{BRANCH, MAIN}) {
                    for (Job j : jobs) {
                        if (j.type == type && j.free(now, who)) {
                            mine = j;
                            break;
                        }
                    }
                    if (mine != null) {
                        break;
                    }
                }
            }
            if (mine == null && !spiralDone && spiral.free(now, who)) {
                mine = spiral;
            }
            if (mine != null) {
                mine.claim = who;
                mine.seen = now;
            }
            return mine;
        }
    }

    private final Map<Long, Mine> mines = new HashMap<>();

    public MineData() {
        super(NAME);
    }

    public MineData(String name) {
        super(name);
    }

    public static MineData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        MineData d = (MineData) storage.getOrLoadData(MineData.class, NAME);
        if (d == null) {
            d = new MineData();
            storage.setData(NAME, d);
        }
        return d;
    }

    public Mine mine(BlockPos chest, int dir) {
        Mine m = mines.get(chest.toLong());
        if (m == null) {
            m = new Mine();
            m.dir = dir;
            m.spiral.type = SPIRAL;
            mines.put(chest.toLong(), m);
            markDirty();
        }
        return m;
    }

    public Mine existing(BlockPos chest) {
        return mines.get(chest.toLong());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        mines.clear();
        for (String k : nbt.getKeySet()) {
            try {
                Mine m = Mine.read(nbt.getCompoundTag(k));
                m.spiral.type = SPIRAL;
                mines.put(Long.parseLong(k), m);
            } catch (NumberFormatException ignored) {
                // мусор
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        for (Map.Entry<Long, Mine> e : mines.entrySet()) {
            nbt.setTag(Long.toString(e.getKey()), e.getValue().write());
        }
        return nbt;
    }
}
