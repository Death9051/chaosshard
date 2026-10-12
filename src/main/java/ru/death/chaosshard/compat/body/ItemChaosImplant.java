package ru.death.chaosshard.compat.body;

import flaxbeard.cyberware.api.CyberwareAPI;
import flaxbeard.cyberware.api.item.ICyberware;
import flaxbeard.cyberware.common.item.ItemCyberware;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

/**
 * Импланты Хаоса для Robotic Parts. Один предмет, четыре вида (мета):
 * 0 — Ядро Хаоса (сердце, заменяет его), 1 — Приёмник Нити (череп), 2 — Жилы Хаоса (мышцы), 3 — Панцирь Скверны (кожа).
 * Конструктор ItemCyberware сам регистрирует предмет, поэтому создавать его нужно внутри события регистрации предметов.
 */
public class ItemChaosImplant extends ItemCyberware {

    public static final String NAME = "chaos_implant";
    public static final String[] SUBS = {"core", "receiver", "veins", "carapace"};
    public static final int CORE = 0, RECEIVER = 1, VEINS = 2, CARAPACE = 3;

    public ItemChaosImplant() {
        super(NAME, new ICyberware.EnumSlot[]{ICyberware.EnumSlot.HEART, ICyberware.EnumSlot.CRANIUM,
                ICyberware.EnumSlot.MUSCLE, ICyberware.EnumSlot.SKIN}, SUBS);
        setEssenceCost(6, 4, 5, 5);
        setWeights(1, 1, 1, 1);
    }

    /** Ядро Хаоса заменяет сердце: без него (или человеческого сердца) Осколок не живёт. */
    @Override
    public boolean isEssential(ItemStack stack) {
        return stack.getMetadata() == CORE;
    }

    /** Ядро несовместимо с другим сердцем (человеческим или кибер). */
    @Override
    public boolean isIncompatible(ItemStack stack, ItemStack other) {
        if (stack.getMetadata() != CORE) {
            return false;
        }
        ICyberware ware = CyberwareAPI.getCyberware(other);
        return ware != null && ware.isEssential(other);
    }

    @Override
    public int installedStackSize(ItemStack stack) {
        return 1;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
