package ru.death.chaosshard.merchant;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Счета игроков у торговца (в медных монетах) и когда он придёт в следующий раз.
 * Одно хранилище на весь сервер (общее для всех измерений).
 */
public class MerchantData extends WorldSavedData {

    public static final String NAME = "chaosshard_merchant";

    public final Map<UUID, Long> balance = new HashMap<>();
    /** Время (мировое, верхний мир), когда торговец сам придёт к Якорю игрока. */
    public final Map<UUID, Long> nextVisit = new HashMap<>();
    /** До какого времени торговец уже стоит у Якоря игрока (чтобы не прийти второй раз). */
    public final Map<UUID, Long> busyUntil = new HashMap<>();

    public MerchantData(String name) {
        super(name);
    }

    public static MerchantData get(World world) {
        MerchantData d = (MerchantData) world.getMapStorage().getOrLoadData(MerchantData.class, NAME);
        if (d == null) {
            d = new MerchantData(NAME);
            world.getMapStorage().setData(NAME, d);
        }
        return d;
    }

    public long balance(UUID id) {
        return balance.getOrDefault(id, 0L);
    }

    public void add(UUID id, long amount) {
        balance.put(id, Math.max(0, balance(id) + amount));
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        balance.clear();
        nextVisit.clear();
        busyUntil.clear();
        NBTTagList l = nbt.getTagList("Players", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            UUID id = t.getUniqueId("Id");
            if (id == null) {
                continue;
            }
            if (t.hasKey("Balance")) {
                balance.put(id, t.getLong("Balance"));
            }
            if (t.hasKey("Next")) {
                nextVisit.put(id, t.getLong("Next"));
            }
            if (t.hasKey("Busy")) {
                busyUntil.put(id, t.getLong("Busy"));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        java.util.Set<UUID> ids = new java.util.HashSet<>(balance.keySet());
        ids.addAll(nextVisit.keySet());
        ids.addAll(busyUntil.keySet());
        NBTTagList l = new NBTTagList();
        for (UUID id : ids) {
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("Id", id);
            if (balance.containsKey(id)) {
                t.setLong("Balance", balance.get(id));
            }
            if (nextVisit.containsKey(id)) {
                t.setLong("Next", nextVisit.get(id));
            }
            if (busyUntil.containsKey(id)) {
                t.setLong("Busy", busyUntil.get(id));
            }
            l.appendTag(t);
        }
        nbt.setTag("Players", l);
        return nbt;
    }
}
