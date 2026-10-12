package ru.death.chaosshard.servant;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Прокачка уникальных зверей (Заяц, Кошка, Тигр). Хранится в мире, а не в звере и не в игроке:
 * зверь может умереть и вернуться из Нити, а хозяин — быть не в сети.
 */
public class BeastData extends WorldSavedData {

    public static final String NAME = "chaosshard_beasts";
    /** Опыт для уровней 2, 3, 4 и для Вознесения (5 — только ритуалом). */
    public static final int[] XP = {0, 100, 300, 700, 1500};
    public static final int PERKS = 8;

    public enum Beast {
        HARE(Servants.Kind.HARE), CAT(Servants.Kind.CAT), TIGER(Servants.Kind.TIGER);

        public final Servants.Kind kind;

        Beast(Servants.Kind kind) {
            this.kind = kind;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Beast of(Servants.Kind k) {
            for (Beast b : values()) {
                if (b.kind == k) {
                    return b;
                }
            }
            return null;
        }

        public static Beast byKey(String s) {
            for (Beast b : values()) {
                if (b.key().equals(s)) {
                    return b;
                }
            }
            return null;
        }
    }

    /** Один зверь одного хозяина. */
    public static class Entry {
        public int xp;
        public boolean ascended, known;
        public int perks;
        /** Мировое время, когда зверь вернётся из Нити (0 — не в Нити). */
        public long returnAt;
        public long saveReady, treatReady, giftDay = -1;
        public UUID entity;
        /** Имя, которое дал хозяин (пусто — имя по виду). */
        public String name = "";
        public final List<ItemStack> bag = new ArrayList<>();
        public final List<ItemStack> stash = new ArrayList<>();

        public int level() {
            return levelOf(xp, ascended);
        }

        public boolean has(int perk) {
            return (perks & (1 << perk)) != 0;
        }

        public int spent() {
            return Integer.bitCount(perks);
        }

        public int points() {
            return Math.max(0, level() - spent());
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("xp", xp);
            t.setBoolean("asc", ascended);
            t.setBoolean("known", known);
            t.setInteger("perks", perks);
            t.setLong("ret", returnAt);
            t.setLong("save", saveReady);
            t.setLong("treat", treatReady);
            t.setLong("gift", giftDay);
            if (entity != null) {
                t.setUniqueId("eid", entity);
            }
            t.setString("name", name);
            t.setTag("bag", list(bag));
            t.setTag("stash", list(stash));
            return t;
        }

        void read(NBTTagCompound t) {
            xp = t.getInteger("xp");
            ascended = t.getBoolean("asc");
            known = t.getBoolean("known");
            perks = t.getInteger("perks");
            returnAt = t.getLong("ret");
            saveReady = t.getLong("save");
            treatReady = t.getLong("treat");
            giftDay = t.hasKey("gift") ? t.getLong("gift") : -1;
            entity = t.hasUniqueId("eid") ? t.getUniqueId("eid") : null;
            name = t.getString("name");
            unlist(t.getTagList("bag", 10), bag);
            unlist(t.getTagList("stash", 10), stash);
        }

        /** Только то, что нужно окну Гримуара. */
        public NBTTagCompound writeClient() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("xp", xp);
            t.setBoolean("asc", ascended);
            t.setBoolean("known", known);
            t.setInteger("perks", perks);
            t.setLong("ret", returnAt);
            t.setBoolean("out", entity != null && returnAt == 0);
            t.setString("name", name);
            return t;
        }
    }

    public static int levelOf(int xp, boolean ascended) {
        if (ascended) {
            return 5;
        }
        for (int l = 3; l >= 1; l--) {
            if (xp >= XP[l]) {
                return l + 1;
            }
        }
        return 1;
    }

    private final Map<UUID, Map<Beast, Entry>> data = new HashMap<>();

    public BeastData() {
        super(NAME);
    }

    public BeastData(String name) {
        super(name);
    }

    public static BeastData get(World world) {
        MapStorage storage = world.getMapStorage();
        BeastData d = (BeastData) storage.getOrLoadData(BeastData.class, NAME);
        if (d == null) {
            d = new BeastData();
            storage.setData(NAME, d);
        }
        return d;
    }

    public Entry entry(UUID owner, Beast b) {
        return data.computeIfAbsent(owner, k -> new java.util.EnumMap<>(Beast.class)).computeIfAbsent(b, k -> new Entry());
    }

    public NBTTagCompound clientTag(UUID owner) {
        NBTTagCompound t = new NBTTagCompound();
        for (Beast b : Beast.values()) {
            t.setTag(b.key(), entry(owner, b).writeClient());
        }
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        data.clear();
        for (String key : nbt.getKeySet()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            NBTTagCompound p = nbt.getCompoundTag(key);
            for (Beast b : Beast.values()) {
                if (p.hasKey(b.key())) {
                    entry(id, b).read(p.getCompoundTag(b.key()));
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        for (Map.Entry<UUID, Map<Beast, Entry>> e : data.entrySet()) {
            NBTTagCompound p = new NBTTagCompound();
            for (Map.Entry<Beast, Entry> be : e.getValue().entrySet()) {
                p.setTag(be.getKey().key(), be.getValue().write());
            }
            nbt.setTag(e.getKey().toString(), p);
        }
        return nbt;
    }

    private static NBTTagList list(List<ItemStack> stacks) {
        NBTTagList l = new NBTTagList();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                l.appendTag(s.writeToNBT(new NBTTagCompound()));
            }
        }
        return l;
    }

    private static void unlist(NBTTagList l, List<ItemStack> out) {
        out.clear();
        for (int i = 0; i < l.tagCount(); i++) {
            ItemStack s = new ItemStack(l.getCompoundTagAt(i));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
    }
}
