package ru.death.chaosshard.heart;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.SlotItemHandler;

/** Окно Сердца: 4 гнезда модулей и инвентарь игрока. Тайминги нароста синхронизируются свойствами окна. */
public class ContainerHeart extends Container {

    public static final int GUI_W = 176;
    public static final int GUI_H = 230;
    public static final int INV_Y = 148;

    public final TileHeart heart;

    // Значения, пришедшие с сервера
    public int tier, timer, interval, growths, maxGrowths;
    private int lastTier = -1, lastTimer = -1, lastInterval = -1, lastGrowths = -1, lastMax = -1;
    private int tick;

    public ContainerHeart(InventoryPlayer inv, TileHeart heart) {
        this.heart = heart;
        for (int i = 0; i < TileHeart.MODULE_SLOTS; i++) {
            final int index = i;
            addSlotToContainer(new SlotItemHandler(heart.modules, i, 152, 20 + i * 20) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return index < heart.getTier() && super.isItemValid(stack);
                }

                @Override
                public boolean isEnabled() {
                    return index < heart.getTier() || getHasStack();
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inv, col, 8 + col * 18, INV_Y + 58));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !heart.isInvalid() && player.getDistanceSq(heart.getPos()) <= 64;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int t = heart.getTier(), tm = heart.getTimer(), iv = heart.interval(), mx = heart.maxGrowths();
        if (tick++ % 10 == 0 || lastGrowths < 0) {
            int g = heart.countGrowths();
            if (g != lastGrowths) {
                for (IContainerListener l : listeners) {
                    l.sendWindowProperty(this, 3, g);
                }
                lastGrowths = g;
            }
        }
        for (IContainerListener l : listeners) {
            if (t != lastTier) l.sendWindowProperty(this, 0, t);
            if (tm != lastTimer) l.sendWindowProperty(this, 1, tm);
            if (iv != lastInterval) l.sendWindowProperty(this, 2, iv);
            if (mx != lastMax) l.sendWindowProperty(this, 4, mx);
        }
        lastTier = t;
        lastTimer = tm;
        lastInterval = iv;
        lastMax = mx;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        switch (id) {
            case 0: tier = data; break;
            case 1: timer = data; break;
            case 2: interval = data; break;
            case 3: growths = data; break;
            case 4: maxGrowths = data; break;
            default: break;
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        int modules = TileHeart.MODULE_SLOTS;
        if (index < modules) {
            if (!mergeItemStack(stack, modules, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ItemHeartModule) {
            boolean moved = false;
            for (int i = 0; i < modules && !stack.isEmpty(); i++) {
                Slot m = inventorySlots.get(i);
                if (!m.isItemValid(stack)) {
                    continue;
                }
                ItemStack in = m.getStack();
                int limit = m.getItemStackLimit(stack);
                if (in.isEmpty()) {
                    m.putStack(stack.splitStack(Math.min(limit, stack.getCount())));
                    moved = true;
                } else if (ItemStack.areItemsEqual(in, stack) && ItemStack.areItemStackTagsEqual(in, stack) && in.getCount() < limit) {
                    int add = Math.min(limit - in.getCount(), stack.getCount());
                    ItemStack grown = in.copy();
                    grown.grow(add);
                    m.putStack(grown);
                    stack.shrink(add);
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else {
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
