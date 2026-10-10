package ru.death.chaosshard.quest;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

/** Проверки чужих машин без их API — через capability и NBT. */
final class TileChecks {

    static final String STARLIGHT = "astralsorcery.liquidstarlight";

    /** Динамо работает (Active, если такой флаг есть) и в баке жидкий звёздный свет. */
    static boolean burnsStarlight(TileEntity te) {
        NBTTagCompound nbt = te.writeToNBT(new NBTTagCompound());
        if (nbt.hasKey("Active") && !nbt.getBoolean("Active")) {
            return false;
        }
        if (te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            IFluidHandler h = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (h != null) {
                for (IFluidTankProperties p : h.getTankProperties()) {
                    FluidStack f = p.getContents();
                    if (f != null && f.getFluid() != null && STARLIGHT.equals(f.getFluid().getName())) {
                        return true;
                    }
                }
            }
        }
        return nbt.toString().contains(STARLIGHT);
    }

    private TileChecks() {
    }
}
