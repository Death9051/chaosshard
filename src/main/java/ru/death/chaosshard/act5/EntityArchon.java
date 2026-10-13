package ru.death.chaosshard.act5;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
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
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.hero.Pose;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.structure.EntityOrderKnight;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Архонт — шестой, тот, кто держит Завесу. Финальный босс (Акт 5, Разлом).
 * <p>
 * Фаза 1 — Свет Опор: пока цело хоть одно ядро пилона, Архонт неуязвим; пилоны по очереди
 * бьют приёмами своих героев, сам он бьёт лучами (столб света над целью, через полторы секунды — удар).
 * Фаза 2 — Завеса: уязвим; переносится за спину цели, лучи чаще, край арены рвётся в пустоту.
 * Фаза 3 — с 35%: лучи по двум целям и вспышка, как у Каэля.
 */
public class EntityArchon extends EntityMob implements Pose.Posed {

    private static final DataParameter<Byte> POSE = EntityDataManager.createKey(EntityArchon.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> PHASE = EntityDataManager.createKey(EntityArchon.class, DataSerializers.BYTE);

    public static final int BEAM_CHARGE = 30, BLINK_EVERY = 160, TEAR_EVERY = 300, TEAR_WARN = 40, TEAR_HOLD = 200;
    public static final float DESPERATE_AT = 0.35F;

    private final BossInfoServer bossInfo = new BossInfoServer(new TextComponentString(""), BossInfo.Color.PURPLE, BossInfo.Overlay.NOTCHED_10);
    private int poseTicks;
    public boolean scripted;
    private int beamTimer, blinkTimer, tearTimer, flashTimer;
    private long lastShieldHint;
    /** Лучи: x, y, z, сколько тиков до удара. */
    private final List<double[]> beams = new ArrayList<>();

    public EntityArchon(World world) {
        super(world);
        setSize(0.9F, 2.9F);
        experienceValue = 2000;
        isImmuneToFire = true;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(POSE, (byte) 0);
        dataManager.register(PHASE, (byte) 1);
    }

    public void setup() {
        double hp = ChaosConfig.order.archonHealth;
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(16);
        getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(18);
        getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(10);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) hp);
        enablePersistence();
        bossInfo.setName(getDisplayName());
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, true));
        tasks.addTask(8, new EntityAIWatchClosest(this, EntityPlayer.class, 24.0F));
        tasks.addTask(8, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, false));
        targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(this, EntityLiving.class, 10, false, false,
                e -> e != null && Servants.isServant(e)));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.28D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(16.0D);
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

    @Override
    public boolean isPotionApplicable(PotionEffect effect) {
        return effect.getPotion() != MobEffects.WITHER && effect.getPotion() != MobEffects.POISON && super.isPotionApplicable(effect);
    }

    public int phase() {
        return dataManager.get(PHASE);
    }

    void setPhase(int p) {
        dataManager.set(PHASE, (byte) p);
    }

    /** Свет Опор хранит его, пока цело хоть одно ядро. */
    public boolean shielded() {
        return phase() == 1;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src == DamageSource.OUT_OF_WORLD) {
            return super.attackEntityFrom(src, amount);
        }
        if (scripted && !src.canHarmInCreative()) {
            return false;
        }
        if (shielded() && !src.canHarmInCreative()) {
            if (!world.isRemote) {
                world.playSound(null, getPosition(), SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.0F, 0.6F);
                Entity who = src.getTrueSource();
                if (who instanceof EntityPlayerMP && world.getTotalWorldTime() - lastShieldHint > 200) {
                    lastShieldHint = world.getTotalWorldTime();
                    ((EntityPlayerMP) who).sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.archon.shielded",
                            Rift.intactPylons((WorldServer) world)), true);
                }
            }
            return false;
        }
        boolean hurt = super.attackEntityFrom(src, amount);
        if (hurt && !world.isRemote && phase() == 2 && getHealth() < getMaxHealth() * DESPERATE_AT) {
            setPhase(3);
            say("phase3");
            ((WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, posX, posY + 1.5, posZ, 200, 2, 2, 2, 0.05);
        }
        return hurt;
    }

    /** Все ядра разбиты — Свет Опор погас. */
    public void unshield() {
        if (phase() == 1) {
            setPhase(2);
            say("phase2");
            world.playSound(null, getPosition(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, 1.5F);
        }
    }

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
            if (phase() == 1 && rand.nextInt(3) == 0) {
                world.spawnParticle(EnumParticleTypes.END_ROD, posX + (rand.nextDouble() - 0.5) * 2, posY + rand.nextDouble() * 3,
                        posZ + (rand.nextDouble() - 0.5) * 2, 0, 0.02, 0);
            }
            return;
        }
        bossInfo.setPercent(getHealth() / getMaxHealth());
        // не падать в пустоту: вернуть на арену
        if (posY < RiftDimension.FLOOR - 6) {
            BlockPos c = RiftDimension.center();
            setPositionAndUpdate(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        }
        if (scripted) {
            setAttackTarget(null);
            getNavigator().clearPath();
            return;
        }
        WorldServer ws = (WorldServer) world;
        tickBeams(ws);
        EntityLivingBase target = getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        int ph = phase();
        int beamEvery = ph == 1 ? 80 : ph == 2 ? 60 : 30;
        if (++beamTimer >= beamEvery) {
            beamTimer = 0;
            beam(target);
            if (ph == 3) {
                EntityPlayer other = world.getClosestPlayer(posX + (rand.nextDouble() - 0.5) * 30, posY, posZ + (rand.nextDouble() - 0.5) * 30, 30, false);
                if (other != null && other != target) {
                    beam(other);
                }
            }
        }
        if (ph >= 2 && ++blinkTimer >= BLINK_EVERY) {
            blinkTimer = 0;
            blinkBehind(target);
        }
        if (ph >= 2 && ++tearTimer >= TEAR_EVERY) {
            tearTimer = 0;
            Rift.tear(ws);
            say("tear");
        }
        if (ph == 3 && ++flashTimer >= 200) {
            flashTimer = 0;
            flash(ws);
        }
    }

    /** Столб света над целью: через BEAM_CHARGE тиков — удар. */
    public void beam(EntityLivingBase t) {
        beams.add(new double[]{t.posX, t.posY, t.posZ, BEAM_CHARGE});
        setPose(Pose.CAST, 12);
        world.playSound(null, t.getPosition(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5F, 1.6F);
    }

    void tickBeams(WorldServer ws) {
        for (int i = beams.size() - 1; i >= 0; i--) {
            double[] b = beams.get(i);
            b[3]--;
            if (b[3] % 3 == 0) {
                for (int y = 0; y < 12; y += 2) {
                    ws.spawnParticle(EnumParticleTypes.END_ROD, b[0], b[1] + y, b[2], 1, 0.15, 0.3, 0.15, 0.0);
                }
                ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, b[0], b[1] + 0.1, b[2], 4, 1.0, 0.0, 1.0, 0.0);
            }
            if (b[3] <= 0) {
                beams.remove(i);
                ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, b[0], b[1] + 1, b[2], 60, 0.5, 2.0, 0.5, 0.2);
                world.playSound(null, new BlockPos(b[0], b[1], b[2]), SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.HOSTILE, 1.2F, 1.8F);
                for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class,
                        new net.minecraft.util.math.AxisAlignedBB(b[0] - 2, b[1] - 1, b[2] - 2, b[0] + 2, b[1] + 3, b[2] + 2),
                        x -> x != this && !isOnSameTeam(x))) {
                    e.attackEntityFrom(DamageSource.causeMobDamage(this).setMagicDamage(), (float) ChaosConfig.order.archonBeam);
                }
            }
        }
    }

    public int pendingBeams() {
        return beams.size();
    }

    /** Перенестись за спину цели. */
    public void blinkBehind(EntityLivingBase t) {
        Vec3d look = t.getLook(1.0F);
        double x = t.posX - look.x * 3, z = t.posZ - look.z * 3;
        BlockPos at = new BlockPos(x, RiftDimension.FLOOR + 1, z);
        if (world.isAirBlock(at) && !world.isAirBlock(at.down())) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 1.5, posZ, 60, 0.4, 1.0, 0.4, 0.5);
            setPositionAndUpdate(x, at.getY(), z);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, posX, posY + 1.5, posZ, 60, 0.4, 1.0, 0.4, 0.5);
            world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.HOSTILE, 1.5F, 0.6F);
        }
    }

    /** Вспышка: слепнут все, кто смотрит на Архонта. */
    public void flash(WorldServer ws) {
        setPose(Pose.RAISE_BOTH, 20);
        ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, posX, posY + 2, posZ, 150, 0.8, 0.8, 0.8, 0.35);
        world.playSound(null, getPosition(), SoundEvents.ENTITY_FIREWORK_TWINKLE, SoundCategory.HOSTILE, 3.0F, 0.8F);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, getEntityBoundingBox().grow(24),
                x -> x != this && !isOnSameTeam(x) && x.isEntityAlive())) {
            if (!canEntityBeSeen(e)) {
                continue;
            }
            Vec3d look = e.getLook(1.0F);
            Vec3d to = new Vec3d(posX - e.posX, posY + 2 - (e.posY + e.getEyeHeight()), posZ - e.posZ).normalize();
            if (look.dotProduct(to) > 0.55) {
                e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 80, 0));
            }
        }
    }

    void say(String what) {
        Rift.say((WorldServer) world, "chaosshard.archon." + what);
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!world.isRemote) {
            say("death");
            Rift.victory((WorldServer) world);
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
        TextComponentTranslation t = new TextComponentTranslation("entity.chaosshard.archon.name");
        t.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
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
    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setByte("Phase", (byte) phase());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("Phase")) {
            setPhase(Math.max(1, tag.getByte("Phase")));
        }
        bossInfo.setName(getDisplayName());
    }
}
