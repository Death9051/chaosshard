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
    CORRUPTION(2, 0, offer("chaosshard:chaos_essence", 0, 4), offer("chaosshard:memory_fragment", 0, 1));

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
            out.add(new ItemStack(o.item.get(), o.count, o.meta));
        }
        return out;
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
            if (r.matches(contents)) {
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
