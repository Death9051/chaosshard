package ru.death.chaosshard.trial;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.List;
import java.util.Random;

/** Ход Испытания: вход, комнаты, условия, защита обломка, выход. */
public class TrialManager {

    public static final String TAG = "chaosshard_trial";
    public static final String TAG_ROOM = "chaosshard_trial_room";
    public static final String TAG_GUARD = "chaosshard_trial_guard";
    public static final String TAG_RISEN = "chaosshard_trial_risen";

    // ------------------------------------------------------------------ вход и выход

    public static void enter(EntityPlayerMP player, int tier, int conditions) {
        WorldServer archive = player.getServer().getWorld(TrialDimension.id());
        TrialData data = TrialData.get(archive);
        TrialData.Trial t = new TrialData.Trial();
        t.id = data.nextId++;
        t.owner = player.getUniqueID();
        t.tier = tier;
        t.conditions = conditions;
        t.returnDim = player.dimension;
        t.returnPos = player.getPosition();
        data.trials.put(t.id, t);
        data.markDirty();
        TrialLayout.build(archive, t);
        BlockPos spawn = t.origin().add(0, 0, 2);
        player.changeDimension(TrialDimension.id(), new ChaosTeleporter(spawn));
        player.sendMessage(Breach.grimoire("chaosshard.trial.enter", tier, describe(conditions)));
    }

    public static String describe(int conditions) {
        StringBuilder sb = new StringBuilder();
        for (TrialCondition c : TrialCondition.values()) {
            if (TrialCondition.has(conditions, c)) {
                sb.append(sb.length() == 0 ? "" : ", ").append(net.minecraft.util.text.translation.I18n.translateToLocal(c.key()));
            }
        }
        return sb.toString();
    }

    public static void finish(EntityPlayerMP player, World world, BlockPos at) {
        if (!TrialDimension.is(world)) {
            return;
        }
        TrialData data = TrialData.get(world);
        TrialData.Trial t = data.at(at);
        if (t == null) {
            return;
        }
        if (!t.done) {
            t.done = true;
            data.completed++;
            data.markDirty();
            int reward = 25 * t.tier;
            ThreadManager.addPoints(player, reward);
            player.sendMessage(Breach.grimoire("chaosshard.trial.done", reward));
            Milestones.grant(player, Milestones.TRIAL);
            int mine = player.getEntityData().getInteger("chaosshard_trials") + 1;
            player.getEntityData().setInteger("chaosshard_trials", mine);
            if (mine >= 3) {
                Milestones.grant(player, Milestones.TRIAL_3);
            }
            if (t.tier >= 3) {
                Milestones.grant(player, Milestones.TRIAL_HARD);
            }
        }
        ChaosTeleporter.toPos(player, t.returnDim, t.returnPos);
    }

    /** Нажата руна: все четыре за 20 секунд открывают проход. */
    public static void onRune(World world, BlockPos pos, EntityPlayer player) {
        TrialData data = TrialData.get(world);
        TrialData.Trial t = data.at(pos);
        if (t == null || t.stage != 2) {
            return;
        }
        BlockPos[] runes = TrialLayout.runePositions(t);
        long now = world.getTotalWorldTime();
        int lit = 0;
        for (int i = 0; i < runes.length; i++) {
            if (runes[i].equals(pos)) {
                t.runes[i] = now;
            }
            if (now - t.runes[i] <= 400 && t.runes[i] > 0) {
                lit++;
            }
        }
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.BLOCKS, 1.0F, 0.6F + 0.2F * lit);
        if (lit >= 4) {
            TrialLayout.openDoor(world, t, 2);
            t.stage = 3;
            data.markDirty();
            player.sendStatusMessage(Breach.grimoire("chaosshard.trial.runes_done"), true);
        } else {
            player.sendStatusMessage(Breach.grimoire("chaosshard.trial.runes", lit), true);
        }
    }

    // ------------------------------------------------------------------ ход по комнатам

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 10 != 0
                || !TrialDimension.is(event.player.world)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        World world = p.world;
        TrialData data = TrialData.get(world);
        TrialData.Trial t = data.at(p.getPosition());
        if (t == null) {
            return;
        }
        applyConditions(p, t);
        BlockPos o = t.origin();
        double z = p.posZ - o.getZ();
        int[][] R = TrialLayout.ROOMS;
        switch (t.stage) {
            case 0:
                if (z >= R[1][1] + 2) {
                    spawnWave(world, t, 1, 4 + 2 * t.tier, false);
                    t.stage = 1;
                    data.markDirty();
                    p.sendStatusMessage(Breach.grimoire("chaosshard.trial.wave"), true);
                }
                break;
            case 1:
                if (alive(world, t, 1) == 0) {
                    TrialLayout.openDoor(world, t, 1);
                    t.stage = 2;
                    data.markDirty();
                    p.sendStatusMessage(Breach.grimoire("chaosshard.trial.cleared"), true);
                }
                break;
            case 3:
                if (z >= R[3][1] + 2) {
                    spawnWave(world, t, 3, 2 + 2 * t.tier, true);
                    t.stage = 4;
                    data.markDirty();
                    p.sendStatusMessage(Breach.grimoire("chaosshard.trial.guard"), true);
                }
                break;
            case 4:
                if (alive(world, t, 3) == 0) {
                    TrialLayout.openDoor(world, t, 3);
                    t.stage = 5;
                    data.markDirty();
                    p.sendStatusMessage(Breach.grimoire("chaosshard.trial.cleared"), true);
                }
                break;
            default:
                break;
        }
    }

    private static void applyConditions(EntityPlayerMP p, TrialData.Trial t) {
        if (TrialCondition.has(t.conditions, TrialCondition.DARK)) {
            p.removePotionEffect(MobEffects.NIGHT_VISION);
        }
        if (TrialCondition.has(t.conditions, TrialCondition.HUNGER)) {
            p.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 40, 1, true, false));
        }
        if (TrialCondition.has(t.conditions, TrialCondition.SLOW)) {
            p.setSprinting(false);
            p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 0, true, false));
            p.removePotionEffect(MobEffects.SPEED);
        }
    }

    private static final String[][] MOBS = {
            {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider"},
            {"minecraft:husk", "minecraft:stray", "minecraft:cave_spider", "minecraft:zombie"},
            {"minecraft:wither_skeleton", "minecraft:vindication_illager", "minecraft:husk", "minecraft:stray"}};

    private static void spawnWave(World world, TrialData.Trial t, int room, int count, boolean guard) {
        Random r = world.rand;
        BlockPos o = t.origin();
        int[] R = TrialLayout.ROOMS[room];
        String[] pool = MOBS[Math.min(t.tier, 3) - 1];
        for (int i = 0; i < count; i++) {
            BlockPos at = o.add(r.nextInt(2 * R[0] - 1) - R[0] + 1, 0, R[1] + 6 + r.nextInt(Math.max(1, R[2] - R[1] - 6)));
            spawn(world, t, room, at, pool[r.nextInt(pool.length)], false);
        }
        if (guard) {
            BlockPos at = o.add(0, 0, (R[1] + R[2]) / 2 + 3);
            EntityLiving g = spawn(world, t, room, at, t.tier >= 2 ? "minecraft:wither_skeleton" : "minecraft:vindication_illager", true);
            if (g != null) {
                g.setCustomNameTag(net.minecraft.util.text.translation.I18n.translateToLocal("chaosshard.trial.guardian"));
                g.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40 + 30 * t.tier);
                g.setHealth(g.getMaxHealth());
                g.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(t.tier >= 3 ? Items.DIAMOND_SWORD : Items.IRON_AXE));
                g.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                g.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1000000, 0, true, false));
                g.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 1000000, t.tier - 1, true, false));
                g.getEntityData().setBoolean(TAG_GUARD, true);
            }
        }
        world.playSound(null, o.add(0, 1, (R[1] + R[2]) / 2), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.6F, 0.6F);
    }

    private static EntityLiving spawn(World world, TrialData.Trial t, int room, BlockPos at, String id, boolean guard) {
        net.minecraft.entity.Entity e = EntityList.createEntityByIDFromName(new ResourceLocation(id), world);
        if (!(e instanceof EntityLiving)) {
            return null;
        }
        EntityLiving m = (EntityLiving) e;
        m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.rand.nextFloat() * 360, 0);
        m.onInitialSpawn(world.getDifficultyForLocation(at), null);
        m.enablePersistence();
        m.getEntityData().setInteger(TAG, t.id);
        m.getEntityData().setInteger(TAG_ROOM, room);
        world.spawnEntity(m);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.5);
        return m;
    }

    private static int alive(World world, TrialData.Trial t, int room) {
        BlockPos o = t.origin();
        int[] R = TrialLayout.ROOMS[room];
        List<EntityLivingBase> list = world.getEntitiesWithinAABB(EntityLivingBase.class,
                new AxisAlignedBB(o.add(-R[0] - 8, -4, R[1] - 8), o.add(R[0] + 8, TrialLayout.HEIGHT + 4, R[2] + 8)),
                e -> e.isEntityAlive() && e.getEntityData().getInteger(TAG) == t.id);
        return list.size();
    }

    /** Мир, где мёртвые встают: павшая тварь обломка иногда поднимается зомби. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        World world = e.world;
        if (world.isRemote || !TrialDimension.is(world) || e.getEntityData().getInteger(TAG) == 0
                || e.getEntityData().getBoolean(TAG_RISEN) || e.getEntityData().getBoolean(TAG_GUARD)) {
            return;
        }
        TrialData.Trial t = TrialData.get(world).trials.get(e.getEntityData().getInteger(TAG));
        if (t != null && TrialCondition.has(t.conditions, TrialCondition.UNDEAD) && world.rand.nextBoolean()) {
            EntityLiving z = spawn(world, t, e.getEntityData().getInteger(TAG_ROOM), e.getPosition(), "minecraft:zombie", false);
            if (z != null) {
                z.getEntityData().setBoolean(TAG_RISEN, true);
            }
        }
    }

    // ------------------------------------------------------------------ обломок нельзя ломать и строить

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (TrialDimension.is(event.getWorld()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (TrialDimension.is(event.getWorld()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBucket(FillBucketEvent event) {
        if (TrialDimension.is(event.getWorld()) && !event.getEntityPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate event) {
        if (TrialDimension.is(event.getWorld())) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public void onSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (TrialDimension.is(event.getWorld())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onGrief(net.minecraftforge.event.entity.EntityMobGriefingEvent event) {
        if (event.getEntity() != null && TrialDimension.is(event.getEntity().world)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
