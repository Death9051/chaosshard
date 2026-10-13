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
    /** Цитадели: владелец → «измерение@позиция». */
    private final Map<String, String> citadels = new HashMap<>();

    public void setCitadel(UUID owner, int dim, BlockPos pos) {
        if (pos == null) {
            citadels.remove(owner.toString());
        } else {
            citadels.put(owner.toString(), dim + "@" + pos.toLong());
        }
        markDirty();
    }

    /** Цитадель владельца: {измерение, позиция} или null. */
    public Object[] citadelOf(UUID owner) {
        String v = citadels.get(owner.toString());
        if (v == null) {
            return null;
        }
        String[] p = v.split("@");
        return new Object[]{Integer.parseInt(p[0]), BlockPos.fromLong(Long.parseLong(p[1]))};
    }

    /** Все Цитадели в измерении. */
    public java.util.List<BlockPos> citadelsIn(int dim) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (String v : citadels.values()) {
            String[] p = v.split("@");
            if (Integer.parseInt(p[0]) == dim) {
                out.add(BlockPos.fromLong(Long.parseLong(p[1])));
            }
        }
        return out;
    }

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
        citadels.clear();
        NBTTagList cl = nbt.getTagList("c", 10);
        for (int i = 0; i < cl.tagCount(); i++) {
            NBTTagCompound t = cl.getCompoundTagAt(i);
            citadels.put(t.getString("k"), t.getString("v"));
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
        NBTTagList cl = new NBTTagList();
        for (Map.Entry<String, String> e : citadels.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("k", e.getKey());
            t.setString("v", e.getValue());
            cl.appendTag(t);
        }
        nbt.setTag("c", cl);
        return nbt;
    }
}
