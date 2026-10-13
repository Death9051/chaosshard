package ru.death.chaosshard.garrot;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/** Гаррот в мире Лучезарии: склонился ли, когда приходил на дуэль в последний раз. */
public class GarrotData extends WorldSavedData {

    public static final String NAME = "chaosshard_garrot";

    /** Гаррот проиграл дуэль и склонился: набегов больше нет, его солдаты служат мне. */
    public boolean submitted;
    /** Время мира последнего прихода на дуэль (для паузы после поражения). */
    public long lastDuel = -1000000;
    public int duels;

    public GarrotData(String name) {
        super(name);
    }

    public GarrotData() {
        this(NAME);
    }

    public static GarrotData get(World world) {
        GarrotData d = (GarrotData) world.getPerWorldStorage().getOrLoadData(GarrotData.class, NAME);
        if (d == null) {
            d = new GarrotData();
            world.getPerWorldStorage().setData(NAME, d);
        }
        return d;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        submitted = tag.getBoolean("Submitted");
        lastDuel = tag.hasKey("LastDuel") ? tag.getLong("LastDuel") : -1000000;
        duels = tag.getInteger("Duels");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setBoolean("Submitted", submitted);
        tag.setLong("LastDuel", lastDuel);
        tag.setInteger("Duels", duels);
        return tag;
    }
}
