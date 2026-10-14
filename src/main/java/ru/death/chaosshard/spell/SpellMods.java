package ru.death.chaosshard.spell;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Внешние модификаторы заклинаний: материалы оружия, трофеи и т. п.
 * Каждый источник даёт множители; здесь они собираются вместе.
 */
public final class SpellMods {

    /** Множитель цены. */
    public static double cost(EntityPlayer p, Spell s) {
        return 1.0;
    }

    /** Множитель силы (урон, длительность, лечение). */
    public static double power(EntityPlayer p, Spell s) {
        return 1.0;
    }

    /** Множитель перезарядки. */
    public static double cooldown(EntityPlayer p, Spell s) {
        return 1.0;
    }

    /** Шанс, что заклинание сработает дважды. */
    public static double twice(EntityPlayer p, Spell s) {
        return 0.0;
    }

    /** Вызывается после успешного заклинания (опыт материалов и т. п.). */
    public static void after(EntityPlayer p, Spell s, int energy) {
    }

    private SpellMods() {
    }
}
