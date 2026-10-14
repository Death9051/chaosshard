package ru.death.chaosshard.hero;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Какие трофеи Светочей стоят в Логове у каждого Осколка (битовая маска по героям). Общая на все миры. */
public class TrophyData extends WorldSavedData {

    public static final String NAME = "chaosshard_trophies";
    private final Map<UUID, Integer> active = new HashMap<>();

    public TrophyData() {
        super(NAME);
    }

    public TrophyData(String name) {
        super(name);
    }

    public static TrophyData get(World world) {
        TrophyData d = (TrophyData) world.getMapStorage().getOrLoadData(TrophyData.class, NAME);
        if (d == null) {
            d = new TrophyData();
            world.getMapStorage().setData(NAME, d);
        }
        return d;
    }

    public void set(UUID owner, Lightbearer h, boolean on) {
        int m = active.getOrDefault(owner, 0);
        int n = on ? m | (1 << h.ordinal()) : m & ~(1 << h.ordinal());
        if (n != m) {
            active.put(owner, n);
            markDirty();
        }
    }

    public boolean has(UUID owner, Lightbearer h) {
        return owner != null && (active.getOrDefault(owner, 0) & (1 << h.ordinal())) != 0;
    }

    public static boolean has(EntityPlayer p, Lightbearer h) {
        return !p.world.isRemote && get(p.world).has(p.getUniqueID(), h);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        active.clear();
        for (String k : nbt.getKeySet()) {
            try {
                active.put(UUID.fromString(k), nbt.getInteger(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        for (Map.Entry<UUID, Integer> e : active.entrySet()) {
            nbt.setInteger(e.getKey().toString(), e.getValue());
        }
        return nbt;
    }
}
