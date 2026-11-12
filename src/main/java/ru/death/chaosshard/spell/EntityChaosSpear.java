package ru.death.chaosshard.spell;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import ru.death.chaosshard.servant.Servants;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Копьё Хаоса — Стрела Хаоса с веткой «Копьё»: настоящий снаряд. Летит прямо, без падения, 2,5 блока за тик,
 * пронзает всех врагов на пути (каждого — один раз) и разбивается о блок. Свиту хозяина и игроков пролетает насквозь.
 * В мир не сохраняется: после перезахода копья нет.
 */
public class EntityChaosSpear extends Entity implements IEntityAdditionalSpawnData {

    public static final double SPEED = 2.5;

    private UUID owner;
    private float damage;
    private int life = 20;
    private boolean burn;
    private final Set<Integer> hit = new HashSet<>();

    public EntityChaosSpear(World world) {
        super(world);
        setSize(0.3F, 0.3F);
        noClip = true;
    }

    public EntityChaosSpear(World world, EntityPlayer shooter, float damage, double range, boolean burn) {
        this(world);
        this.owner = shooter.getUniqueID();
        this.damage = damage;
        this.burn = burn;
        this.life = (int) Math.ceil(range / SPEED) + 1;
        Vec3d look = shooter.getLookVec();
        Vec3d eye = shooter.getPositionEyes(1.0F);
        setPosition(eye.x + look.x * 0.6, eye.y - 0.1 + look.y * 0.6, eye.z + look.z * 0.6);
        motionX = look.x * SPEED;
        motionY = look.y * SPEED;
        motionZ = look.z * SPEED;
        faceMotion();
    }

    private void faceMotion() {
        double h = MathHelper.sqrt(motionX * motionX + motionZ * motionZ);
        rotationYaw = (float) (MathHelper.atan2(motionX, motionZ) * (180D / Math.PI));
        rotationPitch = (float) (MathHelper.atan2(motionY, h) * (180D / Math.PI));
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        ticksExisted++;
        lastTickPosX = posX;
        lastTickPosY = posY;
        lastTickPosZ = posZ;
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        Vec3d from = new Vec3d(posX, posY, posZ);
        Vec3d to = new Vec3d(posX + motionX, posY + motionY, posZ + motionZ);
        if (world.isRemote) {
            // хвост: искры вдоль пройденного отрезка
            for (int i = 0; i < 4; i++) {
                double t = i / 4.0;
                world.spawnParticle(EnumParticleTypes.SPELL_WITCH, posX - motionX * t, posY - motionY * t, posZ - motionZ * t, 0, 0, 0);
            }
            if (rand.nextInt(2) == 0) {
                world.spawnParticle(EnumParticleTypes.DRAGON_BREATH, posX, posY, posZ, 0, 0, 0);
            }
            setPosition(to.x, to.y, to.z);
            return;
        }
        RayTraceResult wall = world.rayTraceBlocks(from, to, false, true, false);
        Vec3d stop = wall != null && wall.typeOfHit == RayTraceResult.Type.BLOCK ? wall.hitVec : to;
        EntityPlayer shooter = owner == null ? null : world.getPlayerEntityByUUID(owner);
        for (Entity e : world.getEntitiesWithinAABBExcludingEntity(this, getEntityBoundingBox().expand(motionX, motionY, motionZ).grow(1.0))) {
            if (!(e instanceof EntityLivingBase) || !e.isEntityAlive() || hit.contains(e.getEntityId()) || e == shooter
                    || e instanceof EntityPlayer || owner != null && Servants.isServantOf(e, owner)) {
                continue;
            }
            if (e.getEntityBoundingBox().grow(0.3).calculateIntercept(from, stop) == null) {
                continue;
            }
            hit.add(e.getEntityId());
            EntityLivingBase t = (EntityLivingBase) e;
            if (shooter instanceof EntityPlayerMP) {
                SpellCaster.hit((EntityPlayerMP) shooter, t, damage);
            } else {
                t.hurtResistantTime = 0;
                t.attackEntityFrom(DamageSource.MAGIC, damage);
            }
            if (burn) {
                t.setFire(5);
                t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 80, 0));
            }
            world.playSound(null, t.posX, t.posY, t.posZ, SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8F, 0.6F);
        }
        if (stop != to) {
            setPosition(stop.x, stop.y, stop.z);
            shatter();
            return;
        }
        setPosition(to.x, to.y, to.z);
        if (ticksExisted > life) {
            shatter();
        }
    }

    /** Копьё рассыпается: вспышка и звон. */
    private void shatter() {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, posX, posY, posZ, 25, 0.25, 0.25, 0.25, 0.04);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, posX, posY, posZ, 12, 0.2, 0.2, 0.2, 0.0);
        }
        world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6F, 1.6F);
        setDead();
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public float getBrightness() {
        return 1.0F;
    }

    @Override
    public int getBrightnessForRender() {
        return 0xF000F0;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        setDead();   // копьё живёт секунду — после загрузки мира его быть не должно
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound tag) {
        return false;   // не сохранять в чанк
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(motionX);
        buf.writeDouble(motionY);
        buf.writeDouble(motionZ);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        motionX = buf.readDouble();
        motionY = buf.readDouble();
        motionZ = buf.readDouble();
        faceMotion();
    }
}
