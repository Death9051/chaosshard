package ru.death.chaosshard.anchor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;

/** Все Якоря Хаоса в мире — чтобы следить за расстоянием между ними и показывать список. */
public class AnchorData extends WorldSavedData {

    public static final String NAME = "chaosshard_anchors";
    public final List<BlockPos> anchors = new ArrayList<>();
    /** Сколько набегов Мрачной Армии уже было. */
    public int raids;

    public AnchorData(String name) {
        super(name);
    }

    public static AnchorData get(World world) {
        AnchorData d = (AnchorData) world.getPerWorldStorage().getOrLoadData(AnchorData.class, NAME);
        if (d == null) {
            d = new AnchorData(NAME);
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    public BlockPos nearest(BlockPos pos, BlockPos except) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : anchors) {
            if (p.equals(except)) {
                continue;
            }
            double d = p.distanceSq(pos);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        anchors.clear();
        raids = nbt.getInteger("raids");
        NBTTagList list = nbt.getTagList("a", 4);
        for (int i = 0; i < list.tagCount(); i++) {
            anchors.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) list.get(i)).getLong()));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (BlockPos p : anchors) {
            list.appendTag(new net.minecraft.nbt.NBTTagLong(p.toLong()));
        }
        nbt.setTag("a", list);
        nbt.setInteger("raids", raids);
        return nbt;
    }
}
