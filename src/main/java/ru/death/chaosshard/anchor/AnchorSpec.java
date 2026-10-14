package ru.death.chaosshard.anchor;

/**
 * Специализации Якоря. Выбор на 3-м уровне, вторая ступень — на 5-м.
 * Сетевые: два эффекта на всю сеть и один на свою землю; местные — наоборот.
 * Порядок не менять: хранится номером.
 */
public enum AnchorSpec {
    BASTION(true, 0),
    ROOTS(true, 0),
    PULSE(true, 0),
    PATH(true, 0),
    WATCH(true, 0),
    CITADEL(false, 0),
    VEIN(false, 9),
    NEST(false, 0),
    FORGE(false, 0),
    HARVEST(false, 27);

    /** Сетевая (иначе местная). */
    public final boolean network;
    /** Мест в хранилище Якоря (0 — без хранилища). */
    public final int storage;

    AnchorSpec(boolean network, int storage) {
        this.network = network;
        this.storage = storage;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static AnchorSpec of(int i) {
        return i >= 0 && i < values().length ? values()[i] : null;
    }

    /** Уровень, на котором выбирается специализация. */
    public static final int CHOOSE_LEVEL = 3;
    /** Уровень второй ступени. */
    public static final int STAGE_LEVEL = 5;
}
