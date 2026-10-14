package ru.death.chaosshard.act5;

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
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.hero.Pose;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.structure.EntityOrderKnight;

import javax.annotation.Nullable;

/**
 * Король Лучезарии, отец Элис. Босс столицы (Акт 5).
 * <p>
 * Указ: раз в 13 секунд две секунды поднимает руки — «на колени!». Не сбили 30 урона — всех рядом
 * давит к земле (Медлительность III, Слабость II); сбили — Король оглушён и открыт.
 * На 75% и 25% зовёт королевскую стражу. С половины здоровья вспоминает Светочей и по очереди
 * берёт их приёмы: вспышку Каэля, свет Ирены, огонь Вельмора, песнь Лиана, рывки Элис.
 */
public class EntityKing extends EntityMob implements Pose.Posed {

    private static final DataParameter<Byte> POSE = EntityDataManager.createKey(EntityKing.class, DataSerializers.BYTE);

    public static final int DECREE_EVERY = 260, DECREE_CHARGE = 40, STAGGER = 90, ECHO_EVERY = 200;
    /** Сколько урона надо нанести за время Указа, чтобы его сбить; песнь Лиана сбивается так же. */
    public static final float DECREE_BREAK = 30F;
    public static final float[] GUARD_AT = {0.75F, 0.25F};
    /** Приёмы Светочей во второй фазе. */
    public static final int ECHO_KAEL = 0, ECHO_IRENA = 1, ECHO_VELMOR = 2, ECHO_LIAN = 3, ECHO_ELIS = 4;

    private final BossInfoServer bossInfo = new BossInfoServer(new TextComponentString(""), BossInfo.Color.YELLOW, BossInfo.Overlay.NOTCHED_10);
    private int poseTicks;
    /** Сцена появления: не дерётся, неуязвим. */
    public boolean scripted;
    private boolean greeted, phase2;
    private int decreeTimer, decreeCharge = -1, stagger, guards;
    private float decreeDamage;
    private int echoTimer, echo = -1, echoTicks, nextEcho;
    private float echoDamage;
    private int dashLeft, dashGap;

    public EntityKing(World world) {
        super(world);
        setSize(0.7F, 2.2F);
        experienceValue = 800;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(POSE, (byte) 0);
    }

    public void setup() {
        double hp = ChaosConfig.order.kingHealth;
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(14);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(16);
        getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(8);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) hp);
        setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
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
                e -> e != null && Servants.isServant(e)));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(32.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(14.0D);
    }

    @Override
    public boolean isOnSameTeam(Entity other) {
        return other instanceof EntityOrderKnight && !Servants.isServant(other) || super.isOnSameTeam(other);
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

    public boolean charging() {
        return decreeCharge >= 0;
    }

    public int echo() {
        return echo;
    }

    public boolean phase2() {
        return phase2;
    }

    // ------------------------------------------------------------------ урон

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (scripted && !src.canHarmInCreative()) {
            return false;
        }
        if (!world.isRemote && stagger > 0) {
            amount *= 1.5F;
        }
        float before = getHealth();
        boolean hurt = super.attackEntityFrom(src, amount);
        if (!hurt || world.isRemote) {
            return hurt;
        }
        float dealt = before - getHealth();
        if (decreeCharge >= 0) {
            decreeDamage += dealt;
            if (decreeDamage >= DECREE_BREAK) {
                decreeCharge = -1;
                stun("decree_broken");
            }
        }
        if (echo == ECHO_LIAN) {
            echoDamage += dealt;
            if (echoDamage >= DECREE_BREAK) {
                endEcho();
                stun("song_broken");
            }
        }
        if (isEntityAlive() && guards < GUARD_AT.length && getHealth() < getMaxHealth() * GUARD_AT[guards]) {
            guards++;
            callGuard();
        }
        if (isEntityAlive() && !phase2 && getHealth() < getMaxHealth() / 2) {
            phase2 = true;
            echoTimer = ECHO_EVERY - 40;
            say("phase2");
            ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, posX, posY + 1.2, posZ, 120, 1.5, 1.5, 1.5, 0.2);
        }
        return hurt;
    }

    void stun(String line) {
        stagger = STAGGER;
        setPose(Pose.STAGGER, STAGGER);
        say(line);
        world.playSound(null, getPosition(), SoundEvents.ENTITY_IRONGOLEM_HURT, SoundCategory.HOSTILE, 1.5F, 0.7F);
    }

    // ------------------------------------------------------------------ бой

    @Override
    public int pose() {
        return dataManager.get(POSE);
    }

    public void setPose(int pose, int ticks) {
        if (pose() != pose) {
            dataManager.set(POSE, (byte) pose);
        }
        poseTicks = ticks;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!world.isRemote && poseTicks > 0 && --poseTicks == 0) {
            dataManager.set(POSE, (byte) 0);
        }
        if (world.isRemote) {
            return;
        }
        bossInfo.setPercent(getHealth() / getMaxHealth());
        if (scripted) {
            setAttackTarget(null);
            getNavigator().clearPath();
            return;
        }
        WorldServer ws = (WorldServer) world;
        EntityLivingBase target = getAttackTarget();
        if (!greeted && target instanceof EntityPlayer && getDistanceSq(target) < 24 * 24) {
            greeted = true;
            say("greet");
        }
        if (stagger > 0) {
            stagger--;
            getNavigator().clearPath();
            if (stagger % 10 == 0) {
                ws.spawnParticle(EnumParticleTypes.CRIT, posX, posY + 2.3, posZ, 6, 0.3, 0.1, 0.3, 0.05);
            }
            return;
        }
        if (target == null || !target.isEntityAlive()) {
            if (ticksExisted % 40 == 0 && getHealth() < getMaxHealth()) {
                heal(10);
            }
            return;
        }
        tickDecree(ws);
        if (phase2) {
            tickEcho(ws, target);
        }
    }

    /** Указ: заряд — потом все рядом на колени. */
    void tickDecree(WorldServer ws) {
        if (decreeCharge >= 0) {
            decreeCharge++;
            getNavigator().clearPath();
            if (decreeCharge % 4 == 0) {
                double a = decreeCharge * 0.3;
                for (int k = 0; k < 6; k++) {
                    double r = 6;
                    ws.spawnParticle(EnumParticleTypes.END_ROD, posX + Math.cos(a + k) * r, posY + 0.2, posZ + Math.sin(a + k) * r, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (decreeCharge >= DECREE_CHARGE) {
                decreeCharge = -1;
                decree(ws);
            }
            return;
        }
        if (echo >= 0) {
            return;
        }
        if (++decreeTimer >= DECREE_EVERY) {
            startDecree();
        }
    }

    /** Начать Указ: DECREE_CHARGE тиков заряда, потом удар. */
    public void startDecree() {
        decreeTimer = 0;
        decreeCharge = 0;
        decreeDamage = 0;
        setPose(Pose.RAISE_BOTH, DECREE_CHARGE);
        say("decree");
        world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_PREPARE_BLINDNESS, SoundCategory.HOSTILE, 2.0F, 0.6F);
    }

    /** Все враги в 20 блоках — на колени. */
    public void decree(WorldServer ws) {
        setPose(Pose.CAST_DOWN, 20);
        world.playSound(null, getPosition(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0F, 0.5F);
        ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 0.5, posZ, 150, 6, 0.2, 6, 0.05);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(20),
                x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
            e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 120, 2));
            e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 120, 1));
            e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 4.0F);
        }
    }

    /** Королевская стража: два капитана. */
    void callGuard() {
        say("guard");
        for (int i = 0; i < 2; i++) {
            EntityOrderKnight g = new EntityOrderKnight(world);
            g.setup(EntityOrderKnight.CAPTAIN);
            g.setLocationAndAngles(posX + rand.nextInt(5) - 2, posY, posZ + rand.nextInt(5) - 2, rand.nextFloat() * 360, 0);
            g.addTag(TAG_ROYAL);
            world.spawnEntity(g);
            g.setAttackTarget(getAttackTarget());
            ((WorldServer) world).spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, g.posX, g.posY + 1, g.posZ, 30, 0.3, 0.8, 0.3, 0.05);
        }
        world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_BASS, SoundCategory.HOSTILE, 3.0F, 0.5F);
    }

    public static final String TAG_ROYAL = "chaosshard_royal_guard";

    // ------------------------------------------------------------------ приёмы Светочей

    void tickEcho(WorldServer ws, EntityLivingBase target) {
        if (echo < 0) {
            if (decreeCharge < 0 && ++echoTimer >= ECHO_EVERY) {
                echoTimer = 0;
                startEcho(nextEcho);
                nextEcho = (nextEcho + 1) % 5;
            }
            return;
        }
        echoTicks++;
        switch (echo) {
            case ECHO_KAEL:
                if (echoTicks == 1) {
                    setPose(Pose.RAISE, 30);
                }
                if (echoTicks < 30) {
                    ws.spawnParticle(EnumParticleTypes.END_ROD, posX, posY + 2.8, posZ, 3, 0.1, 0.3, 0.1, 0.02);
                } else {
                    flash(ws);
                    endEcho();
                }
                break;
            case ECHO_IRENA:
                if (echoTicks == 1) {
                    setPose(Pose.PRAY, 30);
                }
                if (echoTicks % 3 == 0) {
                    double a = echoTicks * 0.4;
                    ws.spawnParticle(EnumParticleTypes.END_ROD, posX + Math.cos(a) * 5, posY + 0.2, posZ + Math.sin(a) * 5, 2, 0.0, 0.1, 0.0, 0.0);
                }
                if (echoTicks >= 30) {
                    for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(5),
                            x -> x != this && !isOnSameTeam(x))) {
                        e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 8.0F);
                    }
                    ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 0.5, posZ, 80, 3, 0.3, 3, 0.05);
                    world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_BELL, SoundCategory.HOSTILE, 2.0F, 1.4F);
                    endEcho();
                }
                break;
            case ECHO_VELMOR:
                if (echoTicks % 6 == 1 && canEntityBeSeen(target)) {
                    setPose(Pose.CAST, 8);
                    double dx = target.posX - posX, dy = target.posY + target.height / 2 - (posY + 1.8), dz = target.posZ - posZ;
                    net.minecraft.entity.projectile.EntitySmallFireball f = new net.minecraft.entity.projectile.EntitySmallFireball(world, this,
                            dx + rand.nextGaussian() * 0.5, dy, dz + rand.nextGaussian() * 0.5);
                    f.posY = posY + 1.8;
                    world.spawnEntity(f);
                    world.playSound(null, getPosition(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0F, 1.2F);
                }
                if (echoTicks >= 30) {
                    endEcho();
                }
                break;
            case ECHO_LIAN:
                if (echoTicks == 1) {
                    setPose(Pose.RAISE_BOTH, 100);
                    say("song");
                }
                getNavigator().clearPath();
                if (echoTicks % 20 == 0) {
                    heal(8);
                    world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_HARP, SoundCategory.HOSTILE, 2.0F, 0.7F + echoTicks / 200F);
                }
                if (echoTicks % 4 == 0) {
                    ws.spawnParticle(EnumParticleTypes.NOTE, posX + (rand.nextDouble() - 0.5) * 2, posY + 2.5, posZ + (rand.nextDouble() - 0.5) * 2,
                            0, rand.nextDouble(), 0, 0, 1.0);
                }
                if (echoTicks >= 100) {
                    endEcho();
                }
                break;
            default: // ELIS
                if (echoTicks == 1) {
                    dashLeft = 3;
                    dashGap = 0;
                }
                if (dashLeft > 0 && --dashGap <= 0) {
                    dashGap = 12;
                    dashLeft--;
                    setPose(Pose.DASH, 10);
                    Vec3d d = new Vec3d(target.posX - posX, 0, target.posZ - posZ).normalize();
                    motionX = d.x * 1.6;
                    motionZ = d.z * 1.6;
                    motionY = 0.2;
                    velocityChanged = true;
                    world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.HOSTILE, 1.0F, 1.4F);
                }
                if (dashLeft <= 0 && dashGap <= 0) {
                    endEcho();
                }
                break;
        }
    }

    void startEcho(int which) {
        echo = which;
        echoTicks = 0;
        echoDamage = 0;
        say("echo." + which);
    }

    void endEcho() {
        echo = -1;
        echoTicks = 0;
    }

    /** Вспышка Каэля: слепнут все, кто смотрит на Короля. */
    public void flash(WorldServer ws) {
        ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 1.6, posZ, 120, 0.6, 0.6, 0.6, 0.35);
        world.playSound(null, getPosition(), SoundEvents.ENTITY_FIREWORK_TWINKLE, SoundCategory.HOSTILE, 3.0F, 1.0F);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(18),
                x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
            if (!canEntityBeSeen(e)) {
                continue;
            }
            Vec3d look = e.getLook(1.0F);
            Vec3d to = new Vec3d(posX - e.posX, posY + getEyeHeight() - (e.posY + e.getEyeHeight()), posZ - e.posZ).normalize();
            if (look.dotProduct(to) > 0.55) {
                e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 80, 0));
                e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 4.0F);
            }
        }
    }

    /** Реплика Короля всем игрокам рядом. */
    void say(String what) {
        ITextComponent line = new TextComponentString("<").appendSibling(new TextComponentTranslation("entity.chaosshard.king.name"))
                .appendSibling(new TextComponentString("> ")).appendSibling(new TextComponentTranslation("chaosshard.king." + what));
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
            if (killer == null && k != null && Servants.owner(k) instanceof EntityPlayerMP) {
                killer = (EntityPlayerMP) Servants.owner(k);
            }
            say("death");
            Crown.kingFell((WorldServer) world, getPosition(), killer);
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
        TextComponentTranslation t = new TextComponentTranslation("entity.chaosshard.king.name");
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
        tag.setBoolean("Greeted", greeted);
        tag.setBoolean("Phase2", phase2);
        tag.setInteger("Guards", guards);
        tag.setInteger("NextEcho", nextEcho);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        greeted = tag.getBoolean("Greeted");
        phase2 = tag.getBoolean("Phase2");
        guards = tag.getInteger("Guards");
        nextEcho = tag.getInteger("NextEcho");
        bossInfo.setName(getDisplayName());
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
