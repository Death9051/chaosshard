package ru.death.chaosshard.breach;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import ru.death.chaosshard.ritual.AltarRituals;
import ru.death.chaosshard.ritual.Ritual;
import ru.death.chaosshard.ritual.TileRitualBowl;

import java.util.ArrayList;
import java.util.List;

/**
 * Подношения на Алтаре Прорыва: 12 мест. Класть можно ПКМ предметом (Shift — всю стопку), в окне Алтаря
 * или кнопкой «Сложить на Алтарь» в Гримуаре. Брошенные на Алтарь предметы по-прежнему тоже идут в дело.
 */
public class TileBreachAltar extends TileEntity implements ITickable {

    public static final int SLOTS = 12;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    public final ItemStackHandler inv = new ItemStackHandler(items) {
        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };
    private int age;

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    public void clear() {
        items.clear();
        changed();
    }

    public void insert(EntityPlayer player, ItemStack held, boolean all) {
        if (AltarRituals.runningAt(pos)) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.ritual.busy"), true);
            return;
        }
        ItemStack rest = held.copy();
        rest.setCount(all ? held.getCount() : 1);
        int want = rest.getCount();
        for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
            rest = inv.insertItem(i, rest, false);
        }
        int put = want - rest.getCount();
        if (put > 0) {
            if (!player.isCreative()) {
                held.shrink(put);
            }
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.4F, 0.8F);
        }
    }

    public void takeLast(EntityPlayer player) {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, items.get(i));
                items.set(i, ItemStack.EMPTY);
                changed();
                return;
            }
        }
    }

    /** Кнопка Гримуара «Сложить на Алтарь». */
    public boolean fill(EntityPlayer player, Ritual r) {
        if (AltarRituals.runningAt(pos)) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.ritual.busy"), true);
            return false;
        }
        boolean ok = TileRitualBowl.fillSlots(player, r, items, world, pos, "chaosshard.magic.fill.ready.altar");
        changed();
        return ok;
    }

    public void dropAll() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 1.0, pos.getZ(), s);
            }
        }
        items.clear();
    }

    @Override
    public void update() {
        age++;
    }

    public int getAge() {
        return age;
    }

    void changed() {
        if (world == null) {
            return;
        }
        markDirty();
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Offerings", inv.serializeNBT());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readItems(tag);
    }

    private void readItems(NBTTagCompound tag) {
        items.clear();
        if (tag.hasKey("Offerings")) {
            NBTTagCompound inner = tag.getCompoundTag("Offerings");
            net.minecraft.nbt.NBTTagList list = inner.getTagList("Items", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                int slot = t.getInteger("Slot");
                if (slot >= 0 && slot < SLOTS) {
                    items.set(slot, new ItemStack(t));
                }
            }
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readItems(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readItems(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World w, net.minecraft.util.math.BlockPos p, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
