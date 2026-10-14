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
        return sigilOf(v) != null;
    }

    /** Знак, стоящий в этой деревне, или null. */
    public BlockPos sigilOf(Village v) {
        for (BlockPos s : sigils) {
            if (belongs(v, s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Знак принадлежит деревне, даже если она перестроилась и её центр уехал: с запасом 16 блоков к радиусу
     * или если у Знака есть хоть одна её дверь (24 блока). Иначе деревня теряла защиту и падала под Скверной.
     */
    public static boolean belongs(Village v, BlockPos sigil) {
        double r = v.getVillageRadius() + 16;
        if (v.getCenter().distanceSq(sigil) <= r * r) {
            return true;
        }
        for (net.minecraft.village.VillageDoorInfo d : v.getVillageDoorInfoList()) {
            if (d.getDoorBlockPos().distanceSq(sigil) <= 24 * 24) {
                return true;
            }
        }
        return false;
    }

    /** Деревня Знака (ближайшая из тех, которым он принадлежит) или null. */
    public static Village villageOf(World world, BlockPos sigil) {
        Village best = null;
        double bd = Double.MAX_VALUE;
        for (Village v : world.getVillageCollection().getVillageList()) {
            if (!belongs(v, sigil)) {
                continue;
            }
            double d = v.getCenter().distanceSq(sigil);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
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
