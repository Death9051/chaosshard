package ru.death.chaosshard.client;

import net.minecraft.nbt.NBTTagCompound;
import ru.death.chaosshard.servant.BeastData;

/** Звери игрока, как их знает клиент (для окна Гримуара). */
public final class ClientBeasts {

    private static NBTTagCompound tag = new NBTTagCompound();

    static void set(NBTTagCompound t) {
        tag = t;
    }

    public static NBTTagCompound get(BeastData.Beast b) {
        return tag.getCompoundTag(b.key());
    }

    public static int level(BeastData.Beast b) {
        NBTTagCompound t = get(b);
        return BeastData.levelOf(t.getInteger("xp"), t.getBoolean("asc"));
    }

    public static boolean has(BeastData.Beast b, int perk) {
        return (get(b).getInteger("perks") & (1 << perk)) != 0;
    }

    public static int points(BeastData.Beast b) {
        return Math.max(0, level(b) - Integer.bitCount(get(b).getInteger("perks")));
    }

    private ClientBeasts() {
    }
}
