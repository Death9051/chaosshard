package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.servant.BeastData.Beast;
import ru.death.chaosshard.servant.BeastData.Entry;

import java.util.List;

/**
 * Уникальные слуги — отголоски старых знакомств: Заяц, Кошка, Тигр. По одному каждого у Осколка.
 * Растут в уровнях (1–5), берут перки, после смерти сами возвращаются из Нити.
 * Способности работают на сервере раз в секунду ({@link #beastTick}).
 */
public abstract class EntityChaosBeast extends EntityCreature {

    private static final DataParameter<Byte> LEVEL = EntityDataManager.createKey(EntityChaosBeast.class, DataSerializers.BYTE);

    public EntityChaosBeast(World world) {
        super(world);
    }

    public abstract Beast beast();

    protected abstract double baseHp();

    protected abstract double speed();

    protected abstract double baseDamage();

    protected double hpPerLevel() {
        return 4;
    }

    protected double damagePerLevel() {
        return 0.5;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(LEVEL, (byte) 1);
    }

    public int level() {
        return dataManager.get(LEVEL);
    }

    public void setLevel(int l) {
        dataManager.set(LEVEL, (byte) l);
    }

    /** Масштаб модели: зверь заметно растёт с уровнем. */
    public float growth() {
        return 1.0F + 0.05F * (level() - 1);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(baseHp());
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(speed());
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(baseDamage());
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24);
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this),
                (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue());
        if (hit) {
            applyEnchantments(this, target);
        }
        return hit;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (world.isRemote) {
            if (level() >= 3 && rand.nextInt(level() >= 5 ? 3 : 8) == 0) {
                world.spawnParticle(level() >= 5 ? EnumParticleTypes.END_ROD : EnumParticleTypes.SPELL_WITCH,
                        posX + (rand.nextDouble() - 0.5) * width, posY + rand.nextDouble() * height, posZ + (rand.nextDouble() - 0.5) * width,
                        0, 0.02, 0);
            }
            return;
        }
        if (ticksExisted % 20 == 0) {
            EntityPlayer o = Servants.owner(this);
            Entry e = Beasts.entry(this);
            if (o != null && e != null && o.world == world) {
                beastTick(o, e, e.level(), ticksExisted / 20);
            }
        }
    }

    /** Раз в секунду, когда хозяин в том же мире. sec — счётчик секунд жизни. */
    protected void beastTick(EntityPlayer o, Entry e, int lvl, int sec) {
    }

    protected boolean near(EntityPlayer o, double r) {
        return o.getDistanceSq(this) < r * r;
    }

    protected WorldServer ws() {
        return (WorldServer) world;
    }

    /** Для Владыки охоты (тигр). */
    public boolean huntReady() {
        return false;
    }

    // ================================================================== Заяц — сборщик и лекарь

    public static class Hare extends EntityChaosBeast {
        private int healCd, cleanseCd;

        public Hare(World w) {
            super(w);
            setSize(0.5F, 0.7F);
        }

        public Beast beast() { return Beast.HARE; }
        protected double baseHp() { return 20; }
        protected double speed() { return 0.42; }
        protected double baseDamage() { return 2; }

        @Override
        protected void beastTick(EntityPlayer o, Entry e, int lvl, int sec) {
            if (o.getDistanceSq(this) > 64 * 64) {
                return;
            }
            if (sec % 2 == 0) {
                collect(o, e, lvl >= 3 ? 12 : 6);
            }
            healCd--;
            cleanseCd--;
            boolean close = near(o, 16);
            // Лечение (ур. 2): хозяину плохо — короткая регенерация
            if (lvl >= 2 && close && healCd <= 0 && o.getHealth() < o.getMaxHealth() * 0.6F) {
                int amp = e.has(Beasts.HARE_MOON) && !world.isDaytime() ? 2 : 1;
                o.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, amp));
                ws().spawnParticle(EnumParticleTypes.HEART, o.posX, o.posY + 1.8, o.posZ, 4, 0.3, 0.2, 0.3, 0.0);
                healCd = e.has(Beasts.HARE_WARM) ? 15 : 30;
                Beasts.addXp(this, 3);
                if (e.has(Beasts.HARE_SHARED)) {
                    for (EntityPlayer other : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(8),
                            x -> x != o && x.getHealth() < x.getMaxHealth() * 0.6F)) {
                        other.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, amp));
                    }
                }
            }
            // Ур. 3: снимает отравление, иссушение и голод
            if (lvl >= 3 && close) {
                o.removePotionEffect(MobEffects.POISON);
                o.removePotionEffect(MobEffects.WITHER);
                o.removePotionEffect(MobEffects.HUNGER);
            }
            // Ур. 4: лечит других слуг рядом
            if (lvl >= 4 && sec % 10 == 0) {
                for (EntityLiving s : world.getEntitiesWithinAABB(EntityLiving.class, getEntityBoundingBox().grow(8),
                        x -> x != this && Servants.isServantOf(x, o.getUniqueID()) && x.getHealth() < x.getMaxHealth() * 0.7F)) {
                    s.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 0));
                }
            }
            // Очищение: снимает любой негативный эффект раз в минуту
            if (e.has(Beasts.HARE_CLEANSE) && close && cleanseCd <= 0) {
                boolean any = false;
                for (PotionEffect pe : new java.util.ArrayList<>(o.getActivePotionEffects())) {
                    if (pe.getPotion().isBadEffect()) {
                        o.removePotionEffect(pe.getPotion());
                        any = true;
                    }
                }
                if (any) {
                    cleanseCd = 60;
                    ws().spawnParticle(EnumParticleTypes.END_ROD, o.posX, o.posY + 1, o.posZ, 20, 0.3, 0.6, 0.3, 0.05);
                }
            }
            // Резвость
            if (e.has(Beasts.HARE_SWIFT) && close) {
                o.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 0, true, false));
            }
        }

        private void collect(EntityPlayer o, Entry e, double r) {
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, getEntityBoundingBox().grow(r),
                    it -> it.isEntityAlive() && !it.cannotPickup());
            boolean picky = e.has(Beasts.HARE_PICKY);
            boolean bag = e.has(Beasts.HARE_BAG) && !Beasts.bagOpen(o.getUniqueID());
            int got = 0;
            for (EntityItem it : items) {
                ItemStack stack = it.getItem();
                if (picky && junk(stack)) {
                    continue;
                }
                ItemStack rest = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(o.inventory), stack.copy(), false);
                if (!rest.isEmpty() && bag) {
                    rest = Beasts.bagInsert(e, rest);
                    Beasts.changed(world, o.getUniqueID());
                }
                if (rest.getCount() == stack.getCount()) {
                    continue;   // некуда — пусть лежит
                }
                ws().spawnParticle(EnumParticleTypes.PORTAL, it.posX, it.posY, it.posZ, 6, 0.1, 0.1, 0.1, 0.2);
                if (rest.isEmpty()) {
                    it.setDead();
                } else {
                    it.setItem(rest);
                }
                got++;
            }
            if (got > 0) {
                Beasts.addXp(this, Math.min(5, got));
            }
            if (e.has(Beasts.HARE_MAGNET)) {
                for (EntityXPOrb orb : world.getEntitiesWithinAABB(EntityXPOrb.class, getEntityBoundingBox().grow(r))) {
                    orb.setPosition(o.posX, o.posY + 0.5, o.posZ);
                }
            }
        }

        private static boolean junk(ItemStack s) {
            Item i = s.getItem();
            return i == Item.getItemFromBlock(Blocks.COBBLESTONE) || i == Item.getItemFromBlock(Blocks.DIRT)
                    || i == Item.getItemFromBlock(Blocks.GRAVEL) || i == Item.getItemFromBlock(Blocks.SAND)
                    || i == Item.getItemFromBlock(Blocks.NETHERRACK) || i == Item.getItemFromBlock(Blocks.STONE)
                    || i == Item.getItemFromBlock(Blocks.SANDSTONE) || i == Items.ROTTEN_FLESH
                    || i == Items.WHEAT_SEEDS || i == Items.POISONOUS_POTATO;
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_RABBIT_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_RABBIT_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_RABBIT_DEATH; }
    }

    // ================================================================== Кошка — добытчица

    public static class Cat extends EntityChaosBeast {
        /** Перерыв между подношениями еды (секунды) по уровням 1–5. */
        private static final int[] FOOD_CD = {180, 150, 120, 90, 60};
        private int foodCd = 30, fishCd = 90;
        private double lastX, lastZ;
        private int still;

        public Cat(World w) {
            super(w);
            setSize(0.6F, 0.7F);
        }

        public Beast beast() { return Beast.CAT; }
        protected double baseHp() { return 24; }
        protected double speed() { return 0.36; }
        protected double baseDamage() { return 4; }

        @Override
        protected void beastTick(EntityPlayer o, Entry e, int lvl, int sec) {
            boolean close = near(o, 16);
            // криперы пятятся от кошки
            if (sec % 2 == 0) {
                int scared = 0;
                for (EntityCreeper c : world.getEntitiesWithinAABB(EntityCreeper.class, getEntityBoundingBox().grow(lvl >= 2 ? 12 : 8))) {
                    if (c.getAttackTarget() != null) {
                        scared++;
                    }
                    calm(c);
                    scare(c);
                }
                if (scared > 0) {
                    Beasts.addXp(this, Math.min(3, scared));
                }
            }
            // Ур. 5: рядом с хозяином криперы не взрываются совсем
            if (lvl >= 5) {
                for (EntityCreeper c : world.getEntitiesWithinAABB(EntityCreeper.class, o.getEntityBoundingBox().grow(16))) {
                    c.setAttackTarget(null);
                    c.setCreeperState(-1);
                }
            }
            if (lvl >= 3 && close) {
                o.addPotionEffect(new PotionEffect(MobEffects.LUCK, 100, 0, true, false));
            }
            if (lvl >= 4 && close && sec % 2 == 0) {
                for (EntityLiving m : world.getEntitiesWithinAABB(EntityLiving.class, o.getEntityBoundingBox().grow(16),
                        x -> x instanceof IMob && !Servants.isServant(x))) {
                    m.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 50, 0, true, false));
                }
            }
            if (e.has(Beasts.CAT_EYE) && close) {
                PotionEffect nv = o.getActivePotionEffect(MobEffects.NIGHT_VISION);
                if (nv == null || nv.getDuration() < 220) {
                    o.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 300, 0, true, false));
                }
            }
            // Мурлыканье: хозяин спит или стоит рядом
            if (e.has(Beasts.CAT_PURR)) {
                boolean stand = Math.abs(o.posX - lastX) < 0.1 && Math.abs(o.posZ - lastZ) < 0.1;
                still = stand ? still + 1 : 0;
                lastX = o.posX;
                lastZ = o.posZ;
                if (near(o, 6) && (o.isPlayerSleeping() || still >= 3) && sec % 2 == 0) {
                    o.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 0, true, false));
                    if (rand.nextInt(4) == 0) {
                        world.playSound(null, getPosition(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.6F, 1.0F);
                    }
                }
            }
            // Охотница на мелочь
            if (e.has(Beasts.CAT_VERMIN) && sec % 2 == 0) {
                for (EntityLiving m : world.getEntitiesWithinAABB(EntityLiving.class, getEntityBoundingBox().grow(8),
                        x -> x instanceof EntitySilverfish || x instanceof EntityEndermite)) {
                    m.attackEntityFrom(DamageSource.causeMobDamage(this), 100);
                }
                for (EntityWitch w : world.getEntitiesWithinAABB(EntityWitch.class, getEntityBoundingBox().grow(12))) {
                    w.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
                    w.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 1));
                }
            }
            // Еда
            foodCd--;
            if (foodCd <= 0 && close && !o.isCreative()) {
                if (o.getFoodStats().getFoodLevel() <= 14) {
                    for (ItemStack s : food(e, lvl)) {
                        drop(o, s);
                    }
                    ws().spawnParticle(EnumParticleTypes.HEART, posX, posY + 0.8, posZ, 3, 0.2, 0.2, 0.2, 0.0);
                    world.playSound(null, getPosition(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.7F, 1.0F);
                    Beasts.addXp(this, 8);
                    foodCd = FOOD_CD[lvl - 1];
                } else if (e.has(Beasts.CAT_HOARD) && e.stash.size() < 3) {
                    e.stash.addAll(food(e, lvl));
                    while (e.stash.size() > 3) {
                        e.stash.remove(e.stash.size() - 1);
                    }
                    Beasts.changed(world, o.getUniqueID());
                    foodCd = FOOD_CD[lvl - 1];
                }
            }
            // Рыбачка: возле воды сама рыбачит
            fishCd--;
            if (e.has(Beasts.CAT_FISHER) && close && fishCd <= 0) {
                fishCd = 90;
                if (nearWater()) {
                    LootContext ctx = new LootContext.Builder(ws()).withLuck(lvl).build();
                    for (ItemStack s : world.getLootTableManager().getLootTableFromLocation(LootTableList.GAMEPLAY_FISHING)
                            .generateLootForPools(rand, ctx)) {
                        drop(o, s);
                    }
                    ws().spawnParticle(EnumParticleTypes.WATER_SPLASH, posX, posY + 0.5, posZ, 20, 0.3, 0.2, 0.3, 0.1);
                    Beasts.addXp(this, 2);
                }
            }
            // Ур. 3: утренний подарок
            long day = world.getWorldTime() / 24000L;
            long time = world.getWorldTime() % 24000L;
            if (lvl >= 3 && close && time < 2000 && e.giftDay != day) {
                e.giftDay = day;
                drop(o, gift());
                Beasts.changed(world, o.getUniqueID());
            }
        }

        private static void calm(EntityCreeper c) {
            c.setAttackTarget(null);
            c.setCreeperState(-1);
        }

        private void scare(EntityCreeper c) {
            Vec3d away = c.getPositionVector().subtract(getPositionVector()).normalize().scale(8);
            c.getNavigator().tryMoveToXYZ(c.posX + away.x, c.posY, c.posZ + away.z, 1.3);
        }

        private boolean nearWater() {
            BlockPos p = getPosition();
            for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-5, -2, -5), p.add(5, 1, 5))) {
                if (world.getBlockState(q).getMaterial() == net.minecraft.block.material.Material.WATER) {
                    return true;
                }
            }
            return false;
        }

        private void drop(EntityPlayer o, ItemStack s) {
            if (s.isEmpty()) {
                return;
            }
            EntityItem it = new EntityItem(world, o.posX, o.posY + 0.3, o.posZ, s);
            it.setPickupDelay(10);
            world.spawnEntity(it);
        }

        /** Еда по уровню: от сырой рыбы до Улова Хаоса. */
        private List<ItemStack> food(Entry e, int lvl) {
            List<ItemStack> out = new java.util.ArrayList<>();
            int portions = 1;
            if (lvl >= 4 && rand.nextInt(4) == 0) {
                portions++;
            }
            if (e.has(Beasts.CAT_GOURMET) && rand.nextInt(4) == 0) {
                portions++;
            }
            for (int i = 0; i < portions; i++) {
                out.add(dish(e, lvl));
            }
            return out;
        }

        private ItemStack dish(Entry e, int lvl) {
            boolean lucky = e.has(Beasts.CAT_LUCKY);
            int salmon = rand.nextInt(2);
            switch (lvl) {
                case 1:
                    return new ItemStack(Items.FISH, 1, salmon);
                case 2:
                    return new ItemStack(Items.COOKED_FISH, 1, salmon);
                case 3:
                    if (lucky && rand.nextInt(20) == 0) {
                        return new ItemStack(ModItems.CHAOS_CATCH);
                    }
                    return mod(salmon == 0 ? "harvestcraft:fishsticksitem" : "harvestcraft:salmononigiriitem",
                            new ItemStack(Items.COOKED_FISH, 2, 1));
                case 4:
                    if (lucky && rand.nextInt(20) == 0) {
                        return new ItemStack(ModItems.CHAOS_CATCH);
                    }
                    return mod(salmon == 0 ? "harvestcraft:fishandchipsitem" : "harvestcraft:fishdinneritem",
                            new ItemStack(Items.COOKED_FISH, 3, 1));
                default:
                    if (rand.nextInt(100) < (lucky ? 25 : 10)) {
                        return new ItemStack(Items.GOLDEN_APPLE);
                    }
                    return new ItemStack(ModItems.CHAOS_CATCH);
            }
        }

        private static ItemStack mod(String id, ItemStack fallback) {
            Item i = Item.REGISTRY.getObject(new ResourceLocation(id));
            return i == null ? fallback : new ItemStack(i);
        }

        private ItemStack gift() {
            int r = rand.nextInt(100);
            if (r < 4) {
                return new ItemStack(ModItems.VEIL_SHARD);
            }
            if (r < 10) {
                return new ItemStack(ModItems.MEMORY_FRAGMENT);
            }
            if (r < 30) {
                return new ItemStack(ModItems.CHAOS_ESSENCE, 1 + rand.nextInt(2));
            }
            Item[] common = {Items.STRING, Items.FEATHER, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.GOLD_NUGGET, Items.SLIME_BALL};
            return new ItemStack(common[rand.nextInt(common.length)], 1 + rand.nextInt(2));
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_CAT_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_CAT_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_CAT_DEATH; }
    }

    // ================================================================== Тигр — боец

    public static class Tiger extends EntityChaosBeast {
        private int leapCd, roarCd, rageCd, hits, huntCd;
        private int lastTarget = -1;

        public Tiger(World w) {
            super(w);
            setSize(1.1F, 1.3F);
            stepHeight = 1.0F;
        }

        public Beast beast() { return Beast.TIGER; }
        protected double baseHp() { return 80; }
        protected double speed() { return 0.33; }
        protected double baseDamage() { return 9; }
        @Override
        protected double hpPerLevel() { return 15; }
        @Override
        protected double damagePerLevel() { return 2; }

        @Override
        protected void applyEntityAttributes() {
            super.applyEntityAttributes();
            getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.7);
        }

        @Override
        public boolean huntReady() {
            if (huntCd > 0) {
                return false;
            }
            huntCd = 8;
            return true;
        }

        @Override
        public void onLivingUpdate() {
            super.onLivingUpdate();
            if (world.isRemote) {
                return;
            }
            // Ур. 2: прыжок на цель (проверка каждые полсекунды)
            if (ticksExisted % 10 == 0 && level() >= 2 && leapCd <= 0) {
                EntityLivingBase t = getAttackTarget();
                if (t != null && onGround) {
                    double d = getDistanceSq(t);
                    if (d > 16 && d < 144) {
                        Vec3d v = t.getPositionVector().subtract(getPositionVector()).normalize();
                        motionX = v.x * 1.1;
                        motionZ = v.z * 1.1;
                        motionY = 0.45;
                        leapCd = 5;
                        world.playSound(null, getPosition(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 0.6F, 0.8F);
                    }
                }
            }
        }

        @Override
        protected void beastTick(EntityPlayer o, Entry e, int lvl, int sec) {
            leapCd--;
            roarCd--;
            rageCd--;
            huntCd--;
            // Ур. 3: рык
            if (lvl >= 3 && roarCd <= 0 && getAttackTarget() != null) {
                roarCd = 20;
                world.playSound(null, getPosition(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 1.2F, 0.5F);
                ws().spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX, posY + 1, posZ, 20, 1.5, 0.3, 1.5, 0.02);
                for (EntityLiving m : world.getEntitiesWithinAABB(EntityLiving.class, getEntityBoundingBox().grow(8),
                        x -> x instanceof IMob && !Servants.isServant(x))) {
                    m.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 120, 1));
                    m.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 120, 0));
                    // Устрашение: мелочь разбегается
                    if (e.has(Beasts.TIGER_DREAD) && m.getMaxHealth() <= 20 && m instanceof EntityCreature) {
                        m.setAttackTarget(null);
                        m.setRevengeTarget(null);
                        Vec3d away = m.getPositionVector().subtract(getPositionVector()).normalize().scale(10);
                        m.getNavigator().tryMoveToXYZ(m.posX + away.x, m.posY, m.posZ + away.z, 1.4);
                    }
                }
            }
            // Ур. 5: ярость, когда хозяину плохо
            if (lvl >= 5 && rageCd <= 0 && near(o, 24) && o.getHealth() < o.getMaxHealth() / 3) {
                rageCd = 60;
                addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 300, 1));
                addPotionEffect(new PotionEffect(MobEffects.SPEED, 300, 1));
                world.playSound(null, getPosition(), SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 0.6F, 1.6F);
                ws().spawnParticle(EnumParticleTypes.REDSTONE, posX, posY + 1, posZ, 40, 0.6, 0.6, 0.6, 0.0);
                for (EntityLiving m : world.getEntitiesWithinAABB(EntityLiving.class, getEntityBoundingBox().grow(12),
                        x -> x instanceof IMob && !Servants.isServant(x))) {
                    m.setAttackTarget(this);
                }
            }
        }

        @Override
        public boolean attackEntityAsMob(Entity target) {
            Entry e = Beasts.entry(this);
            float dmg = (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
            if (e != null) {
                if (e.has(Beasts.TIGER_AMBUSH) && target.getEntityId() != lastTarget) {
                    dmg *= 3;
                }
                if (e.has(Beasts.TIGER_WARDEN) && getEntityData().hasKey(Servants.TAG_GUARD)) {
                    dmg *= 2;
                }
            }
            lastTarget = target.getEntityId();
            boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), dmg);
            if (!hit || e == null || !(target instanceof EntityLivingBase)) {
                return hit;
            }
            applyEnchantments(this, target);
            EntityLivingBase t = (EntityLivingBase) target;
            hits++;
            // Ур. 4: каждый пятый удар — урон Хаосом сквозь броню
            if (e.level() >= 4 && hits % 5 == 0) {
                t.hurtResistantTime = 0;
                t.attackEntityFrom(new EntityDamageSource("chaosshard.chaos", this).setDamageBypassesArmor().setMagicDamage(), dmg * 0.5F);
                ws().spawnParticle(EnumParticleTypes.SPELL_WITCH, t.posX, t.posY + 1, t.posZ, 20, 0.3, 0.5, 0.3, 0.1);
            }
            if (e.has(Beasts.TIGER_BLEED)) {
                t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 60, 0));
            }
            if (e.has(Beasts.TIGER_MARK)) {
                t.getEntityData().setLong(Beasts.TAG_MARK, world.getTotalWorldTime() + 100);
                ws().spawnParticle(EnumParticleTypes.CRIT_MAGIC, t.posX, t.posY + t.height + 0.3, t.posZ, 5, 0.2, 0.1, 0.2, 0.0);
            }
            return true;
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            if (source.isFireDamage() && !world.isRemote) {
                Entry e = Beasts.entry(this);
                if (e != null && e.has(Beasts.TIGER_HIDE)) {
                    extinguish();
                    return false;
                }
            }
            return super.attackEntityFrom(source, amount);
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_POLAR_BEAR_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_POLAR_BEAR_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_POLAR_BEAR_DEATH; }
        @Override
        protected float getSoundPitch() { return 0.7F; }
    }
}
