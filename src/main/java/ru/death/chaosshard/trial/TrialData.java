package ru.death.chaosshard.trial;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Все Испытания мира (хранится глобально). */
public class TrialData extends WorldSavedData {

    public static final String NAME = "chaosshard_trials";
    public int nextId = 1;
    public final Map<Integer, Trial> trials = new HashMap<>();
    public int completed;

    public static final class Trial {
        public int id;
        public UUID owner;
        public int tier;
        public int conditions;
        public int stage;
        public int returnDim;
        public BlockPos returnPos = BlockPos.ORIGIN;
        public boolean done;
        /** Облик и порядок комнат. */
        public int theme;
        public int[] rooms = new int[0];
        /** Текущая комната и её состояние: 0 — не начата, 1 — идёт. */
        public int room, roomState;
        /** Сколько комнат пройдено быстрее срока (Мир часов). */
        public int fast;
        /** Сундуки-засады, уже открытые (Мир долга). */
        public final java.util.Set<Long> debtOpened = new java.util.HashSet<>();

        // ---- не сохраняется: живое состояние комнаты
        public final long[] runes = new long[4];
        public long started, roomStart;
        public int progress, seqPos, showStep;
        public int[] seq = new int[0];
        public long nextEvent, lastSafe;
        public boolean timeUp;
        public java.util.UUID hunter;
        public final java.util.Set<Long> trodden = new java.util.HashSet<>();

        public TrialTheme theme() {
            return TrialTheme.values()[Math.floorMod(theme, TrialTheme.values().length)];
        }

        public TrialRoom roomType(int i) {
            return TrialRoom.values()[rooms[i]];
        }

        /** Границы комнат по z: [z0, z1] внутренности, корридор в 4 блока между ними. */
        public int[][] bounds() {
            int[][] b = new int[rooms.length][2];
            int z = 0;
            for (int i = 0; i < rooms.length; i++) {
                b[i][0] = z;
                b[i][1] = z + roomType(i).length - 1;
                z = b[i][1] + 5;
            }
            return b;
        }

        /** Комната, в которой лежит точка (по z), или -1 — коридор. */
        public int roomAt(BlockPos p) {
            int z = p.getZ() - origin().getZ();
            int[][] b = bounds();
            for (int i = 0; i < b.length; i++) {
                if (z >= b[i][0] && z <= b[i][1]) {
                    return i;
                }
            }
            return -1;
        }

        public BlockPos origin() {
            return new BlockPos(id * 512, 64, 0);
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id);
            if (owner != null) {
                t.setUniqueId("owner", owner);
            }
            t.setInteger("tier", tier);
            t.setInteger("cond", conditions);
            t.setInteger("stage", stage);
            t.setInteger("rdim", returnDim);
            t.setLong("rpos", returnPos.toLong());
            t.setBoolean("done", done);
            t.setInteger("theme", theme);
            t.setIntArray("rooms", rooms);
            t.setInteger("room", room);
            t.setInteger("rstate", roomState);
            t.setInteger("fast", fast);
            long[] d = new long[debtOpened.size()];
            int i = 0;
            for (long v : debtOpened) d[i++] = v;
            net.minecraft.nbt.NBTTagList dl = new net.minecraft.nbt.NBTTagList();
            for (long v : d) dl.appendTag(new net.minecraft.nbt.NBTTagLong(v));
            t.setTag("debt", dl);
            return t;
        }

        static Trial read(NBTTagCompound t) {
            Trial r = new Trial();
            r.id = t.getInteger("id");
            r.owner = t.hasUniqueId("owner") ? t.getUniqueId("owner") : null;
            r.tier = t.getInteger("tier");
            r.conditions = t.getInteger("cond");
            r.stage = t.getInteger("stage");
            r.returnDim = t.getInteger("rdim");
            r.returnPos = BlockPos.fromLong(t.getLong("rpos"));
            r.done = t.getBoolean("done");
            r.theme = t.getInteger("theme");
            r.rooms = t.getIntArray("rooms");
            r.room = t.getInteger("room");
            r.roomState = t.getInteger("rstate");
            r.fast = t.getInteger("fast");
            net.minecraft.nbt.NBTTagList dl = t.getTagList("debt", 4);
            for (int i = 0; i < dl.tagCount(); i++) r.debtOpened.add(((net.minecraft.nbt.NBTTagLong) dl.get(i)).getLong());
            return r;
        }
    }

    public TrialData(String name) {
        super(name);
    }

    public static TrialData get(World world) {
        TrialData d = (TrialData) world.getMapStorage().getOrLoadData(TrialData.class, NAME);
        if (d == null) {
            d = new TrialData(NAME);
            world.getMapStorage().setData(NAME, d);
        }
        return d;
    }

    /** Испытание, в котором стоит точка (по координате x). */
    public Trial at(BlockPos pos) {
        int id = Math.floorDiv(pos.getX() + 256, 512);
        return trials.get(id);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        nextId = Math.max(1, nbt.getInteger("next"));
        completed = nbt.getInteger("completed");
        trials.clear();
        NBTTagList list = nbt.getTagList("t", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            Trial t = Trial.read(list.getCompoundTagAt(i));
            trials.put(t.id, t);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("next", nextId);
        nbt.setInteger("completed", completed);
        NBTTagList list = new NBTTagList();
        for (Trial t : trials.values()) {
            list.appendTag(t.write());
        }
        nbt.setTag("t", list);
        return nbt;
    }
}
