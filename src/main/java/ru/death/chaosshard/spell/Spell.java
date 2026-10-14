package ru.death.chaosshard.spell;

/**
 * Заклинания Гримуара. Открываются уровнями Нити, тратят энергию Хаоса и имеют перезарядку.
 *
 * @param minThread минимальный уровень Нити
 * @param energy    сколько энергии Хаоса тратит
 * @param cooldown  перезарядка в тиках
 * @param onBlock   действует по блоку под взглядом
 * @param onEntity  действует по существу под взглядом
 */
public enum Spell {
    BOOK(0, 0, 0, false, false),
    SPROUT(0, 2, 5, true, false),
    BRIDGE(0, 6, 20, true, false),
    SPRING(1, 4, 20, true, true),
    RIFT(2, 8, 20, true, false),
    STARSIGHT(2, 10, 200, false, false),
    RECALL(3, 40, 1200, false, false),
    /** Рывок: бросок вперёд по взгляду, скачок на блок или бросок к цели с ударом. */
    DASH(1, 8, 40, false, true),
    /** Стрела Хаоса: удар по первой цели на линии взгляда; цепь или взрыв. */
    BOLT(1, 10, 30, false, true),
    /** Форма Хаоса: боевая форма на 30 секунд. */
    FORM(2, 50, 1200, false, false);

    public final int minThread;
    public final int energy;
    public final int cooldown;
    public final boolean onBlock;
    public final boolean onEntity;

    Spell(int minThread, int energy, int cooldown, boolean onBlock, boolean onEntity) {
        this.onEntity = onEntity;
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
