package ru.death.chaosshard.lair;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Логова игроков: по одному в каждом мире (ключ — владелец и измерение). */
public class LairData extends WorldSavedData {

    public static final String NAME = "chaosshard_lairs";
    private final Map<String, BlockPos> lairs = new HashMap<>();

    public LairData(String name) {
        super(name);
    }

    public static LairData get(World world) {
        LairData d = (LairData) world.getMapStorage().getOrLoadData(LairData.class, NAME);
        if (d == null) {
            d = new LairData(NAME);
            world.getMapStorage().setData(NAME, d);
        }
        return d;
    }

    private static String key(UUID owner, int dim) {
        return owner + "@" + dim;
    }

    public BlockPos lairOf(UUID owner, int dim) {
        return lairs.get(key(owner, dim));
    }

    public void set(UUID owner, int dim, BlockPos pos) {
        if (pos == null) {
            lairs.remove(key(owner, dim));
        } else {
            lairs.put(key(owner, dim), pos);
        }
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        lairs.clear();
        NBTTagList list = nbt.getTagList("l", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            lairs.put(t.getString("k"), BlockPos.fromLong(t.getLong("p")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, BlockPos> e : lairs.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("k", e.getKey());
            t.setLong("p", e.getValue().toLong());
            list.appendTag(t);
        }
        nbt.setTag("l", list);
        return nbt;
    }
}
