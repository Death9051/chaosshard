package ru.death.chaosshard.stars;

import net.minecraft.world.World;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;

/**
 * Какие правила неба действуют в мире. Без ссылок на Astral Sorcery —
 * класс можно вызывать даже без него.
 *
 *  ANTECHAMBER — Преддверие: все созвездия, максимум звёздного света;
 *  LUCHEZARIYA — Лучезария: небо Astral Sorcery как обычно (+ Хаос);
 *  CHAOS_ONLY  — любой другой мир: только созвездие Хаоса.
 */
public final class SkyRules {

    public enum Kind {
        ANTECHAMBER,
        LUCHEZARIYA,
        CHAOS_ONLY
    }

    public static Kind classify(World world) {
        if (world == null) {
            return Kind.CHAOS_ONLY;
        }
        return classify(world.provider.getDimension());
    }

    public static Kind classify(int dimension) {
        if (AntechamberDimension.is(dimension)) {
            return Kind.ANTECHAMBER;
        }
        for (int id : ChaosConfig.stars.luchezariyaDimensions) {
            if (id == dimension) {
                return Kind.LUCHEZARIYA;
            }
        }
        return Kind.CHAOS_ONLY;
    }

    /** Все созвездия в полную силу. */
    public static boolean allConstellations(World world) {
        return ChaosConfig.stars.enabled && ChaosConfig.stars.antechamberAllConstellations && classify(world) == Kind.ANTECHAMBER;
    }

    /** Максимум звёздного света: вечная ночь, лучшее место, полная сила. */
    public static boolean maxStarlight(World world) {
        return ChaosConfig.stars.enabled && ChaosConfig.stars.antechamberMaxStarlight && classify(world) == Kind.ANTECHAMBER;
    }

    /** Оставить на небе только Хаос. */
    public static boolean chaosOnly(World world) {
        return ChaosConfig.stars.enabled && ChaosConfig.stars.chaosOnlyElsewhere && classify(world) == Kind.CHAOS_ONLY;
    }

    private SkyRules() {
    }
}
