package ru.death.chaosshard.trial;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.oredict.OreDictionary;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Ход Испытания: вход, комнаты по очереди, законы обломка, защита обломка, выход. */
public class TrialManager {

    public static final String TAG = "chaosshard_trial";
    public static final String TAG_ROOM = "chaosshard_trial_room";
    public static final String TAG_GUARD = "chaosshard_trial_guard";
    public static final String TAG_RISEN = "chaosshard_trial_risen";
    public static final String TAG_HUNTER = "chaosshard_trial_hunter";

    /** Сколько тиков отпущено на комнату в Мире часов. */
    public static final int CLOCK_TICKS = 20 * 75;
    public static final int HOLD_NEED = 600;

    // ------------------------------------------------------------------ вход и выход

    public static void enter(EntityPlayerMP player, int tier, int conditions, int theme) {
        WorldServer archive = player.getServer().getWorld(TrialDimension.id());
        TrialData data = TrialData.get(archive);
        TrialData.Trial t = new TrialData.Trial();
        t.id = data.nextId++;
        t.owner = player.getUniqueID();
        t.tier = tier;
        t.conditions = conditions;
        t.theme = theme;
        t.returnDim = player.dimension;
        t.returnPos = player.getPosition();
        t.rooms = planRooms(tier, new Random(archive.rand.nextLong()));
        data.trials.put(t.id, t);
        data.markDirty();
        TrialLayout.build(archive, t);
        BlockPos spawn = t.origin().add(0, 0, 2);
        player.changeDimension(TrialDimension.id(), new ChaosTeleporter(spawn));
        player.sendMessage(Breach.grimoire("chaosshard.trial.enter", tier, I18n.translateToLocal(t.theme().key()), describe(conditions)));
    }

    /** Вход, 2+ступень комнат-испытаний без повторов, Страж, сокровищница. */
    static int[] planRooms(int tier, Random r) {
        List<TrialRoom> pool = new ArrayList<>(java.util.Arrays.asList(TrialRoom.CHALLENGES));
        java.util.Collections.shuffle(pool, r);
        int n = Math.min(pool.size(), 2 + tier);
        int[] out = new int[n + 3];
        out[0] = TrialRoom.ENTRY.ordinal();
        for (int i = 0; i < n; i++) {
            out[i + 1] = pool.get(i).ordinal();
        }
        out[n + 1] = TrialRoom.GUARD.ordinal();
        out[n + 2] = TrialRoom.TREASURE.ordinal();
        return out;
    }

    public static String describe(int conditions) {
        StringBuilder sb = new StringBuilder();
        for (TrialCondition c : TrialCondition.values()) {
            if (TrialCondition.has(conditions, c)) {
                sb.append(sb.length() == 0 ? "" : ", ").append(I18n.translateToLocal(c.key()));
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
            int reward = 25 * t.tier + 5 * t.fast;
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
        removeHunter(world, t);
        ChaosTeleporter.toPos(player, t.returnDim, t.returnPos);
    }

    // ------------------------------------------------------------------ руны: Руны, Последовательность, Подношение

    public static void onRune(World world, BlockPos pos, EntityPlayer player) {
        TrialData data = TrialData.get(world);
        TrialData.Trial t = data.at(pos);
        if (t == null) {
            return;
        }
        int room = t.roomAt(pos);
        if (room < 0 || room != t.room || t.roomState != 1) {
            return;
        }
        switch (t.roomType(room)) {
            case RUNES: {
                BlockPos[] runes = TrialLayout.runePositions(t, room);
                long now = world.getTotalWorldTime();
                int lit = 0;
                for (int i = 0; i < runes.length; i++) {
                    if (runes[i].equals(pos)) {
                        t.runes[i] = now;
                    }
                    if (t.runes[i] > 0 && now - t.runes[i] <= 400) {
                        lit++;
                    }
                }
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.BLOCKS, 1.0F, 0.6F + 0.2F * lit);
                if (lit >= 4) {
                    complete(world, t, room, player);
                } else {
                    player.sendStatusMessage(Breach.grimoire("chaosshard.trial.runes", lit), true);
                }
                break;
            }
            case SEQUENCE: {
                if (t.showStep < t.seq.length * 2) {
                    return;   // ещё показывает
                }
                BlockPos[] runes = TrialLayout.sequencePositions(t, room);
                int idx = -1;
                for (int i = 0; i < runes.length; i++) {
                    if (runes[i].equals(pos)) idx = i;
                }
                if (idx < 0) {
                    return;
                }
                if (t.seq[t.seqPos] == idx) {
                    t.seqPos++;
                    world.playSound(null, pos, SoundEvents.BLOCK_NOTE_HARP, SoundCategory.BLOCKS, 1.0F, 0.6F + 0.3F * idx);
                    world.scheduleUpdate(pos, world.getBlockState(pos).getBlock(), 10);
                    if (t.seqPos >= t.seq.length) {
                        complete(world, t, room, player);
                    }
                } else {
                    world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BASS, SoundCategory.BLOCKS, 1.0F, 0.5F);
                    player.sendStatusMessage(Breach.grimoire("chaosshard.trial.seq_wrong"), true);
                    spawnAround(world, t, room, 2);
                    t.seqPos = 0;
                    t.showStep = 0;
                    t.nextEvent = world.getTotalWorldTime() + 40;
                    unlightAll(world, runes);
                }
                break;
            }
            case OFFERING: {
                ItemStack held = player.getHeldItemMainhand();
                if (held.isEmpty()) {
                    player.sendStatusMessage(Breach.grimoire("chaosshard.trial.offer_hint", riddle(t, room)), true);
                    unlightAll(world, new BlockPos[]{pos});
                    return;
                }
                boolean ok = fits(held, offeringKind(t, room));
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                if (ok) {
                    world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.8F, 1.4F);
                    complete(world, t, room, player);
                } else {
                    world.playSound(null, pos, SoundEvents.ENTITY_WITHER_HURT, SoundCategory.BLOCKS, 0.6F, 0.8F);
                    player.sendStatusMessage(Breach.grimoire("chaosshard.trial.offer_wrong"), true);
                    spawnAround(world, t, room, 2);
                    unlightAll(world, new BlockPos[]{pos});
                }
                break;
            }
            default:
                break;
        }
    }

    /** Руна гаснет, если её комната ещё не пройдена (руны пройденных комнат горят). */
    public static boolean keepLit(World world, BlockPos pos) {
        TrialData.Trial t = TrialData.get(world).at(pos);
        if (t == null) {
            return false;
        }
        int room = t.roomAt(pos);
        return room >= 0 && room < t.room;
    }

    private static void unlightAll(World world, BlockPos[] runes) {
        for (BlockPos p : runes) {
            net.minecraft.block.state.IBlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof TrialBlocks.Rune && s.getValue(TrialBlocks.Rune.LIT)) {
                world.setBlockState(p, s.withProperty(TrialBlocks.Rune.LIT, false), 3);
            }
        }
    }

    private static void light(World world, BlockPos p, boolean on) {
        net.minecraft.block.state.IBlockState s = world.getBlockState(p);
        if (s.getBlock() instanceof TrialBlocks.Rune) {
            world.setBlockState(p, s.withProperty(TrialBlocks.Rune.LIT, on), 3);
        }
    }

    // ------------------------------------------------------------------ Подношение: загадки

    private static int offeringKind(TrialData.Trial t, int room) {
        return Math.floorMod(t.id * 7 + room * 3, 5);
    }

    public static String riddle(TrialData.Trial t, int room) {
        return I18n.translateToLocal("chaosshard.trial.riddle." + offeringKind(t, room));
    }

    /** Строки таблички над алтарём подношения. */
    public static String[] riddleLines(TrialData.Trial t, int room) {
        return I18n.translateToLocal("chaosshard.trial.riddle." + offeringKind(t, room) + ".sign").split("\\|");
    }

    private static boolean fits(ItemStack s, int kind) {
        Item i = s.getItem();
        Block b = i instanceof ItemBlock ? ((ItemBlock) i).getBlock() : null;
        switch (kind) {
            case 0:   // то, что тянется к солнцу
                return i instanceof ItemSeeds || b instanceof net.minecraft.block.BlockSapling || b instanceof net.minecraft.block.BlockBush
                        || i instanceof net.minecraftforge.common.IPlantable;
            case 1:   // то, что гонит тьму
                return b != null && b.getDefaultState().getLightValue() >= 10;
            case 2:   // то, что отнимает жизнь
                return i instanceof ItemSword || i instanceof ItemAxe || i instanceof ItemBow;
            case 3:   // то, что утоляет голод
                return i instanceof ItemFood;
            default:  // то, что родилось в горне
                for (int id : OreDictionary.getOreIDs(s)) {
                    if (OreDictionary.getOreName(id).startsWith("ingot")) {
                        return true;
                    }
                }
                return false;
        }
    }

    // ------------------------------------------------------------------ ход по комнатам

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || !TrialDimension.is(event.player.world)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        World world = p.world;
        TrialData data = TrialData.get(world);
        TrialData.Trial t = data.at(p.getPosition());
        if (t == null || t.rooms.length == 0) {
            return;
        }
        long now = world.getTotalWorldTime();
        if (t.started == 0) {
            t.started = now;
        }
        everyTick(p, world, t, now);
        if (p.ticksExisted % 10 != 0) {
            return;
        }
        conditions(p, world, t, now);
        int room = t.room;
        if (room >= t.rooms.length) {
            return;
        }
        int[][] b = t.bounds();
        double z = p.posZ - t.origin().getZ();
        TrialRoom type = t.roomType(room);
        if (t.roomState == 0 || t.roomStart == 0) {
            if (type == TrialRoom.ENTRY) {
                t.roomState = 1;
                complete(world, t, room, p);
                return;
            }
            if (z >= b[room][0] + 1 || t.roomState == 1) {
                start(world, t, room, p, now);
            }
            return;
        }
        // Мир часов: время вышло — обломок присылает подкрепление
        if (TrialCondition.has(t.conditions, TrialCondition.CLOCK) && !t.timeUp && now - t.roomStart > CLOCK_TICKS) {
            t.timeUp = true;
            p.sendStatusMessage(Breach.grimoire("chaosshard.trial.clock_out"), true);
            spawnAround(world, t, room, 2 + t.tier);
        }
        switch (type) {
            case ARENA: case GUARD:
                if (alive(world, t, room) == 0) {
                    complete(world, t, room, p);
                }
                break;
            case SEQUENCE:
                sequenceTick(world, t, room, now);
                break;
            case HOLD:
                holdTick(world, t, room, p, now);
                break;
            case TRAPS:
                trapsTick(world, t, room, p, now, z);
                break;
            case PARKOUR:
                if (z >= b[room][1] - 2 && p.posY >= t.origin().getY() - 1) {
                    complete(world, t, room, p);
                }
                break;
            case TREASURE:
                complete(world, t, room, p);
                break;
            default:
                break;
        }
    }

    private static void start(World world, TrialData.Trial t, int room, EntityPlayerMP p, long now) {
        t.roomState = 1;
        t.roomStart = now;
        t.timeUp = false;
        t.progress = 0;
        t.trodden.clear();
        TrialData.get(world).markDirty();
        TrialRoom type = t.roomType(room);
        p.sendStatusMessage(Breach.grimoire("chaosshard.trial.start." + type.name().toLowerCase(java.util.Locale.ROOT)), true);
        switch (type) {
            case ARENA:
                spawnWave(world, t, room, 4 + 2 * t.tier, false);
                break;
            case GUARD:
                spawnWave(world, t, room, 2 + 2 * t.tier, true);
                break;
            case SEQUENCE: {
                Random r = new Random(t.id * 131L + room);
                t.seq = new int[3 + t.tier];
                for (int i = 0; i < t.seq.length; i++) {
                    t.seq[i] = r.nextInt(4);
                }
                t.seqPos = 0;
                t.showStep = 0;
                t.nextEvent = now + 40;
                break;
            }
            case OFFERING:
                p.sendMessage(Breach.grimoire("chaosshard.trial.offer_hint", riddle(t, room)));
                break;
            case TREASURE:
                if (TrialCondition.has(t.conditions, TrialCondition.CLOCK) && t.fast > 0) {
                    List<ItemStack> bonus = new ArrayList<>();
                    bonus.add(new ItemStack(ModItems.VEIL_SHARD, 2 * t.fast));
                    bonus.add(new ItemStack(ModItems.CHAOS_ESSENCE, 2 * t.fast));
                    bonus.add(new ItemStack(ModItems.WORLD_SHARD, t.fast));
                    TrialLayout.addToTreasure(world, t, bonus);
                    p.sendMessage(Breach.grimoire("chaosshard.trial.clock_bonus", t.fast));
                }
                break;
            default:
                break;
        }
    }

    private static void complete(World world, TrialData.Trial t, int room, EntityPlayer p) {
        if (t.room != room || t.roomState != 1) {
            return;
        }
        TrialLayout.openDoor(world, t, room);
        TrialRoom type = t.roomType(room);
        if (TrialCondition.has(t.conditions, TrialCondition.CLOCK) && type != TrialRoom.ENTRY && type != TrialRoom.TREASURE
                && world.getTotalWorldTime() - t.roomStart <= CLOCK_TICKS) {
            t.fast++;
        }
        if (TrialCondition.has(t.conditions, TrialCondition.PRICE) && type != TrialRoom.ENTRY && p != null) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 6));
            ((WorldServer) world).spawnParticle(EnumParticleTypes.HEART, p.posX, p.posY + 1.8, p.posZ, 4, 0.3, 0.2, 0.3, 0.0);
        }
        // в комнате с удержанием и прочих: оставшихся тварей обломок забирает
        if (type != TrialRoom.ARENA && type != TrialRoom.GUARD) {
            for (EntityLivingBase e : roomMobs(world, t, room)) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, e.posX, e.posY + 1, e.posZ, 8, 0.3, 0.5, 0.3, 0.02);
                e.setDead();
            }
        }
        t.room = room + 1;
        t.roomState = 0;
        t.roomStart = 0;
        TrialData.get(world).markDirty();
        if (p != null && type != TrialRoom.ENTRY && type != TrialRoom.TREASURE) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.trial.cleared"), true);
            world.playSound(null, p.getPosition(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 0.8F);
        }
    }

    // ------------------------------------------------------------------ комнаты

    private static void sequenceTick(World world, TrialData.Trial t, int room, long now) {
        if (t.showStep >= t.seq.length * 2 || now < t.nextEvent) {
            return;
        }
        BlockPos[] runes = TrialLayout.sequencePositions(t, room);
        int i = t.showStep / 2;
        boolean on = t.showStep % 2 == 0;
        light(world, runes[t.seq[i]], on);
        if (on) {
            world.playSound(null, runes[t.seq[i]], SoundEvents.BLOCK_NOTE_HARP, SoundCategory.BLOCKS, 1.0F, 0.6F + 0.3F * t.seq[i]);
        }
        t.showStep++;
        t.nextEvent = now + (on ? 12 : 6);
    }

    private static void holdTick(World world, TrialData.Trial t, int room, EntityPlayerMP p, long now) {
        BlockPos o = t.origin();
        int[] b = t.bounds()[room];
        double cz = o.getZ() + (b[0] + b[1]) / 2.0 + 0.5, cx = o.getX() + 0.5;
        boolean in = (p.posX - cx) * (p.posX - cx) + (p.posZ - cz) * (p.posZ - cz) <= 7.0;
        t.progress = Math.max(0, t.progress + (in ? 10 : -5));
        if (now >= t.nextEvent) {
            t.nextEvent = now + 120;
            spawnAround(world, t, room, 1 + t.tier / 2);
        }
        if (p.ticksExisted % 40 == 0) {
            p.sendStatusMessage(Breach.grimoire(in ? "chaosshard.trial.hold" : "chaosshard.trial.hold_out", t.progress * 100 / HOLD_NEED), true);
        }
        if (in) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, cx, o.getY() + 0.2, cz, 6, 1.4, 0.1, 1.4, 0.0);
        }
        if (t.progress >= HOLD_NEED) {
            complete(world, t, room, p);
        }
    }

    private static void trapsTick(World world, TrialData.Trial t, int room, EntityPlayerMP p, long now, double z) {
        int[] b = t.bounds()[room];
        if (z >= b[1] - 1) {
            complete(world, t, room, p);
            return;
        }
        BlockPos o = t.origin();
        Random r = world.rand;
        // стрелы из стен на высоте груди, впереди меня
        if (now % 20 < 10) {
            int side = r.nextBoolean() ? 1 : -1;
            double az = p.posZ + 1 + r.nextInt(3);
            EntityTippedArrow a = new EntityTippedArrow(world, o.getX() + 0.5 + side * 1.6, p.posY + 1.2, az);
            a.shoot(-side, 0.02, 0, 1.6F, 0.0F);
            a.pickupStatus = net.minecraft.entity.projectile.EntityArrow.PickupStatus.DISALLOWED;
            world.spawnEntity(a);
            world.playSound(null, a.posX, a.posY, a.posZ, SoundEvents.BLOCK_DISPENSER_LAUNCH, SoundCategory.BLOCKS, 0.6F, 1.2F);
        }
        // огненный выдох: сначала дым (предупреждение), через секунду — пламя на этой плитке
        if (now >= t.nextEvent) {
            t.nextEvent = now + 30;
            t.progress = (int) Math.floor(z + 2 + r.nextInt(3));
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL, o.getX() + 0.5, o.getY() + 0.1, o.getZ() + t.progress + 0.5,
                    20, 0.6, 0.05, 0.3, 0.01);
        } else if (t.nextEvent - now == 10 && t.progress > 0) {
            double fz = o.getZ() + t.progress + 0.5;
            ((WorldServer) world).spawnParticle(EnumParticleTypes.FLAME, o.getX() + 0.5, o.getY() + 0.3, fz, 40, 0.6, 0.6, 0.3, 0.02);
            world.playSound(null, o.getX(), o.getY(), fz, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.8F, 0.8F);
            if (Math.abs(p.posZ - fz) < 1.0) {
                p.setFire(3);
                p.attackEntityFrom(DamageSource.IN_FIRE, 2 + t.tier);
            }
        }
    }

    /** Паркур: упал в яму — назад на начало комнаты, ценой здоровья. */
    private static void parkourCheck(EntityPlayerMP p, World world, TrialData.Trial t) {
        int room = t.room;
        if (room >= t.rooms.length || t.roomType(room) != TrialRoom.PARKOUR || t.roomState != 1) {
            return;
        }
        BlockPos o = t.origin();
        if (p.posY < o.getY() - 2.5) {
            int[] b = t.bounds()[room];
            p.connection.setPlayerLocation(o.getX() + 0.5, o.getY(), o.getZ() + b[0] + 1.5, 0, 0);
            p.fallDistance = 0;
            p.attackEntityFrom(DamageSource.OUT_OF_WORLD, 3 + t.tier);
            p.sendStatusMessage(Breach.grimoire("chaosshard.trial.parkour_fall"), true);
        }
    }

    // ------------------------------------------------------------------ законы обломка

    private static void everyTick(EntityPlayerMP p, World world, TrialData.Trial t, long now) {
        parkourCheck(p, world, t);
        if (TrialCondition.has(t.conditions, TrialCondition.MEMORY)) {
            memory(p, world, t);
        }
        if (TrialCondition.has(t.conditions, TrialCondition.HUNTER)) {
            hunter(p, world, t, now);
        }
    }

    private static void conditions(EntityPlayerMP p, World world, TrialData.Trial t, long now) {
        if (TrialCondition.has(t.conditions, TrialCondition.SILENCE)) {
            for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, p.getEntityBoundingBox().grow(40),
                    x -> x.isEntityAlive() && x.getEntityData().getInteger(TAG) == t.id)) {
                if (e.getDistanceSq(p) > 36) {
                    e.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 25, 0, true, false));
                } else {
                    e.removePotionEffect(MobEffects.INVISIBILITY);
                }
            }
        }
    }

    /** Мир памяти: по своей же плитке второй раз — больно; пройденные плитки меняют вид. */
    private static void memory(EntityPlayerMP p, World world, TrialData.Trial t) {
        if (!p.onGround || p.isCreative()) {
            return;
        }
        BlockPos under = new BlockPos(p.posX, p.posY - 0.2, p.posZ);
        if (t.roomAt(under) < 0 || t.roomAt(under) != t.room || under.getY() != t.origin().getY() - 1) {
            return;
        }
        long key = under.toLong();
        if (key == t.lastSafe) {
            return;
        }
        t.lastSafe = key;
        if (!t.trodden.add(key)) {
            p.attackEntityFrom(DamageSource.MAGIC, 2);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT_MAGIC, p.posX, p.posY + 0.2, p.posZ, 10, 0.3, 0.1, 0.3, 0.1);
        } else if (world.getBlockState(under).isFullCube()) {
            world.setBlockState(under, t.theme().trodden(), 2);
        }
    }

    /** Мир Охотника: неуязвимый, идёт сквозь стены медленнее шага, бьёт при касании. */
    private static void hunter(EntityPlayerMP p, World world, TrialData.Trial t, long now) {
        if (now - t.started < 300 || t.room >= t.rooms.length - 1) {
            return;
        }
        Entity h = t.hunter == null ? null : ((WorldServer) world).getEntityFromUuid(t.hunter);
        if (!(h instanceof EntityLiving) || !h.isEntityAlive()) {
            BlockPos at = t.origin().add(0, 0, 2);
            Entity e = EntityList.createEntityByIDFromName(new ResourceLocation("minecraft:wither_skeleton"), world);
            if (!(e instanceof EntityLiving)) {
                return;
            }
            EntityLiving m = (EntityLiving) e;
            m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            m.setNoAI(true);
            m.setEntityInvulnerable(true);
            m.setNoGravity(true);
            m.enablePersistence();
            m.setCustomNameTag(I18n.translateToLocal("chaosshard.trial.hunter"));
            m.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            m.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1000000, 0, true, false));
            m.getEntityData().setInteger(TAG, t.id);
            m.getEntityData().setBoolean(TAG_HUNTER, true);
            world.spawnEntity(m);
            ru.death.chaosshard.servant.Resync.later(m, 5);
            t.hunter = m.getUniqueID();
            p.sendMessage(Breach.grimoire("chaosshard.trial.hunter_comes"));
            return;
        }
        double dx = p.posX - h.posX, dy = p.posY - h.posY, dz = p.posZ - h.posZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = 0.05 + 0.01 * t.tier;
        if (d > 1.1) {
            h.noClip = true;
            h.setPositionAndUpdate(h.posX + dx / d * speed, h.posY + dy / d * speed, h.posZ + dz / d * speed);
            h.rotationYaw = ((EntityLiving) h).rotationYawHead = ((EntityLiving) h).renderYawOffset = (float) (Math.atan2(-dx, dz) * 180 / Math.PI);
            if (now % 10 == 0) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, h.posX, h.posY + 1, h.posZ, 3, 0.2, 0.5, 0.2, 0.0);
            }
        } else if (now % 20 == 0) {
            p.attackEntityFrom(DamageSource.causeMobDamage((EntityLivingBase) h), 4 + 2 * t.tier);
            world.playSound(null, h.getPosition(), SoundEvents.ENTITY_WITHER_SKELETON_HURT, SoundCategory.HOSTILE, 1.0F, 0.5F);
        }
    }

    private static void removeHunter(World world, TrialData.Trial t) {
        if (t.hunter != null && world instanceof WorldServer) {
            Entity h = ((WorldServer) world).getEntityFromUuid(t.hunter);
            if (h != null) {
                h.setDead();
            }
            t.hunter = null;
        }
    }

    /** Мир цены: здоровье не восстанавливается само. */
    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!(e instanceof EntityPlayer) || e.world.isRemote || !TrialDimension.is(e.world) || ((EntityPlayer) e).isCreative()) {
            return;
        }
        TrialData.Trial t = TrialData.get(e.world).at(e.getPosition());
        if (t != null && !t.done && TrialCondition.has(t.conditions, TrialCondition.PRICE)) {
            event.setCanceled(true);
        }
    }

    /** Мир долга: открыл сундук в комнате — засада. */
    @SubscribeEvent
    public void onOpenChest(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !TrialDimension.is(world) || world.getBlockState(event.getPos()).getBlock() != Blocks.CHEST) {
            return;
        }
        TrialData.Trial t = TrialData.get(world).at(event.getPos());
        if (t == null || !TrialCondition.has(t.conditions, TrialCondition.DEBT)) {
            return;
        }
        int room = t.roomAt(event.getPos());
        if (room < 0 || t.roomType(room) == TrialRoom.TREASURE || !t.debtOpened.add(event.getPos().toLong())) {
            return;
        }
        TrialData.get(world).markDirty();
        event.getEntityPlayer().sendStatusMessage(Breach.grimoire("chaosshard.trial.debt"), true);
        spawnAround(world, t, room, 2 + t.tier);
    }

    // ------------------------------------------------------------------ твари

    private static void spawnWave(World world, TrialData.Trial t, int room, int count, boolean guard) {
        Random r = world.rand;
        BlockPos o = t.origin();
        int[] b = t.bounds()[room];
        int h = t.roomType(room).half;
        String[] pool = t.theme().mobs(t.tier);
        for (int i = 0; i < count; i++) {
            BlockPos at = o.add(r.nextInt(2 * h - 1) - h + 1, 0, b[0] + 6 + r.nextInt(Math.max(1, b[1] - b[0] - 6)));
            spawn(world, t, room, at, pool[r.nextInt(pool.length)]);
        }
        if (guard) {
            BlockPos at = o.add(0, 0, (b[0] + b[1]) / 2 + 3);
            EntityLiving g = spawn(world, t, room, at, t.theme().guard(t.tier));
            if (g != null) {
                g.setCustomNameTag(I18n.translateToLocal("chaosshard.trial.guardian"));
                g.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40 + 30 * t.tier);
                g.setHealth(g.getMaxHealth());
                g.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(t.tier >= 3 ? Items.DIAMOND_SWORD : Items.IRON_AXE));
                g.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                g.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1000000, 0, true, false));
                g.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 1000000, t.tier - 1, true, false));
                g.getEntityData().setBoolean(TAG_GUARD, true);
            }
        }
        world.playSound(null, o.add(0, 1, (b[0] + b[1]) / 2), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.6F, 0.6F);
    }

    /** Несколько тварей у краёв комнаты (засада, ошибка, подкрепление). */
    private static void spawnAround(World world, TrialData.Trial t, int room, int count) {
        Random r = world.rand;
        BlockPos o = t.origin();
        int[] b = t.bounds()[room];
        int h = Math.max(1, t.roomType(room).half);
        String[] pool = t.theme().mobs(t.tier);
        for (int i = 0; i < count; i++) {
            int x = r.nextBoolean() ? -h + 1 : h - 1;
            if (h <= 1) x = 0;
            BlockPos at = o.add(x, 0, b[0] + 1 + r.nextInt(Math.max(1, b[1] - b[0] - 1)));
            if (t.roomType(room) == TrialRoom.PARKOUR) {
                at = o.add(0, 0, b[0] + (r.nextBoolean() ? 0 : b[1] - b[0]));
            }
            spawn(world, t, room, at, pool[r.nextInt(pool.length)]);
        }
    }

    private static EntityLiving spawn(World world, TrialData.Trial t, int room, BlockPos at, String id) {
        Entity e = EntityList.createEntityByIDFromName(new ResourceLocation(id), world);
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
        ru.death.chaosshard.servant.Resync.later(m, 5);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.5);
        return m;
    }

    private static List<EntityLivingBase> roomMobs(World world, TrialData.Trial t, int room) {
        BlockPos o = t.origin();
        int[] b = t.bounds()[room];
        int h = t.roomType(room).half;
        return world.getEntitiesWithinAABB(EntityLivingBase.class,
                new AxisAlignedBB(o.add(-h - 8, -TrialLayout.PIT - 2, b[0] - 6), o.add(h + 8, TrialLayout.HEIGHT + 4, b[1] + 6)),
                e -> e.isEntityAlive() && e.getEntityData().getInteger(TAG) == t.id && !e.getEntityData().getBoolean(TAG_HUNTER));
    }

    private static int alive(World world, TrialData.Trial t, int room) {
        return roomMobs(world, t, room).size();
    }

    // ------------------------------------------------------------------ Мир эха

    private static final List<Object[]> ECHOES = new ArrayList<>();

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        World world = e.world;
        if (world.isRemote || !TrialDimension.is(world) || e.getEntityData().getInteger(TAG) == 0
                || e.getEntityData().getBoolean(TAG_RISEN) || e.getEntityData().getBoolean(TAG_GUARD)) {
            return;
        }
        TrialData.Trial t = TrialData.get(world).trials.get(e.getEntityData().getInteger(TAG));
        if (t != null && TrialCondition.has(t.conditions, TrialCondition.ECHO)) {
            ResourceLocation key = EntityList.getKey(e);
            if (key != null) {
                ECHOES.add(new Object[]{world, t, e.getEntityData().getInteger(TAG_ROOM), e.getPosition(), key.toString(), world.getTotalWorldTime() + 60});
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_MOB, e.posX, e.posY + 0.5, e.posZ, 30, 0.3, 0.6, 0.3, 0.0);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ECHOES.isEmpty()) {
            return;
        }
        for (Iterator<Object[]> it = ECHOES.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            World world = (World) o[0];
            BlockPos at = (BlockPos) o[3];
            long due = (Long) o[5];
            if (world.getTotalWorldTime() % 5 == 0) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_MOB_AMBIENT, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
            }
            if (world.getTotalWorldTime() < due) {
                continue;
            }
            it.remove();
            boolean dispelled = !world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(at).grow(1.0, 1.0, 1.0)).isEmpty();
            if (dispelled) {
                world.playSound(null, at, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 0.6F, 1.6F);
                continue;
            }
            EntityLiving shade = spawn(world, (TrialData.Trial) o[1], (Integer) o[2], at, (String) o[4]);
            if (shade != null) {
                shade.getEntityData().setBoolean(TAG_RISEN, true);
                shade.setCustomNameTag(I18n.translateToLocal("chaosshard.trial.echo"));
                shade.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(Math.max(4, shade.getMaxHealth() / 2));
                shade.setHealth(shade.getMaxHealth());
                shade.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 1000000, 0, true, false));
                shade.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1000000, 0, true, false));
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
