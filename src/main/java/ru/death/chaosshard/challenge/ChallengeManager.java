package ru.death.chaosshard.challenge;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;
import ru.death.chaosshard.trial.TrialDimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Вызов — волны врагов с наградами. Два места боя:
 * арена в измерении-архиве (вещи при смерти не теряются: смерть на арене — проигрыш, а не смерть),
 * или у своего Якоря — враги идут на Якорь, как в набеге.
 */
public class ChallengeManager {

    public static final String TAG = "chaosshard_challenge";
    public static final String RETURN = "chaosshard_challenge_return";
    public static final int ARENA_R = 16, ANCHOR_REACH = 24, PAUSE = 300, PAUSE_FAST = 60;
    /** Арены — в отрицательных X архива, где обломков-испытаний нет. */
    public static final int ARENA_X = -100000, ARENA_Y = 80;

    private static final UUID FURY_ID = UUID.fromString("6f2b5a90-3c1e-4c8a-9a51-0c4a1e7a1f01");
    private static final UUID SCALE_HP = UUID.fromString("6f2b5a90-3c1e-4c8a-9a51-0c4a1e7a1f02");
    private static final UUID SCALE_DMG = UUID.fromString("6f2b5a90-3c1e-4c8a-9a51-0c4a1e7a1f03");

    static final Map<UUID, Challenge> ACTIVE = new HashMap<>();

    public static final class Challenge {
        public final UUID owner;
        public final ChallengeSpec spec;
        public final boolean arena;
        public final int dim;
        public final BlockPos center;
        public int wave;            // сколько волн уже отбито
        public boolean fighting;
        public int timer;
        public final Set<UUID> mobs = new HashSet<>();
        public UUID hunter;
        /** Бесконечный: после этой волны — закончить с наградой. */
        public boolean lastWave;
        int absent;
        long hunterReady;

        Challenge(UUID owner, ChallengeSpec spec, boolean arena, int dim, BlockPos center) {
            this.owner = owner;
            this.spec = spec;
            this.arena = arena;
            this.dim = dim;
            this.center = center;
        }

        public int effTier() {
            return spec.endless() ? 1 + wave / 3 : spec.tier;
        }

        /** Множитель награды: модификаторы и +25% за бой у Якоря (там риск выше — смерть настоящая, Якорь может пасть). */
        public double mul() {
            return spec.rewardMul() + (arena ? 0 : 0.25);
        }

        boolean bossWave() {
            return spec.endless() ? (wave + 1) % 5 == 0 : wave == spec.waves() - 1;
        }
    }

    public static Challenge of(EntityPlayer p) {
        return p == null ? null : ACTIVE.get(p.getUniqueID());
    }

    public static boolean inArena(EntityPlayer p) {
        Challenge c = of(p);
        return c != null && c.arena;
    }

    // ------------------------------------------------------------------ начало

    /** Свой Якорь в пределах ANCHOR_REACH от точки или null. */
    public static TileAnchor ownAnchorNear(EntityPlayerMP p, BlockPos at) {
        TileAnchor best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos a : AnchorData.get(p.world).anchors) {
            double d = a.distanceSq(at);
            if (d > ANCHOR_REACH * ANCHOR_REACH || d >= bd) {
                continue;
            }
            net.minecraft.tileentity.TileEntity te = p.world.getTileEntity(a);
            if (te instanceof TileAnchor && p.getUniqueID().equals(((TileAnchor) te).getOwner())) {
                best = (TileAnchor) te;
                bd = d;
            }
        }
        return best;
    }

    /** Разбить Сферу Вызова. at — блок, по которому кликнули (null — в воздух). false — нельзя. */
    public static boolean start(EntityPlayerMP p, ChallengeSpec spec, BlockPos at) {
        if (ACTIVE.containsKey(p.getUniqueID())) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.challenge.busy"), true);
            return false;
        }
        if (TrialDimension.is(p.world)) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.challenge.not_here"), true);
            return false;
        }
        TileAnchor anchor = at == null ? null : ownAnchorNear(p, at);
        Challenge c;
        if (anchor != null) {
            c = new Challenge(p.getUniqueID(), spec, false, p.dimension, anchor.getPos());
            if (ChallengeMod.has(spec.mods, ChallengeMod.DARK)) {
                long t = p.world.getWorldTime();
                p.world.setWorldTime(t - t % 24000 + 14000);
            }
            p.sendMessage(Breach.grimoire("chaosshard.challenge.start.anchor", describe(spec), anchor.getPos().getX(), anchor.getPos().getZ()));
        } else {
            WorldServer archive = p.getServer().getWorld(TrialDimension.id());
            ArenaData data = ArenaData.get(archive);
            int id = data.next++;
            data.markDirty();
            BlockPos center = new BlockPos(ARENA_X - id * 256, ARENA_Y, 0);
            ChallengeArena.build(archive, center, spec.faction);
            c = new Challenge(p.getUniqueID(), spec, true, TrialDimension.id(), center);
            NBTTagCompound ret = new NBTTagCompound();
            ret.setInteger("Dim", p.dimension);
            ret.setLong("Pos", p.getPosition().toLong());
            p.getEntityData().setTag(RETURN, ret);
            p.sendMessage(Breach.grimoire("chaosshard.challenge.start.arena", describe(spec)));
            ACTIVE.put(p.getUniqueID(), c);
            p.changeDimension(TrialDimension.id(), new ChaosTeleporter(center));
        }
        ACTIVE.put(p.getUniqueID(), c);
        c.timer = PAUSE;
        Milestones.grant(p, Milestones.CHALLENGE_START);
        return true;
    }

    /** Завести Вызов без переноса (для автотеста: подставной игрок не умеет менять измерение). */
    public static Challenge begin(EntityPlayerMP p, ChallengeSpec spec, BlockPos center, boolean arena, int dim) {
        Challenge c = new Challenge(p.getUniqueID(), spec, arena, dim, center);
        c.timer = PAUSE;
        ACTIVE.put(p.getUniqueID(), c);
        return c;
    }

    /** Снять Вызов без наград (автотест). */
    public static void abort(EntityPlayerMP p) {
        Challenge c = of(p);
        if (c != null) {
            end(c, null);
        }
    }

    public static ITextComponent describe(ChallengeSpec s) {
        ITextComponent t = new TextComponentTranslation(s.faction.key());
        t.appendText(", ");
        t.appendSibling(s.endless() ? new TextComponentTranslation("chaosshard.challenge.endless")
                : new TextComponentTranslation("chaosshard.challenge.tier", s.tier, s.waves()));
        for (ChallengeMod m : ChallengeMod.values()) {
            if (ChallengeMod.has(s.mods, m)) {
                t.appendText(", ");
                t.appendSibling(new TextComponentTranslation(m.key()));
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ ход

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        for (Challenge c : new ArrayList<>(ACTIVE.values())) {
            EntityPlayerMP p = playerOf(server, c.owner);
            tick(server, c, p);
        }
    }

    /** Подставной игрок автотеста или настоящий. */
    static EntityPlayerMP playerOf(MinecraftServer server, UUID id) {
        return ru.death.chaosshard.debug.TestHooks.player(server, id);
    }

    /** Один тик Вызова (вынесено, чтобы автотест мог крутить его вручную). */
    public static void tick(MinecraftServer server, Challenge c, EntityPlayerMP p) {
        WorldServer w = DimensionManager.getWorld(c.dim);
        if (w == null) {
            return;
        }
        if (p == null || p.isDead) {
            if (++c.absent > 1200) {
                fail(c, null, "chaosshard.challenge.fail.gone");
            }
            return;
        }
        if (p.dimension != c.dim) {
            if (c.arena && c.timer >= PAUSE - 40 && c.wave == 0 && !c.fighting) {
                return;   // ещё переносится на арену
            }
            if (++c.absent > (c.arena ? 40 : 600)) {
                fail(c, p, "chaosshard.challenge.fail.left");
            }
            return;
        }
        long now = w.getTotalWorldTime();
        if (c.arena) {
            if (p.getDistanceSq(c.center) > (ARENA_R + 3) * (ARENA_R + 3) || p.posY < c.center.getY() - 3) {
                p.connection.setPlayerLocation(c.center.getX() + 0.5, c.center.getY(), c.center.getZ() + 0.5, p.rotationYaw, p.rotationPitch);
                p.fallDistance = 0;
            }
            c.absent = 0;
        } else {
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(c.center);
            if (!(te instanceof TileAnchor)) {
                fail(c, p, "chaosshard.challenge.fail.anchor");
                return;
            }
            if (p.getDistanceSq(c.center) > 64 * 64) {
                if (++c.absent > 600) {
                    fail(c, p, "chaosshard.challenge.fail.left");
                    return;
                }
            } else {
                c.absent = 0;
            }
        }
        if (!c.fighting) {
            if (c.timer == PAUSE - 1 || c.timer == 200 || c.timer == 100 || c.timer == 60 && pause(c) > 60) {
                p.sendStatusMessage(Breach.grimoire("chaosshard.challenge.next", c.wave + 1, c.timer / 20), true);
            }
            if (--c.timer <= 0) {
                spawnWave(w, c, p);
                c.fighting = true;
            }
            hunter(w, c, p, now);
            return;
        }
        hunter(w, c, p, now);
        if (now % 10 != 0) {
            return;
        }
        // живые бойцы волны
        Iterator<UUID> it = c.mobs.iterator();
        TileAnchor anchor = c.arena ? null : (TileAnchor) w.getTileEntity(c.center);
        boolean atAnchor = false;
        while (it.hasNext()) {
            Entity e = w.getEntityFromUuid(it.next());
            if (!(e instanceof EntityLiving) || !e.isEntityAlive()) {
                it.remove();
                continue;
            }
            EntityLiving m = (EntityLiving) e;
            double r2 = m.getDistanceSq(c.center);
            if (c.arena && (r2 > (ARENA_R + 4) * (ARENA_R + 4) || m.posY < c.center.getY() - 4)) {
                BlockPos s = gate(c, w.rand);
                m.setPositionAndUpdate(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
            }
            if (!c.arena && r2 > 56 * 56) {
                BlockPos s = gate(c, w.rand);
                m.setPositionAndUpdate(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
            }
            if (now % 40 == 0 && m.getAttackTarget() == null) {
                if (c.arena || m.getDistanceSq(p) < 14 * 14) {
                    m.setAttackTarget(p);
                } else {
                    m.getNavigator().tryMoveToXYZ(c.center.getX() + 0.5, c.center.getY(), c.center.getZ() + 0.5, 1.0);
                }
            }
            if (anchor != null && r2 < 25) {
                atAnchor = true;
            }
        }
        if (atAnchor) {
            int before = anchor.getCorrupted();
            anchor.siegeTick(p);
            if (anchor.getCorrupted() < before) {
                fail(c, p, "chaosshard.challenge.fail.anchor");
                return;
            }
        }
        if (c.mobs.isEmpty()) {
            waveCleared(w, c, p);
        }
    }

    static int pause(Challenge c) {
        return ChallengeMod.has(c.spec.mods, ChallengeMod.RELENTLESS) ? PAUSE_FAST : PAUSE;
    }

    /** Точка появления: у арены — по краю, у Якоря — кольцо 20–28 блоков на поверхности. */
    static BlockPos gate(Challenge c, Random r) {
        double a = r.nextDouble() * Math.PI * 2;
        WorldServer w = DimensionManager.getWorld(c.dim);
        if (c.arena) {
            int rr = ARENA_R - 2;
            return new BlockPos(c.center.getX() + Math.cos(a) * rr, c.center.getY(), c.center.getZ() + Math.sin(a) * rr);
        }
        for (int i = 0; i < 10; i++) {
            int rr = 20 + r.nextInt(9);
            BlockPos top = w.getTopSolidOrLiquidBlock(new BlockPos(c.center.getX() + Math.cos(a) * rr, 0, c.center.getZ() + Math.sin(a) * rr));
            if (!w.getBlockState(top.down()).getMaterial().isLiquid() && Math.abs(top.getY() - c.center.getY()) < 12) {
                return top;
            }
            a = r.nextDouble() * Math.PI * 2;
        }
        return w.getTopSolidOrLiquidBlock(c.center.add(3, 0, 3));
    }

    static void spawnWave(WorldServer w, Challenge c, EntityPlayerMP p) {
        Random r = w.rand;
        int tier = c.effTier();
        int n = Math.min(24, 3 + tier + c.wave);
        if (ChallengeMod.has(c.spec.mods, ChallengeMod.HORDE)) {
            n = Math.min(32, n * 3 / 2);
        }
        List<EntityLiving> wave = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            wave.add(c.spec.faction.regular(w, tier, c.wave + 1, r));
        }
        boolean boss = c.bossWave();
        if (boss) {
            wave.add(c.spec.faction.boss(w, tier, r));
        }
        for (EntityLiving m : wave) {
            if (m == null) {
                continue;
            }
            BlockPos at = gate(c, r);
            m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
            if (!(m instanceof ru.death.chaosshard.structure.EntityOrderKnight)) {
                m.onInitialSpawn(w.getDifficultyForLocation(at), null);
            }
            scale(m, c, tier);
            m.enablePersistence();
            m.getEntityData().setUniqueId(TAG, c.owner);
            for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
                m.setDropChance(s, 0.0F);
            }
            if (!ChallengeMod.has(c.spec.mods, ChallengeMod.DARK)) {
                m.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1000000, 0, true, false));
            }
            w.spawnEntity(m);
            ru.death.chaosshard.servant.Resync.later(m, 5);
            if (c.arena) {
                m.setAttackTarget(p);
            }
            c.mobs.add(m.getUniqueID());
            w.spawnParticle(EnumParticleTypes.PORTAL, m.posX, m.posY + 1, m.posZ, 30, 0.3, 0.8, 0.3, 0.5);
        }
        w.playSound(null, c.center, SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO, SoundCategory.HOSTILE, 2.0F, 0.6F);
        p.sendMessage(Breach.grimoire(boss ? "chaosshard.challenge.wave.boss" : "chaosshard.challenge.wave", c.wave + 1, wave.size()));
        if (ChallengeMod.has(c.spec.mods, ChallengeMod.HUNTER) && c.hunter == null) {
            c.hunterReady = w.getTotalWorldTime();
        }
    }

    static void scale(EntityLiving m, Challenge c, int tier) {
        double hp = 0.12 * (tier - 1) + 0.05 * c.wave + (c.spec.endless() ? 0.04 * c.wave : 0);
        if (ChallengeMod.has(c.spec.mods, ChallengeMod.TEMPER)) {
            hp += 0.5;
            if (m.getItemStackFromSlot(EntityEquipmentSlot.CHEST).isEmpty()) {
                m.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            }
            IAttributeInstance arm = m.getEntityAttribute(SharedMonsterAttributes.ARMOR);
            if (arm != null) {
                arm.setBaseValue(arm.getBaseValue() + 4);
            }
        }
        IAttributeInstance mh = m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (mh != null && hp > 0) {
            mh.applyModifier(new AttributeModifier(SCALE_HP, "Вызов", hp, 1));
            m.setHealth(m.getMaxHealth());
        }
        IAttributeInstance dmg = m.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.applyModifier(new AttributeModifier(SCALE_DMG, "Вызов", 0.08 * (tier - 1) + 0.03 * c.wave, 1));
        }
        if (ChallengeMod.has(c.spec.mods, ChallengeMod.FURY)) {
            IAttributeInstance sp = m.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (sp != null) {
                sp.applyModifier(new AttributeModifier(FURY_ID, "Ярость", 0.3, 1));
            }
        }
    }

    // ------------------------------------------------------------------ Преследователь

    static void hunter(WorldServer w, Challenge c, EntityPlayerMP p, long now) {
        if (!ChallengeMod.has(c.spec.mods, ChallengeMod.HUNTER) || c.hunterReady == 0) {
            return;
        }
        Entity h = c.hunter == null ? null : w.getEntityFromUuid(c.hunter);
        if (!(h instanceof EntityLiving) || !h.isEntityAlive()) {
            EntityLiving m = ChallengeFaction.create(w, "minecraft:wither_skeleton", "minecraft:zombie");
            BlockPos at = gate(c, w.rand);
            m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            m.setNoAI(true);
            m.setEntityInvulnerable(true);
            m.setNoGravity(true);
            m.enablePersistence();
            m.setCustomNameTag(new TextComponentTranslation("chaosshard.challenge.hunter").getUnformattedText());
            m.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1000000, 0, true, false));
            m.getEntityData().setUniqueId(TAG, c.owner);
            m.getEntityData().setBoolean("chaosshard_challenge_hunter", true);
            w.spawnEntity(m);
            c.hunter = m.getUniqueID();
            p.sendMessage(Breach.grimoire("chaosshard.challenge.hunter_comes"));
            return;
        }
        double dx = p.posX - h.posX, dy = p.posY - h.posY, dz = p.posZ - h.posZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 1.1) {
            double sp = 0.06 + 0.01 * c.effTier();
            h.noClip = true;
            h.setPositionAndUpdate(h.posX + dx / d * sp, h.posY + dy / d * sp, h.posZ + dz / d * sp);
            h.rotationYaw = ((EntityLiving) h).rotationYawHead = ((EntityLiving) h).renderYawOffset = (float) (Math.atan2(-dx, dz) * 180 / Math.PI);
        } else if (now % 20 == 0) {
            p.attackEntityFrom(DamageSource.causeMobDamage((EntityLivingBase) h), 3 + 2 * c.effTier());
        }
    }

    // ------------------------------------------------------------------ награды

    static void waveCleared(WorldServer w, Challenge c, EntityPlayerMP p) {
        c.wave++;
        c.fighting = false;
        double mul = c.mul();
        int nit = (int) Math.round((4 + 2 * c.wave) * c.effTier() * mul);
        ThreadManager.addPoints(p, nit);
        int rolls = 1 + (c.wave >= 3 ? 1 : 0) + (w.rand.nextDouble() < mul - 1 ? 1 : 0);
        give(w, p, "challenge/wave_" + c.spec.faction.name().toLowerCase(), rolls);
        p.addExperience(8 * c.wave);
        w.playSound(null, p.getPosition(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
        p.sendMessage(Breach.grimoire("chaosshard.challenge.wave.done", c.wave, nit));
        if (c.spec.endless() && !c.lastWave) {
            p.sendMessage(Breach.grimoire("chaosshard.challenge.cashout_hint"));
            if (c.wave >= 10) {
                Milestones.grant(p, Milestones.CHALLENGE_ENDLESS);
            }
        }
        if (!c.spec.endless() && c.wave >= c.spec.waves() || c.spec.endless() && c.lastWave) {
            victory(w, c, p);
            return;
        }
        c.timer = pause(c);
    }

    static void victory(WorldServer w, Challenge c, EntityPlayerMP p) {
        double mul = c.mul();
        int tier = c.spec.endless() ? Math.max(1, c.wave / 3) : c.spec.tier;
        int nit = (int) Math.round(40 * tier * mul);
        ThreadManager.addPoints(p, nit);
        int rolls = tier + (int) Math.floor((mul - 1) * tier);
        give(w, p, "challenge/final_" + c.spec.faction.name().toLowerCase(), Math.max(1, rolls));
        if (!c.arena) {
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(c.center);
            if (te instanceof TileAnchor) {
                ((TileAnchor) te).repelled(tier);
            }
        }
        p.sendMessage(Breach.grimoire("chaosshard.challenge.victory", c.wave, nit));
        Milestones.grant(p, Milestones.CHALLENGE);
        if (c.spec.tier >= 5) {
            Milestones.grant(p, Milestones.CHALLENGE_TOP);
        }
        w.playSound(null, p.getPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        end(c, p);
    }

    static void give(WorldServer w, EntityPlayerMP p, String table, int rolls) {
        LootTable lt = w.getLootTableManager().getLootTableFromLocation(new ResourceLocation(ru.death.chaosshard.Tags.MOD_ID, table));
        LootContext ctx = new LootContext.Builder(w).withPlayer(p).withLuck(p.getLuck()).build();
        for (int i = 0; i < rolls; i++) {
            for (ItemStack s : lt.generateLootForPools(w.rand, ctx)) {
                ItemHandlerHelper.giveItemToPlayer(p, s);
            }
        }
    }

    /** Бесконечный: уйти с накопленным после отбитой волны. */
    public static void cashOut(EntityPlayerMP p) {
        Challenge c = of(p);
        if (c == null || c.fighting) {
            return;
        }
        WorldServer w = DimensionManager.getWorld(c.dim);
        if (c.spec.endless() && c.wave > 0) {
            victory(w, c, p);
        } else {
            fail(c, p, "chaosshard.challenge.fail.abandon");
        }
    }

    static void fail(Challenge c, EntityPlayerMP p, String why) {
        if (p != null) {
            p.sendMessage(Breach.grimoire(why, c.wave));
        }
        end(c, p);
    }

    /** Убрать бойцов, вернуть с арены. */
    static void end(Challenge c, EntityPlayerMP p) {
        ACTIVE.remove(c.owner);
        WorldServer w = DimensionManager.getWorld(c.dim);
        if (w != null) {
            for (UUID id : c.mobs) {
                Entity e = w.getEntityFromUuid(id);
                if (e != null) {
                    w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, e.posX, e.posY + 1, e.posZ, 10, 0.3, 0.5, 0.3, 0.02);
                    e.setDead();
                }
            }
            if (c.hunter != null) {
                Entity h = w.getEntityFromUuid(c.hunter);
                if (h != null) {
                    h.setDead();
                }
            }
        }
        c.mobs.clear();
        if (c.arena && p != null) {
            sendHome(p);
        }
    }

    static void sendHome(EntityPlayerMP p) {
        NBTTagCompound ret = p.getEntityData().getCompoundTag(RETURN);
        p.getEntityData().removeTag(RETURN);
        int dim = ret.hasKey("Dim") ? ret.getInteger("Dim") : 0;
        BlockPos pos = ret.hasKey("Pos") ? BlockPos.fromLong(ret.getLong("Pos")) : BlockPos.ORIGIN;
        if (TrialDimension.is(p.world)) {
            ChaosTeleporter.toPos(p, dim, pos);
        }
    }

    // ------------------------------------------------------------------ события

    /** Смерть на арене — не смерть: проигрыш, вещи при себе, домой с половиной здоровья. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote) {
            return;
        }
        if (e instanceof EntityPlayerMP) {
            Challenge c = of((EntityPlayer) e);
            if (c == null) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) e;
            if (c.arena) {
                event.setCanceled(true);
                p.setHealth(p.getMaxHealth() / 2);
                p.extinguish();
                p.clearActivePotions();
                p.getFoodStats().setFoodLevel(Math.max(10, p.getFoodStats().getFoodLevel()));
            }
            fail(c, p, "chaosshard.challenge.fail.died");
            return;
        }
        if (e.getEntityData().hasUniqueId(TAG)) {
            Challenge c = ACTIVE.get(e.getEntityData().getUniqueId(TAG));
            if (c != null && ChallengeMod.has(c.spec.mods, ChallengeMod.BLAST) && !e.getEntityData().getBoolean("chaosshard_challenge_hunter")) {
                e.world.newExplosion(null, e.posX, e.posY + 0.5, e.posZ, 1.8F, false, false);
            }
        }
    }

    /** Цена крови: здоровье само не растёт. */
    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP && !event.getEntityLiving().world.isRemote) {
            Challenge c = of((EntityPlayer) event.getEntityLiving());
            if (c != null && ChallengeMod.has(c.spec.mods, ChallengeMod.BLOOD_PRICE)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Shift+ПКМ Гримуаром. Между волнами: в бесконечном — уйти с наградой, на ступени — сдаться.
     * Во время волны в бесконечном: «эта волна последняя» — она доигрывается, новых нет, итоговая награда как при уходе.
     */
    @SubscribeEvent
    public void onGrimoire(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote || !event.getEntityPlayer().isSneaking() || event.getItemStack().getItem() != ModItems.GRIMOIRE) {
            return;
        }
        Challenge c = of(event.getEntityPlayer());
        if (c == null) {
            return;
        }
        event.setCanceled(true);
        if (c.fighting) {
            if (!c.spec.endless()) {
                event.getEntityPlayer().sendStatusMessage(Breach.grimoire("chaosshard.challenge.not_now"), true);
                return;
            }
            // бесконечный: эта волна — последняя (повторно — передумать)
            c.lastWave = !c.lastWave;
            event.getEntityPlayer().sendMessage(Breach.grimoire(c.lastWave ? "chaosshard.challenge.last_wave" : "chaosshard.challenge.last_wave.off"));
            return;
        }
        cashOut((EntityPlayerMP) event.getEntityPlayer());
    }

    /** Вошёл, а Вызова уже нет (перезапуск) — с арены домой. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP && TrialDimension.is(event.player.world) && event.player.posX < ARENA_X / 2
                && !ACTIVE.containsKey(event.player.getUniqueID())) {
            sendHome((EntityPlayerMP) event.player);
        }
    }

    // ------------------------------------------------------------------ счётчик арен

    public static class ArenaData extends WorldSavedData {
        public static final String NAME = "chaosshard_arenas";
        public int next = 1;

        public ArenaData(String name) {
            super(name);
        }

        public ArenaData() {
            this(NAME);
        }

        public static ArenaData get(World w) {
            ArenaData d = (ArenaData) w.getPerWorldStorage().getOrLoadData(ArenaData.class, NAME);
            if (d == null) {
                d = new ArenaData();
                w.getPerWorldStorage().setData(NAME, d);
            }
            return d;
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            next = Math.max(1, tag.getInteger("Next"));
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            tag.setInteger("Next", next);
            return tag;
        }
    }

    @SuppressWarnings("unused")
    private static IBlockState air() {
        return Blocks.AIR.getDefaultState();
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        ACTIVE.clear();
    }
}
