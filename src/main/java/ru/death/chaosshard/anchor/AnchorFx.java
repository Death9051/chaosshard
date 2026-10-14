package ru.death.chaosshard.anchor;

/**
 * Местные эффекты специализаций — то, что Якорь может взять у чужой специализации (заимствование).
 * Фишки (генератор Горна, поле Жатвы) и ступени 5-го уровня сюда не входят. Порядок не менять.
 */
public enum AnchorFx {
    BASTION_GUARD(AnchorSpec.BASTION),
    ROOTS_FURY(AnchorSpec.ROOTS),
    PULSE_REGEN(AnchorSpec.PULSE),
    PATH_REGEN(AnchorSpec.PATH),
    WATCH_REVEAL(AnchorSpec.WATCH),
    CITADEL_STRIKE(AnchorSpec.CITADEL),
    CITADEL_FORCE(AnchorSpec.CITADEL),
    VEIN_LOOT(AnchorSpec.VEIN),
    NEST_GUARDS(AnchorSpec.NEST),
    NEST_ESCORT(AnchorSpec.NEST),
    FORGE_SPEED(AnchorSpec.FORGE);

    public final AnchorSpec spec;

    AnchorFx(AnchorSpec spec) {
        this.spec = spec;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static AnchorFx of(int i) {
        return i >= 0 && i < values().length ? values()[i] : null;
    }

    /** Есть ли этот эффект у своей специализации. */
    public static boolean own(AnchorSpec s, AnchorFx fx) {
        return s != null && fx.spec == s;
    }
}
