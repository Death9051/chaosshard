package ru.death.chaosshard.spell;

import java.util.EnumMap;
import java.util.Map;

/**
 * Формы и перки заклинаний (docs/ЗАКЛИНАНИЯ.md).
 * Форма 0 открыта всегда; другие формы открывает перк с {@link Perk#form}, а если такого перка нет — форма открыта сразу.
 * Каждое заклинание получает по очку перков за уровень Нити.
 */
public final class SpellPerks {

    public static final class Perk {
        public final String id;
        public final int max;
        /** Номер формы, которую открывает перк, или -1. */
        public final int form;

        Perk(String id, int max, int form) {
            this.id = id;
            this.max = max;
            this.form = form;
        }

        public String key(Spell s) {
            return s.key() + ".perk." + id;
        }
    }

    private static final Map<Spell, String[]> FORMS = new EnumMap<>(Spell.class);
    private static final Map<Spell, Perk[]> PERKS = new EnumMap<>(Spell.class);

    static {
        def(Spell.SPROUT, new String[]{"point", "circle"},
                p("wide", 1, -1), p("humus", 1, -1), p("flow", 3, -1));
        def(Spell.BRIDGE, new String[]{"forward", "stairs", "platform"},
                p("stairs", 1, 1), p("platform", 1, 2), p("range", 2, -1), p("cheap", 1, -1));
        def(Spell.SPRING, new String[]{"source", "lake", "drain"},
                p("lava", 1, -1), p("lake", 1, 1), p("drain", 1, 2));
        def(Spell.RIFT, new String[]{"cube", "tunnel"},
                p("tunnel", 1, 1), p("depth", 2, -1), p("pull", 1, -1));
        def(Spell.DASH, new String[]{"forward", "leap"},
                p("rush", 1, -1), p("charge", 1, -1), p("range", 2, -1));
        def(Spell.BOLT, new String[]{"single", "chain", "blast"},
                p("chain", 2, 1), p("blast", 1, 2), p("sharp", 2, -1), p("economy", 1, -1));
        def(Spell.STARSIGHT, new String[]{"night", "hunt", "ores"},
                p("hunt", 1, 1), p("ores", 1, 2), p("radius", 2, -1), p("sort", 1, -1));
        def(Spell.FORM, new String[]{"one"},
                p("long", 2, -1), p("power", 2, -1), p("memory", 1, -1));
        def(Spell.RECALL, new String[]{"home", "citadel", "anchor"},
                p("fast", 2, -1), p("cheap", 1, -1));
    }

    private static Perk p(String id, int max, int form) {
        return new Perk(id, max, form);
    }

    private static void def(Spell s, String[] forms, Perk... perks) {
        FORMS.put(s, forms);
        PERKS.put(s, perks);
    }

    public static String[] forms(Spell s) {
        String[] f = FORMS.get(s);
        return f == null ? new String[]{"one"} : f;
    }

    public static Perk[] perks(Spell s) {
        Perk[] p = PERKS.get(s);
        return p == null ? new Perk[0] : p;
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

    private SpellPerks() {
    }
}
