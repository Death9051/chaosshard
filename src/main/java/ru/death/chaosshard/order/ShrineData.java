package ru.death.chaosshard.order;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/** Святыни Ордена в мире (их Святые камни). */
public class ShrineData extends WorldSavedData {

    public static final String NAME = "chaosshard_shrines";
    public final Set<BlockPos> shrines = new LinkedHashSet<>();
    public int desecrated;

    public ShrineData(String name) {
        super(name);
    }

    public static ShrineData get(World world) {
        ShrineData d = (ShrineData) world.getPerWorldStorage().getOrLoadData(ShrineData.class, NAME);
        if (d == null) {
            d = new ShrineData(NAME);
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        shrines.clear();
        NBTTagList list = nbt.getTagList("s", 4);
        for (int i = 0; i < list.tagCount(); i++) {
            shrines.add(BlockPos.fromLong(((NBTTagLong) list.get(i)).getLong()));
        }
        desecrated = nbt.getInteger("desecrated");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (BlockPos p : shrines) {
            list.appendTag(new NBTTagLong(p.toLong()));
        }
        nbt.setTag("s", list);
        nbt.setInteger("desecrated", desecrated);
        return nbt;
    }
}
