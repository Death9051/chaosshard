package ru.death.chaosshard.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import ru.death.chaosshard.breach.Breach;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Стол Хаоса: место под предмет, место под лазурит и одно изучение зачарования за раз.
 * Изучение идёт, пока стол загружен; закончив, учит того, кто его начал (если он не в игре — при следующем открытии).
 */
public class TileChaosEnchanter extends TileEntity implements ITickable {

    public static final int SLOT_ITEM = 0;
    public static final int SLOT_LAPIS = 1;

    public final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_ITEM ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == SLOT_ITEM || isLapis(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    /** Что изучается ("" — ничего). */
    private String research = "";
    private int progress;
    private int total;
    private UUID owner;
    private boolean done;

    public static boolean isLapis(ItemStack s) {
        return s.getItem() == Items.DYE && s.getMetadata() == EnumDyeColor.BLUE.getDyeDamage();
    }

    public String research() {
        return research;
    }

    public boolean busy() {
        return !research.isEmpty();
    }

    public boolean done() {
        return done;
    }

    /** Доля пройденного изучения, 0..1. */
    public float part() {
        return total <= 0 ? 0 : Math.min(1F, progress / (float) total);
    }

    /** Осталось секунд. */
    public int secondsLeft() {
        return Math.max(0, (total - progress + 19) / 20);
    }

    public void start(EntityPlayerMP p, Enchantment e, int ticks) {
        research = EnchanterLogic.id(e);
        progress = 0;
        total = Math.max(1, ticks);
        owner = p.getUniqueID();
        done = false;
        markDirty();
    }

    public void cancel() {
        research = "";
        progress = 0;
        total = 0;
        owner = null;
        done = false;
        markDirty();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || research.isEmpty() || done) {
            return;
        }
        if (++progress >= total) {
            done = true;
            markDirty();
            EntityPlayerMP p = owner == null ? null : world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
            if (p != null) {
                claim(p);
            }
        } else if (progress % 100 == 0) {
            markDirty();
        }
    }

    /** Отдать законченное изучение тому, кто его начал. */
    public void claim(EntityPlayerMP p) {
        if (!done || owner == null || !owner.equals(p.getUniqueID())) {
            return;
        }
        Enchantment e = EnchanterLogic.byId(research);
        cancel();
        if (e != null && EnchantKnowledge.learn(p, e)) {
            p.sendMessage(Breach.grimoire("chaosshard.enchanter.learned", new TextComponentTranslation(e.getName())));
        }
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) inventory;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("inv", inventory.serializeNBT());
        if (!research.isEmpty()) {
            NBTTagCompound r = new NBTTagCompound();
            r.setString("id", research);
            r.setInteger("progress", progress);
            r.setInteger("total", total);
            r.setBoolean("done", done);
            if (owner != null) {
                r.setUniqueId("owner", owner);
            }
            tag.setTag("research", r);
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        inventory.deserializeNBT(tag.getCompoundTag("inv"));
        NBTTagCompound r = tag.getCompoundTag("research");
        research = r.getString("id");
        progress = r.getInteger("progress");
        total = r.getInteger("total");
        done = r.getBoolean("done");
        owner = r.hasUniqueId("owner") ? r.getUniqueId("owner") : null;
    }
}
