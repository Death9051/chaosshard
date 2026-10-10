package ru.death.chaosshard.village;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.Random;
import java.util.UUID;

/** Дань деревни: раз в день складывается в Знак (или в соседний сундук). */
public class TilePatronSigil extends TileEntity implements ITickable {

    private final ItemStackHandler stored = new ItemStackHandler(9);
    private UUID owner;
    private int timer;

    public void setOwner(EntityPlayerMP player) {
        owner = player.getUniqueID();
        markDirty();
    }

    @Override
    public void update() {
        if (world.isRemote || ++timer < ChaosConfig.order.tributeInterval) {
            return;
        }
        timer = 0;
        Village v = world.getVillageCollection().getNearestVillage(pos, 32);
        if (v == null) {
            return;
        }
        int doors = Math.max(1, v.getNumVillageDoors());
        Random r = world.rand;
        ItemStack[] tribute = {
                new ItemStack(Items.EMERALD, 1 + r.nextInt(1 + doors / 4)),
                new ItemStack(Items.BREAD, 3 + r.nextInt(4)),
                new ItemStack(r.nextBoolean() ? Items.WHEAT : (r.nextBoolean() ? Items.CARROT : Items.POTATO), 4 + r.nextInt(8)),
                r.nextInt(3) == 0 ? new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(3)) : ItemStack.EMPTY
        };
        for (ItemStack t : tribute) {
            if (!t.isEmpty()) {
                ItemStack left = pushToNeighbour(t);
                if (!left.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(stored, left, false);
                }
            }
        }
        markDirty();
        if (owner != null && world.getMinecraftServer() != null) {
            EntityPlayerMP p = world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
            if (p != null) {
                ThreadManager.addPoints(p, ChaosConfig.order.tributeThread);
                p.sendStatusMessage(Breach.grimoire("chaosshard.village.tribute", pos.getX(), pos.getZ()), true);
            }
        }
    }

    private ItemStack pushToNeighbour(ItemStack stack) {
        for (EnumFacing f : EnumFacing.VALUES) {
            TileEntity te = world.getTileEntity(pos.offset(f));
            if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite())) {
                IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite());
                if (h != null) {
                    stack = ItemHandlerHelper.insertItemStacked(h, stack, false);
                    if (stack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return stack;
    }

    public void giveTo(EntityPlayer player) {
        boolean any = false;
        for (int i = 0; i < stored.getSlots(); i++) {
            ItemStack s = stored.extractItem(i, 64, false);
            if (!s.isEmpty()) {
                any = true;
                ItemHandlerHelper.giveItemToPlayer(player, s);
            }
        }
        if (!any) {
            int left = Math.max(0, (ChaosConfig.order.tributeInterval - timer) / 20);
            player.sendMessage(Breach.grimoire("chaosshard.village.empty", left / 60));
        }
        markDirty();
    }

    public void dropAll() {
        for (int i = 0; i < stored.getSlots(); i++) {
            ItemStack s = stored.getStackInSlot(i);
            if (!s.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), s);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("stored", stored.serializeNBT());
        tag.setInteger("timer", timer);
        if (owner != null) {
            tag.setUniqueId("owner", owner);
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        stored.deserializeNBT(tag.getCompoundTag("stored"));
        timer = tag.getInteger("timer");
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
