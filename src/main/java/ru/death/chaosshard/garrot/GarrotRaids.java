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

    /** Набег у края с Якорем в anchor, против игрока target. */
    public static void spawn(WorldServer world, BlockPos anchor, EntityPlayerMP target) {
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return;
        }
        Random r = world.rand;
        double ang = r.nextDouble() * Math.PI * 2;
        int dist = 22 + r.nextInt(8);
        BlockPos base = world.getHeight(new BlockPos(anchor.getX() + Math.cos(ang) * dist, 0, anchor.getZ() + Math.sin(ang) * dist));
        if (!world.isBlockLoaded(base)) {
            base = world.getHeight(target.getPosition().add(12, 0, 12));
        }
        AnchorData data = AnchorData.get(world);
        int number = ++data.raids;
        data.markDirty();

        if (!spawnAncientWarfare(world, base, target, number)) {
            spawnVanilla(world, base, target, number);
        }
        world.playSound(null, base, net.minecraft.init.SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO, SoundCategory.HOSTILE, 2.0F, 0.5F);
        world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 40, 2, 1, 2, 0.02);
        say(target, "chaosshard.garrot.raid." + Math.min(number, RAID_LINES));
        target.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.garrot.where", base.getX(), base.getZ()));
    }

    /** Запасной вариант без Ancient Warfare: переодетые ванильные мобы. */
    private static void spawnVanilla(WorldServer world, BlockPos base, EntityPlayerMP target, int number) {
        Random r = world.rand;
        EntityWitherSkeleton captain = new EntityWitherSkeleton(world);
        setup(world, captain, base, target);
        captain.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        captain.setItemStackToSlot(EntityEquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
        captain.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        double hp = 50 + 10 * Math.min(number, 6);
        captain.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        captain.setHealth((float) hp);
        captain.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6 + Math.min(number, 6));
        captain.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.captain").getUnformattedText());
        captain.setGlowing(true);
        captain.addTag(TAG_CAPTAIN);

        int soldiers = 3 + Math.min(number, 4);
        for (int i = 0; i < soldiers; i++) {
            BlockPos p = world.getHeight(base.add(r.nextInt(7) - 3, 0, r.nextInt(7) - 3));
            EntityLiving s = r.nextInt(3) == 0 ? new EntitySkeleton(world) : new EntityVindicator(world);
            setup(world, s, p, target);
            if (s instanceof AbstractSkeleton) {
                s.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            } else {
                s.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            }
            s.setItemStackToSlot(EntityEquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
            s.setItemStackToSlot(EntityEquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET));
            s.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.soldier").getUnformattedText());
        }
    }

    /**
     * Мрачная Армия из Ancient Warfare 2: фракция «evil» (в переводе сборки — Мрачная Армия).
     * Капитан — элитный лидер, отряд — солдаты, лучники, с третьего набега — палачи.
     * API у AW нет, поэтому через отражение: AWNPCEntities.createNpc(world, type, subtype, faction).
     */
    private static boolean spawnAncientWarfare(WorldServer world, BlockPos base, EntityPlayerMP target, int number) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfarenpc") && !net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfare")) {
            return false;
        }
        try {
            Class<?> cls = Class.forName("net.shadowmage.ancientwarfare.npc.init.AWNPCEntities");
            java.lang.reflect.Method create = cls.getMethod("createNpc", World.class, String.class, String.class, String.class);
            Random r = world.rand;
            EntityLiving captain = (EntityLiving) create.invoke(null, world, "faction.leader.elite", "", FACTION);
            if (captain == null) {
                return false;
            }
            setup(world, captain, base, target);
            double hp = 80 + 15 * Math.min(number, 6);
            captain.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
            captain.setHealth((float) hp);
            captain.setGlowing(true);
            captain.addTag(TAG_CAPTAIN);
            int soldiers = 3 + Math.min(number, 4);
            for (int i = 0; i < soldiers; i++) {
                String type;
                int roll = r.nextInt(10);
                if (number >= 3 && roll < 2) {
                    type = "faction.soldier.elite";
                } else if (roll < 5) {
                    type = "faction.archer";
                } else {
                    type = "faction.soldier";
                }
                EntityLiving s = (EntityLiving) create.invoke(null, world, type, "", FACTION);
                if (s != null) {
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
                    new ItemStack(ModItems.MEMORY_FRAGMENT)));
        }
    }

    private static net.minecraft.nbt.NBTTagCompound persisted(EntityPlayer player) {
        net.minecraft.nbt.NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new net.minecraft.nbt.NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
