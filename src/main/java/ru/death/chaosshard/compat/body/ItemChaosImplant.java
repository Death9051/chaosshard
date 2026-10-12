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
public class ItemChaosImplant extends ItemCyberware implements flaxbeard.cyberware.api.item.IMenuItem {

    public static final String NAME = "chaos_implant";
    public static final String[] SUBS = {"core", "receiver", "veins", "carapace", "warden", "eye", "lungs", "bones", "claws", "stride", "wither", "scales", "wings"};
    public static final int CORE = 0, RECEIVER = 1, VEINS = 2, CARAPACE = 3, WARDEN = 4, EYE = 5, LUNGS = 6, BONES = 7,
            CLAWS = 8, STRIDE = 9, WITHER = 10, SCALES = 11, WINGS = 12;

    public ItemChaosImplant() {
        super(NAME, new ICyberware.EnumSlot[]{ICyberware.EnumSlot.HEART, ICyberware.EnumSlot.CRANIUM,
                ICyberware.EnumSlot.MUSCLE, ICyberware.EnumSlot.SKIN, ICyberware.EnumSlot.CRANIUM, ICyberware.EnumSlot.EYES,
                ICyberware.EnumSlot.LUNGS, ICyberware.EnumSlot.BONE, ICyberware.EnumSlot.HAND, ICyberware.EnumSlot.LEG,
                ICyberware.EnumSlot.LOWER_ORGANS, ICyberware.EnumSlot.SKIN, ICyberware.EnumSlot.ARM}, SUBS);
        setEssenceCost(6, 4, 5, 5, 6, 4, 4, 5, 4, 4, 7, 6, 6);
        setWeights(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
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

    // ------------------------------------------------------------------ Око Хаоса включается в меню Cyberware

    @Override
    public boolean hasMenu(ItemStack stack) {
        return stack.getMetadata() == EYE;
    }

    @Override
    public void use(net.minecraft.entity.Entity entity, ItemStack stack) {
        flaxbeard.cyberware.api.item.EnableDisableHelper.toggle(stack);
    }

    @Override
    public String getUnlocalizedLabel(ItemStack stack) {
        return flaxbeard.cyberware.api.item.EnableDisableHelper.getUnlocalizedLabel(stack);
    }

    @Override
    public float[] getColor(ItemStack stack) {
        return flaxbeard.cyberware.api.item.EnableDisableHelper.isEnabled(stack)
                ? new float[]{0.75F, 0.45F, 1.0F} : null;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
