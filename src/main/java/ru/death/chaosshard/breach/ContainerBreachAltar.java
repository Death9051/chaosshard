package ru.death.chaosshard.breach;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Окно Алтаря Прорыва: 12 мест для подношений (3 ряда по 4) и сумка игрока. */
public class ContainerBreachAltar extends Container {

    public static final int SX = 14, SY = 34, PX = 14, PY = 118;

    public final TileBreachAltar tile;

    public ContainerBreachAltar(EntityPlayer player, TileBreachAltar tile) {
        this.tile = tile;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                addSlotToContainer(new SlotItemHandler(tile.inv, r * 4 + c, SX + c * 18, SY + r * 18) {
                    @Override
                    public boolean isItemValid(ItemStack stack) {
                        return !ru.death.chaosshard.ritual.AltarRituals.runningAt(tile.getPos());
                    }
                });
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlotToContainer(new Slot(player.inventory, c + r * 9 + 9, PX + c * 18, PY + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlotToContainer(new Slot(player.inventory, c, PX + c * 18, PY + 58));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return !tile.isInvalid() && p.getDistanceSq(tile.getPos()) <= 64;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack st = slot.getStack();
        ItemStack copy = st.copy();
        int altar = TileBreachAltar.SLOTS;
        if (index < altar) {
            if (!mergeItemStack(st, altar, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ru.death.chaosshard.ritual.AltarRituals.runningAt(tile.getPos()) || !mergeItemStack(st, 0, altar, false)) {
            return ItemStack.EMPTY;
        }
        if (st.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }
}
