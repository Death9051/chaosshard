package ru.death.chaosshard.compat;

import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/** Baubles: надеть аксессуар в свободный подходящий слот (могила возвращает их сразу на тело). */
public final class BaublesCompat {

    private BaublesCompat() {
    }

    public static boolean loaded() {
        return Loader.isModLoaded("baubles");
    }

    /** Надеть, если это аксессуар и есть свободный подходящий слот. true — надет (стопка ушла целиком). */
    public static boolean equip(EntityPlayer p, ItemStack s) {
        return loaded() && Impl.equip(p, s);
    }

    /** Отдельный класс: классы Baubles грузятся только если мод есть. */
    private static final class Impl {
        static boolean equip(EntityPlayer p, ItemStack s) {
            if (s.isEmpty() || s.getCount() != 1) {
                return false;
            }
            IBauble b = s.hasCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null)
                    ? s.getCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null)
                    : s.getItem() instanceof IBauble ? (IBauble) s.getItem() : null;
            IBaublesItemHandler h = BaublesApi.getBaublesHandler(p);
            if (b == null || h == null || !b.canEquip(s, p)) {
                return false;
            }
            for (int i = 0; i < h.getSlots(); i++) {
                if (h.getStackInSlot(i).isEmpty() && h.isItemValidForSlot(i, s, p)) {
                    h.setStackInSlot(i, s.copy());
                    b.onEquipped(h.getStackInSlot(i), p);
                    return true;
                }
            }
            return false;
        }
    }
}
