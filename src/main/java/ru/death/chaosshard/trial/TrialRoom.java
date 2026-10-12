package ru.death.chaosshard.trial;

/** Виды комнат обломка: полуширина и длина внутренности. */
public enum TrialRoom {
    ENTRY(4, 9), ARENA(7, 15), RUNES(5, 11), SEQUENCE(5, 11), HOLD(6, 13), TRAPS(1, 20), PARKOUR(3, 18),
    OFFERING(4, 9), GUARD(7, 15), TREASURE(4, 9);

    public final int half, length;

    TrialRoom(int half, int length) {
        this.half = half;
        this.length = length;
    }

    /** Комнаты-испытания, из которых собирается обломок. */
    public static final TrialRoom[] CHALLENGES = {ARENA, RUNES, SEQUENCE, HOLD, TRAPS, PARKOUR, OFFERING};

    public String key() {
        return "chaosshard.trial.room." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
