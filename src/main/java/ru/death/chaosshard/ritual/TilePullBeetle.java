package ru.death.chaosshard.ritual;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Жучок Тяги: раз в 4 тика тянет вещи и опыт в {@link #RADIUS} блоках, вещи кладёт в хранилище под собой. */
public class TilePullBeetle extends TileEntity implements ITickable {

    public static final int RADIUS = 16;

    @Override
    public void update() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % 4 != (pos.toLong() & 3)) {
            return;
        }
        TileEntity below = world.getTileEntity(pos.down());
        IItemHandler inv = below == null ? null : below.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (inv == null) {
            return;
        }
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.3, cz = pos.getZ() + 0.5;
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(RADIUS);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box, x -> x instanceof EntityItem || x instanceof EntityXPOrb)) {
            if (!e.isEntityAlive()) {
                continue;
            }
            double dx = cx - e.posX, dy = cy - e.posY, dz = cz - e.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
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
