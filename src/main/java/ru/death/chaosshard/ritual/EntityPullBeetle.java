package ru.death.chaosshard.ritual;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.breach.Breach;

/**
 * Жучок Тяги: сфера Тяги, поставленная на хранилище (сундук, бочку, ящик), оживает жучком Хаоса.
 * Сидит на крышке и чуть ползает по ней; без срока стягивает вещи и сферы опыта в {@link #RADIUS} блоках,
 * вещи складывает в хранилище под собой. Открывать хранилище не мешает: луч взгляда его не замечает,
 * пока не присядешь. Присесть и ПКМ по жучку — забрать его обратно сферой. Хранилище сломали — жучок
 * снова становится сферой и падает рядом.
 */
public class EntityPullBeetle extends Entity {

    public static final int RADIUS = 16;

    private BlockPos home = BlockPos.ORIGIN;
    private double baseX, baseZ;
    private double goalX, goalZ;
    private int nextWander;

    // плавность на клиенте
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw;
    private int lerpSteps;

    public EntityPullBeetle(World world) {
        super(world);
        setSize(0.45F, 0.3F);
    }

    public EntityPullBeetle(World world, BlockPos inventory, double topY) {
        this(world);
        this.home = inventory.toImmutable();
        this.baseX = inventory.getX() + 0.5;
        this.baseZ = inventory.getZ() + 0.5;
        this.goalX = baseX;
        this.goalZ = baseZ;
        setLocationAndAngles(baseX, topY, baseZ, world.rand.nextFloat() * 360F, 0);
    }

    public BlockPos home() {
        return home;
    }

    @Override
    protected void entityInit() {
    }

    private IItemHandler inventory() {
        TileEntity te = world.getTileEntity(home);
        return te == null ? null : te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            if (lerpSteps > 0) {
                double k = 1.0 / lerpSteps;
                setPosition(posX + (lerpX - posX) * k, posY + (lerpY - posY) * k, posZ + (lerpZ - posZ) * k);
                rotationYaw += MathHelper.wrapDegrees(lerpYaw - rotationYaw) * k;
                lerpSteps--;
            }
            if (rand.nextInt(12) == 0) {
                world.spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 0.2, posZ, (rand.nextDouble() - 0.5) * 0.4, 0.1, (rand.nextDouble() - 0.5) * 0.4);
            }
            return;
        }
        wander();
        long t = world.getTotalWorldTime();
        if (t % 20 == (getEntityId() & 15) && inventory() == null) {
            leave();
            return;
        }
        if (t % 4 == 0) {
            pull();
        }
    }

    /** Чуть ползает по крышке: новая точка раз в 2–5 секунд, разворачивается к ней. */
    private void wander() {
        if (--nextWander <= 0) {
            goalX = baseX + (rand.nextDouble() - 0.5) * 0.45;
            goalZ = baseZ + (rand.nextDouble() - 0.5) * 0.45;
            nextWander = 40 + rand.nextInt(60);
        }
        double dx = goalX - posX, dz = goalZ - posZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 0.01) {
            float want = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90F;
            rotationYaw += MathHelper.clamp(MathHelper.wrapDegrees(want - rotationYaw), -12F, 12F);
            double step = Math.min(d, 0.012);
            setPosition(posX + dx / d * step, posY, posZ + dz / d * step);
        }
    }

    /** Вещи и сферы опыта плывут к жучку; дошедшие вещи ложатся в хранилище. */
    private void pull() {
        IItemHandler inv = inventory();
        AxisAlignedBB box = new AxisAlignedBB(home).grow(RADIUS);
        double cx = posX, cy = posY + 0.3, cz = posZ;
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box, x -> x instanceof EntityItem || x instanceof EntityXPOrb)) {
            if (!e.isEntityAlive()) {
                continue;
            }
            double dx = cx - e.posX, dy = cy - e.posY, dz = cz - e.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1.3 && e instanceof EntityItem && inv != null) {
                EntityItem ei = (EntityItem) e;
                ItemStack rest = ItemHandlerHelper.insertItemStacked(inv, ei.getItem().copy(), false);
                if (rest.getCount() != ei.getItem().getCount()) {
                    world.playSound(null, home, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.2F, 1.6F);
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

    /** Снова стать сферой и выпасть рядом (хранилище сломали или жучка забрали). */
    private void leave() {
        ItemStack sphere = ItemRitualSphere.of(Ritual.PULL, 0);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 0.2, posZ, 30, 0.2, 0.2, 0.2, 0.3);
        }
        entityDropItem(sphere, 0.2F);
        setDead();
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (!player.isSneaking() || hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!world.isRemote) {
            ItemStack sphere = ItemRitualSphere.of(Ritual.PULL, 0);
            if (!player.inventory.addItemStackToInventory(sphere)) {
                entityDropItem(sphere, 0.2F);
            }
            world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6F, 0.8F);
            player.sendStatusMessage(Breach.grimoire("chaosshard.ritual.pull.beetle_taken"), true);
            setDead();
        }
        return true;
    }

    /** Лучом взгляда ловится, только когда игрок крадётся: иначе клик проходит к хранилищу под ним. */
    @Override
    public boolean canBeCollidedWith() {
        return !world.isRemote || ChaosShard.proxy.localSneaking();
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return source != DamageSource.OUT_OF_WORLD;
    }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpSteps = Math.max(1, steps);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        home = BlockPos.fromLong(tag.getLong("home"));
        baseX = home.getX() + 0.5;
        baseZ = home.getZ() + 0.5;
        goalX = posX;
        goalZ = posZ;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setLong("home", home.toLong());
    }
}
