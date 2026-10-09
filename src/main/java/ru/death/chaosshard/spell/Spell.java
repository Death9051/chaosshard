package ru.death.chaosshard.spell;

/**
 * Заклинания Гримуара. Открываются уровнями Нити, стоят сытости и имеют перезарядку.
 *
 * @param minThread минимальный уровень Нити
 * @param hunger    сколько «половинок окорочка» съедает (усталость × 4)
 * @param cooldown  перезарядка в тиках
 * @param onBlock   нужно ли указывать на блок
 */
public enum Spell {
    BOOK(0, 0, 0, false),
    SPROUT(0, 1, 5, true),
    BRIDGE(0, 2, 20, true),
    SPRING(1, 1, 20, true),
    RIFT(2, 2, 20, true),
    STARSIGHT(2, 2, 200, false),
    RECALL(3, 4, 1200, false);

    public final int minThread;
    public final int hunger;
    public final int cooldown;
    public final boolean onBlock;

    Spell(int minThread, int hunger, int cooldown, boolean onBlock) {
        this.minThread = minThread;
        this.hunger = hunger;
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
