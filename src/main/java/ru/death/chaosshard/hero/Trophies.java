package ru.death.chaosshard.hero;

import net.minecraft.item.ItemStack;
import ru.death.chaosshard.init.ModItems;

/** Трофеи Светочей — падают к ногам павшего героя. */
public final class Trophies {

    public static ItemStack of(Lightbearer h) {
        switch (h) {
            case KAEL: return ModItems.KAEL_BLADE == null ? ItemStack.EMPTY : new ItemStack(ModItems.KAEL_BLADE);
            case LIAN: return ModItems.LIAN_LUTE == null ? ItemStack.EMPTY : new ItemStack(ModItems.LIAN_LUTE);
            case ELIS: return ModItems.ELIS_SIGNET == null ? ItemStack.EMPTY : new ItemStack(ModItems.ELIS_SIGNET);
            case VELMOR: return ModItems.VELMOR_STAFF == null ? ItemStack.EMPTY : new ItemStack(ModItems.VELMOR_STAFF);
            case IRENA: return ModItems.IRENA_ROSARY == null ? ItemStack.EMPTY : new ItemStack(ModItems.IRENA_ROSARY);
            default: return ItemStack.EMPTY;
        }
    }

    private Trophies() {
    }
}
