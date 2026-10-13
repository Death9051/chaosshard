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
            tag.setTag(h.id(), t);
        }
        return tag;
    }
}
