package ru.death.chaosshard.compat;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.fml.common.Loader;
import ru.death.chaosshard.ChaosShard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Характеристики зачарований из Apotheosis (через отражение, без зависимости при сборке).
 * <ul>
 * <li>{@code EnchModule.getEnchInfo(e)} — свой максимум уровня и своя «сила» для каждого уровня (их настраивает
 * конфиг Apotheosis, в том числе уровни выше ванильных: Острота VI, VII…).</li>
 * <li>{@code maxNormalPower} / {@code maxPower} — потолки силы стола: обычные полки дают не больше первого,
 * всё вместе (с Адскими полками) — не больше второго.</li>
 * <li>{@code BLACKLISTED_ENCHANTS} — зачарования, которые Apotheosis убрал из игры.</li>
 * </ul>
 * Без Apotheosis или при сбое отражения всё берётся у самого зачарования.
 */
public final class ApotheosisEnchant {

    private static boolean tried, ok;
    private static Method getInfo, infoMax, infoMinPower, infoMaxPower;
    private static Field maxNormal, maxTotal, blacklist;

    private ApotheosisEnchant() {
    }

    private static boolean init() {
        if (tried) {
            return ok;
        }
        tried = true;
        if (!Loader.isModLoaded("apotheosis")) {
            return false;
        }
        try {
            Class<?> module = Class.forName("shadows.ench.EnchModule");
            Class<?> info = Class.forName("shadows.ench.EnchantmentInfo");
            getInfo = module.getMethod("getEnchInfo", Enchantment.class);
            infoMax = info.getMethod("getMaxLevel");
            infoMinPower = info.getMethod("getMinPower", int.class);
            infoMaxPower = info.getMethod("getMaxPower", int.class);
            maxNormal = module.getField("maxNormalPower");
            maxTotal = module.getField("maxPower");
            blacklist = module.getField("BLACKLISTED_ENCHANTS");
            ok = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            ChaosShard.LOG.warn("Характеристики зачарований Apotheosis не подключились: {}", e.toString());
        }
        return ok;
    }

    public static boolean active() {
        return init();
    }

    private static Object info(Enchantment e) {
        try {
            return init() ? getInfo.invoke(null, e) : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    public static int maxLevel(Enchantment e) {
        Object i = info(e);
        if (i != null) {
            try {
                return Math.max(1, (Integer) infoMax.invoke(i));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // ниже — ванильное
            }
        }
        return Math.max(1, e.getMaxLevel());
    }

    public static int minPower(Enchantment e, int level) {
        Object i = info(e);
        if (i != null) {
            try {
                return (Integer) infoMinPower.invoke(i, level);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // ниже — ванильное
            }
        }
        return e.getMinEnchantability(level);
    }

    public static int maxPower(Enchantment e, int level) {
        Object i = info(e);
        if (i != null) {
            try {
                return (Integer) infoMaxPower.invoke(i, level);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // ниже — ванильное
            }
        }
        return e.getMaxEnchantability(level);
    }

    /** Потолок силы от обычных полок (без Apotheosis — нет). */
    public static float maxNormalPower() {
        try {
            return init() ? maxNormal.getFloat(null) : Float.MAX_VALUE;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Float.MAX_VALUE;
        }
    }

    /** Потолок общей силы полок (без Apotheosis — нет). */
    public static float maxPower() {
        try {
            return init() ? maxTotal.getFloat(null) : Float.MAX_VALUE;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Float.MAX_VALUE;
        }
    }

    public static boolean blacklisted(Enchantment e) {
        try {
            return init() && ((Collection<?>) blacklist.get(null)).contains(e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
