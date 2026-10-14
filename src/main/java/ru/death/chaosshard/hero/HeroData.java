package ru.death.chaosshard.hero;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/** Опоры Завесы в Лучезарии: где стоит каждая и пала ли. Хранится в мире Лучезарии. */
public class HeroData extends WorldSavedData {

    public static final String NAME = "chaosshard_heroes";
    /** Нет / Знамение указало место / Опора стоит, Светоч жив / Опора пала. */
    public static final int NONE = 0, PLANNED = 1, STANDING = 2, FALLEN = 3;

    public static final class Pillar {
        public int state = NONE;
        /** Запись постройки в OrderStructureData. */
        public long entry = -1;
        public int x, y, z;
        /** Волна Скверны после падения: текущий радиус, -1 — не идёт. */
        public int fallRing = -1;
        /** Когда рухнет колонна (время мира), 0 — не ждёт. */
        public long collapseAt;
        /** Катсцена появления героя уже показана. */
        public boolean introShown;
        /** Связанные постройки (монастыри Ирены и т.п.) — записи OrderStructureData. */
        public final java.util.List<Long> linked = new java.util.ArrayList<>();
        /** Святыни этих построек, когда они встали. */
        public final java.util.List<BlockPos> shrines = new java.util.ArrayList<>();
        /** Где Светоч встал на Опоре (для ремонта, если он пропал); null — неизвестно. */
        public BlockPos heroAt;
        /** Где Светоча видели в последний раз (Вельмор умеет сбегать к печатям). */
        public BlockPos lastSeen;
        /** Сам Светоч этой Опоры. */
        public java.util.UUID heroId;

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    public final Pillar[] pillars = new Pillar[Lightbearer.values().length];

    public HeroData(String name) {
        super(name);
        for (int i = 0; i < pillars.length; i++) {
            pillars[i] = new Pillar();
        }
    }

    public HeroData() {
        this(NAME);
    }

    public static HeroData get(World world) {
        HeroData d = (HeroData) world.getPerWorldStorage().getOrLoadData(HeroData.class, NAME);
        if (d == null) {
            d = new HeroData();
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    public Pillar of(Lightbearer h) {
        return pillars[h.ordinal()];
    }

    public boolean fallen(Lightbearer h) {
        return of(h).state == FALLEN;
    }

    /** Сколько Опор уже пало. */
    public int fallenCount() {
        int n = 0;
        for (Pillar p : pillars) {
            if (p.state == FALLEN) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        for (Lightbearer h : Lightbearer.values()) {
            NBTTagCompound t = tag.getCompoundTag(h.id());
            Pillar p = pillars[h.ordinal()];
            p.state = t.getInteger("State");
            p.entry = t.hasKey("Entry") ? t.getLong("Entry") : -1;
            p.x = t.getInteger("X");
            p.y = t.getInteger("Y");
            p.z = t.getInteger("Z");
            p.fallRing = t.hasKey("Ring") ? t.getInteger("Ring") : -1;
            p.collapseAt = t.getLong("CollapseAt");
            p.introShown = t.getBoolean("Intro");
            p.linked.clear();
            for (long l : readLongs(t, "Linked")) {
                p.linked.add(l);
            }
            p.shrines.clear();
            for (long l : readLongs(t, "Shrines")) {
                p.shrines.add(BlockPos.fromLong(l));
            }
            p.heroAt = t.hasKey("HeroAt") ? BlockPos.fromLong(t.getLong("HeroAt")) : null;
            p.lastSeen = t.hasKey("Seen") ? BlockPos.fromLong(t.getLong("Seen")) : null;
            p.heroId = t.hasUniqueId("Hero") ? t.getUniqueId("Hero") : null;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        for (Lightbearer h : Lightbearer.values()) {
            Pillar p = pillars[h.ordinal()];
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("State", p.state);
            t.setLong("Entry", p.entry);
            t.setInteger("X", p.x);
            t.setInteger("Y", p.y);
            t.setInteger("Z", p.z);
            t.setInteger("Ring", p.fallRing);
            t.setLong("CollapseAt", p.collapseAt);
            t.setBoolean("Intro", p.introShown);
            writeLongs(t, "Linked", p.linked);
            java.util.List<Long> sh = new java.util.ArrayList<>();
            for (BlockPos b : p.shrines) {
                sh.add(b.toLong());
            }
            writeLongs(t, "Shrines", sh);
            if (p.heroAt != null) {
                t.setLong("HeroAt", p.heroAt.toLong());
            }
            if (p.lastSeen != null) {
                t.setLong("Seen", p.lastSeen.toLong());
            }
            if (p.heroId != null) {
                t.setUniqueId("Hero", p.heroId);
            }
            tag.setTag(h.id(), t);
        }
        return tag;
    }

    static long[] readLongs(NBTTagCompound t, String key) {
        net.minecraft.nbt.NBTTagList l = t.getTagList(key, net.minecraftforge.common.util.Constants.NBT.TAG_LONG);
        long[] out = new long[l.tagCount()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((net.minecraft.nbt.NBTTagLong) l.get(i)).getLong();
        }
        return out;
    }

    static void writeLongs(NBTTagCompound t, String key, java.util.List<Long> vals) {
        net.minecraft.nbt.NBTTagList l = new net.minecraft.nbt.NBTTagList();
        for (long v : vals) {
            l.appendTag(new net.minecraft.nbt.NBTTagLong(v));
        }
        t.setTag(key, l);
    }
}
