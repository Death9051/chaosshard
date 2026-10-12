package ru.death.chaosshard.garrot;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityVindicator;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.Random;

/**
 * Мрачная Армия Гаррота: набеги на захваченные края. Капитан и отряд солдат.
 * Гаррот считает Лучезарию своей и очень любит говорить.
 */
public class GarrotRaids {

    public static final String TAG_ARMY = "chaosshard_garrot";
    public static final String TAG_CAPTAIN = "chaosshard_garrot_captain";
    private static final String TAG_KILLS = "chaosshard_captains";
    private static final int ARMY_COLOR = 0x1C1A24;
    private static final int RAID_LINES = 4;
    /** Фракция Ancient Warfare, которая играет Мрачную Армию. */
    public static final String FACTION = "evil";

    public static final String TAG_RAID = "chaosshard_raid", TAG_RAID_LVL = "chaosshard_raid_lvl";
    public static final String TAG_BANNER = "chaosshard_bannerman", TAG_PYRO = "chaosshard_pyromancer";

    /** Набег у Якоря anchor (по уровню сети), против хозяина target. angle — откуда идут. */
    public static void spawn(WorldServer world, BlockPos anchor, EntityPlayerMP target, int level, double angle) {
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return;
        }
        AnchorData data = AnchorData.get(world);
        int number = ++data.raids;
        data.markDirty();
        int squads = level >= 4 ? 2 : 1;
        BlockPos first = null;
        for (int sq = 0; sq < squads; sq++) {
            double ang = angle + sq * Math.PI;
            int dist = 22 + world.rand.nextInt(8);
            BlockPos base = world.getHeight(new BlockPos(anchor.getX() + Math.cos(ang) * dist, 0, anchor.getZ() + Math.sin(ang) * dist));
            if (!world.isBlockLoaded(base)) {
                base = world.getHeight(target.getPosition().add(12, 0, 12));
            }
            if (first == null) {
                first = base;
            }
            CURRENT_ANCHOR = anchor;
            CURRENT_LEVEL = level;
            boolean lead = sq == 0;
            if (!spawnAncientWarfare(world, base, target, number, level, lead)) {
                spawnVanilla(world, base, target, number, level, lead);
            }
            specials(world, base, target, level, lead);
            world.playSound(null, base, net.minecraft.init.SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO, SoundCategory.HOSTILE, 2.0F, 0.5F);
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 40, 2, 1, 2, 0.02);
        }
        say(target, "chaosshard.garrot.raid." + Math.min(number, RAID_LINES));
        target.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.raid.start", level, squads, first.getX(), first.getZ(),
                anchor.getX(), anchor.getZ()));
    }

    /** Старый вызов (отладка): набег уровня 1 с случайной стороны. */
    public static void spawn(WorldServer world, BlockPos anchor, EntityPlayerMP target) {
        spawn(world, anchor, target, 1, world.rand.nextDouble() * Math.PI * 2);
    }

    private static BlockPos CURRENT_ANCHOR;
    private static int CURRENT_LEVEL = 1;

    /** Отряд разом уходит (поражение капитана или взятый Якорь). */
    public static void retreat(World world) {
        RETREATS.put(world.provider.getDimension(), 4);
    }

    /** Запасной вариант без Ancient Warfare: переодетые ванильные мобы. */
    private static void spawnVanilla(WorldServer world, BlockPos base, EntityPlayerMP target, int number, int level, boolean lead) {
        Random r = world.rand;
        EntityLiving captain;
        if (lead) {
            EntityWitherSkeleton c = new EntityWitherSkeleton(world);
            setup(world, c, base, target);
            boolean commander = level >= 5;
            c.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(commander ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
            c.setItemStackToSlot(EntityEquipmentSlot.CHEST, commander ? new ItemStack(Items.DIAMOND_CHESTPLATE) : dyed(Items.LEATHER_CHESTPLATE));
            c.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(commander ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
            double hp = 50 + 10 * Math.min(number, 6) + 20 * (level - 1);
            c.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
            c.setHealth((float) hp);
            c.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6 + Math.min(number, 6) + level);
            c.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation(commander ? "chaosshard.garrot.commander" : "chaosshard.garrot.captain").getUnformattedText());
            c.setGlowing(true);
            c.addTag(TAG_CAPTAIN);
            captain = c;
        }
        int soldiers = 2 + level;
        for (int i = 0; i < soldiers; i++) {
            BlockPos p = world.getHeight(base.add(r.nextInt(7) - 3, 0, r.nextInt(7) - 3));
            boolean archer = level >= 2 && r.nextInt(3) == 0;
            EntityLiving s = archer ? new EntitySkeleton(world) : new EntityVindicator(world);
            setup(world, s, p, target);
            if (s instanceof AbstractSkeleton) {
                s.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            } else {
                s.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            }
            s.setItemStackToSlot(EntityEquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
            s.setItemStackToSlot(EntityEquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET));
            s.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation(archer ? "chaosshard.garrot.archer" : "chaosshard.garrot.soldier").getUnformattedText());
        }
    }

    /** Особые бойцы: щитоносцы и знаменосец с 3-го уровня, пиромант на 5-м. С Ancient Warfare — его солдаты в наших скинах. */
    private static void specials(WorldServer world, BlockPos base, EntityPlayerMP target, int level, boolean lead) {
        if (specialsAW(world, base, target, level, lead)) {
            return;
        }
        Random r = world.rand;
        if (level >= 3) {
            for (int i = 0; i < 2; i++) {
                net.minecraft.entity.monster.EntityHusk s = new net.minecraft.entity.monster.EntityHusk(world);
                setup(world, s, world.getHeight(base.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), target);
                s.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                s.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                s.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                s.setItemStackToSlot(EntityEquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET));
                s.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                s.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
                s.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40);
                s.setHealth(40);
                s.setChild(false);
                s.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.shield").getUnformattedText());
            }
            if (lead) {
                EntityVindicator b = new EntityVindicator(world);
                setup(world, b, world.getHeight(base.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), target);
                ItemStack banner = new ItemStack(Items.BANNER, 1, 0);   // чёрное знамя
                b.setItemStackToSlot(EntityEquipmentSlot.HEAD, banner);
                b.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                b.setItemStackToSlot(EntityEquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
                b.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(36);
                b.setHealth(36);
                b.addTag(TAG_BANNER);
                b.setGlowing(true);
                b.setCustomNameTag(TextFormatting.GOLD + new TextComponentTranslation("chaosshard.garrot.bannerman").getUnformattedText());
            }
        }
        if (level >= 5 && lead) {
            EntitySkeleton p = new EntitySkeleton(world);
            setup(world, p, world.getHeight(base.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), target);
            ItemStack bow = new ItemStack(Items.BOW);
            bow.addEnchantment(net.minecraft.init.Enchantments.FLAME, 1);
            bow.addEnchantment(net.minecraft.init.Enchantments.POWER, 2);
            p.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, bow);
            p.setItemStackToSlot(EntityEquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET));
            p.setItemStackToSlot(EntityEquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
            p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.FIRE_RESISTANCE, 1000000, 0, false, false));
            p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40);
            p.setHealth(40);
            p.addTag(TAG_PYRO);
            p.setGlowing(true);
            p.setCustomNameTag(TextFormatting.RED + new TextComponentTranslation("chaosshard.garrot.pyromancer").getUnformattedText());
        }
    }

    // ------------------------------------------------------------------ Ancient Warfare: особые бойцы в своих скинах

    private static EntityLiving awNpc(WorldServer world, String type) throws Exception {
        Class<?> cls = Class.forName("net.shadowmage.ancientwarfare.npc.init.AWNPCEntities");
        java.lang.reflect.Method create = cls.getMethod("createNpc", World.class, String.class, String.class, String.class);
        return (EntityLiving) create.invoke(null, world, type, "", FACTION);
    }

    /** Свой скин солдату AW: chaosshard:textures/entity/garrot/<name>.png (развёртка скина игрока 64×64). */
    private static void skin(EntityLiving npc, String name) {
        try {
            Class<?> settingsCls = Class.forName("net.shadowmage.ancientwarfare.npc.skin.NpcSkinSettings");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> typeCls = (Class<Enum>) Class.forName("net.shadowmage.ancientwarfare.npc.skin.NpcSkinSettings$SkinType");
            Object settings = settingsCls.newInstance();
            @SuppressWarnings("unchecked")
            Object npcType = Enum.valueOf(typeCls, "NPC_TYPE");
            settingsCls.getMethod("setSkinType", typeCls).invoke(settings, npcType);
            settingsCls.getMethod("setRandom", boolean.class).invoke(settings, false);
            settingsCls.getMethod("setNpcTypeSkin", net.minecraft.util.ResourceLocation.class).invoke(settings,
                    new net.minecraft.util.ResourceLocation(ru.death.chaosshard.Tags.MOD_ID, "textures/entity/garrot/" + name + ".png"));
            npc.getClass().getMethod("setSkinSettings", settingsCls).invoke(npc, settings);
        } catch (Throwable t) {
            ru.death.chaosshard.ChaosShard.LOG.warn("Скин {} для солдата AW не встал: {}", name, t.toString());
        }
    }

    private static boolean specialsAW(WorldServer world, BlockPos base, EntityPlayerMP target, int level, boolean lead) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfarenpc") && !net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfare")) {
            return false;
        }
        try {
            Random r = world.rand;
            if (level >= 3) {
                for (int i = 0; i < 2; i++) {
                    EntityLiving s = awNpc(world, "faction.soldier.elite");
                    if (s == null) {
                        return false;
                    }
                    skin(s, "shieldbearer");
                    setup(world, s, world.getHeight(base.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), target);
                    s.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    s.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                    s.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    s.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                    s.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
                    s.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(45);
                    s.setHealth(45);
                    s.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.shield").getUnformattedText());
                }
                if (lead) {
                    EntityLiving b = awNpc(world, "faction.soldier");
                    if (b != null) {
                        skin(b, "bannerman");
                        setup(world, b, world.getHeight(base.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), target);
                        b.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.BANNER, 1, 0));
                        b.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                        b.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40);
                        b.setHealth(40);
                        b.addTag(TAG_BANNER);
                        b.setGlowing(true);
                        b.setCustomNameTag(TextFormatting.GOLD + new TextComponentTranslation("chaosshard.garrot.bannerman").getUnformattedText());
                    }
                }
            }
            if (level >= 5 && lead) {
                EntityLiving p = awNpc(world, "faction.archer");
                if (p != null) {
                    skin(p, "pyromancer");
                    setup(world, p, world.getHeight(base.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), target);
                    ItemStack bow = new ItemStack(Items.BOW);
                    bow.addEnchantment(net.minecraft.init.Enchantments.FLAME, 1);
                    bow.addEnchantment(net.minecraft.init.Enchantments.POWER, 2);
                    p.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, bow);
                    p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.FIRE_RESISTANCE, 1000000, 0, false, false));
                    p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(45);
                    p.setHealth(45);
                    p.addTag(TAG_PYRO);
                    p.setGlowing(true);
                    p.setCustomNameTag(TextFormatting.RED + new TextComponentTranslation("chaosshard.garrot.pyromancer").getUnformattedText());
                }
            }
            return true;
        } catch (Throwable t) {
            ru.death.chaosshard.ChaosShard.LOG.warn("Особые бойцы через Ancient Warfare не вышли, беру ванильных: {}", t.toString());
            return false;
        }
    }

    /**
     * Мрачная Армия из Ancient Warfare 2: фракция «evil» (в переводе сборки — Мрачная Армия).
     * Капитан — элитный лидер, отряд — солдаты, лучники (со 2-го уровня), элитные солдаты (с 3-го).
     * API у AW нет, поэтому через отражение: AWNPCEntities.createNpc(world, type, subtype, faction).
     */
    private static boolean spawnAncientWarfare(WorldServer world, BlockPos base, EntityPlayerMP target, int number, int level, boolean lead) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfarenpc") && !net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfare")) {
            return false;
        }
        try {
            Class<?> cls = Class.forName("net.shadowmage.ancientwarfare.npc.init.AWNPCEntities");
            java.lang.reflect.Method create = cls.getMethod("createNpc", World.class, String.class, String.class, String.class);
            Random r = world.rand;
            if (lead) {
                EntityLiving captain = (EntityLiving) create.invoke(null, world, "faction.leader.elite", "", FACTION);
                if (captain == null) {
                    return false;
                }
                skin(captain, level >= 5 ? "commander" : "captain");
                setup(world, captain, base, target);
                double hp = 80 + 15 * Math.min(number, 6) + 25 * (level - 1);
                captain.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
                captain.setHealth((float) hp);
                captain.setGlowing(true);
                captain.addTag(TAG_CAPTAIN);
                if (level >= 5) {
                    captain.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.commander").getUnformattedText());
                }
            }
            int soldiers = 2 + level;
            for (int i = 0; i < soldiers; i++) {
                String type;
                int roll = r.nextInt(10);
                if (level >= 3 && roll < 2) {
                    type = "faction.soldier.elite";
                } else if (level >= 2 && roll < 5) {
                    type = "faction.archer";
                } else {
                    type = "faction.soldier";
                }
                EntityLiving s = (EntityLiving) create.invoke(null, world, type, "", FACTION);
                if (s != null) {
                    skin(s, type.equals("faction.archer") ? "archer" : "soldier");
                    setup(world, s, world.getHeight(base.add(r.nextInt(7) - 3, 0, r.nextInt(7) - 3)), target);
                }
            }
            return true;
        } catch (Throwable t) {
            ru.death.chaosshard.ChaosShard.LOG.warn("Мрачная Армия через Ancient Warfare не вышла, беру ванильных мобов: {}", t.toString());
            return false;
        }
    }

    private static void setup(World world, EntityLiving e, BlockPos p, EntityPlayer target) {
        e.setLocationAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, world.rand.nextFloat() * 360, 0);
        e.onInitialSpawn(world.getDifficultyForLocation(p), (IEntityLivingData) null);
        e.enablePersistence();
        e.addTag(TAG_ARMY);
        if (CURRENT_ANCHOR != null) {
            e.getEntityData().setLong(TAG_RAID, CURRENT_ANCHOR.toLong());
            e.getEntityData().setInteger(TAG_RAID_LVL, CURRENT_LEVEL);
        }
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            e.setDropChance(slot, 0.0F);
        }
        world.spawnEntity(e);
        e.setAttackTarget(target);
    }

    private static ItemStack dyed(net.minecraft.item.Item item) {
        ItemStack s = new ItemStack(item);
        ((ItemArmor) item).setColor(s, ARMY_COLOR);
        return s;
    }

    /** Реплика Гаррота. */
    public static void say(EntityPlayer player, String key) {
        TextComponentTranslation name = new TextComponentTranslation("chaosshard.garrot.name");
        name.getStyle().setColor(TextFormatting.DARK_RED).setBold(true);
        TextComponentTranslation line = new TextComponentTranslation(key);
        line.getStyle().setColor(TextFormatting.GRAY);
        player.sendMessage(name.appendText(" ").appendSibling(line));
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        Entity e = event.getEntityLiving();
        if (e.world.isRemote || !e.getTags().contains(TAG_CAPTAIN)) {
            return;
        }
        Entity killer = event.getSource().getTrueSource();
        if (!(killer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) killer;
        net.minecraft.nbt.NBTTagCompound data = persisted(player);
        int kills = data.getInteger(TAG_KILLS) + 1;
        data.setInteger(TAG_KILLS, kills);
        ThreadManager.addPoints(player, ChaosConfig.order.captainReward);
        say(player, kills >= 3 ? "chaosshard.garrot.retreat.3" : "chaosshard.garrot.retreat." + kills);
        Milestones.grant(player, Milestones.GARROT_1);
        if (kills >= 3) {
            Milestones.grant(player, Milestones.GARROT_3);
        }
        // Якорь, на который шли, крепнет
        if (e.getEntityData().hasKey(TAG_RAID)) {
            ru.death.chaosshard.anchor.TileAnchor a = ru.death.chaosshard.anchor.AnchorClusters.at(e.world, BlockPos.fromLong(e.getEntityData().getLong(TAG_RAID)));
            int lvl = Math.max(1, e.getEntityData().getInteger(TAG_RAID_LVL));
            if (a != null) {
                a.repelled(lvl);
            }
            player.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.raid.repelled", 50 * lvl));
            if (lvl >= 4) {
                Milestones.grant(player, "raid_hard");
            }
        }
        // Остальной отряд бежит — убираем на следующих тиках сервера, вне события смерти
        RETREATS.put(e.world.provider.getDimension(), 4);
    }

    /** Измерение → сколько ещё тиков проверять, что отряд ушёл. */
    private static final java.util.Map<Integer, Integer> RETREATS = new java.util.HashMap<>();

    @SubscribeEvent
    public void onServerTick(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || RETREATS.isEmpty()) {
            return;
        }
        java.util.Iterator<java.util.Map.Entry<Integer, Integer>> it = RETREATS.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Integer> r = it.next();
            WorldServer world = net.minecraftforge.common.DimensionManager.getWorld(r.getKey());
            int left = r.getValue() - 1;
            if (world != null) {
                for (Entity other : new java.util.ArrayList<>(world.loadedEntityList)) {
                    if (other.isDead || !other.getTags().contains(TAG_ARMY)) {
                        continue;
                    }
                    world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, other.posX, other.posY + 1, other.posZ, 12, 0.3, 0.5, 0.3, 0.02);
                    if (left <= 1 && other instanceof net.minecraft.entity.EntityLivingBase) {
                        // Последняя попытка: если кто-то держится за мир — добить из пустоты
                        other.attackEntityFrom(net.minecraft.util.DamageSource.OUT_OF_WORLD, 100000.0F);
                    }
                    world.removeEntity(other);
                }
            }
            if (left <= 0) {
                it.remove();
            } else {
                r.setValue(left);
            }
        }
    }

    /** Отряд идёт на Якорь: бьёт тех, кто рядом (меня и слуг), иначе — к Якорю; у Якоря — осада. */
    @SubscribeEvent
    public void onArmyUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        net.minecraft.entity.EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e.ticksExisted % 20 != 7 || !(e instanceof EntityLiving) || !e.getTags().contains(TAG_ARMY)
                || !e.getEntityData().hasKey(TAG_RAID)) {
            return;
        }
        EntityLiving m = (EntityLiving) e;
        BlockPos anchor = BlockPos.fromLong(m.getEntityData().getLong(TAG_RAID));
        net.minecraft.entity.EntityLivingBase t = m.getAttackTarget();
        if (t == null || !t.isEntityAlive() || t.getDistanceSq(m) > 24 * 24) {
            net.minecraft.entity.EntityLivingBase best = null;
            double bd = 12 * 12;
            for (net.minecraft.entity.EntityLivingBase c : m.world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class,
                    m.getEntityBoundingBox().grow(12), x -> x.isEntityAlive() && (x instanceof EntityPlayer && !((EntityPlayer) x).isCreative()
                            && !((EntityPlayer) x).isSpectator() || ru.death.chaosshard.servant.Servants.isServant(x)))) {
                double d = c.getDistanceSq(m);
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
            m.setAttackTarget(best);
            if (best == null) {
                m.getNavigator().tryMoveToXYZ(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.0);
            }
        }
        WorldServer ws = (WorldServer) m.world;
        if (m.getDistanceSq(anchor) <= 6 * 6) {
            ru.death.chaosshard.anchor.TileAnchor a = ru.death.chaosshard.anchor.AnchorClusters.at(m.world, anchor);
            if (a != null) {
                a.siegeTick(null);
            }
        }
        // Знаменосец: Скверна вокруг отступает; Пиромант: выжигает корку
        if (m.ticksExisted % 40 == 7 && (m.getTags().contains(TAG_BANNER) || m.getTags().contains(TAG_PYRO))) {
            boolean pyro = m.getTags().contains(TAG_PYRO);
            int r = pyro ? 8 : 6;
            for (int i = 0; i < 12; i++) {
                BlockPos p = m.world.getHeight(m.getPosition().add(m.world.rand.nextInt(2 * r + 1) - r, 0, m.world.rand.nextInt(2 * r + 1) - r)).down();
                if (ru.death.chaosshard.anchor.Corruption.cleanse(m.world, p)) {
                    ws.spawnParticle(pyro ? EnumParticleTypes.FLAME : EnumParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.02);
                    if (pyro && m.world.isAirBlock(p.up()) && m.world.rand.nextInt(3) == 0) {
                        m.world.setBlockState(p.up(), net.minecraft.init.Blocks.FIRE.getDefaultState(), 11);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        Entity e = event.getEntityLiving();
        if (e.world.isRemote || !e.getTags().contains(TAG_CAPTAIN)) {
            return;
        }
        event.getDrops().add(new net.minecraft.entity.item.EntityItem(e.world, e.posX, e.posY, e.posZ,
                new ItemStack(ModItems.GARROT_BANNER)));
        if (e.world.rand.nextBoolean()) {
            event.getDrops().add(new net.minecraft.entity.item.EntityItem(e.world, e.posX, e.posY, e.posZ,
                    new ItemStack(ModItems.WORLD_SHARD)));
        }
        int lvl = Math.max(1, e.getEntityData().getInteger(TAG_RAID_LVL));
        event.getDrops().add(new net.minecraft.entity.item.EntityItem(e.world, e.posX, e.posY, e.posZ,
                new ItemStack(ModItems.CHAOS_ESSENCE, 1 + lvl)));
    }

    private static net.minecraft.nbt.NBTTagCompound persisted(EntityPlayer player) {
        net.minecraft.nbt.NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new net.minecraft.nbt.NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
