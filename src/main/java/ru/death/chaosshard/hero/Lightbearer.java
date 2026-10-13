package ru.death.chaosshard.hero;

import ru.death.chaosshard.ritual.Ritual;

/**
 * Светочи — пять героев Ордена, каждый стережёт свою Опору Завесы. Проходятся строго по порядку.
 * Порядковый номер записан в мирах (имя постройки pillar_N, вехи) — только в конец.
 */
public enum Lightbearer {
    KAEL,
    IRENA,
    VELMOR,
    LIAN,
    ELIS;

    /** Готовые герои: Знамение остальных пока не работает. */
    public static final int READY = 2;

    public String id() {
        return name().toLowerCase();
    }

    public String structure() {
        return "pillar_" + ordinal();
    }

    /** Веха «Опора пала». */
    public String fallenMilestone() {
        return "pillar_" + id();
    }

    /** Веха «Знамение указало Опору». */
    public String omenMilestone() {
        return "omen_" + id();
    }

    public String nameKey() {
        return "entity.chaosshard.hero_" + id() + ".name";
    }

    public Lightbearer previous() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }

    public boolean ready() {
        return ordinal() < READY;
    }

    /** Ритуал Знамения этого героя. */
    public Ritual omen() {
        switch (this) {
            case IRENA: return Ritual.OMEN_IRENA;
            case VELMOR: return Ritual.OMEN_VELMOR;
            case LIAN: return Ritual.OMEN_LIAN;
            case ELIS: return Ritual.OMEN_ELIS;
            default: return Ritual.OMEN_KAEL;
        }
    }

    public static Lightbearer byOmen(Ritual r) {
        for (Lightbearer h : values()) {
            if (h.omen() == r) {
                return h;
            }
        }
        return null;
    }

    /** Герой по имени постройки pillar_N или null. */
    public static Lightbearer byStructure(String name) {
        if (name == null || !name.startsWith("pillar_")) {
            return null;
        }
        try {
            int i = Integer.parseInt(name.substring(7));
            return i >= 0 && i < values().length ? values()[i] : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
