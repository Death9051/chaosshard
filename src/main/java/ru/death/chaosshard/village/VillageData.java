package ru.death.chaosshard.village;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/** Судьбы деревень: под покровительством (Знак) или поглощённые Скверной. */
public class VillageData extends WorldSavedData {

    public static final String NAME = "chaosshard_villages";
    /** Знаки покровителя. */
    public final Set<BlockPos> sigils = new LinkedHashSet<>();
    /** Центры павших деревень. */
    public final Set<BlockPos> fallen = new LinkedHashSet<>();

    public VillageData(String name) {
        super(name);
    }

    public static VillageData get(World world) {
        VillageData d = (VillageData) world.getPerWorldStorage().getOrLoadData(VillageData.class, NAME);
        if (d == null) {
            d = new VillageData(NAME);
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    /** Деревня под покровительством: в её пределах стоит Знак. */
    public boolean isPatron(Village v) {
        for (BlockPos s : sigils) {
            if (v.isBlockPosWithinSqVillageRadius(s)) {
                return true;
            }
        }
        return false;
    }

    /** Знак, стоящий в этой деревне, или null. */
    public BlockPos sigilOf(Village v) {
        for (BlockPos s : sigils) {
            if (v.isBlockPosWithinSqVillageRadius(s)) {
                return s;
            }
        }
        return null;
    }

    public boolean isFallen(Village v) {
        for (BlockPos f : fallen) {
            if (v.isBlockPosWithinSqVillageRadius(f)) {
                return true;
            }
        }
        return false;
    }

    private static NBTTagList save(Set<BlockPos> set) {
        NBTTagList list = new NBTTagList();
        for (BlockPos p : set) {
            list.appendTag(new NBTTagLong(p.toLong()));
        }
        return list;
    }

    private static void load(NBTTagList list, Set<BlockPos> set) {
        set.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            set.add(BlockPos.fromLong(((NBTTagLong) list.get(i)).getLong()));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        load(nbt.getTagList("sigils", 4), sigils);
        load(nbt.getTagList("fallen", 4), fallen);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("sigils", save(sigils));
        nbt.setTag("fallen", save(fallen));
        return nbt;
    }
}
