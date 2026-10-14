package ru.death.chaosshard.structure;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.Tags;

import javax.annotation.Nullable;

/**
 * Стража Ордена в постройках Лучезарии: человек в латах.
 * Вид: 0 — страж, 1 — капитан, 2 — Хранитель святилища (босс с полосой здоровья).
 * Нападает только на игроков и их слуг; жителей не трогает, на солнце не горит, не исчезает.
 */
public class EntityOrderKnight extends EntityMob {

    public static final int GUARD = 0, CAPTAIN = 1, KEEPER = 2;
    public static final String TAG = "chaosshard_order";
    private static final DataParameter<Integer> VARIANT = EntityDataManager.createKey(EntityOrderKnight.class, DataSerializers.VARINT);

    private final BossInfoServer bossInfo = new BossInfoServer(new TextComponentTranslation("entity.chaosshard.order_keeper.name"),
            BossInfo.Color.YELLOW, BossInfo.Overlay.NOTCHED_10);
    private boolean calledHelp;
    /** Постройка, к которой привязан (для Хранителя — чтобы погасить лучи). */
    public long structureId = -1;

    public EntityOrderKnight(World world) {
        super(world);
        setSize(0.6F, 1.95F);
        bossInfo.setVisible(false);
        experienceValue = 10;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(VARIANT, GUARD);
    }

    public int variant() {
        return dataManager.get(VARIANT);
    }

    /** Настроить вид: здоровье, урон, снаряжение, имя. */
    public void setup(int variant) {
        dataManager.set(VARIANT, variant);
        double hp, dmg, armor;
        switch (variant) {
            case CAPTAIN:
                hp = 70; dmg = 8; armor = 8;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                setCustomNameTag(TextFormatting.GOLD + new TextComponentTranslation("entity.chaosshard.order_captain.name").getUnformattedText());
                experienceValue = 30;
                break;
            case KEEPER:
                hp = 320; dmg = 10; armor = 10;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
                getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                experienceValue = 150;
                bossInfo.setVisible(true);
                break;
            default:
                hp = 32; dmg = 5; armor = 4;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(rand.nextInt(3) == 0 ? Items.IRON_AXE : Items.IRON_SWORD));
                break;
        }
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(dmg);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(armor);
        setHealth((float) hp);
        for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
            setDropChance(s, 0.0F);
        }
        enablePersistence();
        addTag(TAG);
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, false));
        tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 0.9D));
        tasks.addTask(7, new EntityAIWanderAvoidWater(this, 0.7D));
        tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 10.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
        targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(this, EntityLiving.class, 10, true, false,
                e -> e != null && ru.death.chaosshard.servant.Servants.isServant(e)));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.27D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(5.0D);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(4.0D);
    }

    @Override
    public boolean isOnSameTeam(Entity other) {
        if (other instanceof EntityOrderKnight) {
            // стражники на службе у Осколка (Перстень Элис) — уже не свои
            return ru.death.chaosshard.servant.Servants.isServant(this) == ru.death.chaosshard.servant.Servants.isServant(other);
        }
        return super.isOnSameTeam(other);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        // стражник по Перстню Элис: отслужил — уходит
        if (!world.isRemote && ticksExisted % 20 == 0 && getEntityData().hasKey(ru.death.chaosshard.hero.ItemElisSignet.UNTIL)
                && world.getTotalWorldTime() > getEntityData().getLong(ru.death.chaosshard.hero.ItemElisSignet.UNTIL)) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 1, posZ, 30, 0.3, 0.8, 0.3, 0.05);
            holdPeace = false;
            setDead();
            return;
        }
        if (world.isRemote || variant() != KEEPER) {
            return;
        }
        bossInfo.setPercent(getHealth() / getMaxHealth());
        EntityLivingBase target = getAttackTarget();
        // «Свет Ордена»: раз в 6 секунд отбрасывает и замедляет всех рядом
        if (target != null && ticksExisted % 120 == 0) {
            for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(6),
                    x -> x != this && !(x instanceof EntityOrderKnight))) {
                double dx = e.posX - posX, dz = e.posZ - posZ;
                double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                e.knockBack(this, 1.4F, -dx / d, -dz / d);
                e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
                e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 4.0F);
            }
            ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, posX, posY + 1, posZ, 60, 2.5, 0.6, 2.5, 0.08);
            world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 1.5F, 1.3F);
        }
        // на половине здоровья зовёт двух стражей
        if (!calledHelp && getHealth() < getMaxHealth() / 2) {
            calledHelp = true;
            for (int i = 0; i < 2; i++) {
                EntityOrderKnight g = new EntityOrderKnight(world);
                g.setup(GUARD);
                g.setLocationAndAngles(posX + rand.nextInt(5) - 2, posY, posZ + rand.nextInt(5) - 2, rand.nextFloat() * 360, 0);
                if (hasHome()) {
                    g.setHomePosAndDistance(getHomePosition(), 14);
                }
                world.spawnEntity(g);
                ((WorldServer) world).spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, g.posX, g.posY + 1, g.posZ, 30, 0.3, 0.8, 0.3, 0.05);
            }
            world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 1.5F, 0.8F);
        }
        // вне боя понемногу залечивается
        if (target == null && ticksExisted % 40 == 0 && getHealth() < getMaxHealth()) {
            heal(4.0F);
        }
    }

    @Override
    public void addTrackingPlayer(EntityPlayerMP player) {
        super.addTrackingPlayer(player);
        bossInfo.addPlayer(player);
    }

    @Override
    public void removeTrackingPlayer(EntityPlayerMP player) {
        super.removeTrackingPlayer(player);
        bossInfo.removePlayer(player);
    }

    @Override
    public ITextComponent getDisplayName() {
        if (variant() == KEEPER) {
            return new TextComponentTranslation("entity.chaosshard.order_keeper.name");
        }
        return super.getDisplayName();
    }

    @Nullable
    @Override
    protected ResourceLocation getLootTable() {
        switch (variant()) {
            case CAPTAIN: return new ResourceLocation(Tags.MOD_ID, "entities/order_captain");
            case KEEPER: return new ResourceLocation(Tags.MOD_ID, "entities/order_keeper");
            default: return new ResourceLocation(Tags.MOD_ID, "entities/order_guard");
        }
    }

    @Nullable
    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        return data;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Variant", variant());
        tag.setBoolean("CalledHelp", calledHelp);
        tag.setLong("Structure", structureId);
        if (hasHome()) {
            tag.setLong("Home", getHomePosition().toLong());
            tag.setInteger("HomeR", (int) getMaximumHomeDistance());
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataManager.set(VARIANT, tag.getInteger("Variant"));
        calledHelp = tag.getBoolean("CalledHelp");
        structureId = tag.hasKey("Structure") ? tag.getLong("Structure") : -1;
        if (tag.hasKey("Home")) {
            setHomePosAndDistance(BlockPos.fromLong(tag.getLong("Home")), tag.getInteger("HomeR"));
        }
        if (variant() == KEEPER) {
            bossInfo.setVisible(true);
        }
        if (hasCustomName()) {
            bossInfo.setName(getDisplayName());
        }
    }

    @Override
    protected net.minecraft.util.SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected net.minecraft.util.SoundEvent getHurtSound(DamageSource src) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Override
    protected net.minecraft.util.SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PLAYER_DEATH;
    }

    // ------------------------------------------------------------------ мирный режим
    /** Мирный режим не уносит сюжетного противника: EntityMob в «Мирном» сам себя убирает. */
    private boolean holdPeace;

    @Override
    public void onUpdate() {
        holdPeace = !world.isRemote && world.getDifficulty() == net.minecraft.world.EnumDifficulty.PEACEFUL;
        try {
            super.onUpdate();
        } finally {
            holdPeace = false;
        }
    }

    @Override
    public void setDead() {
        if (holdPeace && getHealth() > 0 && posY >= -64) {
            return;
        }
        super.setDead();
    }
}
