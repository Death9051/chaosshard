package ru.death.chaosshard.client.magic;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Свита, как её последний раз прислал сервер (для страницы «Моя свита» в Гримуаре). */
public final class ClientRetinue {

    private static NBTTagCompound tag = new NBTTagCompound();
    private static long at;

    private ClientRetinue() {
    }

    public static void set(NBTTagCompound t) {
        tag = t;
        at = System.currentTimeMillis();
    }

    public static NBTTagList list() {
        return tag.getTagList("list", 10);
    }

    public static int limit() {
        return tag.getInteger("limit");
    }

    /** Сколько миллисекунд назад пришёл список. */
    public static long age() {
        return at == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - at;
    }
}
