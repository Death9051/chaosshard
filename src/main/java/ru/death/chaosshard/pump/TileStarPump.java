package ru.death.chaosshard.pump;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.ItemAnchorSeal;
import ru.death.chaosshard.anchor.TileAnchor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Звёздный насос: выкачивает звёздный свет из захваченной земли.
 * Работает только в радиусе захваченного Якоря (уровень 2+). Даёт жидкий звёздный свет:
 * 5 мВ/с за уровень Якоря, ночью вдвое больше, печать Жатвы — ещё +50%.
 * Жидкость забирают трубами с любой стороны или ведром (ПКМ).
 */
public class TileStarPump extends TileEntity implements ITickable {

    public static final int CAPACITY = 16000;
    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        public boolean canFill() {
            return false;
        }
    };
    private UUID owner;
    private int timer;
    /** Сколько качает сейчас (мВ/с), 0 — стоит. Для сведений и частиц. */
    private int rate;
    private long pumped;

    public static Fluid starlight() {
        return FluidRegistry.getFluid("astralsorcery.liquidstarlight");
    }

    public void setOwner(EntityPlayerMP p) {
        owner = p.getUniqueID();
        markDirty();
    }

    public int getRate() {
        return rate;
    }

    public FluidTank getTank() {
        return tank;
    }

    @Override
    public void update() {
        if (world.isRemote || ++timer % 20 != 0) {
            return;
        }
        Fluid f = starlight();
        TileAnchor anchor = anchorHere();
        if (f == null || anchor == null) {
            rate = 0;
            return;
        }
        double r = 5.0 * anchor.level();
        if (!world.isDaytime()) {
            r *= 2;
        }
        r *= 1 + 0.5 * anchor.power(ItemAnchorSeal.Type.HARVEST);
        rate = (int) Math.round(r);
        int filled = tank.fillInternal(new FluidStack(f, rate), true);
        pumped += filled;
        if (filled > 0) {
            markDirty();
            if (timer % 60 == 0) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                        3, 0.15, 0.3, 0.15, 0.02);
            }
            if (pumped >= 1000 && owner != null && world.getMinecraftServer() != null) {
                EntityPlayerMP p = world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
                if (p != null) {
                    ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.PUMP);
                }
            }
        }
    }

    /** Захваченный Якорь, в радиусе которого стоит насос, или null. */
    public TileAnchor anchorHere() {
        for (BlockPos p : AnchorData.get(world).anchors) {
            if (!world.isBlockLoaded(p)) {
                continue;
            }
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileAnchor)) {
                continue;
            }
            TileAnchor a = (TileAnchor) te;
            double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            if (a.isCaptured() && dx * dx + dz * dz <= (double) a.radius() * a.radius()) {
                return a;
            }
        }
        return null;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(tank);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tank.writeToNBT(tag);
        if (owner != null) {
            tag.setUniqueId("owner", owner);
        }
        tag.setLong("pumped", pumped);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tank.readFromNBT(tag);
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
        pumped = tag.getLong("pumped");
    }
}
