package ru.death.chaosshard.act5;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;

/** Столица Лучезарии: где стоит, что погашено, жив ли Король, открыт ли Разлом. Хранится в мире Лучезарии. */
public class CapitalData extends WorldSavedData {

    public static final String NAME = "chaosshard_capital";
    /** Нет / Знамение указало / стоит / барьер пал, Король ждёт / Король пал. */
    public static final int NONE = 0, PLANNED = 1, STANDING = 2, OPEN = 3, FALLEN = 4;

    public int state = NONE;
    public long entry = -1;
    public int x, y, z;
    public final List<BlockPos> beacons = new ArrayList<>();
    public final List<BlockPos> barrier = new ArrayList<>();
    public BlockPos king, rift;
    public boolean kingSpawned, introShown, riftOpen;

    public CapitalData() {
        super(NAME);
    }

    public CapitalData(String name) {
        super(name);
    }

    public static CapitalData get(World world) {
        CapitalData d = (CapitalData) world.getPerWorldStorage().getOrLoadData(CapitalData.class, NAME);
        if (d == null) {
            d = new CapitalData();
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    public BlockPos center() {
        return new BlockPos(x, y, z);
    }

    static NBTTagList list(List<BlockPos> ps) {
        NBTTagList l = new NBTTagList();
        for (BlockPos p : ps) {
            l.appendTag(new NBTTagLong(p.toLong()));
        }
        return l;
    }

    static void read(NBTTagList l, List<BlockPos> into) {
        into.clear();
        for (int i = 0; i < l.tagCount(); i++) {
            into.add(BlockPos.fromLong(((NBTTagLong) l.get(i)).getLong()));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        state = tag.getInteger("State");
        entry = tag.getLong("Entry");
        x = tag.getInteger("X");
        y = tag.getInteger("Y");
        z = tag.getInteger("Z");
        read(tag.getTagList("Beacons", 4), beacons);
        read(tag.getTagList("Barrier", 4), barrier);
        king = tag.hasKey("King") ? BlockPos.fromLong(tag.getLong("King")) : null;
        rift = tag.hasKey("Rift") ? BlockPos.fromLong(tag.getLong("Rift")) : null;
        kingSpawned = tag.getBoolean("KingSpawned");
        introShown = tag.getBoolean("Intro");
        riftOpen = tag.getBoolean("RiftOpen");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setInteger("State", state);
        tag.setLong("Entry", entry);
        tag.setInteger("X", x);
        tag.setInteger("Y", y);
        tag.setInteger("Z", z);
        tag.setTag("Beacons", list(beacons));
        tag.setTag("Barrier", list(barrier));
        if (king != null) {
            tag.setLong("King", king.toLong());
        }
        if (rift != null) {
            tag.setLong("Rift", rift.toLong());
        }
        tag.setBoolean("KingSpawned", kingSpawned);
        tag.setBoolean("Intro", introShown);
        tag.setBoolean("RiftOpen", riftOpen);
        return tag;
    }
}
