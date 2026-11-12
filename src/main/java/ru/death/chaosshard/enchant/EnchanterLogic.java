package ru.death.chaosshard.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.compat.ApotheosisEnchant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Правила Стола Хаоса. Общие для сервера и клиента: клиент по ним рисует список и цены, сервер проверяет и платит.
 * <ul>
 * <li>Сила полок считается по тем же местам, что у ванильного стола, но без потолка в 15 и через
 * {@link ForgeHooks#getEnchantPower}, поэтому модовые полки (Apotheosis, Quark и любые другие) идут со своим весом.</li>
 * <li>Сила стола = 3 × сила полок. Уровень зачарования доступен, если его минимальная «сила» не выше силы стола:
 * 15 обычных полок дают 45 — это Острота V и Защита IV; уровни выше ванильных требуют полок сильнее.</li>
 * <li>С Apotheosis максимумы уровней и «сила» каждого уровня берутся из его характеристик зачарований
 * ({@link ApotheosisEnchant}), а потолки силы полок — из его настроек.</li>
 * </ul>
 */
public final class EnchanterLogic {

    /** Во сколько раз сила стола больше силы полок. */
    public static final float POWER_PER_SHELF = 3.0F;

    private EnchanterLogic() {
    }

    /**
     * Сила полок вокруг стола (места как у ванильного стола зачарований). С Apotheosis — по его правилам: обычные
     * полки (сила не больше 1) дают не больше {@code maxNormalPower}, всё вместе — не больше {@code maxPower}.
     */
    public static float shelfPower(World w, BlockPos pos) {
        float[] p = new float[2];
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                if ((z == 0 && x == 0) || !w.isAirBlock(pos.add(x, 0, z)) || !w.isAirBlock(pos.add(x, 1, z))) {
                    continue;
                }
                add(p, w, pos.add(x * 2, 0, z * 2));
                add(p, w, pos.add(x * 2, 1, z * 2));
                if (x != 0 && z != 0) {
                    add(p, w, pos.add(x * 2, 0, z));
                    add(p, w, pos.add(x * 2, 1, z));
                    add(p, w, pos.add(x, 0, z * 2));
                    add(p, w, pos.add(x, 1, z * 2));
                }
            }
        }
        return Math.min(ApotheosisEnchant.maxPower(), Math.min(p[0], ApotheosisEnchant.maxNormalPower()) + p[1]);
    }

    private static void add(float[] p, World w, BlockPos at) {
        float v = ForgeHooks.getEnchantPower(w, at);
        p[v > 1 ? 1 : 0] += v;
    }

    public static float tablePower(float shelves) {
        return shelves * POWER_PER_SHELF;
    }

    /** Наибольший уровень, который стол с такой силой может наложить; 0 — ни одного. */
    public static int maxLevelAt(Enchantment e, float power) {
        for (int k = maxLevel(e); k >= 1; k--) {
            if (powerFor(e, k) <= power) {
                return k;
            }
        }
        return 0;
    }

    /** Максимум уровня (с Apotheosis — его настройка, иначе — самого зачарования). */
    public static int maxLevel(Enchantment e) {
        return ApotheosisEnchant.maxLevel(e);
    }

    /** Сколько силы стола нужно для этого уровня. */
    public static int powerFor(Enchantment e, int level) {
        return Math.max(0, ApotheosisEnchant.minPower(e, level));
    }

    /** Книга: обычная, зачарованная или «типовая» книга Apotheosis (принимает зачарования своего вида). */
    public static boolean isBook(ItemStack s) {
        return s.getItem() instanceof ItemBook || s.getItem() == Items.ENCHANTED_BOOK;
    }

    /** Можно ли это зачарование вообще положить на предмет (без учёта совместимости). */
    public static boolean fits(Enchantment e, ItemStack s) {
        if (s.isEmpty() || s.getCount() != 1 || !e.isAllowedOnBooks() && isBook(s)) {
            return false;
        }
        if (s.getItem() == Items.BOOK || s.getItem() == Items.ENCHANTED_BOOK) {
            return true;
        }
        if (s.getItem() instanceof ItemBook) {
            return s.getItem().canApplyAtEnchantingTable(s, e);
        }
        return e.canApply(s);
    }

    /** Зачарования на предмете (у книг — записанные в них). */
    public static Map<Enchantment, Integer> on(ItemStack s) {
        return EnchantmentHelper.getEnchantments(s);
    }

    public static int currentLevel(ItemStack s, Enchantment e) {
        Integer l = EnchantmentHelper.getEnchantments(s).get(e);
        return l == null ? 0 : l;
    }

    /** Уже стоящее на предмете зачарование, с которым это несовместимо; null — конфликта нет. */
    public static Enchantment conflict(ItemStack s, Enchantment e) {
        for (Map.Entry<Enchantment, Integer> en : EnchantmentHelper.getEnchantments(s).entrySet()) {
            if (en.getKey() != e && !e.isCompatibleWith(en.getKey())) {
                return en.getKey();
            }
        }
        return null;
    }

    /** Сколько других зачарований уже на предмете (каждое добавляет уровень опыта к цене). */
    public static int others(ItemStack s, Enchantment e) {
        int n = 0;
        for (Enchantment ex : EnchantmentHelper.getEnchantments(s).keySet()) {
            if (ex != e) {
                n++;
            }
        }
        return n;
    }

    private static int base(Enchantment e, int level) {
        return level <= 0 ? 0 : Math.max(1, (int) Math.ceil(powerFor(e, level) / 3.0));
    }

    /** Цена в уровнях опыта: по «зачаровываемости» уровня, при повышении — только разница. */
    public static int xpCost(Enchantment e, int level, int current, int others) {
        return Math.max(1, base(e, level) - base(e, current)) + others;
    }

    /** Цена в лазурите: по лазуриту за каждый новый уровень. */
    public static int lapisCost(int level, int current) {
        return Math.max(1, level - current);
    }

    /** Лазурит за изучение — по редкости зачарования; сокровищные (Починка и подобные) — на половину дороже. */
    public static int researchLapis(Enchantment e) {
        int base;
        switch (e.getRarity()) {
            case COMMON:
                base = 8;
                break;
            case UNCOMMON:
                base = 16;
                break;
            case RARE:
                base = 32;
                break;
            default:
                base = 48;
                break;
        }
        return Math.min(64, e.isTreasureEnchantment() ? base * 3 / 2 : base);
    }

    /** Время изучения в тиках: 1–8 минут по редкости, полки ускоряют (15 полок — вдвое). */
    public static int researchTicks(Enchantment e, float shelves) {
        int sec;
        switch (e.getRarity()) {
            case COMMON:
                sec = 60;
                break;
            case UNCOMMON:
                sec = 120;
                break;
            case RARE:
                sec = 240;
                break;
            default:
                sec = 480;
                break;
        }
        return Math.max(200, Math.round(sec * 20 / (1.0F + Math.max(0, shelves) / 15.0F)));
    }

    /** Изучать за лазурит можно всё, кроме убранного из игры Apotheosis. */
    public static boolean researchable(Enchantment e) {
        return !ApotheosisEnchant.blacklisted(e);
    }

    /** Все зачарования игры по алфавиту ключей (порядок стабильный на обеих сторонах). */
    public static List<Enchantment> all() {
        List<Enchantment> list = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValuesCollection());
        list.removeIf(e -> e == null || e.getRegistryName() == null);
        list.sort(Comparator.comparing(e -> e.getRegistryName().toString()));
        return list;
    }

    public static Enchantment byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return ForgeRegistries.ENCHANTMENTS.getValue(new net.minecraft.util.ResourceLocation(id));
    }

    public static String id(Enchantment e) {
        return e.getRegistryName() == null ? "" : e.getRegistryName().toString();
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public static String roman(int n) {
        return n >= 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }
}
