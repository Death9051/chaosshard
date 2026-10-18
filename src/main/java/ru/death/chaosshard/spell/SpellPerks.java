package ru.death.chaosshard.spell;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ветки заклинаний (замечание №27, docs/ЗАКЛИНАНИЯ.md).
 * У каждого заклинания две ветки по три ступени: a1–a3 и b1–b3. Первая ступень выбирает ветку и закрывает другую;
 * ветка превращает заклинание в другое, следующие ступени его развивают. Цена ступеней — 1, 2, 2 очка
 * (очков на заклинание — уровень Нити, так что вся ветка — к Нити 5).
 * Формы (выбор режима по кругу) остались только у Зова Нити — там это выбор места.
 */
public final class SpellPerks {

    public static final class Perk {
        public final String id;
        public final int max;
        /** Номер формы, которую открывает перк, или -1. */
        public final int form;
        /** 0 — ветка A, 1 — ветка B. */
        public final int branch;
        /** Ступень 1–3. */
        public final int tier;

        Perk(String id, int branch, int tier, int form) {
            this.id = id;
            this.max = 1;
            this.branch = branch;
            this.tier = tier;
            this.form = form;
        }

        public String key(Spell s) {
            return s.key() + ".perk." + id;
        }

        /** Сколько очков стоит ступень. */
        public int cost() {
            return tier == 1 ? 1 : 2;
        }
    }

    private static final Map<Spell, String[]> FORMS = new EnumMap<>(Spell.class);
    private static final Perk[] STANDARD = {
            new Perk("a1", 0, 1, -1), new Perk("a2", 0, 2, -1), new Perk("a3", 0, 3, -1),
            new Perk("b1", 1, 1, -1), new Perk("b2", 1, 2, -1), new Perk("b3", 1, 3, -1)};
    /** Зов Нити: ветка B («Метка») открывает четвёртое место — метку. */
    private static final Perk[] RECALL = {
            new Perk("a1", 0, 1, -1), new Perk("a2", 0, 2, -1), new Perk("a3", 0, 3, -1),
            new Perk("b1", 1, 1, 3), new Perk("b2", 1, 2, -1), new Perk("b3", 1, 3, -1)};

    static {
        FORMS.put(Spell.RECALL, new String[]{"home", "citadel", "anchor", "mark"});
    }

    public static String[] forms(Spell s) {
        String[] f = FORMS.get(s);
        return f == null ? new String[]{"one"} : f;
    }

    public static Perk[] perks(Spell s) {
        if (s == Spell.BOOK) {
            return new Perk[0];
        }
        return s == Spell.RECALL ? RECALL : STANDARD;
    }

    public static int index(Spell s, String id) {
        Perk[] all = perks(s);
        for (int i = 0; i < all.length; i++) {
            if (all[i].id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Перк, открывающий форму, или null (форма открыта сразу). */
    public static Perk opener(Spell s, int form) {
        for (Perk p : perks(s)) {
            if (p.form == form) {
                return p;
            }
        }
        return null;
    }

    public static String formKey(Spell s, int form) {
        String[] f = forms(s);
        return s.key() + ".form." + f[Math.max(0, Math.min(form, f.length - 1))];
    }

    /** Ключ названия ветки: 0 — A, 1 — B. */
    public static String branchKey(Spell s, int branch) {
        return s.key() + ".branch." + (branch == 0 ? "a" : "b");
    }

    private SpellPerks() {
    }
}
