package ru.death.chaosshard.ritual;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/** Поставленная сфера: длительный ритуал действует вокруг неё, пока не выгорит (Тяга не выгорает). */
public class TileRitualSphere extends TileEntity implements ITickable {

    private int ritual = 0;
    private int ticksLeft;
    private int age;
    private UUID owner;
    /** Тяга над хранилищем: собранный, ещё не свёрнутый опыт. */
    private int xp;

    public void start(Ritual r, int ticks, EntityPlayer by) {
        ritual = Math.max(0, r.sphereIndex());
        ticksLeft = ticks;
        owner = by == null ? null : by.getUniqueID();
        sync();
    }

    public Ritual ritual() {
        return Ritual.sphere(ritual);
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public int getAge() {
        return age;
    }

    /** Предмет-сфера с остатком времени (поднять или сломать). */
    public ItemStack toItem() {
        return ItemRitualSphere.of(ritual(), ticksLeft);
    }

    @Override
    public void update() {
        age++;
        if (world.isRemote) {
            return;
        }
        Ritual r = ritual();
        if (!r.endless() && --ticksLeft <= 0) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.1);
            world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.8F, 1.4F);
            world.setBlockToAir(pos);
            return;
        }
        RitualEffects.tick(world, pos, r, age);
        if (r == Ritual.PULL && age % 4 == 0) {
            int got = RitualEffects.pull(world, pos);
            if (got > 0) {
                xp += got;
                markDirty();
            }
            if (xp > 0 && age % ItemXpPouch.PACK_TICKS < 4) {
                TileEntity below = world.getTileEntity(pos.down());
                xp = ItemXpPouch.pack(below == null ? null : below.getCapability(
                        net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, net.minecraft.util.EnumFacing.UP), xp);
            }
        }
        if (age % 10 == 0) {
            EnumParticleTypes p = r == Ritual.GROWTH ? EnumParticleTypes.VILLAGER_HAPPY
                    : r == Ritual.STORM ? EnumParticleTypes.FIREWORKS_SPARK : EnumParticleTypes.SPELL_WITCH;
            ((WorldServer) world).spawnParticle(p, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.01);
        }
        if (age % 200 == 0) {
            markDirty();
        }
    }

    /** Сферу забрали или сломали: несвёрнутый опыт рассыпается сферами опыта. */
    public void releaseXp() {
        int left = xp;
        xp = 0;
        while (left > 0) {
            int part = net.minecraft.entity.item.EntityXPOrb.getXPSplit(left);
            left -= part;
            world.spawnEntity(new net.minecraft.entity.item.EntityXPOrb(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, part));
        }
    }

    private void sync() {
        markDirty();
        if (world != null) {
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Ritual", ritual);
        tag.setInteger("Ticks", ticksLeft);
        tag.setInteger("Xp", xp);
        if (owner != null) {
            tag.setUniqueId("Owner", owner);
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        ritual = tag.getInteger("Ritual");
        ticksLeft = tag.getInteger("Ticks");
        xp = tag.getInteger("Xp");
        owner = tag.hasUniqueId("Owner") ? tag.getUniqueId("Owner") : null;
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
}
