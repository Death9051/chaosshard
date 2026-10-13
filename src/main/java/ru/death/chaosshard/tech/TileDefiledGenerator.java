package ru.death.chaosshard.tech;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.anchor.Corruption;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.quest.Milestones;

import javax.annotation.Nullable;

/**
 * Скверный генератор: пьёт Скверну из-под себя и отдаёт RF/FE соседям — машинам и кабелям любых модов.
 * Работает только на осквернённом блоке внутри края Якоря; выработка — по уровню Якоря.
 */
public class TileDefiledGenerator extends TileEntity implements ITickable {

    /** Моды, ради которых генератор и делался: веха за первую отданную им энергию. */
    static final String[][] MODS = {
            {"mekanism", Milestones.POWER_MEKANISM}, {"mekanismgenerators", Milestones.POWER_MEKANISM},
            {"thermalexpansion", Milestones.POWER_THERMAL}, {"thermaldynamics", Milestones.POWER_THERMAL},
            {"extrautils2", Milestones.POWER_EXU2}, {"cyclicmagic", Milestones.POWER_CYCLIC}};

    /** Запас: снаружи только отдаёт. */
    static final class Store extends EnergyStorage {
        Store() {
            super(ChaosConfig.tech.generatorBuffer, 0, Integer.MAX_VALUE);
        }

        void set(int v) {
            energy = Math.max(0, Math.min(capacity, v));
        }
    }

    final Store store = new Store();
    /** Выработка за тик сейчас (0 — стоит). Синхронизируется на клиент для искр и подсказки. */
    private int rate;
    private int level;

    public int rate() {
        return rate;
    }

    public int anchorLevel() {
        return level;
    }

    public int stored() {
        return store.getEnergyStored();
    }

    /** Почему стоит: 0 — работает, 1 — не на Скверне, 2 — вне края Якоря. */
    public int reason() {
        if (!Corruption.isCorrupted(world.getBlockState(pos.down()))) {
            return 1;
        }
        return TileAnchor.covering(world, pos) == null ? 2 : 0;
    }

    @Override
    public void update() {
        if (world.isRemote) {
            if (rate > 0 && world.rand.nextInt(6) == 0) {
                world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        (world.rand.nextDouble() - 0.5) * 0.6, 0.2, (world.rand.nextDouble() - 0.5) * 0.6);
            }
            return;
        }
        if (world.getTotalWorldTime() % 20 == (pos.toLong() & 15)) {
            TileAnchor a = Corruption.isCorrupted(world.getBlockState(pos.down())) ? TileAnchor.covering(world, pos) : null;
            int lvl = a == null ? 0 : a.level();
            int r = lvl * ChaosConfig.tech.generatorPerLevel;
            if (r != rate || lvl != level) {
                rate = r;
                level = lvl;
                sync();
            }
        }
        if (rate > 0) {
            int room = store.getMaxEnergyStored() - store.getEnergyStored();
            if (room > 0) {
                store.set(store.getEnergyStored() + Math.min(room, rate));
                markDirty();
                if (world.getTotalWorldTime() % 100 == 0) {
                    grantNear(Milestones.DEFILED_POWER);
                }
            }
        }
        push();
    }

    /** Отдать энергию соседям, что умеют принимать. */
    private void push() {
        int left = Math.min(store.getEnergyStored(), ChaosConfig.tech.generatorOutput);
        if (left <= 0) {
            return;
        }
        for (EnumFacing f : EnumFacing.VALUES) {
            TileEntity te = world.getTileEntity(pos.offset(f));
            if (te == null || te instanceof TileDefiledGenerator || !te.hasCapability(CapabilityEnergy.ENERGY, f.getOpposite())) {
                continue;
            }
            IEnergyStorage other = te.getCapability(CapabilityEnergy.ENERGY, f.getOpposite());
            if (other == null || !other.canReceive()) {
                continue;
            }
            int sent = other.receiveEnergy(left, false);
            if (sent > 0) {
                store.extractEnergy(sent, false);
                markDirty();
                left -= sent;
                if (world.getTotalWorldTime() % 100 == 0) {
                    ResourceLocation id = TileEntity.getKey(te.getClass());
                    if (id != null) {
                        for (String[] m : MODS) {
                            if (m[0].equals(id.getNamespace())) {
                                grantNear(m[1]);
                            }
                        }
                    }
                }
            }
            if (left <= 0) {
                break;
            }
        }
    }

    private void grantNear(String milestone) {
        for (EntityPlayerMP p : world.getEntitiesWithinAABB(EntityPlayerMP.class, new AxisAlignedBB(pos).grow(24))) {
            Milestones.grant(p, milestone);
        }
    }

    private void sync() {
        markDirty();
        if (world instanceof WorldServer) {
            net.minecraft.block.state.IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    @Override
    public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityEnergy.ENERGY || super.hasCapability(cap, side);
    }

    @Override
    public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        return cap == CapabilityEnergy.ENERGY ? CapabilityEnergy.ENERGY.cast(store) : super.getCapability(cap, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("rf", store.getEnergyStored());
        tag.setInteger("rate", rate);
        tag.setInteger("level", level);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        store.set(tag.getInteger("rf"));
        rate = tag.getInteger("rate");
        level = tag.getInteger("level");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
