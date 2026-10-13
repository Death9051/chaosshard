package ru.death.chaosshard.garrot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityWitherSkull;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.breach.Breach;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Гаррот и его люди.
 * LORD — сам тёмный лорд, босс дуэли: удар секирой с замахом (конус перед ним), со второй фазы —
 * шаг сквозь тень за спину и залпы черепов издалека, на третьей — тени-двойники. До смерти не доходит:
 * на 8% здоровья склоняется.
 * SHADOW — тень-двойник с третьей фазы. VASSAL — солдат Мрачной армии на службе (гарнизон Якоря).
 */
public class EntityGarrot extends EntityCreature {

    public static final int LORD = 0, SHADOW = 1, VASSAL = 2;
    private static final DataParameter<Integer> VARIANT = EntityDataManager.createKey(EntityGarrot.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> KNEEL = EntityDataManager.createKey(EntityGarrot.class, DataSerializers.BOOLEAN);
    private static final UUID RAGE_ID = UUID.fromString("2b6f0d4e-71a3-4a9e-b1c2-5f8e0a7d3c11");
    private static final UUID WINDUP_ID = UUID.fromString("9c4e2a10-3d5b-4f71-8e02-6a1b7c9d0e22");

    public static final int CLEAVE_EVERY = 160, CLEAVE_WINDUP = 30, STEP_EVERY = 260, VOLLEY_EVERY = 120, RECOVER = 40;
    public static final float YIELD_AT = 0.08F;

    private final BossInfoServer bossInfo = new BossInfoServer(new TextComponentTranslation("entity.chaosshard.garrot.name"),
            BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10);
    /** Сцена: не дерётся и не выбирает цели, ходит по сценарию. */
    public boolean scripted;
    /** Склонился — неуязвим, ждёт ухода. */
    public boolean yielded;
    /** С кем дуэль: остальные его не ранят. */
    public UUID duelist;
    private int cleaveTimer, windup = -1, stepTimer, volleyTimer, life, recover;
    private boolean phase2, phase3, empowered;
    private long lastHint;

    public EntityGarrot(World world) {
        super(world);
        setSize(0.6F, 1.95F);
        experienceValue = 0;
        bossInfo.setVisible(false);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(VARIANT, LORD);
        dataManager.register(KNEEL, false);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(32.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
    }

    public int variant() {
        return dataManager.get(VARIANT);
    }

    public boolean kneeling() {
        return dataManager.get(KNEEL);
    }

    public void setup(int variant) {
        dataManager.set(VARIANT, variant);
        double hp, dmg, armor;
        switch (variant) {
            case LORD:
                hp = 900; dmg = 14; armor = 18;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
                getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(8);
                getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
                getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.32D);
                bossInfo.setVisible(true);
                break;
            case SHADOW:
                hp = 40; dmg = 8; armor = 4;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
                getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35D);
                setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.shadow").getUnformattedText());
                break;
            default: // VASSAL
                hp = 50; dmg = 7; armor = 8;
                boolean shield = rand.nextBoolean();
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(shield ? Items.IRON_SWORD : Items.IRON_AXE));
                if (shield) {
                    setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                }
                setCustomNameTag(TextFormatting.DARK_RED + new TextComponentTranslation("chaosshard.garrot.vassal").getUnformattedText());
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
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, true));
        tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 0.9D));
        tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 16.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        float dmg = (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        if (empowered) {
            dmg *= 1.5F;
            empowered = false;
        }
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
        if (hit && target instanceof EntityLivingBase) {
            ((EntityLivingBase) target).knockBack(this, 0.5F, MathHelper.sin(rotationYaw * 0.017453292F), -MathHelper.cos(rotationYaw * 0.017453292F));
            applyEnchantments(this, target);
        }
        return hit;
    }

    @Override
    public boolean isOnSameTeam(Entity other) {
        if (variant() != VASSAL && other instanceof EntityGarrot && ((EntityGarrot) other).variant() != VASSAL) {
            return true;
        }
        return super.isOnSameTeam(other);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean isNonBoss() {
        return variant() != LORD;
    }

    // ------------------------------------------------------------------ урон

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (variant() == LORD && !world.isRemote) {
            if (yielded || scripted) {
                return false;
            }
            Entity who = src.getTrueSource();
            // дуэль один на один: чужие удары не в счёт
            if (duelist != null && who != null && !duelist.equals(who.getUniqueID())) {
                if (who instanceof EntityPlayerMP || ru.death.chaosshard.servant.Servants.isServant(who)) {
                    EntityPlayer owner = who instanceof EntityPlayer ? (EntityPlayer) who : ru.death.chaosshard.servant.Servants.owner(who);
                    if (owner != null) {
                        owner.sendStatusMessage(Breach.grimoire("chaosshard.garrot.duel.alone"), true);
                    }
                }
                return false;
            }
        }
        boolean r = super.attackEntityFrom(src, amount);
        if (variant() == LORD && !world.isRemote && !yielded && getHealth() <= getMaxHealth() * YIELD_AT) {
            setHealth(getMaxHealth() * YIELD_AT);
            GarrotDuel.yielded((WorldServer) world, this);
        }
        return r;
    }

    @Override
    public void onDeath(DamageSource cause) {
        if (variant() == LORD && !world.isRemote && !yielded) {
            // до смерти не доходит: склоняется
            setHealth(getMaxHealth() * YIELD_AT);
            GarrotDuel.yielded((WorldServer) world, this);
            return;
        }
        super.onDeath(cause);
    }

    public void kneel(boolean on) {
        dataManager.set(KNEEL, on);
        setSneaking(on);
    }

    // ------------------------------------------------------------------ бой

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote) {
            return;
        }
        int v = variant();
        if (v == SHADOW) {
            if (++life > 1200) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1, posZ, 20, 0.3, 0.6, 0.3, 0.02);
                setDead();
            }
            if (ticksExisted % 4 == 0) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL, posX, posY + 1, posZ, 2, 0.2, 0.5, 0.2, 0.0);
            }
            return;
        }
        if (v != LORD) {
            return;
        }
        bossInfo.setPercent(getHealth() / getMaxHealth());
        // Гаррот без дуэли (сервер перезапустился посреди боя) уходит сам
        if (!GarrotDuel.owns(this)) {
            if (++life > 100) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1, posZ, 40, 0.4, 1.0, 0.4, 0.03);
                setDead();
            }
            return;
        }
        life = 0;
        if (scripted || yielded) {
            setAttackTarget(null);
            windup = -1;
            return;
        }
        EntityLivingBase target = getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        // после удара секирой — передышка: стоит, не бьёт
        if (recover > 0) {
            if (--recover == 0) {
                setSpeed(false);
            }
            if (recover % 5 == 0) {
                ws.spawnParticle(EnumParticleTypes.CLOUD, posX, posY + 2.2, posZ, 2, 0.2, 0.1, 0.2, 0.01);
            }
            return;
        }
        // замах секирой → удар конусом перед собой
        if (windup >= 0) {
            windup++;
            getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
            ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 2.3, posZ, 3, 0.3, 0.1, 0.3, 0.01);
            if (windup >= CLEAVE_WINDUP) {
                windup = -1;
                cleave(ws);
                recover = RECOVER;   // секира ушла в землю — время ударить
            }
            return;
        }
        if (++cleaveTimer >= CLEAVE_EVERY && getDistance(target) < 6) {
            cleaveTimer = 0;
            windup = 0;
            setSpeed(true);
            world.playSound(null, getPosition(), SoundEvents.ENTITY_IRONGOLEM_ATTACK, SoundCategory.HOSTILE, 1.5F, 0.5F);
            if (target instanceof EntityPlayerMP && world.getTotalWorldTime() - lastHint > 600) {
                lastHint = world.getTotalWorldTime();
                ((EntityPlayerMP) target).sendStatusMessage(Breach.grimoire("chaosshard.garrot.duel.cleave"), true);
            }
            return;
        }
        if (!phase2 && getHealth() < getMaxHealth() * 0.6F) {
            phase2 = true;
            GarrotDuel.line(ws, this, "chaosshard.garrot.duel.phase2");
        }
        if (!phase3 && getHealth() < getMaxHealth() * 0.3F) {
            phase3 = true;
            GarrotDuel.line(ws, this, "chaosshard.garrot.duel.phase3");
            IAttributeInstance sp = getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            sp.removeModifier(RAGE_ID);
            sp.applyModifier(new AttributeModifier(RAGE_ID, "Ярость Гаррота", 0.2, 1));
            for (int i = 0; i < 3; i++) {
                EntityGarrot s = new EntityGarrot(world);
                s.setup(SHADOW);
                double a = i * Math.PI * 2 / 3;
                s.setLocationAndAngles(posX + Math.cos(a) * 3, posY, posZ + Math.sin(a) * 3, rand.nextFloat() * 360, 0);
                s.setAttackTarget(target);
                if (hasHome()) {
                    s.setHomePosAndDistance(getHomePosition(), (int) getMaximumHomeDistance());
                }
                world.spawnEntity(s);
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, s.posX, s.posY + 1, s.posZ, 30, 0.3, 0.8, 0.3, 0.02);
            }
            world.playSound(null, getPosition(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.5F, 0.7F);
        }
        if (phase2) {
            // шаг сквозь тень — за спину цели
            if (++stepTimer >= STEP_EVERY) {
                stepTimer = 0;
                Vec3d look = target.getLookVec();
                double bx = target.posX - look.x * 2.5, bz = target.posZ - look.z * 2.5;
                net.minecraft.util.math.BlockPos top = world.getHeight(new net.minecraft.util.math.BlockPos(bx, 0, bz));
                if (Math.abs(top.getY() - target.posY) < 4) {
                    ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1, posZ, 30, 0.3, 0.8, 0.3, 0.02);
                    setPositionAndUpdate(bx, top.getY(), bz);
                    ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1, posZ, 30, 0.3, 0.8, 0.3, 0.02);
                    world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.5F);
                    empowered = true;
                }
            }
            // залп черепов издалека
            if (++volleyTimer >= VOLLEY_EVERY && getDistance(target) > 7) {
                volleyTimer = 0;
                for (int i = -1; i <= 1; i++) {
                    double dx = target.posX - posX, dy = target.posY + target.height / 2 - (posY + 1.6), dz = target.posZ - posZ;
                    double spread = i * 0.25;
                    double rx = dx * Math.cos(spread) - dz * Math.sin(spread), rz = dx * Math.sin(spread) + dz * Math.cos(spread);
                    EntityWitherSkull sk = new EntityWitherSkull(world, this, rx, dy, rz);
                    sk.posY = posY + 1.6;
                    world.spawnEntity(sk);
                }
                world.playSound(null, getPosition(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 1.2F, 0.8F);
            }
        }
    }

    /** Удар секирой: все не свои в 4,5 блока перед ним (±60°). */
    public void cleave(WorldServer ws) {
        double yaw = Math.toRadians(renderYawOffset);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, posX + fx * 2, posY + 1, posZ + fz * 2, 4, 1.2, 0.2, 1.2, 0.0);
        world.playSound(null, getPosition(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0F, 0.6F);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(4.5),
                x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
            double dx = e.posX - posX, dz = e.posZ - posZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 4.5 || d > 0.3 && (dx * fx + dz * fz) / d < 0.5) {
                continue;
            }
            e.attackEntityFrom(DamageSource.causeMobDamage(this), 18.0F);
            e.knockBack(this, 1.5F, -dx / Math.max(0.3, d), -dz / Math.max(0.3, d));
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 1));
        }
    }

    private void setSpeed(boolean windingUp) {
        IAttributeInstance sp = getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        sp.removeModifier(WINDUP_ID);
        if (windingUp) {
            sp.applyModifier(new AttributeModifier(WINDUP_ID, "Замах", -0.9, 2));
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

    public void showBar(boolean on) {
        bossInfo.setVisible(on && variant() == LORD);
    }

    @Override
    public ITextComponent getDisplayName() {
        if (variant() == LORD) {
            TextComponentTranslation t = new TextComponentTranslation("entity.chaosshard.garrot.name");
            t.getStyle().setColor(TextFormatting.DARK_RED);
            return t;
        }
        return super.getDisplayName();
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
        tag.setInteger("Variant", variant());
        tag.setBoolean("Yielded", yielded);
        tag.setBoolean("Phase2", phase2);
        tag.setBoolean("Phase3", phase3);
        tag.setInteger("Life", life);
        if (duelist != null) {
            tag.setUniqueId("Duelist", duelist);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        dataManager.set(VARIANT, tag.getInteger("Variant"));
        yielded = tag.getBoolean("Yielded");
        phase2 = tag.getBoolean("Phase2");
        phase3 = tag.getBoolean("Phase3");
        life = tag.getInteger("Life");
        duelist = tag.hasUniqueId("Duelist") ? tag.getUniqueId("Duelist") : null;
        bossInfo.setVisible(variant() == LORD);
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
