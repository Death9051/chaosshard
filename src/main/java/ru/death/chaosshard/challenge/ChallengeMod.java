package ru.death.chaosshard.challenge;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Модификаторы Вызова: игрок добавляет их подношениями на Алтарь, каждый — +25% к награде. */
public enum ChallengeMod {
    /** Враги быстрее. */
    FURY(Items.BLAZE_POWDER, 0),
    /** Враги крепче и в броне. */
    TEMPER(Item.getItemFromBlock(Blocks.IRON_BLOCK), 0),
    /** Пауза между волнами — 3 секунды. */
    RELENTLESS(Items.SUGAR, 0),
    /** Враги не светятся, на Якоре — ночь. */
    DARK(Items.DYE, 0),
    /** Здоровье само не восстанавливается. */
    BLOOD_PRICE(Items.REDSTONE, 0),
    /** Врагов в полтора раза больше. */
    HORDE(Items.BONE, 0),
    /** Убитые взрываются (не ломая блоков). */
    BLAST(Items.GUNPOWDER, 0),
    /** С первой волны за тобой ходит неуязвимый Преследователь. */
    HUNTER(Items.SPIDER_EYE, 0);

    public final Item item;
    public final int meta;

    ChallengeMod(Item item, int meta) {
        this.item = item;
        this.meta = meta;
    }

    public boolean is(ItemStack s) {
        return !s.isEmpty() && s.getItem() == item && (item != Items.DYE || s.getMetadata() == meta);
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static boolean has(int mods, ChallengeMod m) {
        return (mods & m.bit()) != 0;
    }

    public String key() {
        return "chaosshard.challenge.mod." + name().toLowerCase();
    }

    public static int count(int mods) {
        return Integer.bitCount(mods);
    }
}
