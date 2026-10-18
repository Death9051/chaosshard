package ru.death.chaosshard.ritual;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ритуалы Хаоса. Круг: Чаша в центре и 8 знаков мела на расстоянии 2 (по сторонам и диагоналям).
 * В Чашу кладут подношения, ПКМ Гримуаром по Чаше — ритуал начинается.
 * Набор подношений должен совпасть точно (порядок не важен).
 */
public enum Ritual {
    /** Рост: растения вокруг растут сами, 10 минут. */
    GROWTH(0, 20 * 60 * 10, offer("minecraft:dye", 15, 8), offer("chaosshard:chaos_essence", 0, 1)),
    /** Небо: смена погоды (ясно ↔ дождь). */
    SKY(0, 0, offer("chaosshard:chaos_essence", 0, 1), offer("chaosshard:veil_shard", 0, 1)),
    /** Оберег: враждебные твари не появляются в радиусе 32 игровые сутки. */
    WARD(1, 24000, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:iron_ingot", 0, 2)),
    /** Буря Хаоса: 30 секунд молнии бьют по враждебным тварям вокруг. */
    STORM(1, 20 * 30, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:gunpowder", 0, 2)),
    /** Зов тварей: через миг появляются три враждебные твари — ради добычи. */
    SUMMON(1, 0, offer("chaosshard:chaos_essence", 0, 1), offer("minecraft:rotten_flesh", 0, 4), offer("minecraft:bone", 0, 2)),
    /** Порыв Скверны: ближайший Якорь (до 48 блоков) разом оскверняет округу. */
    CORRUPTION(2, 0, offer("chaosshard:chaos_essence", 0, 4), offer("chaosshard:world_shard", 0, 1)),
    /** Призыв: Скорлупа Хаоса — слуга из песчаного мертвеца. */
    SERVANT_HUSK(0, 0, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:rotten_flesh", 0, 8)),
    /** Призыв: Костяной страж. */
    SERVANT_BONE(2, 0, offer("chaosshard:chaos_essence", 0, 3), offer("minecraft:bone", 0, 8), offer("minecraft:coal", 0, 4)),
    /** Призыв: Палач Хаоса. */
    SERVANT_EXECUTIONER(3, 0, offer("chaosshard:chaos_essence", 0, 4), offer("minecraft:iron_axe", 0, 1), offer("minecraft:emerald", 0, 4)),
    /** Уникальный слуга: Заяц-добытчик. */
    SERVANT_HARE(1, 0, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:rabbit_foot", 0, 1), offer("minecraft:carrot", 0, 8)),
    /** Уникальный слуга: Кошка. */
    SERVANT_CAT(2, 0, offer("chaosshard:chaos_essence", 0, 3), offer("minecraft:fish", 0, 8), offer("minecraft:string", 0, 4)),
    /** Уникальный слуга: Тигр. */
    SERVANT_TIGER(4, 0, offer("chaosshard:chaos_essence", 0, 6), offer("minecraft:blaze_rod", 0, 4), offer("minecraft:beef", 0, 8)),
    /** Добытчик: Жнец. */
    SERVANT_REAPER(1, 0, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:wheat_seeds", 0, 16), offer("minecraft:stone_hoe", 0, 1)),
    /** Добытчик: Лесоруб. */
    SERVANT_LUMBERJACK(1, 0, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:sapling", -1, 8), offer("minecraft:stone_axe", 0, 1)),
    /** Добытчик: Рудокоп. */
    SERVANT_MINER(2, 0, offer("chaosshard:chaos_essence", 0, 3), offer("minecraft:stone_pickaxe", 0, 1), offer("minecraft:torch", 0, 16)),

    // ---------------------------------------------------------------- младшие (Чаша); только в конец — номера записаны в мирах
    /** Сумерки: день сменяется ночью, ночь — рассветом. */
    DUSK(0, 0, offer("chaosshard:chaos_essence", 0, 1), offer("minecraft:coal", -1, 4)),
    /** Приплод: звери в 12 блоках от Чаши готовы к приплоду. */
    BREED(1, 0, offer("chaosshard:chaos_essence", 0, 1), offer("minecraft:wheat", 0, 8), offer("minecraft:carrot", 0, 8)),
    /** Тяга (сфера): 5 минут вещи и опыт в 12 блоках стягиваются к сфере, а под ней — в сундук. */
    PULL(1, 20 * 60 * 5, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:ender_pearl", 0, 1), offer("minecraft:iron_ingot", 0, 2)),

    // ---------------------------------------------------------------- старшие (Алтарь Прорыва и 4 шпиля)
    /** Взор Нити: твари в 96 блоках светятся минуту; Гримуар называет ближайшие постройки и святыни Ордена. */
    SIGHT(2, 0, offer("chaosshard:chaos_essence", 0, 4), offer("minecraft:ender_eye", 0, 2), offer("chaosshard:veil_shard", 0, 2)),
    /** Зов из Нити: все звери, ушедшие в Нить, возвращаются сразу. */
    RECALL(2, 0, offer("chaosshard:chaos_essence", 0, 4), offer("minecraft:golden_apple", 0, 1), offer("chaosshard:veil_shard", 0, 2)),
    /** Кровавая луна: 5 минут вокруг Алтаря лезут твари, а убитые роняют Эссенцию Хаоса и вдвое больше опыта. */
    BLOOD_MOON(3, 20 * 60 * 5, offer("chaosshard:chaos_essence", 0, 6), offer("minecraft:redstone", 0, 16), offer("minecraft:bone", 0, 16)),
    /** Великий порыв: каждый твой Якорь в 256 блоках разом оскверняет округу. */
    GREAT_SURGE(4, 0, offer("chaosshard:chaos_essence", 0, 8), offer("chaosshard:world_shard", 0, 2), offer("chaosshard:veil_shard", 0, 4)),
    /** Вызов: состав собирается из частей (см. ChallengeSpec); в списке Гримуара — основа подношений. */
    CHALLENGE(3, 0, offer("chaosshard:chaos_essence", 0, 4), offer("chaosshard:veil_shard", 0, 2)),

    // ---------------------------------------------------------------- Знамения Опор (Акт 4, старшие)
    // Опора Светоча встаёт в ещё не созданных землях, Гримуар называет координаты. Строго по порядку.
    /** Знамение Каэля. */
    OMEN_KAEL(3, 0, offer("chaosshard:chaos_essence", 0, 6), offer("chaosshard:veil_shard", 0, 3),
            offer("chaosshard:world_shard", 0, 2), offer("minecraft:golden_sword", 0, 1)),
    /** Знамение Ирены. */
    OMEN_IRENA(3, 0, offer("chaosshard:chaos_essence", 0, 8), offer("chaosshard:veil_shard", 0, 4),
            offer("chaosshard:world_shard", 0, 3), offer("minecraft:speckled_melon", 0, 4)),
    /** Знамение Вельмора. */
    OMEN_VELMOR(4, 0, offer("chaosshard:chaos_essence", 0, 10), offer("chaosshard:veil_shard", 0, 4),
            offer("chaosshard:world_shard", 0, 3), offer("minecraft:ender_pearl", 0, 8)),
    /** Знамение Лиана. */
    OMEN_LIAN(4, 0, offer("chaosshard:chaos_essence", 0, 12), offer("chaosshard:veil_shard", 0, 5),
            offer("chaosshard:world_shard", 0, 4), offer("minecraft:noteblock", 0, 4)),
    /** Знамение Элис. */
    OMEN_ELIS(5, 0, offer("chaosshard:chaos_essence", 0, 16), offer("chaosshard:veil_shard", 0, 6),
            offer("chaosshard:world_shard", 0, 5), offer("minecraft:golden_horse_armor", 0, 1)),

    // ---------------------------------------------------------------- на добыче с Ордена (Reliquary); только в конец
    /** Порченая святость (сфера): ближайшая святыня Ордена в 64 блоках осквернена. Святая вода — с рыцарей Ордена. */
    HOLY_DEFILE(2, 0, offer("xreliquary:glowing_water", 0, 2), offer("chaosshard:chaos_essence", 0, 2)),
    /** Падшее перо (сфера): мои слуги в 32 блоках — Скорость II, Сопротивление I и Регенерация I на 5 минут. */
    FALLEN_FEATHER(3, 0, offer("xreliquary:angelic_feather", 0, 1), offer("chaosshard:chaos_essence", 0, 3)),

    // ---------------------------------------------------------------- Малые планы; только в конец
    /** Нюх Нити (сфера): в Незере — ближайшая крепость, в Крае — город, в остальных мирах — крепость с порталом в Край. */
    SCENT(1, 0, offer("chaosshard:chaos_essence", 0, 2), offer("minecraft:ender_eye", 0, 1), offer("minecraft:blaze_powder", 0, 1)),

    // ---------------------------------------------------------------- Акт 5; только в конец
    /** Знамение Короны (старший, сфера): столица встаёт в нетронутых землях, Гримуар называет координаты. */
    OMEN_CROWN(5, 0, offer("chaosshard:order_seal", 0, 3), offer("chaosshard:chaos_essence", 0, 16),
            offer("chaosshard:world_shard", 0, 6), offer("chaosshard:veil_shard", 0, 6)),

    // ---------------------------------------------------------------- Заклинания; только в конец
    /** Чистые страницы (сфера): сбросить все перки заклинаний — очки вернутся. */
    SPELL_RESET(1, 0, offer("chaosshard:chaos_essence", 0, 4), offer("minecraft:paper", 0, 8), offer("minecraft:book", 0, 1)),
    /** Закалка (сфера): +300 опыта материалам Хаоса инструмента во второй руке. */
    MATERIAL_TEMPER(2, 0, offer("chaosshard:chaos_essence", 0, 8), offer("minecraft:blaze_powder", 0, 2), offer("minecraft:iron_ingot", 0, 2)),

    // ---------------------------------------------------------------- Якоря; только в конец
    /** Перекройка (сфера): на земле своего Якоря — сбросить его специализацию (−25% опыта Якоря). */
    ANCHOR_RESHAPE(2, 0, offer("chaosshard:chaos_essence", 0, 12), offer("chaosshard:veil_shard", 0, 4), offer("minecraft:ender_pearl", 0, 2)),
    /** Эволюция (сфера): на земле своего Якоря 5-го уровня — сделать его Альфой. */
    ANCHOR_ALPHA(4, 0, offer("chaosshard:chaos_essence", 0, 32), offer("chaosshard:world_shard", 0, 8), offer("chaosshard:veil_shard", 0, 8),
            offer("minecraft:nether_star", 0, 1)),

    // ---------------------------------------------------------------- Смерть; только в конец
    /** Возврат (сфера): вещи, выпавшие при последних смертях, исчезают с места гибели и приходят ко мне. */
    RECLAIM(1, 0, offer("chaosshard:chaos_essence", 0, 4), offer("minecraft:ender_pearl", 0, 2), offer("minecraft:gold_ingot", 0, 2));

    /** Старшие ритуалы — на Алтаре Прорыва, а не в Чаше. */
    public boolean isAltar() {
        return this == SIGHT || this == RECALL || this == BLOOD_MOON || this == GREAT_SURGE || this == CHALLENGE
                || isOmen() || this == OMEN_CROWN || this == ANCHOR_ALPHA;
    }

    /** Знамение Опоры (Акт 4). */
    public boolean isOmen() {
        return this == OMEN_KAEL || this == OMEN_IRENA || this == OMEN_VELMOR || this == OMEN_LIAN || this == OMEN_ELIS;
    }

    /** Старший ритуал по подношениям, брошенным на Алтарь. */
    public static Ritual findAltar(List<ItemStack> contents) {
        for (Ritual r : values()) {
            if (r.isAltar() && r != CHALLENGE && r.matches(contents)) {
                return r;
            }
        }
        return null;
    }

    /** Ритуал призыва слуги этого типа. */
    public static Ritual summonFor(ru.death.chaosshard.servant.Servants.Kind k) {
        switch (k) {
            case HUSK: return SERVANT_HUSK;
            case BONE: return SERVANT_BONE;
            case EXECUTIONER: return SERVANT_EXECUTIONER;
            case HARE: return SERVANT_HARE;
            case CAT: return SERVANT_CAT;
            case TIGER: return SERVANT_TIGER;
            case REAPER: return SERVANT_REAPER;
            case LUMBERJACK: return SERVANT_LUMBERJACK;
            default: return SERVANT_MINER;
        }
    }



    /** Ритуалы с эффектом на месте: запечатываются в сферу, которую ставят, где нужно. */
    public static final Ritual[] SPHERES = {GROWTH, WARD, STORM, CORRUPTION, PULL,
            DUSK, BREED, SIGHT, RECALL, BLOOD_MOON, GREAT_SURGE,
            OMEN_KAEL, OMEN_IRENA, OMEN_VELMOR, OMEN_LIAN, OMEN_ELIS, HOLY_DEFILE, FALLEN_FEATHER, SCENT, OMEN_CROWN, SPELL_RESET, MATERIAL_TEMPER, ANCHOR_RESHAPE, ANCHOR_ALPHA, RECLAIM};

    /** Номер сферы (метаданные предмета) или -1. */
    public int sphereIndex() {
        for (int i = 0; i < SPHERES.length; i++) {
            if (SPHERES[i] == this) {
                return i;
            }
        }
        return -1;
    }

    public boolean isSphere() {
        return sphereIndex() >= 0;
    }

    public static Ritual sphere(int meta) {
        return meta >= 0 && meta < SPHERES.length ? SPHERES[meta] : GROWTH;
    }

    /** Все подношения есть в игре (ритуалы на предметах других модов без этих модов не показываются и не идут). */
    public boolean available() {
        for (Offering o : offerings) {
            if (o.item.get() == Items.AIR) {
                return false;
            }
        }
        return true;
    }

    public boolean isSummon() {
        return this == SERVANT_HUSK || this == SERVANT_BONE || this == SERVANT_EXECUTIONER
                || this == SERVANT_HARE || this == SERVANT_CAT || this == SERVANT_TIGER
                || this == SERVANT_REAPER || this == SERVANT_LUMBERJACK || this == SERVANT_MINER;
    }

    public final int minThread;
    /** Длительность в тиках; 0 — мгновенный. */
    public final int duration;
    private final Offering[] offerings;

    Ritual(int minThread, int duration, Offering... offerings) {
        this.minThread = minThread;
        this.duration = duration;
        this.offerings = offerings;
    }

    public String key() {
        return "chaosshard.ritual." + name().toLowerCase();
    }

    public List<ItemStack> offeringStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (Offering o : offerings) {
            out.add(new ItemStack(o.item.get(), o.count, Math.max(0, o.meta)));
        }
        return out;
    }

    public int offeringCount() {
        return offerings.length;
    }

    /** Сколько нужно i-го подношения. */
    public int offeringNeed(int i) {
        return offerings[i].count;
    }

    /** Подходит ли стопка как i-е подношение (вариант -1 — любой). */
    public boolean offeringMatches(int i, ItemStack s) {
        Offering o = offerings[i];
        return !s.isEmpty() && s.getItem() == o.item.get() && (o.meta < 0 || s.getMetadata() == o.meta);
    }

    /** Совпадает ли содержимое Чаши с подношениями ритуала. */
    public boolean matches(List<ItemStack> contents) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : contents) {
            if (!s.isEmpty()) {
                left.add(s.copy());
            }
        }
        for (Offering o : offerings) {
            int need = o.count;
            for (ItemStack s : left) {
                if (need > 0 && s.getItem() == o.item.get() && (o.meta < 0 || s.getMetadata() == o.meta)) {
                    int take = Math.min(need, s.getCount());
                    s.shrink(take);
                    need -= take;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        for (ItemStack s : left) {
            if (!s.isEmpty()) {
                return false;   // лишнее в Чаше
            }
        }
        return true;
    }

    public static Ritual find(List<ItemStack> contents) {
        for (Ritual r : values()) {
            if (!r.isAltar() && r.available() && r.matches(contents)) {
                return r;
            }
        }
        return null;
    }

    public static Ritual byIndex(int i) {
        Ritual[] all = values();
        return i >= 0 && i < all.length ? all[i] : null;
    }

    // ------------------------------------------------------------------ подношения

    static final class Offering {
        final java.util.function.Supplier<Item> item;
        final int meta;
        final int count;

        Offering(java.util.function.Supplier<Item> item, int meta, int count) {
            this.item = item;
            this.meta = meta;
            this.count = count;
        }
    }

    /** Предметы ищутся по имени при использовании: enum может загрузиться раньше реестра предметов. */
    private static Offering offer(String id, int meta, int count) {
        return new Offering(() -> {
            Item item = Item.getByNameOrId(id);
            return item == null ? Items.AIR : item;
        }, meta, count);
    }

    @SuppressWarnings("unused")
    private static List<Ritual> all() {
        return Arrays.asList(values());
    }
}
