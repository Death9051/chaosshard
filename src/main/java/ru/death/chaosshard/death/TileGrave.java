package ru.death.chaosshard.death;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Могила: чьи вещи и какие. Хранит сколько угодно стопок (и стопки больше 64). */
public class TileGrave extends TileEntity {

    private UUID owner;
    private String ownerName = "";
    private long time;
    private final List<ItemStack> items = new ArrayList<>();

    public void fill(EntityPlayer p, List<ItemStack> stacks) {
        owner = p.getUniqueID();
        ownerName = p.getName();
        time = world.getTotalWorldTime();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                items.add(s.copy());
            }
        }
        markDirty();
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public int count() {
        int n = 0;
        for (ItemStack s : items) {
            n += s.getCount();
        }
        return n;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Отдать всё игроку: броня — сразу на пустые места, остальное — в инвентарь (лишнее упадёт у ног). Сколько предметов. */
    public int giveTo(EntityPlayer p) {
        int n = 0;
        for (ItemStack s : items) {
            n += s.getCount();
            EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(s);
            if (slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR && p.getItemStackFromSlot(slot).isEmpty()) {
                p.setItemStackToSlot(slot, s);
            } else {
                ItemHandlerHelper.giveItemToPlayer(p, s);
            }
        }
        items.clear();
        markDirty();
        return n;
    }

    /** Забрать стопки (для выброса на землю, если могилу снесло). */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>(items);
        items.clear();
        markDirty();
        return out;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (owner != null) {
            tag.setUniqueId("Owner", owner);
        }
        tag.setString("OwnerName", ownerName);
        tag.setLong("Time", time);
        NBTTagList l = new NBTTagList();
        for (ItemStack s : items) {
            NBTTagCompound t = s.writeToNBT(new NBTTagCompound());
            t.setInteger("BigCount", s.getCount());   // Count в NBT — байт, а стопки модов бывают больше
            l.appendTag(t);
        }
        tag.setTag("Items", l);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        owner = tag.hasUniqueId("Owner") ? tag.getUniqueId("Owner") : null;
        ownerName = tag.getString("OwnerName");
        time = tag.getLong("Time");
        items.clear();
        NBTTagList l = tag.getTagList("Items", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            ItemStack s = new ItemStack(t);
            if (!s.isEmpty()) {
                if (t.hasKey("BigCount")) {
                    s.setCount(t.getInteger("BigCount"));
                }
                items.add(s);
            }
        }
    }
}
