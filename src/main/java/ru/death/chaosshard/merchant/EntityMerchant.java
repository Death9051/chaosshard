package ru.death.chaosshard.merchant;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/**
 * Торговец: стоит у Якоря, бродит рядом, неуязвим. ПКМ — окно торговли. Когда срок выходит, уходит в дым.
 */
public class EntityMerchant extends EntityCreature {

    private UUID owner;
    private BlockPos anchor = BlockPos.ORIGIN;
    private long leaveAt;

    public EntityMerchant(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        enablePersistence();
    }

    void setup(UUID owner, BlockPos anchor, long leaveAt) {
        this.owner = owner;
        this.anchor = anchor;
        this.leaveAt = leaveAt;
        setHomePosAndDistance(anchor, 6);
    }

    public UUID owner() {
        return owner;
    }

    public BlockPos anchor() {
        return anchor;
    }

    /** Сколько тиков он ещё пробудет. */
    public long leaveIn() {
        return leaveAt - world.getTotalWorldTime();
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F, 1.0F));
        tasks.addTask(2, new EntityAIMoveTowardsRestriction(this, 0.5));
        tasks.addTask(3, new EntityAIWanderAvoidWater(this, 0.4, 0.002F));
        tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 10.0F));
        tasks.addTask(5, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.5);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source == DamageSource.OUT_OF_WORLD) {
            return super.attackEntityFrom(source, amount);
        }
        return false;
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return source != DamageSource.OUT_OF_WORLD;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBeLeashedTo(EntityPlayer player) {
        return false;
    }

    @Override
    protected boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            getLookHelper().setLookPositionWithEntity(player, 30F, 30F);
            Merchant.open((EntityPlayerMP) player, this, true);
        }
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!world.isRemote) {
            if (owner == null || leaveIn() <= 0) {
                leave();
                return;
            }
            if (ticksExisted % 40 == 0 && getDistanceSq(anchor) > 24 * 24) {
                // утащили или забрёл — вернуть к Якорю
                BlockPos at = Merchant.standNear(world, anchor, rand);
                if (at != null) {
                    setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                }
            }
        }
    }

    /** Уйти: дым и звук. */
    public void leave() {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1, posZ, 20, 0.3, 0.6, 0.3, 0.02);
            world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE, SoundCategory.NEUTRAL, 0.8F, 0.8F);
        }
        setDead();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        if (owner != null) {
            nbt.setUniqueId("MerchantOwner", owner);
        }
        nbt.setLong("MerchantAnchor", anchor.toLong());
        nbt.setLong("MerchantLeave", leaveAt);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        owner = nbt.hasUniqueId("MerchantOwner") ? nbt.getUniqueId("MerchantOwner") : null;
        anchor = BlockPos.fromLong(nbt.getLong("MerchantAnchor"));
        leaveAt = nbt.getLong("MerchantLeave");
        setHomePosAndDistance(anchor, 6);
    }

    @Override
    protected net.minecraft.util.SoundEvent getAmbientSound() {
        return null;
    }
}
