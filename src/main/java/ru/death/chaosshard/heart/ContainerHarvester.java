package ru.death.chaosshard.heart;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Хранилище Жнеца: 2 ряда по 9, класть руками нельзя — только забирать. */
public class ContainerHarvester extends Container {

    public final TileHarvester tile;

    public ContainerHarvester(InventoryPlayer inv, TileHarvester tile) {
        this.tile = tile;
        for (int i = 0; i < TileHarvester.SLOTS; i++) {
            addSlotToContainer(new SlotItemHandler(tile.inventory, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 68 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inv, col, 8 + col * 18, 126));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.getDistanceSq(tile.getPos()) <= 64;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack() || index >= TileHarvester.SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (!mergeItemStack(stack, TileHarvester.SLOTS, inventorySlots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }
}
