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
    /** Внутренняя батарея (FE). */
    private int energy;
    /** Сколько пришло с земли за последнюю секунду (FE/т в среднем) и от скольких источников. */
    private int inflow, forges, generators;
    private int inflowAcc;

    public static final int[] RADIUS = {8, 16, 24};
    public static final int[] POWER = {40, 160, 640};
    /** Ёмкость батареи и предел отдачи соседям за тик по уровню. */
    public static final int[] CAPACITY = {100000, 500000, 2000000};
    public static final int[] OUTPUT = {2000, 8000, 32000};

    public int energy() {
        return energy;
    }

    public void setEnergy(int e) {
        energy = Math.max(0, Math.min(capacity(), e));
        markDirty();
    }

    public int capacity() {
        return CAPACITY[level - 1];
    }

    public int output() {
        return OUTPUT[level - 1];
    }

    public int inflow() {
        return inflow;
    }

    public int forges() {
        return forges;
    }

    public int generators() {
        return generators;
    }

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
        // своя выработка — в батарею, из батареи — машинам вокруг
        energy = Math.min(capacity(), energy + power());
        int left = Math.min(energy, output());
        for (EnumFacing f : EnumFacing.VALUES) {
            if (left <= 0) {
                break;
            }
            TileEntity te = world.getTileEntity(pos.offset(f));
            if (te != null && !(te instanceof ru.death.chaosshard.anchor.TileAnchor) && !(te instanceof ru.death.chaosshard.tech.TileDefiledGenerator)
                    && te.hasCapability(CapabilityEnergy.ENERGY, f.getOpposite())) {
                IEnergyStorage es = te.getCapability(CapabilityEnergy.ENERGY, f.getOpposite());
                if (es != null && es.canReceive()) {
                    int sent = es.receiveEnergy(left, false);
                    left -= sent;
                    energy -= sent;
                }
            }
        }
        ++timer;
        if (timer % 20 == 0) {
            drawFromLand();
            markDirty();
        }
        if (level >= 3 && timer % 40 == 0) {
            citadelGuard();
        }
        if (timer % 100 != 0) {
            return;
        }
        if (level >= 3) {
            citadelMend();
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

    /**
     * Приток с земли: раз в секунду логово забирает запас у Горнов (Якоря с путём Горн) своего хозяина и у Скверных
     * генераторов на его захваченной земле в этом мире — без проводов, сколько влезет в батарею.
     */
    public void drawFromLand() {
        if (owner == null) {
            return;
        }
        int got = 0, f = 0, g = 0;
        for (TileEntity te : new java.util.ArrayList<>(world.loadedTileEntityList)) {
            int room = capacity() - energy;
            if (room <= 0) {
                break;
            }
            if (te.isInvalid()) {
                continue;
            }
            if (te instanceof ru.death.chaosshard.anchor.TileAnchor) {
                ru.death.chaosshard.anchor.TileAnchor a = (ru.death.chaosshard.anchor.TileAnchor) te;
                if (a.getSpec() == ru.death.chaosshard.anchor.AnchorSpec.FORGE && owner.equals(a.getOwner()) && a.isCaptured()) {
                    f++;
                    int e = Math.min(room, a.forgeEnergy);
                    if (e > 0) {
                        a.forgeEnergy -= e;
                        a.markDirty();
                        energy += e;
                        got += e;
                    }
                }
            } else if (te instanceof ru.death.chaosshard.tech.TileDefiledGenerator) {
                ru.death.chaosshard.anchor.TileAnchor cov = ru.death.chaosshard.anchor.TileAnchor.covering(world, te.getPos());
                if (cov != null && owner.equals(cov.getOwner())) {
                    g++;
                    IEnergyStorage es = te.getCapability(CapabilityEnergy.ENERGY, null);
                    if (es != null && es.canExtract()) {
                        int e = es.extractEnergy(room, false);
                        energy += e;
                        got += e;
                    }
                }
            }
        }
        inflow = got / 20;
        forges = f;
        generators = g;
    }

    /** Дыхание Цитадели: до трёх врагов в стенах логова раз в две секунды обжигает драконьим дыханием. */
    public int citadelGuard() {
        int n = 0;
        for (net.minecraft.entity.EntityLivingBase e : world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class,
                new AxisAlignedBB(pos).grow(radius()), x -> x.isEntityAlive() && x instanceof net.minecraft.entity.monster.IMob
                        && !ru.death.chaosshard.servant.Servants.isServant(x) && x.getDistanceSq(pos) <= (double) radius() * radius())) {
            if (n >= 3) {
                break;
            }
            e.attackEntityFrom(net.minecraft.util.DamageSource.MAGIC, CITADEL_BREATH);
            ((net.minecraft.world.WorldServer) world).spawnParticle(net.minecraft.util.EnumParticleTypes.DRAGON_BREATH, e.posX, e.posY + e.height / 2,
                    e.posZ, 30, 0.4, 0.5, 0.4, 0.02);
            world.playSound(null, e.getPosition(), net.minecraft.init.SoundEvents.ENTITY_ENDERDRAGON_SHOOT, net.minecraft.util.SoundCategory.BLOCKS, 0.6F, 1.4F);
            n++;
        }
        return n;
    }

    public static final float CITADEL_BREATH = 6.0F;

    /** Звезда Незера в стенах Цитадели: снаряжение хозяина понемногу чинится. */
    public int citadelMend() {
        EntityPlayerMP o = ownerOnline();
        if (o == null || !covers(o)) {
            return 0;
        }
        int n = 0;
        for (net.minecraft.item.ItemStack s : o.inventory.mainInventory) {
            n += mend(s);
        }
        for (net.minecraft.item.ItemStack s : o.inventory.armorInventory) {
            n += mend(s);
        }
        n += mend(o.inventory.offHandInventory.get(0));
        return n;
    }

    public static int mend(net.minecraft.item.ItemStack s) {
        if (!s.isEmpty() && s.isItemStackDamageable() && s.isItemDamaged()) {
            s.setItemDamage(Math.max(0, s.getItemDamage() - 2));
            return 1;
        }
        return 0;
    }

    /** Батарея наружу: принимает от любых кабелей и отдаёт тем, что сами тянут (не больше предела за тик). */
    private final IEnergyStorage out = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int n = Math.max(0, Math.min(maxReceive, capacity() - energy));
            if (!simulate) {
                energy += n;
            }
            return n;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int n = Math.max(0, Math.min(Math.min(maxExtract, output()), energy));
            if (!simulate) {
                energy -= n;
            }
            return n;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
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
        tag.setInteger("energy", energy);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
        ownerName = tag.getString("ownerName");
        level = Math.max(1, tag.getInteger("level"));
        energy = tag.getInteger("energy");
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
