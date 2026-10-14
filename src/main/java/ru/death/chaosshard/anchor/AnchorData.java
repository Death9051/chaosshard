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

    /** Сводка по Якорю — чтобы сеть знала о Якорях в незагруженных чанках. */
    public static final class Rec {
        public java.util.UUID owner;
        public int level = 1;
        public int xp;
        public int land;
        public boolean captured;
        /** Опыт, пришедший переливом, пока Якорь не загружен. */
        public int inbox;
        /** Специализация ({@link AnchorSpec}), −1 — нет. */
        public int spec = -1;
        /** Уровень Альфы (0 — не Альфа). */
        public int alpha;
        /** Сколько печатей каждого вида стоит (для печатей Альфы на всю сеть). */
        public int[] seals = new int[0];
        /** Заимствованные эффекты ({@link AnchorFx}), −1 — пусто. */
        public int[] borrow = new int[0];
    }

    public final java.util.Map<Long, Rec> recs = new java.util.HashMap<>();
    /** Запас перелива по владельцам (уже с коэффициентом). */
    public final java.util.Map<java.util.UUID, Integer> reserve = new java.util.HashMap<>();

    public Rec rec(BlockPos p) {
        return recs.get(p.toLong());
    }

    public Rec recOrNew(BlockPos p) {
        return recs.computeIfAbsent(p.toLong(), k -> new Rec());
    }

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
        recs.clear();
        NBTTagList rl = nbt.getTagList("r", 10);
        for (int i = 0; i < rl.tagCount(); i++) {
            NBTTagCompound t = rl.getCompoundTagAt(i);
            Rec r = new Rec();
            if (t.hasUniqueId("o")) {
                r.owner = t.getUniqueId("o");
            }
            r.level = t.getInteger("l");
            r.xp = t.getInteger("x");
            r.land = t.getInteger("d");
            r.captured = t.getBoolean("c");
            r.inbox = t.getInteger("i");
            r.spec = t.hasKey("s") ? t.getInteger("s") : -1;
            r.alpha = t.getInteger("a");
            r.seals = t.getIntArray("se");
            r.borrow = t.getIntArray("b");
            recs.put(t.getLong("p"), r);
        }
        reserve.clear();
        NBTTagList res = nbt.getTagList("res", 10);
        for (int i = 0; i < res.tagCount(); i++) {
            NBTTagCompound t = res.getCompoundTagAt(i);
            if (t.hasUniqueId("o")) {
                reserve.put(t.getUniqueId("o"), t.getInteger("n"));
            }
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
        NBTTagList rl = new NBTTagList();
        for (java.util.Map.Entry<Long, Rec> e : recs.entrySet()) {
            Rec r = e.getValue();
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("p", e.getKey());
            if (r.owner != null) {
                t.setUniqueId("o", r.owner);
            }
            t.setInteger("l", r.level);
            t.setInteger("x", r.xp);
            t.setInteger("d", r.land);
            t.setBoolean("c", r.captured);
            t.setInteger("i", r.inbox);
            t.setInteger("s", r.spec);
            t.setInteger("a", r.alpha);
            t.setIntArray("se", r.seals);
            t.setIntArray("b", r.borrow);
            rl.appendTag(t);
        }
        nbt.setTag("r", rl);
        NBTTagList res = new NBTTagList();
        for (java.util.Map.Entry<java.util.UUID, Integer> e : reserve.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("o", e.getKey());
            t.setInteger("n", e.getValue());
            res.appendTag(t);
        }
        nbt.setTag("res", res);
        return nbt;
    }
}
