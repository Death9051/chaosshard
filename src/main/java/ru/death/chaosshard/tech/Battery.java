package ru.death.chaosshard.tech;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.energy.EnergyStorage;

/**
 * Приёмник энергии Forge (RF/FE) для блоков Хаоса: принимает от любых машин и кабелей
 * (Mekanism, Thermal, Extra Utilities 2, Cyclic), наружу не отдаёт.
 */
public class Battery extends EnergyStorage {

    private final Runnable onChange;

    public Battery(int capacity, int maxReceive, Runnable onChange) {
        super(capacity, maxReceive, 0);
        this.onChange = onChange;
    }

    /** Списать, если хватает. */
    public boolean draw(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        onChange.run();
        return true;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int got = super.receiveEnergy(maxReceive, simulate);
        if (got > 0 && !simulate) {
            onChange.run();
        }
        return got;
    }

    public void set(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }

    public void write(NBTTagCompound tag, String key) {
        tag.setInteger(key, energy);
    }

    public void read(NBTTagCompound tag, String key) {
        set(tag.getInteger(key));
    }
}
