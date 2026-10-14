package ru.death.chaosshard.spell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.List;

/**
 * Внешние модификаторы заклинаний: материалы оружия, трофеи и т. п.
 * Источники регистрируют {@link Hook}; здесь множители собираются вместе.
 * Совместимость с модами подключается только если мод стоит, поэтому сюда приходят интерфейсы, а не классы модов.
 */
public final class SpellMods {

    public interface Hook {
        default double cost(EntityPlayer p, Spell s) { return 1.0; }
        default double power(EntityPlayer p, Spell s) { return 1.0; }
        default double cooldown(EntityPlayer p, Spell s) { return 1.0; }
        default double twice(EntityPlayer p, Spell s) { return 0.0; }
        default int maxEnergy(EntityPlayer p) { return 0; }
        /** Шире на столько блоков (Разлом и т. п.). */
        default int area(EntityPlayer p, Spell s) { return 0; }
        /** Сколько энергии может дать сверх своей (запас оружия, кровь). */
        default int extraEnergy(EntityPlayer p, Spell s) { return 0; }
        /** Заплатить часть цены сверх энергии игрока. Возвращает, сколько покрыто. */
        default int payExtra(EntityPlayerMP p, Spell s, int need) { return 0; }
        /** Перед тратой энергии игрока: взять из своего запаса. Возвращает, сколько покрыто. */
        default int payFirst(EntityPlayerMP p, Spell s, int cost) { return 0; }
        /** Заклинание оплачено целиком из стороннего запаса — без перезарядки? */
        default boolean freeCooldown(EntityPlayer p, Spell s, int fromStore, int cost) { return false; }
        default void after(EntityPlayerMP p, Spell s, int energy) { }
    }

    private static final List<Hook> HOOKS = new ArrayList<>();

    public static void register(Hook h) {
        HOOKS.add(h);
    }

    public static double cost(EntityPlayer p, Spell s) {
        double m = 1.0;
        for (Hook h : HOOKS) m *= h.cost(p, s);
        return m;
    }

    public static double power(EntityPlayer p, Spell s) {
        double m = 1.0;
        for (Hook h : HOOKS) m *= h.power(p, s);
        return m;
    }

    public static double cooldown(EntityPlayer p, Spell s) {
        double m = 1.0;
        for (Hook h : HOOKS) m *= h.cooldown(p, s);
        return m;
    }

    public static double twice(EntityPlayer p, Spell s) {
        double c = 0;
        for (Hook h : HOOKS) c += h.twice(p, s);
        return c;
    }

    public static int maxEnergy(EntityPlayer p) {
        int n = 0;
        for (Hook h : HOOKS) n += h.maxEnergy(p);
        return n;
    }

    public static int area(EntityPlayer p, Spell s) {
        int n = 0;
        for (Hook h : HOOKS) n += h.area(p, s);
        return n;
    }

    public static int extraEnergy(EntityPlayer p, Spell s) {
        int n = 0;
        for (Hook h : HOOKS) n += h.extraEnergy(p, s);
        return n;
    }

    /** Оплатить заклинание: сначала сторонние запасы, потом энергия игрока, остаток — «кровью» и т. п. Возвращает, без перезарядки ли. */
    public static boolean pay(EntityPlayerMP p, Spell s, int cost) {
        int left = cost;
        int fromStore = 0;
        for (Hook h : HOOKS) {
            if (left <= 0) break;
            int got = Math.min(left, h.payFirst(p, s, left));
            left -= got;
            fromStore += got;
        }
        int own = (int) Math.min(left, ChaosEnergy.get(p));
        if (own > 0) {
            ChaosEnergy.spend(p, own);
            left -= own;
        }
        for (Hook h : HOOKS) {
            if (left <= 0) break;
            left -= Math.min(left, h.payExtra(p, s, left));
        }
        boolean free = false;
        for (Hook h : HOOKS) free |= h.freeCooldown(p, s, fromStore, cost);
        return free;
    }

    public static void after(EntityPlayerMP p, Spell s, int energy) {
        for (Hook h : HOOKS) h.after(p, s, energy);
    }

    private SpellMods() {
    }
}
