package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.List;
import java.util.UUID;

/**
 * Слуги Хаоса — твари, призванные по Нити (ритуалом в Чаше). Служат владельцу:
 * следуют за ним, бьют его врагов и любых тварей рядом, не трогают его самого.
 * ПКМ Гримуаром по слуге — «за мной» / «стереги здесь». Shift+ПКМ пустой рукой — отпустить.
 * Слуг не больше, чем 1 + уровень Нити.
 */
public class Servants {

    public static final String TAG_OWNER = "chaosshard_servant";
    public static final String TAG_GUARD = "chaosshard_servant_guard";
    /** Тип слуги (имя Kind). */
    public static final String TAG_KIND = "chaosshard_kind";
    /** Сундук, за которым закреплён добытчик. */
    public static final String TAG_CHEST = "chaosshard_chest";
    /** Направление шахты Рудокопа (горизонтальный индекс). */
    public static final String TAG_DIR = "chaosshard_dir";

    public enum Kind {
        HUSK("minecraft:husk", "Скорлупа Хаоса", 30, 0),
        BONE("minecraft:wither_skeleton", "Костяной страж", 40, 2),
        EXECUTIONER("minecraft:vindication_illager", "Палач Хаоса", 50, 3),
        HARE("chaosshard:servant_hare", "Заяц", 20, 1),
        TIGER("chaosshard:servant_tiger", "Тигр", 80, 4),
        CAT("chaosshard:servant_cat", "Кошка", 24, 2),
        REAPER("minecraft:husk", "Жнец", 26, 1),
        LUMBERJACK("minecraft:husk", "Лесоруб", 30, 1),
        MINER("minecraft:husk", "Рудокоп", 34, 2);

        /** Уникальный слуга: у Осколка может быть только один такой. */
        public boolean unique() {
            return this == HARE || this == TIGER || this == CAT;
        }

        /** Добытчик: работает у сундука. */
        public boolean gatherer() {
            return this == REAPER || this == LUMBERJACK || this == MINER;
        }

        /** Обычные слуги (свита): бойцы и добытчики. */
        public static final Kind[] COMMON = {HUSK, BONE, EXECUTIONER, REAPER, LUMBERJACK, MINER};

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public final String id, name;
        public final int health, minThread;

        Kind(String id, String name, int health, int minThread) {
            this.id = id;
            this.name = name;
            this.health = health;
            this.minThread = minThread;
        }
    }

    /** Обычных слуг (без зверей): 2 + уровень Нити. */
    public static int limit(EntityPlayer p) {
        return 2 + ThreadBonus.level(p);
    }

    /** Тип слуги: из метки, а у старых слуг — по виду сущности. */
    public static Kind kindOf(Entity e) {
        if (e == null) {
            return null;
        }
        String k = e.getEntityData().getString(TAG_KIND);
        if (!k.isEmpty()) {
            try {
                return Kind.valueOf(k);
            } catch (IllegalArgumentException ignored) {
                // старая метка
            }
        }
        net.minecraft.util.ResourceLocation key = EntityList.getKey(e);
        if (key == null) {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (!kind.gatherer() && kind.id.equals(key.toString())) {
                return kind;
            }
        }
        return null;
    }

    /** Обычных слуг у игрока (звери не считаются). */
    public static int count(EntityPlayerMP p) {
        int n = 0;
        for (WorldServer w : p.getServer().worlds) {
            for (Entity e : w.loadedEntityList) {
                if (isServantOf(e, p.getUniqueID()) && e.isEntityAlive() && !(e instanceof EntityChaosBeast)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Есть ли уже у игрока такой уникальный слуга. */
    public static boolean hasUnique(EntityPlayerMP p, Kind kind) {
        String id = kind.id;
        for (WorldServer w : p.getServer().worlds) {
            for (Entity e : w.loadedEntityList) {
                if (isServantOf(e, p.getUniqueID()) && e.isEntityAlive()) {
                    net.minecraft.util.ResourceLocation key = EntityList.getKey(e);
                    if (key != null && key.toString().equals(id)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isServant(Entity e) {
        return e != null && e.getEntityData().hasUniqueId(TAG_OWNER);
    }

    public static boolean isServantOf(Entity e, UUID owner) {
        return isServant(e) && owner.equals(e.getEntityData().getUniqueId(TAG_OWNER));
    }

    public static EntityPlayer owner(Entity e) {
        if (!isServant(e) || e.world.getMinecraftServer() == null) {
            return null;
        }
        return e.world.getMinecraftServer().getPlayerList().getPlayerByUUID(e.getEntityData().getUniqueId(TAG_OWNER));
    }

    /** Призвать слугу рядом с точкой (ритуал). null — не вышло. */
    public static EntityLiving summon(EntityPlayerMP owner, Kind kind, BlockPos at) {
        EntityLiving m = spawn(owner, kind, at);
        if (m == null) {
            return null;
        }
        WorldServer ws = (WorldServer) owner.world;
        ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, m.posX, m.posY + 1, m.posZ, 40, 0.3, 0.6, 0.3, 0.03);
        if (m instanceof EntityChaosBeast) {
            Beasts.onSummoned(owner, (EntityChaosBeast) m);
        }
        Milestones.grant(owner, Milestones.SERVANT);
        if (kind == Kind.BONE) {
            Milestones.grant(owner, Milestones.SERVANT_BONE);
        } else if (kind == Kind.EXECUTIONER) {
            Milestones.grant(owner, Milestones.SERVANT_EXEC);
        }
        if (kind.unique()) {
            Milestones.grant(owner, "servant_" + kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (count(owner) >= 3) {
            Milestones.grant(owner, Milestones.SERVANT_3);
        }
        return m;
    }

    /** Создать слугу и поставить в мир, без вех и эффектов. */
    public static EntityLiving spawn(EntityPlayerMP owner, Kind kind, BlockPos at) {
        World world = owner.world;
        Entity e = EntityList.createEntityByIDFromName(new ResourceLocation(kind.id), world);
        if (!(e instanceof EntityCreature)) {
            return null;
        }
        EntityCreature m = (EntityCreature) e;
        m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, owner.rotationYaw, 0);
        m.onInitialSpawn(world.getDifficultyForLocation(at), null);
        m.getEntityData().setUniqueId(TAG_OWNER, owner.getUniqueID());
        m.getEntityData().setString(TAG_KIND, kind.name());
        m.getEntityData().setString("chaosshard_owner_name", owner.getName());
        m.setCustomNameTag((kind.unique() ? "§d" : "§5") + kind.name + " §7(" + owner.getName() + ")");
        m.setAlwaysRenderNameTag(false);
        m.enablePersistence();
        m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(kind.health + 5 * ThreadBonus.level(owner));
        m.setHealth(m.getMaxHealth());
        if (kind == Kind.BONE) {
            m.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        } else if (kind == Kind.EXECUTIONER) {
            m.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
        m.setDropChance(EntityEquipmentSlot.MAINHAND, 0F);
        if (m instanceof EntityChaosBeast) {
            m.getEntityData().setInteger("chaosshard_thread_hp", ThreadBonus.level(owner));
            Beasts.preSpawn(owner, (EntityChaosBeast) m);
        }
        world.spawnEntity(m);
        if (!(m instanceof EntityChaosBeast)) {
            ServantGear.refresh(m, owner);
        }
        m.setHealth(m.getMaxHealth());
        return m;
    }

    // ------------------------------------------------------------------ разум слуги (не сохраняется — ставится при каждой загрузке)

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        if (event.getWorld().isRemote || !isServant(e) || !(e instanceof EntityCreature)) {
            return;
        }
        EntityCreature m = (EntityCreature) e;
        Kind kind = kindOf(m);
        m.tasks.taskEntries.clear();
        m.targetTasks.taskEntries.clear();
        m.setCanPickUpLoot(false);
        m.tasks.addTask(0, new EntityAISwimming(m));
        m.tasks.addTask(2, new EntityAIAttackMelee(m, 1.2D, true));
        if (kind != null && kind.gatherer()) {
            // добытчик: работает у сундука, отбивается, только если тронули
            switch (kind) {
                case REAPER: m.tasks.addTask(3, new ru.death.chaosshard.servant.work.WorkReaper(m)); break;
                case LUMBERJACK: m.tasks.addTask(3, new ru.death.chaosshard.servant.work.WorkLumberjack(m)); break;
                default: m.tasks.addTask(3, new ru.death.chaosshard.servant.work.WorkMiner(m)); break;
            }
            m.tasks.addTask(4, new FollowOwner(m));
            m.tasks.addTask(7, new EntityAIWatchClosest(m, EntityPlayer.class, 8.0F));
            m.tasks.addTask(8, new EntityAILookIdle(m));
            m.targetTasks.addTask(1, new EntityAIHurtByTarget(m, false));
            if (m.getEntityData().hasKey(TAG_CHEST)) {
                m.setHomePosAndDistance(BlockPos.fromLong(m.getEntityData().getLong(TAG_CHEST)), 32);
            }
        } else {
            m.tasks.addTask(4, new FollowOwner(m));
            m.tasks.addTask(6, new EntityAIWanderAvoidWater(m, 0.8D));
            m.tasks.addTask(7, new EntityAIWatchClosest(m, EntityPlayer.class, 8.0F));
            m.tasks.addTask(8, new EntityAILookIdle(m));
            m.targetTasks.addTask(1, new DefendOwner(m));
            m.targetTasks.addTask(2, new EntityAIHurtByTarget(m, false));
            m.targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(m, EntityLiving.class, 10, true, false,
                    t -> t instanceof IMob && !isServant(t)));
            if (m.getEntityData().hasKey(TAG_GUARD)) {
                m.setHomePosAndDistance(BlockPos.fromLong(m.getEntityData().getLong(TAG_GUARD)), 8);
            }
        }
        if (!(m instanceof EntityChaosBeast)) {
            ServantGear.refresh(m, owner(m));
        }
    }

    /** Следовать за владельцем (если не стережёт место). Далеко отстал — переместиться к нему. */
    static class FollowOwner extends EntityAIBase {
        private final EntityCreature m;

        FollowOwner(EntityCreature m) {
            this.m = m;
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            if (m.getEntityData().hasKey(TAG_GUARD) || m.getEntityData().hasKey(TAG_CHEST) || m.getAttackTarget() != null) {
                return false;
            }
            EntityPlayer o = owner(m);
            return o != null && o.world == m.world && m.getDistanceSq(o) > 36;
        }

        @Override
        public void updateTask() {
            EntityPlayer o = owner(m);
            if (o == null) {
                return;
            }
            if (m.getDistanceSq(o) > 24 * 24) {
                m.setPositionAndUpdate(o.posX, o.posY, o.posZ);
            } else {
                m.getNavigator().tryMoveToEntityLiving(o, 1.15D);
            }
        }
    }

    /** Бить того, кто напал на владельца или на кого напал владелец. */
    static class DefendOwner extends EntityAITarget {
        private EntityLivingBase target;

        DefendOwner(EntityCreature m) {
            super(m, false);
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            EntityPlayer o = owner(taskOwner);
            if (o == null || o.world != taskOwner.world) {
                return false;
            }
            EntityLivingBase t = o.getRevengeTarget();
            if (t == null || !t.isEntityAlive()) {
                t = o.getLastAttackedEntity();
            }
            if (t == null || t == o || isServant(t) || !t.isEntityAlive()) {
                return false;
            }
            target = t;
            return isSuitableTarget(t, false);
        }

        @Override
        public void startExecuting() {
            taskOwner.setAttackTarget(target);
            super.startExecuting();
        }
    }

    // ------------------------------------------------------------------ переходы между мирами

    /** Игрок → мир, из которого он ушёл. Слуги переходят на следующем тике. */
    private static final java.util.Map<UUID, Integer> MOVED = new java.util.HashMap<>();

    @SubscribeEvent
    public void onChangedDimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.player.world.isRemote) {
            MOVED.put(event.player.getUniqueID(), event.fromDim);
        }
    }

    /** Свита, что ходит за мной (не стережёт место и не закреплена за сундуком), переходит со мной в другой мир. */
    @SubscribeEvent
    public void onServerTick(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || MOVED.isEmpty()) {
            return;
        }
        java.util.Map<UUID, Integer> moved = new java.util.HashMap<>(MOVED);
        MOVED.clear();
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        for (java.util.Map.Entry<UUID, Integer> me : moved.entrySet()) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(me.getKey());
            WorldServer from = net.minecraftforge.common.DimensionManager.getWorld(me.getValue());
            if (p == null || from == null || p.dimension == me.getValue()) {
                continue;
            }
            // в Испытания свита не ходит — ждёт у входа
            if (ru.death.chaosshard.trial.TrialDimension.is(p.world)) {
                continue;
            }
            List<Entity> list = new java.util.ArrayList<>(from.loadedEntityList);
            int n = 0;
            for (Entity e : list) {
                if (!e.isEntityAlive() || !isServantOf(e, p.getUniqueID()) || e.getEntityData().hasKey(TAG_GUARD)
                        || e.getEntityData().hasKey(TAG_CHEST) || e.isRiding() || e.isBeingRidden()) {
                    continue;
                }
                final double ox = (n % 3 - 1) * 1.2, oz = (n / 3 % 3 - 1) * 1.2;
                n++;
                e.changeDimension(p.dimension, (world, entity, yaw) -> {
                    entity.setLocationAndAngles(p.posX + ox, p.posY, p.posZ + oz, p.rotationYaw, 0);
                    entity.motionX = entity.motionY = entity.motionZ = 0;
                });
            }
            if (n > 0) {
                ((WorldServer) p.world).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 40, 1.0, 0.8, 1.0, 0.5);
            }
        }
    }

    // ------------------------------------------------------------------ верность

    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        EntityLivingBase t = event.getTarget();
        if (t == null || !isServant(e) || !(e instanceof EntityLiving)) {
            return;
        }
        if (isServant(t) || t.getUniqueID().equals(e.getEntityData().getUniqueId(TAG_OWNER))) {
            ((EntityLiving) e).setAttackTarget(null);
        }
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        Entity src = event.getSource().getTrueSource();
        EntityLivingBase hurt = event.getEntityLiving();
        // слуга не бьёт владельца и других слуг; владелец случайно не бьёт своего слугу (если не присел)
        if (isServant(src) && (isServant(hurt) || hurt.getUniqueID().equals(src.getEntityData().getUniqueId(TAG_OWNER)))) {
            event.setCanceled(true);
        } else if (src instanceof EntityPlayer && isServantOf(hurt, src.getUniqueID()) && !src.isSneaking()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity e = event.getTarget();
        EntityPlayer p = event.getEntityPlayer();
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND || !isServantOf(e, p.getUniqueID())) {
            return;
        }
        ItemStack held = p.getHeldItemMainhand();
        NBTTagCompound d = e.getEntityData();
        Kind kind = kindOf(e);
        if (held.getItem() == ModItems.GRIMOIRE && kind != null && kind.gatherer()) {
            if (p.isSneaking()) {
                ru.death.chaosshard.servant.work.Work.unbind((EntityCreature) e);
                p.sendStatusMessage(Breach.grimoire("chaosshard.work.unbound"), true);
            } else {
                ru.death.chaosshard.servant.work.Work.select(p, e);
                p.sendStatusMessage(Breach.grimoire("chaosshard.work.selected",
                        new net.minecraft.util.text.TextComponentTranslation("chaosshard.servant.kind." + kind.key())), true);
            }
            event.setCanceled(true);
            return;
        }
        if (held.getItem() == ModItems.GRIMOIRE) {
            if (d.hasKey(TAG_GUARD)) {
                d.removeTag(TAG_GUARD);
                ((EntityCreature) e).detachHome();
                p.sendStatusMessage(Breach.grimoire("chaosshard.servant.follow"), true);
            } else {
                d.setLong(TAG_GUARD, e.getPosition().toLong());
                ((EntityCreature) e).setHomePosAndDistance(e.getPosition(), 8);
                if (e instanceof EntityChaosBeast) {
                    Beasts.refresh((EntityChaosBeast) e);
                }
                p.sendStatusMessage(Breach.grimoire("chaosshard.servant.guard"), true);
            }
            event.setCanceled(true);
        } else if (held.isEmpty() && p.isSneaking()) {
            ((WorldServer) e.world).spawnParticle(EnumParticleTypes.PORTAL, e.posX, e.posY + 1, e.posZ, 40, 0.3, 0.6, 0.3, 0.5);
            e.setDead();
            if (e instanceof EntityChaosBeast) {
                BeastData.Entry be = Beasts.entry((EntityChaosBeast) e);
                if (be != null) {
                    be.entity = null;
                    Beasts.changed(e.world, p.getUniqueID());
                }
            }
            p.sendStatusMessage(Breach.grimoire("chaosshard.servant.dismiss"), true);
            event.setCanceled(true);
        } else if (held.getItem() == Items.ROTTEN_FLESH || held.getItem() == Items.BONE) {
            // подкормить: лечит
            ((EntityLivingBase) e).heal(6);
            ((EntityLivingBase) e).addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 0));
            if (!p.isCreative()) {
                held.shrink(1);
            }
            event.setCanceled(true);
        }
    }

    /** Список слуг игрока рядом (для сведений). */
    public static List<EntityLiving> near(EntityPlayer p, double r) {
        return p.world.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(r), e -> isServantOf(e, p.getUniqueID()));
    }
}
