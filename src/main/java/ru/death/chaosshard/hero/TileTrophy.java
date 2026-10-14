package ru.death.chaosshard.hero;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Трофей Светоча на подставке: кто хозяин и стоит ли он в стенах его Логова. */
public class TileTrophy extends TileEntity implements ITickable {

    private UUID owner;
    private int timer;
    private boolean inLair;

    public void setOwner(UUID o) {
        owner = o;
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public Lightbearer hero() {
        int i = world == null ? 0 : world.getBlockState(pos).getValue(BlockTrophy.HERO);
        return Lightbearer.values()[Math.max(0, Math.min(i, Lightbearer.values().length - 1))];
    }

    public boolean inLair() {
        return inLair;
    }

    /** Проверить, стоит ли трофей в стенах Логова хозяина, и записать это. */
    public boolean validate() {
        if (world == null || world.isRemote || owner == null) {
            return false;
        }
        boolean ok = false;
        BlockPos lair = ru.death.chaosshard.lair.LairData.get(world).lairOf(owner, world.provider.getDimension());
        if (lair != null && world.isBlockLoaded(lair)) {
            TileEntity te = world.getTileEntity(lair);
            if (te instanceof ru.death.chaosshard.lair.TileLair && owner.equals(((ru.death.chaosshard.lair.TileLair) te).getOwner())) {
                int r = ((ru.death.chaosshard.lair.TileLair) te).radius();
                ok = lair.distanceSq(pos) <= (double) r * r;
            }
        } else if (lair != null) {
            return inLair;   // Логово не загружено — не трогаем
        }
        if (ok != inLair) {
            inLair = ok;
            markDirty();
        }
        TrophyData.get(world).set(owner, hero(), ok);
        return ok;
    }

    @Override
    public void update() {
        if (!world.isRemote && ++timer % 100 == 1) {
            validate();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        if (owner != null) {
            c.setUniqueId("owner", owner);
        }
        c.setBoolean("lair", inLair);
        return c;
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        owner = c.hasUniqueId("owner") ? c.getUniqueId("owner") : null;
        inLair = c.getBoolean("lair");
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
