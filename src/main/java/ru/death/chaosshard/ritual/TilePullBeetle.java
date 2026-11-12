package ru.death.chaosshard.ritual;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Жучок Тяги: раз в 4 тика тянет вещи и опыт в {@link #RADIUS} блоках, вещи кладёт в хранилище под собой.
 * Опыт копит в себе и раз в {@link ItemXpPouch#PACK_TICKS} сворачивает всё накопленное в Мешочек опыта.
 */
public class TilePullBeetle extends TileEntity implements ITickable {

    public static final int RADIUS = 16;

    private int xp;

    /** Накопленный, ещё не свёрнутый опыт. */
    public int storedXp() {
        return xp;
    }

    /** Свернуть накопленное сейчас (для теста и при смене соседей). */
    public void packNow() {
        IItemHandler inv = inventory();
        int was = xp;
        xp = ItemXpPouch.pack(inv, xp);
        if (xp != was) {
            changed();
        }
    }

    private IItemHandler inventory() {
        TileEntity below = world.getTileEntity(pos.down());
        return below == null ? null : below.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
    }

    /** Жучка сломали: несвёрнутый опыт рассыпается сферами. */
    public void releaseXp() {
        int left = xp;
        xp = 0;
        while (left > 0) {
            int part = EntityXPOrb.getXPSplit(left);
            left -= part;
            world.spawnEntity(new EntityXPOrb(world, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, part));
        }
    }

    /** Опыт изменился: сохранить и показать клиенту (брюшко раздувается). */
    private void changed() {
        markDirty();
        IBlockState st = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, st, st, 2);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Xp", xp);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        xp = tag.getInteger("Xp");
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % 4 != (pos.toLong() & 3)) {
            return;
        }
        IItemHandler inv = inventory();
        if (inv == null) {
            return;
        }
        if (xp > 0 && world.getTotalWorldTime() % ItemXpPouch.PACK_TICKS < 4) {
            packNow();
        }
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.3, cz = pos.getZ() + 0.5;
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(RADIUS);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box, x -> x instanceof EntityItem || x instanceof EntityXPOrb)) {
            if (!e.isEntityAlive()) {
                continue;
            }
            double dx = cx - e.posX, dy = cy - e.posY, dz = cz - e.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1.3 && e instanceof EntityXPOrb) {
                xp += ((EntityXPOrb) e).getXpValue();
                e.setDead();
                changed();
                continue;
            }
            if (d < 1.3 && e instanceof EntityItem) {
                EntityItem ei = (EntityItem) e;
                ItemStack rest = ItemHandlerHelper.insertItemStacked(inv, ei.getItem().copy(), false);
                if (rest.getCount() != ei.getItem().getCount()) {
                    world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.2F, 1.6F);
                }
                if (rest.isEmpty()) {
                    ei.setDead();
                } else {
                    ei.setItem(rest);
                }
                continue;
            }
            if (d > 0.7) {
                double sp = Math.min(0.35, 0.14 + d * 0.01);
                e.motionX = dx / d * sp;
                e.motionY = dy / d * sp + 0.03;
                e.motionZ = dz / d * sp;
                e.velocityChanged = true;
            }
        }
    }
}
