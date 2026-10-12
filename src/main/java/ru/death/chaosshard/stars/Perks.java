package ru.death.chaosshard.stars;

import net.minecraft.entity.player.EntityPlayer;
import ru.death.chaosshard.ChaosShard;

/** Безопасный доступ к перкам Хаоса (без Astral Sorcery — всё false). */
public final class Perks {

    private static boolean on() {
        return ChaosShard.astralLoaded;
    }

    public static boolean spark(EntityPlayer p) {
        return on() && ChaosPerks.has(p, 0);
    }

    public static boolean night(EntityPlayer p) {
        return on() && ChaosPerks.has(p, 1);
    }

    public static boolean shadow(EntityPlayer p) {
        return on() && ChaosPerks.has(p, 2);
    }

    public static boolean hunger(EntityPlayer p) {
        return on() && ChaosPerks.has(p, 3);
    }

    public static boolean well(EntityPlayer p) {
        return on() && ChaosPerks.has(p, 4);
    }

    private Perks() {
    }
}
