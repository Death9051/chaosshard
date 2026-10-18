package ru.death.chaosshard.spell;

/**
 * Глифы — модификаторы заклинаний (замечание №27). Вкладываются в гнёзда заклинания на его странице в Гримуаре.
 * Каждый что-то даёт и что-то забирает. Номер в enum — метаданные предмета (только дописывать в конец).
 */
public enum SpellGlyph {
    /** Ширь: шире действие, дороже. */
    WIDE(0x8AD0FF),
    /** Мощь: сильнее, дольше перезарядка. */
    POTENT(0xFF7A5A),
    /** Бережливость: дешевле, чуть слабее. */
    THRIFT(0x9FE07A),
    /** Спешка: перезарядка вдвое короче, дороже. */
    HASTE(0xFFE070),
    /** Эхо: шанс, что заклинание повторится, дороже. */
    ECHO(0xC890FF),
    /** Кровь: часть цены — здоровьем, чуть сильнее. */
    BLOOD(0xD03040),
    /** Накал: разогрев вдвое быстрее, чуть дороже. */
    KINDLING(0xFF9A30);

    public final int color;

    SpellGlyph(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase();
    }

    public String key() {
        return "chaosshard.glyph." + id();
    }

    public static SpellGlyph byMeta(int meta) {
        SpellGlyph[] all = values();
        return meta >= 0 && meta < all.length ? all[meta] : null;
    }

    /** Подходит ли глиф заклинанию (там, где он ничего бы не менял, — нет). */
    public boolean fits(Spell s) {
        switch (this) {
            case WIDE:
                return s == Spell.SPROUT || s == Spell.RIFT || s == Spell.STARSIGHT || s == Spell.BRIDGE || s == Spell.SPRING || s == Spell.BOLT;
            case POTENT:
                return s == Spell.SPROUT || s == Spell.SPRING || s == Spell.STARSIGHT || s == Spell.DASH || s == Spell.BOLT || s == Spell.FORM;
            case ECHO:
                return s != Spell.RECALL && s != Spell.FORM && s != Spell.DASH && s != Spell.STARSIGHT;
            default:
                return s != Spell.BOOK;
        }
    }
}
