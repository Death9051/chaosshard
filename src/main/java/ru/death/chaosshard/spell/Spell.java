package ru.death.chaosshard.spell;

/**
 * Заклинания Гримуара. Открываются уровнями Нити, тратят энергию Хаоса и имеют перезарядку.
 *
 * @param minThread минимальный уровень Нити
 * @param energy    сколько энергии Хаоса тратит
 * @param cooldown  перезарядка в тиках
 * @param onBlock   действует по блоку под взглядом
 * @param onEntity  действует по существу под взглядом
 * @param combat    боевое (в Гримуаре — раздел «Боевые»); мирные и боевые ветки не смешивают
 */
public enum Spell {
    BOOK(0, 0, 0, false, false, false),
    SPROUT(0, 2, 5, true, false, false),
    /** Тропа Хаоса (бывший Мост пустоты): временные плиты застывшего Хаоса. */
    BRIDGE(0, 6, 40, false, false, false),
    /** Пожирание (бывший Источник): Осколок поглощает область. */
    SPRING(1, 6, 20, true, false, false),
    RIFT(2, 8, 20, true, false, false),
    STARSIGHT(2, 10, 200, false, false, false),
    RECALL(3, 40, 1200, false, false, false),
    /** Рывок: бросок к врагу с ударом. */
    DASH(1, 8, 40, false, true, true),
    /** Стрела Хаоса: удар по цели на линии взгляда; по блоку — взрыв без разрушений. */
    BOLT(1, 10, 30, false, true, true),
    /** Форма Хаоса: боевая форма на 30 секунд. */
    FORM(2, 50, 1200, false, false, true),
    /** Проклятие: метка на враге — по нему бьют сильнее. */
    CURSE(2, 12, 100, false, true, true),
    /** Щит: поглощающий щит на 20 секунд. */
    SHIELD(1, 15, 400, false, false, true);

    public final int minThread;
    public final int energy;
    public final int cooldown;
    public final boolean onBlock;
    public final boolean onEntity;
    /** Боевое (иначе мирное). */
    public final boolean combat;

    Spell(int minThread, int energy, int cooldown, boolean onBlock, boolean onEntity, boolean combat) {
        this.onEntity = onEntity;
        this.minThread = minThread;
        this.energy = energy;
        this.cooldown = cooldown;
        this.onBlock = onBlock;
        this.combat = combat;
    }

    public String key() {
        return "chaosshard.spell." + name().toLowerCase();
    }

    public static Spell byIndex(int i) {
        Spell[] all = values();
        return i >= 0 && i < all.length ? all[i] : BOOK;
    }
}
