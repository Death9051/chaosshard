package ru.death.chaosshard.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** Постройки Ордена в измерении: запланированные, поставленные, несостоявшиеся. */
public class OrderStructureData extends WorldSavedData {

    public static final String NAME = "chaosshard_order_structures";
    public static final int PLANNED = 0, PLACED = 1, FAILED = 2;

    public static class Entry {
        public long id;
        public String name;
        public int x, y, z, rot;
        public long seed;
        public int state;
        public boolean keeperDead;
        public final List<BlockPos> beams = new ArrayList<>();

        public BlockPos origin() {
            return new BlockPos(x, y, z);
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("Id", id);
            t.setString("Name", name);
            t.setInteger("X", x);
            t.setInteger("Y", y);
            t.setInteger("Z", z);
            t.setInteger("Rot", rot);
            t.setLong("Seed", seed);
            t.setInteger("State", state);
            t.setBoolean("KeeperDead", keeperDead);
            NBTTagList bl = new NBTTagList();
            for (BlockPos p : beams) {
                NBTTagCompound c = new NBTTagCompound();
                c.setLong("P", p.toLong());
                bl.appendTag(c);
            }
            t.setTag("Beams", bl);
            return t;
        }

        static Entry read(NBTTagCompound t) {
            Entry e = new Entry();
            e.id = t.getLong("Id");
            e.name = t.getString("Name");
            e.x = t.getInteger("X");
            e.y = t.getInteger("Y");
            e.z = t.getInteger("Z");
            e.rot = t.getInteger("Rot");
            e.seed = t.getLong("Seed");
            e.state = t.getInteger("State");
            e.keeperDead = t.getBoolean("KeeperDead");
            NBTTagList bl = t.getTagList("Beams", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < bl.tagCount(); i++) {
                e.beams.add(BlockPos.fromLong(bl.getCompoundTagAt(i).getLong("P")));
            }
            return e;
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    public long nextId = 1;

    public OrderStructureData(String name) {
        super(name);
    }

    public OrderStructureData() {
        this(NAME);
    }

    public static OrderStructureData get(World world) {
        OrderStructureData d = (OrderStructureData) world.getPerWorldStorage().getOrLoadData(OrderStructureData.class, NAME);
        if (d == null) {
            d = new OrderStructureData();
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    public Entry byId(long id) {
        for (Entry e : entries) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    /** Ближайшая постройка (любого состояния, кроме несостоявшихся), в пределах r по горизонтали. */
    public Entry near(BlockPos p, int r) {
        Entry best = null;
        long bd = Long.MAX_VALUE;
        for (Entry e : entries) {
            if (e.state == FAILED) {
                continue;
            }
            long dx = e.x - p.getX(), dz = e.z - p.getZ();
            long d = dx * dx + dz * dz;
            if (d <= (long) r * r && d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();
        NBTTagList l = tag.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) {
            entries.add(Entry.read(l.getCompoundTagAt(i)));
        }
        nextId = Math.max(1, tag.getLong("NextId"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList l = new NBTTagList();
        for (Entry e : entries) {
            l.appendTag(e.write());
        }
        tag.setTag("Entries", l);
        tag.setLong("NextId", nextId);
        return tag;
    }
}
