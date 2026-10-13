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
    public static final int FLASH_EVERY = 180, FLASH_CHARGE = 30, DASH_EVERY = 140, DASH_TICKS = 14, STAGGER = 80, FLASH_OPEN = 50;
    /** Сколько урона проходит сквозь щит спереди. */
    public static final float FRONT_PASS = 0.1F;

    private final BossInfoServer bossInfo = new BossInfoServer(new TextComponentString(""), BossInfo.Color.YELLOW, BossInfo.Overlay.NOTCHED_10);
    public long structureId = -1;
    private boolean greeted, phase2;
    /** Сцена появления: не дерётся, неуязвим. */
    public boolean scripted;
    /** Ирена: таймеры святой земли, лечения стражи, щита; щит держится shield тиков. */
    private int holyTimer, holyCharge = -1, shieldTimer, shield;
    private long lastLitHint;
    /** Вельмор: снаряды, ловушки, отскок; сколько раз уже сбегал; где прячется; оглушение. */
    private int boltTimer, trapTimer, blinkTimer, escapes, awayTicks, stun;
    private BlockPos away, pillarHome;
    private int pillarHomeR;
    private boolean returnAfterStun;
    private final java.util.List<double[]> traps = new java.util.ArrayList<>();
    /** Лиан: какая песнь (-1 — молчит), сколько тянет, сколько урона сбило, пауза, тишина. */
    private int song = -1, songTicks, songGap, silence;
    private float songDamage;
    private int nextSong;
    /** Элис: рывки цепочкой, передышка, сколько раз звала стражу. */
    private int chainTimer, chainLeft, chainGap, winded, calls;
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
            case LIAN:
                hp = 520; dmg = 7; armor = 8;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, Trophies.of(Lightbearer.LIAN));
                break;
            case ELIS:
                hp = 900; dmg = 14; armor = 18;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                setItemStackToSlot(EntityEquipmentSlot.OFFHAND, Trophies.of(Lightbearer.ELIS));
                getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.34D);
                break;
            case VELMOR:
                hp = 650; dmg = 8; armor = 10;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, Trophies.of(Lightbearer.VELMOR));
                break;
            case IRENA:
                hp = 700; dmg = 9; armor = 12;
                setItemStackToSlot(EntityEquipmentSlot.MAINHAND, Trophies.of(Lightbearer.IRENA));
                break;
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
                e -> e != null && ru.death.chaosshard.servant.Servants.isServant(e)));
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
        return other instanceof EntityOrderKnight && !ru.death.chaosshard.servant.Servants.isServant(other)
                || other instanceof EntityHero || super.isOnSameTeam(other);
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
        if (scripted && !src.canHarmInCreative()) {
            return false;
        }
        if (!world.isRemote && hero() == Lightbearer.VELMOR && away != null && stun <= 0 && !src.canHarmInCreative()) {
            world.playSound(null, getPosition(), SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.0F, 0.6F);
            Entity who = src.getTrueSource();
            if (who instanceof EntityPlayerMP && world.getTotalWorldTime() - lastLitHint > 200) {
                lastLitHint = world.getTotalWorldTime();
                ((EntityPlayerMP) who).sendStatusMessage(Breach.grimoire("chaosshard.hero.velmor.warded"), true);
            }
            return false;
        }
        if (!world.isRemote && hero() == Lightbearer.IRENA) {
            if (shield > 0 && !src.canHarmInCreative()) {
                world.playSound(null, getPosition(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.6F, 1.8F);
                return false;
            }
            Entity who = src.getTrueSource();
            if (who instanceof EntityPlayerMP && world.getTotalWorldTime() - lastLitHint > 300) {
                int lit = Pillars.lit(world, Lightbearer.IRENA);
                if (lit > 0) {
                    lastLitHint = world.getTotalWorldTime();
                    ((EntityPlayerMP) who).sendStatusMessage(Breach.grimoire("chaosshard.hero.irena.lit", lit, HeroData.get(world).of(Lightbearer.IRENA).linked.size()), true);
                }
            }
        }
        if (!world.isRemote && hero() == Lightbearer.KAEL && !src.isUnblockable() && stagger <= 0 && fromFront(src)) {
            amount *= FRONT_PASS;
            world.playSound(null, getPosition(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0F, 0.8F + rand.nextFloat() * 0.3F);
            Entity who = src.getTrueSource();
            if (who instanceof EntityPlayerMP && world.getTotalWorldTime() - lastFrontHint > 400) {
                lastFrontHint = world.getTotalWorldTime();
                ((EntityPlayerMP) who).sendStatusMessage(Breach.grimoire("chaosshard.hero.kael.front"), true);
            }
        }
        if (!world.isRemote && hero() == Lightbearer.LIAN && silence > 0) {
            amount *= 1.5F;
        }
        if (!world.isRemote && hero() == Lightbearer.ELIS) {
            if (winded > 0) {
                amount *= 1.5F;
            } else if (guarded()) {
                amount *= 0.4F;
                Entity who = src.getTrueSource();
                if (who instanceof EntityPlayerMP && world.getTotalWorldTime() - lastLitHint > 300) {
                    lastLitHint = world.getTotalWorldTime();
                    ((EntityPlayerMP) who).sendStatusMessage(Breach.grimoire("chaosshard.hero.elis.guarded"), true);
                }
            }
        }
        float before = getHealth();
        boolean hurt = super.attackEntityFrom(src, amount);
        if (hurt && !world.isRemote && hero() == Lightbearer.LIAN && song >= 0) {
            songDamage += before - getHealth();
            if (songDamage >= songBreak()) {
                silenced();
                Entity who = src.getTrueSource();
                if (who instanceof EntityPlayerMP) {
                    ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) who, "lian_silenced");
                } else if (who != null && ru.death.chaosshard.servant.Servants.owner(who) instanceof EntityPlayerMP) {
                    ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) ru.death.chaosshard.servant.Servants.owner(who), "lian_silenced");
                }
            }
        }
        if (hurt && !world.isRemote && hero() == Lightbearer.ELIS && isEntityAlive() && calls < ELIS_CALL_AT.length
                && getHealth() < getMaxHealth() * ELIS_CALL_AT[calls]) {
            calls++;
            callGuard();
        }
        if (hurt && !world.isRemote && hero() == Lightbearer.VELMOR && isEntityAlive() && escapes < ESCAPE_AT.length
                && getHealth() < getMaxHealth() * ESCAPE_AT[escapes]) {
            escapes++;
            escape();
        }
        return hurt;
    }

    /** Вельмор сбегает к печати на этих долях здоровья. */
    public static final float[] ESCAPE_AT = {0.66F, 0.33F};

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
        if (scripted) {
            setAttackTarget(null);
            return;
        }
        EntityLivingBase target = getAttackTarget();
        if (!greeted && target instanceof EntityPlayer && getDistanceSq(target) < 24 * 24) {
            greeted = true;
            say("greet");
        }
        if (hero() == Lightbearer.KAEL) {
            kael(target);
        } else if (hero() == Lightbearer.IRENA) {
            irena(target);
        } else if (hero() == Lightbearer.VELMOR) {
            velmor(target);
        } else if (hero() == Lightbearer.LIAN) {
            lian(target);
        } else if (hero() == Lightbearer.ELIS) {
            elis(target);
        }
        // вне боя понемногу залечивается
        if (target == null && ticksExisted % 40 == 0 && getHealth() < getMaxHealth()) {
            heal(6.0F);
        }
    }

    /**
     * Ирена: пока горят монастыри, молитва лечит её (6 здоровья в секунду за каждый); раз в 5 секунд
     * освящает землю под собой — кто рядом, обжигается светом; лечит стражу. С половины здоровья —
     * святой щит на 3 секунды раз в 15 и двое стражей на помощь.
     */
    private void irena(EntityLivingBase target) {
        WorldServer ws = (WorldServer) world;
        if (shield > 0) {
            shield--;
            if (shield % 3 == 0) {
                ws.spawnParticle(EnumParticleTypes.END_ROD, posX, posY + 1.2, posZ, 6, 0.6, 0.9, 0.6, 0.01);
            }
        }
        if (ticksExisted % 20 == 0) {
            int lit = Pillars.lit(world, Lightbearer.IRENA);
            if (lit > 0 && getHealth() < getMaxHealth()) {
                heal(6.0F * lit);
                ws.spawnParticle(EnumParticleTypes.HEART, posX, posY + 2.4, posZ, lit, 0.4, 0.2, 0.4, 0.0);
            }
        }
        if (target == null || !target.isEntityAlive()) {
            holyCharge = -1;
            return;
        }
        // освящение: заряд секунду, потом свет бьёт всех не своих в 5 блоках
        if (holyCharge >= 0) {
            holyCharge++;
            for (int i = 0; i < 6; i++) {
                double a = rand.nextDouble() * Math.PI * 2;
                ws.spawnParticle(EnumParticleTypes.END_ROD, posX + Math.cos(a) * 5, posY + 0.1, posZ + Math.sin(a) * 5, 1, 0, 0.05, 0, 0.0);
            }
            if (holyCharge >= 30) {
                holyCharge = -1;
                ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 0.3, posZ, 80, 2.5, 0.2, 2.5, 0.05);
                world.playSound(null, getPosition(), SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 1.5F, 1.4F);
                for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(5),
                        x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
                    e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 6.0F);
                    e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0));
                }
                for (EntityOrderKnight k : world.getEntitiesWithinAABB(EntityOrderKnight.class, getEntityBoundingBox().grow(16))) {
                    k.heal(10.0F);
                    ws.spawnParticle(EnumParticleTypes.HEART, k.posX, k.posY + 2, k.posZ, 2, 0.3, 0.2, 0.3, 0.0);
                }
            }
            return;
        }
        if (++holyTimer >= 100) {
            holyTimer = 0;
            holyCharge = 0;
            world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_BELL, SoundCategory.HOSTILE, 2.0F, 1.2F);
        }
        if (!phase2 && getHealth() < getMaxHealth() / 2) {
            phase2 = true;
            say("phase2");
            for (int i = 0; i < 2; i++) {
                EntityOrderKnight k = new EntityOrderKnight(world);
                k.setup(EntityOrderKnight.GUARD);
                k.setLocationAndAngles(posX + rand.nextInt(7) - 3, posY, posZ + rand.nextInt(7) - 3, rand.nextFloat() * 360, 0);
                if (hasHome()) {
                    k.setHomePosAndDistance(getHomePosition(), 16);
                }
                world.spawnEntity(k);
                ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, k.posX, k.posY + 1, k.posZ, 30, 0.3, 0.8, 0.3, 0.05);
            }
        }
        if (phase2 && ++shieldTimer >= 400) {
            shieldTimer = 0;
            shield = 40;
            world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.HOSTILE, 2.0F, 1.0F);
        }
    }

    /**
     * Вельмор: огненные стрелы издалека, рунные ловушки под ногами (секунда на то, чтобы сойти),
     * отскок, если подошли вплотную. На 66% и 33% сбегает к целой печати отступления и прячется за щитом,
     * пока печать не разобьют (тогда оглушён и открыт) или пока не отдышится (30 секунд) — потом возвращается.
     * Если печатей не осталось — побег срывается, он оглушён.
     */
    private void velmor(EntityLivingBase target) {
        WorldServer ws = (WorldServer) world;
        tickTraps(ws);
        if (stun > 0) {
            stun--;
            motionX = motionZ = 0;
            if (stun % 4 == 0) {
                ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, posX, posY + 2.3, posZ, 6, 0.3, 0.2, 0.3, 0.05);
            }
            if (stun == 0 && returnAfterStun) {
                returnAfterStun = false;
                goHome();
            }
            return;
        }
        if (away != null) {
            awayTicks++;
            setAttackTarget(null);
            if (awayTicks % 20 == 0 && getHealth() < getMaxHealth()) {
                heal(getMaxHealth() * 0.005F);
            }
            if (awayTicks % 3 == 0) {
                double a = awayTicks * 0.3;
                ws.spawnParticle(EnumParticleTypes.PORTAL, posX + Math.cos(a) * 1.2, posY + 1.2, posZ + Math.sin(a) * 1.2, 3, 0, 0.4, 0, 0.3);
            }
            if (awayTicks >= 600) {
                goHome();
            }
            return;
        }
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        double d = getDistance(target);
        // отскок от того, кто подошёл вплотную
        if (++blinkTimer >= 160 && d < 2.5) {
            blinkTimer = 0;
            for (int i = 0; i < 8; i++) {
                double a = rand.nextDouble() * Math.PI * 2, r = 7 + rand.nextInt(4);
                BlockPos at = world.getHeight(new BlockPos(posX + Math.cos(a) * r, 0, posZ + Math.sin(a) * r));
                if (Math.abs(at.getY() - posY) < 4 && (!hasHome() || at.distanceSq(getHomePosition()) < getMaximumHomeDistance() * getMaximumHomeDistance())) {
                    blink(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    break;
                }
            }
        }
        // огненные стрелы
        if (++boltTimer >= 60 && canEntityBeSeen(target)) {
            boltTimer = 0;
            for (int i = 0; i < 2; i++) {
                double dx = target.posX - posX, dy = target.posY + target.height / 2 - (posY + 1.8), dz = target.posZ - posZ;
                net.minecraft.entity.projectile.EntitySmallFireball f = new net.minecraft.entity.projectile.EntitySmallFireball(world, this,
                        dx + rand.nextGaussian() * 0.6, dy, dz + rand.nextGaussian() * 0.6);
                f.posY = posY + 1.8;
                world.spawnEntity(f);
            }
            world.playSound(null, getPosition(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0F, 1.2F);
        }
        // рунные ловушки под целью
        if (++trapTimer >= 160) {
            trapTimer = 0;
            for (int i = 0; i < 3; i++) {
                double tx = target.posX + (i == 0 ? 0 : rand.nextInt(7) - 3), tz = target.posZ + (i == 0 ? 0 : rand.nextInt(7) - 3);
                traps.add(new double[]{tx, target.posY, tz, 0});
            }
            world.playSound(null, target.getPosition(), SoundEvents.ENTITY_ILLAGER_PREPARE_MIRROR, SoundCategory.HOSTILE, 1.5F, 1.0F);
        }
    }

    private void tickTraps(WorldServer ws) {
        java.util.Iterator<double[]> it = traps.iterator();
        while (it.hasNext()) {
            double[] t = it.next();
            t[3]++;
            if (t[3] % 2 == 0) {
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4 + t[3] * 0.1;
                    ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, t[0] + Math.cos(a) * 2, t[1] + 0.1, t[2] + Math.sin(a) * 2, 1, 0, 0, 0, 0.0);
                }
            }
            if (t[3] >= TRAP_DELAY) {
                it.remove();
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, t[0], t[1] + 0.5, t[2], 1, 0, 0, 0, 0.0);
                world.playSound(null, new BlockPos(t[0], t[1], t[2]), SoundEvents.EVOCATION_FANGS_ATTACK, SoundCategory.HOSTILE, 1.2F, 1.0F);
                for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class,
                        new net.minecraft.util.math.AxisAlignedBB(t[0] - 2, t[1] - 1, t[2] - 2, t[0] + 2, t[1] + 2, t[2] + 2),
                        x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
                    e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), 6.0F);
                    e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
                }
            }
        }
    }

    public static final int TRAP_DELAY = 30;

    private void blink(double x, double y, double z) {
        ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 1, posZ, 40, 0.3, 0.8, 0.3, 0.6);
        setPositionAndUpdate(x, y, z);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 1, posZ, 40, 0.3, 0.8, 0.3, 0.6);
        world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.2F);
    }

    /** Сбежать к ближайшей целой печати. */
    void escape() {
        java.util.List<BlockPos> seals = Pillars.intactSeals(world, Lightbearer.VELMOR);
        if (seals.isEmpty()) {
            say("no_seals");
            stun = 100;
            return;
        }
        BlockPos seal = seals.get(rand.nextInt(seals.size()));
        if (pillarHome == null && hasHome()) {
            pillarHome = getHomePosition();
            pillarHomeR = (int) getMaximumHomeDistance();
        }
        say("escape");
        BlockPos at = world.getHeight(seal.add(2, 0, 0));
        blink(seal.getX() + 2.5, Math.max(at.getY(), seal.getY() - 1), seal.getZ() + 0.5);
        away = seal;
        awayTicks = 0;
        setHomePosAndDistance(seal, 4);
        setAttackTarget(null);
        for (EntityPlayer pl : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(160))) {
            pl.sendMessage(Breach.grimoire("chaosshard.hero.velmor.fled", seal.getX(), seal.getZ()));
        }
    }

    /** Печать разбита: если он прятался за ней — оглушён и открыт, потом вернётся на Опору. */
    public void onSealBroken(BlockPos pos) {
        if (away != null && away.equals(pos)) {
            away = null;
            stun = 100;
            returnAfterStun = true;
            setHealth(Math.max(1, getHealth() - getMaxHealth() * 0.1F));
            say("seal_here");
        }
    }

    void goHome() {
        away = null;
        awayTicks = 0;
        if (pillarHome != null) {
            setHomePosAndDistance(pillarHome, pillarHomeR);
            blink(pillarHome.getX() + 0.5, pillarHome.getY(), pillarHome.getZ() + 0.5);
        }
        say("back");
    }

    public boolean warded() {
        return away != null;
    }

    public boolean stunned() {
        return stun > 0;
    }

    // ------------------------------------------------------------------ Лиан

    /** Песни: доблесть (стражу — сила и стойкость), хор (ещё двое стражей), колыбельная (врагов — замедление и слабость). */
    public static final int SONG_VALOR = 0, SONG_CHOIR = 1, SONG_LULLABY = 2;
    public static final int SONG_LEN = 140, SONG_LEN2 = 100, SONG_GAP = 140, SILENCE = 240;

    float songBreak() {
        return phase2 ? 40F : 30F;
    }

    /**
     * Лиан поёт, стоя на месте: ноты над головой. Сбить песню можно только уроном — 30 (на второй половине 40)
     * за время песни. Сбитый Лиан замолкает на 8 секунд и получает в полтора раза больше урона.
     * Допетая песнь работает: доблесть — страже сила, хор — двое новых стражей, колыбельная — всем врагам замедление.
     */
    private void lian(EntityLivingBase target) {
        WorldServer ws = (WorldServer) world;
        if (silence > 0) {
            silence--;
            if (silence % 6 == 0) {
                ws.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, posX, posY + 2.4, posZ, 4, 0.2, 0.1, 0.2, 0.01);
            }
            return;
        }
        if (!phase2 && getHealth() < getMaxHealth() / 2) {
            phase2 = true;
            say("phase2");
        }
        if (song >= 0) {
            songTicks++;
            motionX = motionZ = 0;
            getNavigator().clearPath();
            if (songTicks % 4 == 0) {
                ws.spawnParticle(EnumParticleTypes.NOTE, posX + (rand.nextDouble() - 0.5), posY + 2.5, posZ + (rand.nextDouble() - 0.5), 0,
                        rand.nextDouble(), 0, 0, 1.0);
            }
            if (songTicks % 10 == 0) {
                world.playSound(null, getPosition(), song == SONG_LULLABY ? SoundEvents.BLOCK_NOTE_FLUTE : song == SONG_CHOIR
                        ? SoundEvents.BLOCK_NOTE_CHIME : SoundEvents.BLOCK_NOTE_HARP, SoundCategory.HOSTILE, 2.0F,
                        0.6F + ((songTicks / 10) % 5) * 0.15F);
            }
            if (songTicks >= (phase2 ? SONG_LEN2 : SONG_LEN)) {
                finishSong(ws);
            }
            return;
        }
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        if (++songGap >= SONG_GAP) {
            songGap = 0;
            song = nextSong;
            nextSong = (nextSong + 1) % 3;
            songTicks = 0;
            songDamage = 0;
            for (EntityPlayer pl : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(24))) {
                pl.sendStatusMessage(Breach.grimoire("chaosshard.hero.lian.song" + song, (int) songBreak()), true);
            }
        }
    }

    private void finishSong(WorldServer ws) {
        int s = song;
        song = -1;
        ws.spawnParticle(EnumParticleTypes.NOTE, posX, posY + 2, posZ, 30, 3, 1, 3, 1.0);
        switch (s) {
            case SONG_VALOR:
                for (EntityOrderKnight k : world.getEntitiesWithinAABB(EntityOrderKnight.class, getEntityBoundingBox().grow(16))) {
                    k.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 400, 1));
                    k.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 400, 0));
                }
                addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 400, 0));
                break;
            case SONG_CHOIR:
                for (int i = 0; i < 2; i++) {
                    EntityOrderKnight k = new EntityOrderKnight(world);
                    k.setup(EntityOrderKnight.GUARD);
                    k.setLocationAndAngles(posX + rand.nextInt(7) - 3, posY, posZ + rand.nextInt(7) - 3, rand.nextFloat() * 360, 0);
                    if (hasHome()) {
                        k.setHomePosAndDistance(getHomePosition(), 16);
                    }
                    world.spawnEntity(k);
                    ws.spawnParticle(EnumParticleTypes.NOTE, k.posX, k.posY + 2, k.posZ, 8, 0.4, 0.4, 0.4, 1.0);
                }
                break;
            default:
                for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(16),
                        x -> x != this && !isOnSameTeam(x) && !(x instanceof EntityOrderKnight) && x.isEntityAlive())) {
                    e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 200, 1));
                    e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 200, 0));
                }
                break;
        }
        say("sang" + s);
    }

    void silenced() {
        song = -1;
        silence = SILENCE;
        songGap = 0;
        world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_BASEDRUM, SoundCategory.HOSTILE, 2.0F, 0.5F);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 2.2, posZ, 20, 0.3, 0.2, 0.3, 0.02);
        say("silenced");
    }

    public boolean singing() {
        return song >= 0;
    }

    public boolean silent() {
        return silence > 0;
    }

    /** Для проверок: начать песнь сейчас. */
    public void startSong(int s) {
        song = s;
        songTicks = 0;
        songDamage = 0;
        silence = 0;
    }

    // ------------------------------------------------------------------ Элис

    /** Элис зовёт королевскую стражу на этих долях здоровья. */
    public static final float[] ELIS_CALL_AT = {0.75F, 0.5F, 0.25F};
    public static final int CHAIN_EVERY = 200, CHAIN_LEN = 3, WINDED = 80;

    /** Пока рядом жив кто-то из королевской стражи, Элис получает 40% урона. */
    public boolean guarded() {
        return !world.getEntitiesWithinAABB(EntityOrderKnight.class, getEntityBoundingBox().grow(24),
                k -> k.isEntityAlive() && k.getTags().contains(ROYAL)).isEmpty();
    }

    public static final String ROYAL = "chaosshard_royal";

    /**
     * Элис: рывки цепочкой по три (каждый бьёт и отбрасывает), после цепочки две секунды переводит дух —
     * получает в полтора раза больше урона. На 75%, 50% и 25% зовёт королевскую стражу: пока стража жива,
     * Элис почти неуязвима.
     */
    private void elis(EntityLivingBase target) {
        WorldServer ws = (WorldServer) world;
        if (winded > 0) {
            winded--;
            if (winded % 5 == 0) {
                ws.spawnParticle(EnumParticleTypes.CLOUD, posX, posY + 1.8, posZ, 3, 0.2, 0.1, 0.2, 0.01);
            }
        }
        if (dashing > 0) {
            dashing--;
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 1, posZ, 3, 0.2, 0.4, 0.2, 0.02);
            for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(0.8),
                    x -> x != this && !isOnSameTeam(x) && !(x instanceof EntityOrderKnight) && x.isEntityAlive())) {
                if (e.hurtResistantTime <= 10) {
                    e.attackEntityFrom(DamageSource.causeMobDamage(this), 12.0F);
                    e.knockBack(this, 1.4F, posX - e.posX, posZ - e.posZ);
                }
            }
            return;
        }
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        if (chainLeft > 0) {
            if (--chainGap <= 0) {
                chainLeft--;
                chainGap = 12;
                dashAt(target, 1.7);
                if (chainLeft == 0) {
                    winded = WINDED;
                }
            }
            return;
        }
        if (winded <= 0 && ++chainTimer >= CHAIN_EVERY && getDistance(target) < 20) {
            chainTimer = 0;
            chainLeft = CHAIN_LEN;
            chainGap = 1;
            world.playSound(null, getPosition(), SoundEvents.ENTITY_HORSE_GALLOP, SoundCategory.HOSTILE, 2.0F, 1.0F);
        }
    }

    private void dashAt(EntityLivingBase target, double speed) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        motionX = dx / len * speed;
        motionZ = dz / len * speed;
        motionY = 0.2;
        velocityChanged = true;
        dashing = 10;
        world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.HOSTILE, 1.2F, 1.6F);
    }

    /** Королевская стража: капитан и двое стражей с королевским знаком. */
    void callGuard() {
        say("call" + calls);
        WorldServer ws = (WorldServer) world;
        for (int i = 0; i < 3; i++) {
            EntityOrderKnight k = new EntityOrderKnight(world);
            k.setup(i == 0 ? EntityOrderKnight.CAPTAIN : EntityOrderKnight.GUARD);
            k.addTag(ROYAL);
            k.setCustomNameTag(net.minecraft.util.text.TextFormatting.GOLD
                    + new TextComponentTranslation("chaosshard.hero.elis.royal").getUnformattedText());
            double a = i * Math.PI * 2 / 3 + rand.nextDouble();
            k.setLocationAndAngles(posX + Math.cos(a) * 4, posY, posZ + Math.sin(a) * 4, rand.nextFloat() * 360, 0);
            if (hasHome()) {
                k.setHomePosAndDistance(getHomePosition(), 18);
            }
            world.spawnEntity(k);
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, k.posX, k.posY + 1, k.posZ, 30, 0.3, 0.8, 0.3, 0.05);
        }
        world.playSound(null, getPosition(), SoundEvents.BLOCK_NOTE_BASS, SoundCategory.HOSTILE, 3.0F, 0.5F);
    }

    public boolean winded() {
        return winded > 0;
    }

    public boolean shielded() {
        return shield > 0;
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
            // после вспышки опускает щит — окно для удара спереди
            stagger = FLASH_OPEN;
            setStaggerSlow(true);
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
        tag.setInteger("Escapes", escapes);
        if (away != null) {
            tag.setLong("Away", away.toLong());
        }
        if (pillarHome != null) {
            tag.setLong("PillarHome", pillarHome.toLong());
            tag.setInteger("PillarHomeR", pillarHomeR);
        }
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
        escapes = tag.getInteger("Escapes");
        away = tag.hasKey("Away") ? BlockPos.fromLong(tag.getLong("Away")) : null;
        if (tag.hasKey("PillarHome")) {
            pillarHome = BlockPos.fromLong(tag.getLong("PillarHome"));
            pillarHomeR = tag.getInteger("PillarHomeR");
        }
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
