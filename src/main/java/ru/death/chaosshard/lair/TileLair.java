package ru.death.chaosshard.lair;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import ru.death.chaosshard.thread.ThreadBonus;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Сердце Логова. Уровни: 1 — Ложе, 2 — Башня, 3 — Цитадель.
 * В радиусе логова (8/16/24) у владельца быстрее восстанавливается энергия Хаоса и идёт регенерация.
 * Само сердце — источник силы Хаоса для машин: отдаёт энергию (FE/RF) соседним блокам.
 */
public class TileLair extends TileEntity implements ITickable {

    private UUID owner;
    private String ownerName = "";
    private int level = 1;
    private int timer;
    /** Сколько энергии ещё можно отдать в этом тике. */
    private int budget;

    public static final int[] RADIUS = {8, 16, 24};
    public static final int[] POWER = {40, 160, 640};

    public void setOwner(EntityPlayerMP p) {
        owner = p.getUniqueID();
        ownerName = p.getName();
        markDirty();
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int l) {
        level = Math.max(1, Math.min(3, l));
        markDirty();
        if (world != null) {
            net.minecraft.block.state.IBlockState s = world.getBlockState(pos);
            world.setBlockState(pos, s.withProperty(BlockLair.LEVEL, level), 3);
        }
    }

    public int radius() {
        return RADIUS[level - 1];
    }

    /** Сила Хаоса для машин: базовая по уровню логова, +25% за уровень Нити владельца (если он рядом). */
    public int power() {
        int p = POWER[level - 1];
        EntityPlayerMP o = ownerOnline();
        if (o != null && o.world == world) {
            p = (int) (p * (1 + 0.25 * ThreadBonus.level(o)));
        }
        return p;
    }

    private EntityPlayerMP ownerOnline() {
        return owner == null || world.getMinecraftServer() == null ? null
                : world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
    }

    public boolean covers(EntityPlayer p) {
        return p.world == world && p.getDistanceSq(pos) <= (double) radius() * radius();
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        // энергия машинам вокруг — каждый тик
        budget = power();
        int left = budget;
        for (EnumFacing f : EnumFacing.VALUES) {
            if (left <= 0) {
                break;
            }
            TileEntity te = world.getTileEntity(pos.offset(f));
            if (te != null && te.hasCapability(CapabilityEnergy.ENERGY, f.getOpposite())) {
                IEnergyStorage es = te.getCapability(CapabilityEnergy.ENERGY, f.getOpposite());
                if (es != null && es.canReceive()) {
                    left -= es.receiveEnergy(left, false);
                    budget = left;
                }
            }
        }
        if (++timer % 100 != 0) {
            return;
        }
        // отдых: регенерация в стенах логова
        List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(pos).grow(radius()));
        for (EntityPlayer p : players) {
            if (owner != null && owner.equals(p.getUniqueID()) && covers(p)) {
                p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 110, level >= 3 ? 1 : 0, true, false));
                if (p instanceof EntityPlayerMP) {
                    ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) p, "lair_" + level);
                }
            }
        }
    }

    /** Отдаёт энергию и кабелям, которые сами её тянут. */
    private final IEnergyStorage out = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int n = Math.min(maxExtract, budget);
            if (!simulate) {
                budget -= n;
            }
            return n;
        }

        @Override
        public int getEnergyStored() {
            return budget;
        }

        @Override
        public int getMaxEnergyStored() {
            return power();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY ? CapabilityEnergy.ENERGY.cast(out) : super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (owner != null) {
            tag.setUniqueId("owner", owner);
        }
        tag.setString("ownerName", ownerName);
        tag.setInteger("level", level);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
        ownerName = tag.getString("ownerName");
        level = Math.max(1, tag.getInteger("level"));
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    /** Множитель восстановления энергии Хаоса для игрока: в своём логове ×2 (Цитадель ×3). */
    public static double energyMultiplier(EntityPlayer p) {
        BlockPos home = LairData.get(p.world).lairOf(p.getUniqueID(), p.world.provider.getDimension());
        if (home == null || !p.world.isBlockLoaded(home)) {
            return 1.0;
        }
        TileEntity te = p.world.getTileEntity(home);
        if (te instanceof TileLair && ((TileLair) te).covers(p)) {
            return ((TileLair) te).level >= 3 ? 3.0 : 2.0;
        }
        return 1.0;
    }
}
