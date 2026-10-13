package ru.death.chaosshard.hero;

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
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.structure.EntityOrderKnight;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Светоч — герой Ордена, страж Опоры Завесы. Боссы Акта 4; повадки — по герою.
 * <p>
 * Каэль Сияющий: спереди почти неуязвим (щит и вера), раз в 8 секунд поднимает клинок —
 * кто в этот миг смотрит на него, слепнет. На половине здоровья зовёт капитанов и начинает
 * рывки; после рывка две секунды открыт со всех сторон.
 */
public class EntityHero extends EntityMob {

    private static final DataParameter<Integer> HERO = EntityDataManager.createKey(EntityHero.class, DataSerializers.VARINT);
    private static final UUID STAGGER_ID = UUID.fromString("6f1c7e8a-0b9d-4f3a-9c21-7a3e5d2b1c40");

    /** Каэль: как часто вспышка и сколько её заряжать, как часто рывок. */
    public static final int FLASH_EVERY = 160, FLASH_CHARGE = 30, DASH_EVERY = 110, DASH_TICKS = 14, STAGGER = 40;
    /** Сколько урона проходит сквозь щит спереди. */
    public static final float FRONT_PASS = 0.1F;

    private final BossInfoServer bossInfo = new BossInfoServer(new TextComponentString(""), BossInfo.Color.YELLOW, BossInfo.Overlay.NOTCHED_10);
    public long structureId = -1;
    private boolean greeted, phase2;
    private int flashTimer, dashTimer, dashing, stagger;
    private long lastFrontHint;

    public EntityHero(World world) {
        super(world);
        setSize(0.7F, 2.2F);
        experienceValue = 400;
        isImmuneToFire = false;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(HERO, 0);
    }

    public Lightbearer hero() {
        int i = dataManager.get(HERO);
        Lightbearer[] all = Lightbearer.values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    /** Настроить героя: здоровье, урон, снаряжение. */
    public void setup(Lightbearer h) {
        dataManager.set(HERO, h.ordinal());
        double hp, dmg, armor;
        switch (h) {
            default: // KAEL
                hp = 600; dmg = 12; armor = 16;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, Trophies.of(Lightbearer.KAEL));
                setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                break;
        }
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(dmg);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(armor);
        getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(6);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) hp);
        for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
            setDropChance(s, 0.0F);
        }
        enablePersistence();
        addTag(EntityOrderKnight.TAG);
        bossInfo.setName(getDisplayName());
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, true));
        tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 0.9D));
        tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
        targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(this, EntityLiving.class, 10, true, false,
                e -> e != null && ru.death.chaosshard.servant.Servants.kindOf(e) != null));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(32.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(12.0D);
    }

    @Override
    public boolean isOnSameTeam(Entity other) {
        return other instanceof EntityOrderKnight || other instanceof EntityHero || super.isOnSameTeam(other);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean isNonBoss() {
        return false;
    }

    public boolean staggered() {
        return stagger > 0;
    }

    // ------------------------------------------------------------------ урон

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!world.isRemote && hero() == Lightbearer.KAEL && !src.isUnblockable() && stagger <= 0 && fromFront(src)) {
            amount *= FRONT_PASS;
            world.playSound(null, getPosition(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0F, 0.8F + rand.nextFloat() * 0.3F);
            Entity who = src.getTrueSource();
            if (who instanceof EntityPlayerMP && world.getTotalWorldTime() - lastFrontHint > 400) {
                lastFrontHint = world.getTotalWorldTime();
                ((EntityPlayerMP) who).sendStatusMessage(Breach.grimoire("chaosshard.hero.kael.front"), true);
            }
        }
        return super.attackEntityFrom(src, amount);
    }

    /** Удар пришёл спереди (в конусе ±60° от взгляда). */
    boolean fromFront(DamageSource src) {
        Vec3d from = src.getDamageLocation();
        if (from == null) {
            return false;
        }
        double dx = from.x - posX, dz = from.z - posZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.0E-3) {
            return true;
        }
        double yaw = Math.toRadians(renderYawOffset);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return (dx * fx + dz * fz) / d > 0.5;
    }

    // ------------------------------------------------------------------ бой

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote) {
            return;
        }
        bossInfo.setPercent(getHealth() / getMaxHealth());
        EntityLivingBase target = getAttackTarget();
        if (!greeted && target instanceof EntityPlayer && getDistanceSq(target) < 24 * 24) {
            greeted = true;
            say("greet");
        }
        if (hero() == Lightbearer.KAEL) {
            kael(target);
        }
        // вне боя понемногу залечивается
        if (target == null && ticksExisted % 40 == 0 && getHealth() < getMaxHealth()) {
            heal(6.0F);
        }
    }

    private void kael(EntityLivingBase target) {
        WorldServer ws = (WorldServer) world;
        if (stagger > 0 && --stagger == 0) {
            setStaggerSlow(false);
        }
        // рывок
        if (dashing > 0) {
            dashing--;
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 1, posZ, 4, 0.2, 0.4, 0.2, 0.02);
            for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(0.8),
                    x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
                if (e.hurtResistantTime <= 10) {
                    e.attackEntityFrom(DamageSource.causeMobDamage(this), 10.0F);
                    e.knockBack(this, 1.6F, posX - e.posX, posZ - e.posZ);
                }
            }
            if (dashing == 0) {
                stagger = STAGGER;
                setStaggerSlow(true);
                ws.spawnParticle(EnumParticleTypes.CRIT, posX, posY + 1.2, posZ, 30, 0.4, 0.5, 0.4, 0.1);
                world.playSound(null, getPosition(), SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, 1.0F, 0.7F);
            }
            return;
        }
        if (target == null || !target.isEntityAlive()) {
            flashTimer = 0;
            return;
        }
        // вторая фаза: капитаны и рывки
        if (!phase2 && getHealth() < getMaxHealth() / 2) {
            phase2 = true;
            say("phase2");
            for (int i = 0; i < 2; i++) {
                EntityOrderKnight k = new EntityOrderKnight(world);
                k.setup(EntityOrderKnight.CAPTAIN);
                k.setLocationAndAngles(posX + rand.nextInt(7) - 3, posY, posZ + rand.nextInt(7) - 3, rand.nextFloat() * 360, 0);
                if (hasHome()) {
                    k.setHomePosAndDistance(getHomePosition(), 16);
                }
                world.spawnEntity(k);
                ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, k.posX, k.posY + 1, k.posZ, 30, 0.3, 0.8, 0.3, 0.05);
            }
            world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 2.0F, 0.8F);
        }
        if (phase2 && stagger <= 0 && ++dashTimer >= DASH_EVERY) {
            double d = getDistance(target);
            if (d > 4 && d < 18) {
                dashTimer = 0;
                double dx = target.posX - posX, dz = target.posZ - posZ;
                double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                motionX = dx / len * 1.5;
                motionZ = dz / len * 1.5;
                motionY = 0.25;
                velocityChanged = true;
                dashing = DASH_TICKS;
                world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.HOSTILE, 1.5F, 1.4F);
                return;
            }
        }
        // ослепляющая вспышка: заряд — и кто смотрит на него, слепнет
        flashTimer++;
        if (flashTimer == FLASH_EVERY - FLASH_CHARGE) {
            world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_PREPARE_BLINDNESS, SoundCategory.HOSTILE, 2.0F, 1.2F);
            for (EntityPlayer pl : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(20))) {
                pl.sendStatusMessage(Breach.grimoire("chaosshard.hero.kael.raise"), true);
            }
        }
        if (flashTimer > FLASH_EVERY - FLASH_CHARGE && flashTimer < FLASH_EVERY) {
            ws.spawnParticle(EnumParticleTypes.END_ROD, posX, posY + 2.6, posZ, 3, 0.1, 0.3, 0.1, 0.02);
        }
        if (flashTimer >= FLASH_EVERY) {
            flashTimer = 0;
            flash(ws);
        }
    }

    /** Вспышка: слепнут все, кто в 18 блоках смотрит на Каэля и видит его. */
    public void flash(WorldServer ws) {
        ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 1.6, posZ, 120, 0.6, 0.6, 0.6, 0.35);
        ws.spawnParticle(EnumParticleTypes.END_ROD, posX, posY + 1.6, posZ, 80, 1.5, 1.5, 1.5, 0.2);
        world.playSound(null, getPosition(), SoundEvents.ENTITY_FIREWORK_TWINKLE, SoundCategory.HOSTILE, 3.0F, 1.0F);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(18),
                x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
            if (!canEntityBeSeen(e)) {
                continue;
            }
            Vec3d look = e.getLook(1.0F);
            Vec3d to = new Vec3d(posX - e.posX, posY + getEyeHeight() - (e.posY + e.getEyeHeight()), posZ - e.posZ).normalize();
            if (look.dotProduct(to) > 0.55) {
                e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 100, 0));
                e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 100, 0));
                e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 4.0F);
            }
        }
    }

    private void setStaggerSlow(boolean on) {
        IAttributeInstance sp = getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        sp.removeModifier(STAGGER_ID);
        if (on) {
            sp.applyModifier(new AttributeModifier(STAGGER_ID, "Каэль открыт", -0.8, 2));
        }
    }

    /** Реплика героя всем игрокам рядом. */
    void say(String what) {
        ITextComponent line = new TextComponentString("<").appendSibling(new TextComponentTranslation(hero().nameKey()))
                .appendSibling(new TextComponentString("> ")).appendSibling(new TextComponentTranslation("chaosshard.hero." + hero().id() + "." + what));
        line.getStyle().setColor(TextFormatting.GOLD);
        for (EntityPlayer pl : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(48))) {
            pl.sendMessage(line);
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!world.isRemote) {
            Entity k = cause.getTrueSource();
            EntityPlayerMP killer = k instanceof EntityPlayerMP ? (EntityPlayerMP) k : null;
            if (killer == null && k != null && ru.death.chaosshard.servant.Servants.owner(k) instanceof EntityPlayerMP) {
                killer = (EntityPlayerMP) ru.death.chaosshard.servant.Servants.owner(k);
            }
            say("death");
            Pillars.fall((WorldServer) world, hero(), getPosition(), killer);
        }
    }

    // ------------------------------------------------------------------ служебное

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
        TextComponentTranslation t = new TextComponentTranslation(hero().nameKey());
        t.getStyle().setColor(TextFormatting.GOLD);
        return t;
    }

    @Nullable
    @Override
    protected ResourceLocation getLootTable() {
        return null;
    }

    @Nullable
    @Override
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        return data;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Hero", dataManager.get(HERO));
        tag.setLong("Structure", structureId);
        tag.setBoolean("Greeted", greeted);
        tag.setBoolean("Phase2", phase2);
        if (hasHome()) {
            tag.setLong("Home", getHomePosition().toLong());
            tag.setInteger("HomeR", (int) getMaximumHomeDistance());
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataManager.set(HERO, tag.getInteger("Hero"));
        structureId = tag.hasKey("Structure") ? tag.getLong("Structure") : -1;
        greeted = tag.getBoolean("Greeted");
        phase2 = tag.getBoolean("Phase2");
        if (tag.hasKey("Home")) {
            setHomePosAndDistance(BlockPos.fromLong(tag.getLong("Home")), tag.getInteger("HomeR"));
        }
        bossInfo.setName(getDisplayName());
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
}
