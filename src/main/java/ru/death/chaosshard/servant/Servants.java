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
    /** Якорь, в гарнизоне которого стоит боец (вне основной свиты). */
    public static final String TAG_ANCHOR = "chaosshard_anchor";
    /** Направление шахты Рудокопа (горизонтальный индекс). */
    public static final String TAG_DIR = "chaosshard_dir";

    public enum Kind {
        HUSK("chaosshard:servant_husk", "Хаск", 30, 0),
        BONE("chaosshard:servant_bone", "Костяной страж", 40, 2),
        EXECUTIONER("chaosshard:servant_executioner", "Палач Хаоса", 50, 3),
        HARE("chaosshard:servant_hare", "Заяц", 20, 1),
        TIGER("chaosshard:servant_tiger", "Тигр", 80, 4),
        CAT("chaosshard:servant_cat", "Кошка", 24, 2),
        REAPER("chaosshard:servant_reaper", "Жнец", 26, 1),
        LUMBERJACK("chaosshard:servant_lumberjack", "Лесоруб", 30, 1),
        MINER("chaosshard:servant_miner", "Рудокоп", 34, 2);

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
            if (kind.id.equals(key.toString())) {
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
                if (isServantOf(e, p.getUniqueID()) && e.isEntityAlive() && !(e instanceof EntityChaosBeast)
                        && !e.getEntityData().hasKey(TAG_ANCHOR) && !e.getEntityData().hasKey("chaosshard_nest")) {
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
        return ru.death.chaosshard.debug.TestHooks.player(e.world.getMinecraftServer(), e.getEntityData().getUniqueId(TAG_OWNER));
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
        Resync.later(m, 5);
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
        if (kind != null && !kind.unique() && !kind.id.equals(String.valueOf(EntityList.getKey(m)))) {
            migrate(m, kind);
            event.setCanceled(true);
            return;
        }
        m.tasks.taskEntries.clear();
        m.targetTasks.taskEntries.clear();
        m.setCanPickUpLoot(false);
        m.tasks.addTask(0, new EntityAISwimming(m));
        if (m instanceof EntityChaosBeast.Hare || m instanceof EntityChaosBeast.Cat) {
            // заяц и кошка не дерутся: идут за хозяином, а если ранят — убегают
            m.tasks.addTask(1, new net.minecraft.entity.ai.EntityAIPanic(m, 1.4D));
            m.tasks.addTask(4, new FollowBeast(m));
            m.tasks.addTask(6, new EntityAIWanderAvoidWater(m, 0.8D));
            m.tasks.addTask(7, new EntityAIWatchClosest(m, EntityPlayer.class, 8.0F));
            m.tasks.addTask(8, new EntityAILookIdle(m));
            m.setAttackTarget(null);
            return;
        }
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
            m.tasks.addTask(4, m instanceof EntityChaosBeast ? new FollowBeast(m) : new FollowOwner(m));
            m.tasks.addTask(6, new EntityAIWanderAvoidWater(m, 0.8D));
            m.tasks.addTask(7, new EntityAIWatchClosest(m, EntityPlayer.class, 8.0F));
            m.tasks.addTask(8, new EntityAILookIdle(m));
            m.targetTasks.addTask(1, new DefendOwner(m));
            m.targetTasks.addTask(2, new EntityAIHurtByTarget(m, false));
            m.targetTasks.addTask(3, new EntityAINearestAttackableTarget<>(m, EntityLiving.class, 10, true, false,
                    t -> (t instanceof IMob || t.getTags().contains(ru.death.chaosshard.garrot.GarrotRaids.TAG_ARMY)) && !isServant(t)));
            if (m.getEntityData().hasKey(TAG_ANCHOR)) {
                m.setHomePosAndDistance(BlockPos.fromLong(m.getEntityData().getLong(TAG_ANCHOR)), 16);
            } else if (m.getEntityData().hasKey(TAG_GUARD)) {
                m.setHomePosAndDistance(BlockPos.fromLong(m.getEntityData().getLong(TAG_GUARD)), 8);
            }
        }
        if (!(m instanceof EntityChaosBeast)) {
            ServantGear.refresh(m, owner(m));
        }
    }

    /** Слуга из старой версии (ванильный хаск, скелет, поборник) — пересадить в своё тело. */
    private static void migrate(EntityCreature old, Kind kind) {
        NBTTagCompound tag = old.writeToNBT(new NBTTagCompound());
        World world = old.world;
        old.setDead();
        world.getMinecraftServer().addScheduledTask(() -> {
            Entity n = EntityList.createEntityByIDFromName(new ResourceLocation(kind.id), world);
            if (n == null) {
                return;
            }
            n.readFromNBT(tag);
            world.spawnEntity(n);
        });
    }

    /** Ходит за мной (не стережёт место, не у сундука, не в гарнизоне). */
    static boolean isFollower(Entity m) {
        return !m.getEntityData().hasKey(TAG_GUARD) && !m.getEntityData().hasKey(TAG_CHEST) && !m.getEntityData().hasKey(TAG_ANCHOR);
    }

    /**
     * Следование строем. У каждого слуги своё место полукругом за спиной хозяина (ближний ряд — до 5 мест, дальше — второй ряд).
     * Дальше 7 блоков — догоняет своё место; ближе 1.6 — отходит на него, а не стоит вплотную. Место пересчитывается,
     * только когда хозяин сместился, поэтому свита не мечется, пока я верчу головой. Отстал больше чем на 24 — переносится
     * на свободное место рядом.
     */
    static class FollowOwner extends EntityAIBase {
        static final double START = 7.0, CROWD = 1.6, ARRIVE = 1.4, TELEPORT = 24.0;
        private final EntityCreature m;
        private net.minecraft.util.math.Vec3d slot, anchor;
        private int repath, timeout;

        FollowOwner(EntityCreature m) {
            this.m = m;
            setMutexBits(1);
        }

        private EntityPlayer target() {
            if (!isFollower(m) || m.getAttackTarget() != null) {
                return null;
            }
            EntityPlayer o = owner(m);
            return o != null && o.world == m.world && !o.isSpectator() ? o : null;
        }

        @Override
        public boolean shouldExecute() {
            EntityPlayer o = target();
            if (o == null) {
                return false;
            }
            double d = m.getDistance(o);
            return d > START || (d < CROWD && !o.isPlayerSleeping());
        }

        @Override
        public boolean shouldContinueExecuting() {
            EntityPlayer o = target();
            if (o == null || timeout <= 0) {
                return false;
            }
            updateSlot(o, false);
            return m.getDistance(o) > TELEPORT || m.getDistanceSq(slot.x, slot.y, slot.z) > ARRIVE * ARRIVE;
        }

        @Override
        public void startExecuting() {
            repath = 0;
            timeout = 400;
            EntityPlayer o = target();
            if (o != null) {
                updateSlot(o, true);
            }
        }

        @Override
        public void resetTask() {
            m.getNavigator().clearPath();
        }

        @Override
        public void updateTask() {
            EntityPlayer o = owner(m);
            if (o == null) {
                return;
            }
            timeout--;
            m.getLookHelper().setLookPositionWithEntity(o, 10.0F, m.getVerticalFaceSpeed());
            if (m.getDistance(o) > TELEPORT) {
                updateSlot(o, true);
                BlockPos safe = safeSpot(m.world, slot, o);
                m.getNavigator().clearPath();
                m.setPositionAndUpdate(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                Resync.later(m, 3);
                return;
            }
            if (--repath > 0) {
                return;
            }
            repath = 10;
            double d = m.getDistance(o);
            double speed = d > 12 ? 1.3 : d > START ? 1.15 : 1.0;
            if (!m.getNavigator().tryMoveToXYZ(slot.x, slot.y, slot.z, speed)) {
                // место за стеной или в воздухе — хотя бы подойти, не упираясь в хозяина
                net.minecraft.util.math.Vec3d mid = o.getPositionVector().add(slot.subtract(o.getPositionVector()).scale(0.6));
                if (!m.getNavigator().tryMoveToXYZ(mid.x, mid.y, mid.z, speed) && d > START) {
                    m.getNavigator().tryMoveToEntityLiving(o, speed);
                }
            }
        }

        /** Место в строю: полукруг за спиной хозяина. Пересчёт — когда хозяин ушёл от прошлой точки дальше 2.5 блока. */
        private void updateSlot(EntityPlayer o, boolean force) {
            net.minecraft.util.math.Vec3d at = o.getPositionVector();
            if (!force && slot != null && anchor != null && anchor.squareDistanceTo(at) < 2.5 * 2.5) {
                return;
            }
            anchor = at;
            slot = slotFor(m, o);
        }
    }

    /**
     * Звери не встают в строй, у каждого своя повадка.
     * <ul>
     * <li>Кошка гуляет сама по себе: держится в 3–9 блоках, отстав — неспешно подходит не ко мне, а куда-то рядом;
     * время от времени сама перебирается на новое место поблизости и подолгу сидит.</li>
     * <li>Заяц крутится вокруг: часто перескакивает с места на место в 2–6 блоках, отстав — бегом догоняет.</li>
     * <li>Тигр идёт у правого плеча, чуть впереди, как телохранитель.</li>
     * </ul>
     * Вплотную не подходит никто: кто оказался ближе 1.6 блока, отходит. Отстал больше чем на 24 — переносится рядом.
     */
    static class FollowBeast extends EntityAIBase {
        private final EntityCreature m;
        private net.minecraft.util.math.Vec3d target, anchor;
        private int idle, timeout;

        FollowBeast(EntityCreature m) {
            this.m = m;
            setMutexBits(1);
        }

        private BeastData.Beast kind() {
            return m instanceof EntityChaosBeast ? ((EntityChaosBeast) m).beast() : BeastData.Beast.CAT;
        }

        private EntityPlayer owner() {
            if (!isFollower(m) || m.getAttackTarget() != null) {
                return null;
            }
            EntityPlayer o = Servants.owner(m);
            return o != null && o.world == m.world && !o.isSpectator() ? o : null;
        }

        /** Дальше этого зверь идёт догонять. */
        private double far() {
            switch (kind()) {
                case HARE: return 8;
                case TIGER: return 5;
                default: return 10;
            }
        }

        private double speed(double d) {
            switch (kind()) {
                case HARE: return d > far() ? 1.35 : 1.15;
                case TIGER: return d > 10 ? 1.25 : 1.05;
                default: return d > 16 ? 1.2 : 0.95;
            }
        }

        /** Случайная точка в кольце вокруг хозяина. */
        private net.minecraft.util.math.Vec3d around(EntityPlayer o, double r0, double r1) {
            double a = m.getRNG().nextDouble() * Math.PI * 2;
            double r = r0 + m.getRNG().nextDouble() * (r1 - r0);
            return new net.minecraft.util.math.Vec3d(o.posX + Math.cos(a) * r, o.posY, o.posZ + Math.sin(a) * r);
        }

        /** Место тигра: у правого плеча, чуть впереди. */
        private net.minecraft.util.math.Vec3d flank(EntityPlayer o) {
            double a = Math.toRadians(o.renderYawOffset + 70);
            return new net.minecraft.util.math.Vec3d(o.posX - Math.sin(a) * 2.5, o.posY, o.posZ + Math.cos(a) * 2.5);
        }

        @Override
        public boolean shouldExecute() {
            EntityPlayer o = owner();
            if (o == null) {
                return false;
            }
            double d = m.getDistance(o);
            BeastData.Beast k = kind();
            if (d < CROWD && !o.isPlayerSleeping()) {
                target = around(o, 2.2, 3.2);
                return true;
            }
            if (k == BeastData.Beast.TIGER) {
                net.minecraft.util.math.Vec3d at = o.getPositionVector();
                if (anchor == null || anchor.squareDistanceTo(at) > 2.0 * 2.0 || d > far()) {
                    anchor = at;
                    target = flank(o);
                    return m.getDistanceSq(target.x, target.y, target.z) > 1.5 * 1.5;
                }
                return false;
            }
            if (d > far()) {
                target = k == BeastData.Beast.HARE ? around(o, 2, 4) : around(o, 3, 5.5);
                return true;
            }
            if (--idle <= 0) {
                // сама решила перебраться: Кошка — редко и недалеко, Заяц — часто
                // shouldExecute зовётся раз в 3 тика: Заяц — раз в 6–13 с, Кошка — раз в 15–37 с
                idle = k == BeastData.Beast.HARE ? 20 + m.getRNG().nextInt(25) : 100 + m.getRNG().nextInt(150);
                target = k == BeastData.Beast.HARE ? around(o, 2, 6) : around(o, 3, 7);
                return true;
            }
            return false;
        }

        @Override
        public void startExecuting() {
            timeout = 200;
            EntityPlayer o = Servants.owner(m);
            double d = o == null ? 0 : m.getDistance(o);
            if (!m.getNavigator().tryMoveToXYZ(target.x, target.y, target.z, speed(d)) && o != null && d > far()) {
                m.getNavigator().tryMoveToEntityLiving(o, speed(d));
            }
        }

        @Override
        public boolean shouldContinueExecuting() {
            return timeout > 0 && owner() != null && !m.getNavigator().noPath();
        }

        @Override
        public void updateTask() {
            timeout--;
            EntityPlayer o = Servants.owner(m);
            if (o == null) {
                return;
            }
            double d = m.getDistance(o);
            if (d > FollowOwner.TELEPORT) {
                BlockPos safe = safeSpot(m.world, kind() == BeastData.Beast.TIGER ? flank(o) : around(o, 2.5, 4), o);
                m.getNavigator().clearPath();
                m.setPositionAndUpdate(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                Resync.later(m, 3);
                return;
            }
            // хозяин ушёл далеко от выбранной точки — выбрать новую рядом с ним
            if (timeout % 20 == 0 && target.squareDistanceTo(o.getPositionVector()) > (far() + 2) * (far() + 2)) {
                target = kind() == BeastData.Beast.TIGER ? flank(o) : around(o, 2.5, 5);
                m.getNavigator().tryMoveToXYZ(target.x, target.y, target.z, speed(d));
            }
        }

        @Override
        public void resetTask() {
            m.getNavigator().clearPath();
        }

        static final double CROWD = FollowOwner.CROWD;
    }

    /** Место слуги в строю за спиной хозяина. */
    static net.minecraft.util.math.Vec3d slotFor(EntityCreature m, EntityPlayer o) {
        List<EntityCreature> crew = m.world.getEntitiesWithinAABB(EntityCreature.class, o.getEntityBoundingBox().grow(32),
                e -> e.isEntityAlive() && isServantOf(e, o.getUniqueID()) && isFollower(e) && !(e instanceof EntityChaosBeast));
        crew.sort(java.util.Comparator.comparingInt(Entity::getEntityId));
        int i = Math.max(0, crew.indexOf(m));
        int n = Math.max(1, crew.size());
        int ring = i < 5 ? 0 : 1;
        int inRing = ring == 0 ? Math.min(5, n) : Math.min(7, n - 5);
        int j = ring == 0 ? i : i - 5;
        double radius = ring == 0 ? 2.8 : 4.4;
        double step = ring == 0 ? 36 : 28;
        double ang = Math.toRadians(o.renderYawOffset + 180 + (j - (inRing - 1) / 2.0) * step);
        // в Minecraft «вперёд» по yaw — это (-sin, cos)
        return new net.minecraft.util.math.Vec3d(o.posX - Math.sin(ang) * radius, o.posY, o.posZ + Math.cos(ang) * radius);
    }

    /** Свободная точка рядом с желаемым местом: твёрдый пол, два блока воздуха, не жидкость. Иначе — рядом с хозяином. */
    static BlockPos safeSpot(World w, net.minecraft.util.math.Vec3d want, EntityPlayer o) {
        BlockPos base = new BlockPos(want);
        for (int r = 0; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos q = base.add(dx, dy, dz);
                        if (standable(w, q) && q.distanceSq(o.getPosition()) > 1.5) {
                            return q;
                        }
                    }
                }
            }
        }
        return o.getPosition();
    }

    static boolean standable(World w, BlockPos q) {
        net.minecraft.block.state.IBlockState floor = w.getBlockState(q.down());
        return floor.getMaterial().blocksMovement() && !floor.getMaterial().isLiquid()
                && !w.getBlockState(q).getMaterial().blocksMovement() && !w.getBlockState(q).getMaterial().isLiquid()
                && !w.getBlockState(q.up()).getMaterial().blocksMovement();
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

    // ------------------------------------------------------------------ переходы между мирами — ServantTravel

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
        // шипы осквернённой земли (лозоколюч Defiled Lands) своих не ранят
        if (event.getSource() == net.minecraft.util.DamageSource.CACTUS && isServant(hurt)) {
            event.setCanceled(true);
            return;
        }
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
        if (event.getWorld().isRemote) {
            // клиент не знает хозяина, но в Привязке Гримуар по существу — только указание: не читать и не листать
            if (event.getHand() == EnumHand.MAIN_HAND && e instanceof net.minecraft.entity.EntityLiving
                    && ru.death.chaosshard.spell.SpellCaster.binding(p)) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            }
            return;
        }
        if (event.getHand() != EnumHand.MAIN_HAND || !isServantOf(e, p.getUniqueID())) {
            return;
        }
        ItemStack held = p.getHeldItemMainhand();
        NBTTagCompound d = e.getEntityData();
        Kind kind = kindOf(e);
        if (held.getItem() == ModItems.GRIMOIRE && !ru.death.chaosshard.spell.SpellCaster.bindMode(held)) {
            return;   // указания — только в режиме Привязки
        }
        if (held.getItem() == ModItems.GRIMOIRE && kind != null && kind.gatherer()) {
            if (p.isSneaking()) {
                ru.death.chaosshard.servant.work.Work.unbind((EntityCreature) e);
                p.sendStatusMessage(Breach.grimoire("chaosshard.work.unbound"), true);
            } else {
                int n = ru.death.chaosshard.servant.work.Work.select(p, e);
                p.sendStatusMessage(Breach.grimoire("chaosshard.work.selected",
                        new net.minecraft.util.text.TextComponentTranslation("chaosshard.servant.kind." + kind.key()), n), true);
            }
            event.setCanceled(true);
            return;
        }
        if (held.getItem() == ModItems.GRIMOIRE && kind != null && !kind.unique()) {
            if (p.isSneaking()) {
                // снова за мной (из гарнизона — только если в свите есть место)
                if (d.hasKey(TAG_ANCHOR) && p instanceof EntityPlayerMP && count((EntityPlayerMP) p) >= limit(p) && !p.isCreative()) {
                    p.sendStatusMessage(Breach.grimoire("chaosshard.servant.limit", limit(p)), true);
                } else {
                    d.removeTag(TAG_ANCHOR);
                    d.removeTag(TAG_GUARD);
                    ((EntityCreature) e).detachHome();
                    p.sendStatusMessage(Breach.grimoire("chaosshard.servant.follow"), true);
                }
            } else {
                int n = ru.death.chaosshard.servant.work.Work.select(p, e);
                p.sendStatusMessage(Breach.grimoire("chaosshard.work.selected_fighter",
                        new net.minecraft.util.text.TextComponentTranslation("chaosshard.servant.kind." + kind.key()), n), true);
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

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        ServantTravel.clearState();
    }
}
