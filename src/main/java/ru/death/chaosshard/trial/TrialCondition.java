package ru.death.chaosshard.trial;

/** Условия обломка — то, чем запомнился погибший мир. */
public enum TrialCondition {
    /** Мир без света: ни ламп, ни ночного зрения. */
    DARK,
    /** Мир, где мёртвые встают: убитые твари иногда поднимаются снова. */
    UNDEAD,
    /** Мир голода: голод не отпускает. */
    HUNGER,
    /** Мир, где нельзя бежать: ни бега, ни спешки. */
    SLOW;

    public String key() {
        return "chaosshard.trial.cond." + name().toLowerCase();
    }

    public static boolean has(int mask, TrialCondition c) {
        return (mask & (1 << c.ordinal())) != 0;
    }
}
