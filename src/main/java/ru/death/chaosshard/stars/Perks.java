package ru.death.chaosshard.stars;

import net.minecraft.entity.player.EntityPlayer;
import ru.death.chaosshard.ChaosShard;

/** Безопасный доступ к перкам Хаоса (без Astral Sorcery — всё false). */
public final class Perks {

    private static boolean on() {
        return ChaosShard.astralLoaded;
    }

    public static boolean spark(EntityPlayer p) {
        return on() && ChaosPerks.has(p, ChaosPerks.SPARK);
    }

    public static boolean night(EntityPlayer p) {
        return on() && ChaosPerks.has(p, ChaosPerks.NIGHT);
    }

    public static boolean shadow(EntityPlayer p) {
        return on() && ChaosPerks.has(p, ChaosPerks.SHADOW);
    }

    public static boolean hunger(EntityPlayer p) {
        return on() && ChaosPerks.has(p, ChaosPerks.HUNGER);
    }

    public static boolean well(EntityPlayer p) {
        return on() && ChaosPerks.has(p, ChaosPerks.WELL);
    }

    private Perks() {
    }
}
