package ru.death.chaosshard.thread;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import ru.death.chaosshard.Tags;

import javax.annotation.Nullable;

/**
 * Хранение Нити на игроке (capability).
 */
public final class ThreadCapability {

    @CapabilityInject(IThread.class)
    public static Capability<IThread> THREAD = null;

    public static final ResourceLocation KEY = new ResourceLocation(Tags.MOD_ID, "thread");

    public static void register() {
        CapabilityManager.INSTANCE.register(IThread.class, new Storage(), Impl::new);
    }

    /** Нить игрока; у живого игрока всегда есть. */
    @Nullable
    public static IThread get(EntityPlayer player) {
        return THREAD == null ? null : player.getCapability(THREAD, null);
    }

    public static class Impl implements IThread {
        private int points;
        /** -1: Нить ещё не инициализирована, этап 0 не выдан. */
        private int announcedLevel = -1;

        @Override
        public int getPoints() {
            return points;
        }

        @Override
        public void setPoints(int points) {
            this.points = Math.max(0, points);
        }

        @Override
        public int getAnnouncedLevel() {
            return announcedLevel;
        }

        @Override
        public void setAnnouncedLevel(int level) {
            this.announcedLevel = Math.max(-1, level);
        }
    }

    public static class Storage implements Capability.IStorage<IThread> {
        @Nullable
        @Override
        public NBTBase writeNBT(Capability<IThread> capability, IThread instance, EnumFacing side) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("points", instance.getPoints());
            tag.setInteger("announced", instance.getAnnouncedLevel());
            return tag;
        }

        @Override
        public void readNBT(Capability<IThread> capability, IThread instance, EnumFacing side, NBTBase nbt) {
            if (nbt instanceof NBTTagCompound) {
                NBTTagCompound tag = (NBTTagCompound) nbt;
                instance.setPoints(tag.getInteger("points"));
                instance.setAnnouncedLevel(tag.hasKey("announced") ? tag.getInteger("announced") : -1);
            }
        }
    }

    /** Провайдер, который вешается на каждого игрока. */
    public static class Provider implements ICapabilitySerializable<NBTTagCompound> {
        private final IThread instance = new Impl();

        @Override
        public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == THREAD;
        }

        @Nullable
        @Override
        public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
            return capability == THREAD ? THREAD.cast(instance) : null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return (NBTTagCompound) THREAD.getStorage().writeNBT(THREAD, instance, null);
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            THREAD.getStorage().readNBT(THREAD, instance, null, nbt);
        }
    }

    private ThreadCapability() {
    }
}
