package ru.death.chaosshard.trial;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Сокровища обломка: чем выше ступень ключа, тем богаче. */
public final class TrialLoot {

    public static List<ItemStack> roll(TrialData.Trial t, Random r) {
        int k = t.tier;
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(ModItems.VEIL_SHARD, 2 + r.nextInt(3) * k));
        out.add(new ItemStack(ModItems.WORLD_SHARD, 1 + r.nextInt(2) + k));
        out.add(new ItemStack(ModItems.CHAOS_ESSENCE, 2 + r.nextInt(3) * k));
        out.add(new ItemStack(Items.DIAMOND, k + r.nextInt(2)));
        out.add(new ItemStack(Items.GOLD_INGOT, 3 + r.nextInt(5)));
        out.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 * k));
        out.add(EnchantmentHelper.addRandomEnchantment(r, new ItemStack(Items.BOOK), 10 * k + 5, true));
        Item comp = Item.getByNameOrId("cyberware:component");
        if (comp != null) {
            for (int i = 0; i < 1 + k; i++) {
                out.add(new ItemStack(comp, 1 + r.nextInt(2), r.nextInt(10)));
            }
        }
        if (k >= 2) {
            out.add(new ItemStack(ModItems.DISTORTED_STARMETAL, 1 + r.nextInt(2)));
        }
        if (k >= 3 && r.nextInt(3) == 0) {
            Item seal = ModItems.SEALS.values().toArray(new Item[0])[r.nextInt(ModItems.SEALS.size())];
            out.add(new ItemStack(seal));
        }
        // иногда — ключ к следующему обломку
        if (r.nextBoolean()) {
            out.add(new ItemStack(ModItems.TRIAL_KEY));
        }
        return out;
    }

    private TrialLoot() {
    }
}
