package ru.death.chaosshard.compat.journeymap;

import net.minecraft.nbt.NBTTagCompound;

/** Последний список меток Опор с сервера (без ссылок на JourneyMap: класс грузится и без него). */
public final class PillarMarksClient {

    static NBTTagCompound marks = new NBTTagCompound();

    public static void set(NBTTagCompound t) {
        marks = t == null ? new NBTTagCompound() : t;
    }

    public static NBTTagCompound get() {
        return marks;
    }

    private PillarMarksClient() {
    }
}
