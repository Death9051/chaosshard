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
        /** Время активации рун (мир, тики), не сохраняется. */
        public final long[] runes = new long[4];

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
