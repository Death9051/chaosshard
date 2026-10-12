package ru.death.chaosshard.spell;

/**
 * Заклинания Гримуара. Открываются уровнями Нити, тратят энергию Хаоса и имеют перезарядку.
 *
 * @param minThread минимальный уровень Нити
 * @param energy    сколько энергии Хаоса тратит
 * @param cooldown  перезарядка в тиках
 * @param onBlock   нужно ли указывать на блок
 */
public enum Spell {
    BOOK(0, 0, 0, false),
    SPROUT(0, 2, 5, true),
    BRIDGE(0, 6, 20, true),
    SPRING(1, 4, 20, true),
    RIFT(2, 8, 20, true),
    STARSIGHT(2, 10, 200, false),
    RECALL(3, 40, 1200, false),
    /** Рывок: бросок вперёд по взгляду. */
    DASH(1, 8, 40, false),
    /** Стрела Хаоса: удар по первой цели на линии взгляда. */
    BOLT(1, 10, 30, false),
    /** Форма Хаоса: боевая форма на 30 секунд. */
    FORM(2, 50, 1200, false);

    public final int minThread;
    public final int energy;
    public final int cooldown;
    public final boolean onBlock;

    Spell(int minThread, int energy, int cooldown, boolean onBlock) {
        this.minThread = minThread;
        this.energy = energy;
        this.cooldown = cooldown;
        this.onBlock = onBlock;
    }

    public String key() {
        return "chaosshard.spell." + name().toLowerCase();
    }

    public static Spell byIndex(int i) {
        Spell[] all = values();
        return i >= 0 && i < all.length ? all[i] : BOOK;
    }
}
