package ru.death.chaosshard.anchor;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.garrot.GarrotRaids;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketHeartBolt;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellMods;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Специализации Якоря: что они делают в мире. */
public final class AnchorSpecs {

    public static final int FORGE_BUFFER = 200000;
    public static final int FIELD_RADIUS = 12;
    public static final String TAG_FIELD = "chaosshard_field";
    public static final String TAG_NEST = "chaosshard_nest";
    public static final String TAG_ESCORT = "chaosshard_nest_escort";
    public static final String TAG_SEEN = "chaosshard_seen";
    private static final UUID PATH_SPEED = UUID.fromString("6b1f3c2a-4d0e-4a7b-9c55-2f8e1a0d7c31");
    private static final GameProfile HARVEST_PROFILE = new GameProfile(UUID.fromString("8e0c6a10-5b4f-4c1a-a3d2-7f6b9e2c4d58"), "[Жатва]");

    // ------------------------------------------------------------------ тик Якоря

    public static void tick(TileAnchor a) {
        World w = a.getWorld();
        if (!(w instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) w;
        long wt = w.getTotalWorldTime();
        int ph = (int) (a.getPos().toLong() & 63);
        if (a.isCaptured() && wt % 60 == ph % 60 && a.netStage(AnchorSpec.BASTION) > 0) {
            strike(a, armyNear(a), (float) (4 * a.netStage(AnchorSpec.BASTION)), 0xFF5050);
        }
        if (a.isCaptured() && wt % 40 == ph % 40 && a.net(AnchorSpec.WATCH) > 0) {
            watch(a);
        }
        AnchorSpec s = a.getSpec();
        boolean boost = a.netAlpha() >= 10;   // Альфа 10-го уровня: ступени сильнее
        if (a.hasFx(AnchorFx.CITADEL_STRIKE) && wt % 60 == ph % 60) {
            strike(a, hostileNear(a, a.radius()), 8.0F, 0xB070FF);
        }
        if (a.hasFx(AnchorFx.PATH_REGEN) && wt % 80 == ph % 80) {
            pathRegen(a);
        }
        if (a.hasFx(AnchorFx.WATCH_REVEAL) && wt % 40 == (ph + 20) % 40) {
            for (EntityLiving e : ws.getEntitiesWithinAABB(EntityLiving.class, land(a), x -> x instanceof IMob && x.isInvisible())) {
                e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 60, 0, true, false));
            }
        }
        if (a.hasFx(AnchorFx.VEIN_LOOT) && wt % 20 == ph % 20) {
            a.specTimer += 20;
            if (a.specTimer >= 6000) {
                a.specTimer = 0;
                veinLoot(a);
            }
            if (s == AnchorSpec.VEIN && a.stage()) {
                a.veinTimer += 20;
                if (a.veinTimer >= (boost ? 48000 : 72000)) {
                    a.veinTimer = 0;
                    vein(a);
                }
            }
        }
        if (wt % 200 == ph % 200) {
            if (a.hasFx(AnchorFx.NEST_GUARDS) || a.hasFx(AnchorFx.NEST_ESCORT)) {
                nest(a);
            } else if (!nestOf(a, false).isEmpty()) {
                dismissNest(a);
            }
        }
        if (s == AnchorSpec.FORGE) {
            forge(a, wt, boost);
        }
        if (a.hasFx(AnchorFx.FORGE_SPEED)) {
            machines(a, wt, s == AnchorSpec.FORGE && a.stage());
        }
        if (s == AnchorSpec.HARVEST && wt % 20 == ph % 20) {
            field(a, wt % 40 == ph % 40);
        }
    }

    /** Земля Якоря как коробка. */
    public static AxisAlignedBB land(TileAnchor a) {
        int r = a.radius();
        BlockPos p = a.getPos();
        return new AxisAlignedBB(p.getX() - r, p.getY() - 32, p.getZ() - r, p.getX() + r + 1, p.getY() + 48, p.getZ() + r + 1);
    }

    public static boolean inLand(TileAnchor a, Entity e) {
        double dx = e.posX - a.getPos().getX() - 0.5, dz = e.posZ - a.getPos().getZ() - 0.5;
        return dx * dx + dz * dz <= (double) a.radius() * a.radius();
    }

    static boolean enemy(Entity e, UUID owner) {
        if (!(e instanceof EntityLivingBase) || !e.isEntityAlive() || Servants.isServant(e)) {
            return false;
        }
        return e instanceof IMob || e.getTags().contains(GarrotRaids.TAG_ARMY);
    }

    private static EntityLivingBase hostileNear(TileAnchor a, int r) {
        EntityLivingBase best = null;
        double bd = Double.MAX_VALUE;
        for (EntityLivingBase e : a.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, land(a),
                x -> enemy(x, a.getOwner()) && inLand(a, x))) {
            double d = e.getDistanceSq(a.getPos());
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static EntityLivingBase armyNear(TileAnchor a) {
        EntityLivingBase best = null;
        double bd = Double.MAX_VALUE;
        for (EntityLivingBase e : a.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, land(a),
                x -> x.isEntityAlive() && x.getTags().contains(GarrotRaids.TAG_ARMY) && inLand(a, x))) {
            double d = e.getDistanceSq(a.getPos());
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Молния с вершины колонны. */
    static void strike(TileAnchor a, EntityLivingBase target, float dmg, int color) {
        if (target == null || dmg <= 0) {
            return;
        }
        BlockPos tip = a.getPos().up(Math.max(0, a.level() - 1));
        ChaosNetwork.CHANNEL.sendToAllAround(new PacketHeartBolt(tip.getX() + 0.5, tip.getY() + 1.0, tip.getZ() + 0.5,
                        target.posX, target.posY + target.height * 0.6, target.posZ, color),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(a.getWorld().provider.getDimension(),
                        target.posX, target.posY, target.posZ, 96));
        target.attackEntityFrom(DamageSource.MAGIC, dmg);
    }

    private static void pathRegen(TileAnchor a) {
        UUID o = a.getOwner();
        if (o == null) {
            return;
        }
        for (EntityLivingBase e : a.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, land(a),
                x -> inLand(a, x) && (o.equals(x.getUniqueID()) || Servants.isServantOf(x, o)))) {
            e.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 0, true, false));
        }
    }

    /** Дозор: враги, вошедшие на землю сети, подсвечиваются на 10 секунд (один раз). */
    private static void watch(TileAnchor a) {
        UUID o = a.getOwner();
        for (EntityLivingBase e : a.getWorld().getEntitiesWithinAABB(EntityLivingBase.class, land(a),
                x -> inLand(a, x) && (enemy(x, o) || (x instanceof EntityPlayer && o != null && !o.equals(x.getUniqueID())
                        && !(x instanceof FakePlayer))))) {
            if (!e.getEntityData().getBoolean(TAG_SEEN)) {
                e.getEntityData().setBoolean(TAG_SEEN, true);
                e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 200, 0, true, false));
            }
        }
    }

    // ------------------------------------------------------------------ Жила

    private static final Object[][] ORES = {
            {"minecraft:coal_ore", 40}, {"minecraft:iron_ore", 25}, {"minecraft:redstone_ore", 12}, {"minecraft:gold_ore", 10},
            {"minecraft:lapis_ore", 8}, {"minecraft:diamond_ore", 3}, {"minecraft:emerald_ore", 2}};

    private static ItemStack ore(Random r) {
        int total = 0;
        for (Object[] o : ORES) {
            total += (Integer) o[1];
        }
        int roll = r.nextInt(total);
        for (Object[] o : ORES) {
            roll -= (Integer) o[1];
            if (roll < 0) {
                Item it = Item.getByNameOrId((String) o[0]);
                return it == null ? ItemStack.EMPTY : new ItemStack(it);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Раз в 5 минут — добыча с осквернённой земли в хранилище. */
    public static int veinLoot(TileAnchor a) {
        Random r = a.getWorld().rand;
        int rolls = (2 + a.level()) * (a.getSpec() == AnchorSpec.VEIN ? 2 : 1);
        int put = 0;
        Item dark = Item.getByNameOrId("evilcraft:dark_gem");
        for (int i = 0; i < rolls; i++) {
            ItemStack st;
            double x = r.nextDouble();
            if (x < 0.12) {
                st = ore(r);
            } else if (x < 0.27 && dark != null) {
                st = new ItemStack(dark);
            } else if (x < 0.6) {
                st = new ItemStack(ru.death.chaosshard.init.ModBlocks.CHAOS_STONE, 2);
            } else {
                st = new ItemStack(ru.death.chaosshard.init.ModBlocks.CHAOS_CRUST, 2);
            }
            if (!st.isEmpty() && insert(a, st, AnchorSpec.VEIN.storage).isEmpty()) {
                put++;
            }
        }
        return put;
    }

    /** Положить в первые size мест хранилища; вернуть остаток. */
    static ItemStack insert(TileAnchor a, ItemStack st, int size) {
        ItemStack rest = st;
        for (int i = 0; i < size && !rest.isEmpty(); i++) {
            rest = a.storage.insertItem(i, rest, false);
        }
        return rest;
    }

    /** Раз в час — рудная жила в камне под землёй Якоря. */
    public static BlockPos vein(TileAnchor a) {
        World w = a.getWorld();
        Random r = w.rand;
        int rad = Math.max(8, a.radius());
        for (int tries = 0; tries < 80; tries++) {
            int x = a.getPos().getX() + r.nextInt(rad * 2 + 1) - rad;
            int z = a.getPos().getZ() + r.nextInt(rad * 2 + 1) - rad;
            if (!w.isBlockLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos top = w.getHeight(new BlockPos(x, 0, z));
            if (top.getY() < 16) {
                continue;
            }
            BlockPos at = new BlockPos(x, 6 + r.nextInt(Math.max(1, top.getY() - 16)), z);
            if (!stone(w.getBlockState(at))) {
                continue;
            }
            ItemStack oreItem = ore(r);
            if (!(oreItem.getItem() instanceof net.minecraft.item.ItemBlock)) {
                continue;
            }
            IBlockState ore = ((net.minecraft.item.ItemBlock) oreItem.getItem()).getBlock().getDefaultState();
            int n = 6 + r.nextInt(7), placed = 0;
            BlockPos p = at;
            for (int i = 0; i < n * 3 && placed < n; i++) {
                if (stone(w.getBlockState(p))) {
                    w.setBlockState(p, ore, 2);
                    placed++;
                }
                p = p.offset(EnumFacing.random(r));
            }
            EntityPlayerMP o = owner(a);
            if (o != null) {
                o.sendMessage(Breach.grimoire("chaosshard.anchor.spec.vein.found", at.getX(), at.getY(), at.getZ(),
                        new net.minecraft.util.text.TextComponentTranslation(oreItem.getTranslationKey() + ".name")));
            }
            return at;
        }
        return null;
    }

    private static boolean stone(IBlockState s) {
        return s.getBlock() == Blocks.STONE || s.getBlock() == ru.death.chaosshard.init.ModBlocks.CHAOS_STONE
                || Corruption.isCorrupted(s) && s.getMaterial() == net.minecraft.block.material.Material.ROCK;
    }

    static EntityPlayerMP owner(TileAnchor a) {
        if (a.getOwner() == null || a.getWorld().getMinecraftServer() == null) {
            return null;
        }
        return ru.death.chaosshard.debug.TestHooks.player(a.getWorld().getMinecraftServer(), a.getOwner());
    }

    // ------------------------------------------------------------------ Гнездо

    public static List<EntityLiving> nestOf(TileAnchor a, boolean escorts) {
        long key = a.getPos().toLong();
        AxisAlignedBB box = escorts ? land(a).grow(256, 64, 256) : land(a).grow(16);
        return a.getWorld().getEntitiesWithinAABB(EntityLiving.class, box, x -> x.isEntityAlive()
                && x.getEntityData().hasKey(TAG_NEST) && x.getEntityData().getLong(TAG_NEST) == key
                && x.getEntityData().getBoolean(TAG_ESCORT) == escorts);
    }

    /** Гнездо: держать число тварей-защитников и двух сопровождающих. */
    public static void nest(TileAnchor a) {
        EntityPlayerMP o = owner(a);
        if (o == null || o.world != a.getWorld()) {
            return;
        }
        boolean stage = a.getSpec() == AnchorSpec.NEST && a.stage();
        int want = !a.hasFx(AnchorFx.NEST_GUARDS) ? 0 : 2 + a.level() + (stage ? (a.netAlpha() >= 10 ? 4 : 2) : 0);
        List<EntityLiving> guards = nestOf(a, false);
        if (guards.size() < want) {
            BlockPos at = a.getWorld().getHeight(a.getPos().add(a.getWorld().rand.nextInt(9) - 4, 0, a.getWorld().rand.nextInt(9) - 4));
            EntityLiving m = Servants.spawn(o, Servants.Kind.HUSK, at);
            if (m != null) {
                boolean leader = stage && guards.stream().noneMatch(x -> x.getEntityData().getBoolean("chaosshard_nest_leader"));
                m.getEntityData().setLong(TAG_NEST, a.getPos().toLong());
                m.getEntityData().setLong(Servants.TAG_ANCHOR, a.getPos().toLong());
                if (m instanceof net.minecraft.entity.EntityCreature) {
                    ((net.minecraft.entity.EntityCreature) m).setHomePosAndDistance(a.getPos(), Math.max(16, a.radius()));
                }
                double hp = m.getMaxHealth() * (stage ? 1.5 : 1.0) * (leader ? 2.0 : 1.0);
                m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
                if (leader) {
                    m.getEntityData().setBoolean("chaosshard_nest_leader", true);
                    m.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(
                            m.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue() * 2);
                }
                m.setHealth(m.getMaxHealth());
                m.setCustomNameTag((leader ? "§4Вожак Гнезда" : "§5Тварь Гнезда") + " §7(" + o.getName() + ")");
                ((WorldServer) a.getWorld()).spawnParticle(EnumParticleTypes.DRAGON_BREATH, m.posX, m.posY + 1, m.posZ, 20, 0.3, 0.6, 0.3, 0.02);
            }
        }
        // сопровождающие: появляются, пока хозяин на этой земле
        if (a.hasFx(AnchorFx.NEST_ESCORT) && inLand(a, o) && nestOf(a, true).size() < 2) {
            EntityLiving m = Servants.spawn(o, Servants.Kind.HUSK, a.getWorld().getHeight(o.getPosition().add(1, 0, 1)));
            if (m != null) {
                m.getEntityData().setLong(TAG_NEST, a.getPos().toLong());
                m.getEntityData().setBoolean(TAG_ESCORT, true);
                m.setCustomNameTag("§5Тварь Гнезда §7(" + o.getName() + ")");
            }
        }
    }

    /** Гнездо больше не Гнездо — твари уходят. */
    public static void dismissNest(TileAnchor a) {
        long key = a.getPos().toLong();
        for (WorldServer w : a.getWorld().getMinecraftServer().worlds) {
            for (Entity e : new java.util.ArrayList<>(w.loadedEntityList)) {
                if (e.getEntityData().hasKey(TAG_NEST) && e.getEntityData().getLong(TAG_NEST) == key && w == a.getWorld()) {
                    ((WorldServer) w).spawnParticle(EnumParticleTypes.SMOKE_LARGE, e.posX, e.posY + 1, e.posZ, 10, 0.3, 0.5, 0.3, 0.02);
                    e.setDead();
                }
            }
        }
    }

    // ------------------------------------------------------------------ Горн

    /** Выработка Горна, RF за тик. */
    public static int forgeRate(TileAnchor a) {
        int r = 20 * a.level() + a.getLand() / 50;
        return a.stage() ? r * 2 : r;
    }

    private static void forge(TileAnchor a, long wt, boolean boost) {
        int rate = forgeRate(a);
        if (boost && a.stage()) {
            rate = rate * 3 / 2;
        }
        a.forgeEnergy = Math.min(FORGE_BUFFER, a.forgeEnergy + rate);
        World w = a.getWorld();
        for (EnumFacing f : EnumFacing.values()) {
            if (a.forgeEnergy <= 0) {
                break;
            }
            TileEntity te = w.getTileEntity(a.getPos().offset(f));
            if (te == null || te instanceof TileAnchor) {
                continue;
            }
            IEnergyStorage es = te.getCapability(CapabilityEnergy.ENERGY, f.getOpposite());
            if (es != null && es.canReceive()) {
                a.forgeEnergy -= es.receiveEnergy(Math.min(a.forgeEnergy, 20000), false);
            }
        }
    }

    /** Машины у Якоря работают быстрее: в полтора раза, на 2-й ступени Горна — вдвое. */
    private static void machines(TileAnchor a, long wt, boolean stage) {
        if (wt % (stage ? 1 : 2) != 0) {
            return;
        }
        World w = a.getWorld();
        for (long key : a.hum.positions()) {
            BlockPos p = BlockPos.fromLong(key);
            if (!w.isBlockLoaded(p)) {
                continue;
            }
            TileEntity te = w.getTileEntity(p);
            if (te instanceof ITickable && !te.isInvalid() && ru.death.chaosshard.spell.SpellAccel.accelerable(w, p)) {
                ((ITickable) te).update();
            }
        }
    }

    // ------------------------------------------------------------------ Жатва

    public static List<EntityLiving> fieldMobs(TileAnchor a) {
        long key = a.getPos().toLong();
        return a.getWorld().getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(a.getPos()).grow(48),
                x -> x.isEntityAlive() && x.getEntityData().getLong(TAG_FIELD) == key);
    }

    private static FakePlayer reaper(WorldServer ws) {
        return FakePlayerFactory.get(ws, HARVEST_PROFILE);
    }

    private static void field(TileAnchor a, boolean pulse) {
        WorldServer w = (WorldServer) a.getWorld();
        BlockPos c = a.getPos();
        List<EntityLiving> mobs = fieldMobs(a);
        // держать на поле
        for (EntityLiving m : mobs) {
            double dx = m.posX - c.getX() - 0.5, dz = m.posZ - c.getZ() - 0.5;
            if (dx * dx + dz * dz > (FIELD_RADIUS + 1) * (FIELD_RADIUS + 1)) {
                double ang = Math.atan2(dz, dx);
                BlockPos back = w.getHeight(new BlockPos(c.getX() + Math.cos(ang) * (FIELD_RADIUS - 2), 0, c.getZ() + Math.sin(ang) * (FIELD_RADIUS - 2)));
                m.setPositionAndUpdate(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
            }
        }
        if (pulse) {
            FakePlayer fp = reaper(w);
            for (EntityLiving m : mobs) {
                m.attackEntityFrom(DamageSource.causePlayerDamage(fp), 3.0F);
                m.setRevengeTarget(null);
                ChaosNetwork.CHANNEL.sendToAllAround(new PacketHeartBolt(c.getX() + 0.5, c.getY() + a.level(), c.getZ() + 0.5,
                                m.posX, m.posY + m.height * 0.5, m.posZ, 0x60FF90),
                        new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(w.provider.getDimension(), m.posX, m.posY, m.posZ, 64));
            }
        }
        int cap = 6 + a.level();
        if (mobs.size() < cap) {
            spawnField(a, w);
        }
    }

    /** Поставить моба на поле Жатвы (без оглядки на свет). */
    public static EntityLiving spawnField(TileAnchor a, WorldServer w) {
        Random r = w.rand;
        BlockPos c = a.getPos();
        for (int tries = 0; tries < 6; tries++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 3 + r.nextDouble() * (FIELD_RADIUS - 3);
            BlockPos at = w.getHeight(new BlockPos(c.getX() + Math.cos(ang) * dist, 0, c.getZ() + Math.sin(ang) * dist));
            if (Math.abs(at.getY() - c.getY()) > 8 || w.getBlockState(at.down()).getMaterial().isLiquid()) {
                continue;
            }
            EntityLiving m = fieldMob(a, w, at);
            if (m == null) {
                return null;
            }
            m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
            if (!w.checkNoEntityCollision(m.getEntityBoundingBox()) || !w.getCollisionBoxes(m, m.getEntityBoundingBox()).isEmpty()) {
                continue;
            }
            m.onInitialSpawn(w.getDifficultyForLocation(at), null);
            m.enablePersistence();
            m.getEntityData().setLong(TAG_FIELD, c.toLong());
            if (!w.spawnEntity(m)) {
                return null;
            }
            w.spawnParticle(EnumParticleTypes.SPELL_WITCH, m.posX, m.posY + 1, m.posZ, 10, 0.3, 0.5, 0.3, 0.02);
            return m;
        }
        return null;
    }

    private static EntityLiving fieldMob(TileAnchor a, WorldServer w, BlockPos at) {
        ItemStack egg = a.sample.getStackInSlot(0);
        if (a.stage() && egg.getItem() instanceof ItemMonsterPlacer) {
            ResourceLocation id = ItemMonsterPlacer.getNamedIdFrom(egg);
            Entity e = id == null ? null : EntityList.createEntityByIDFromName(id, w);
            if (e instanceof EntityLiving && e.isNonBoss()) {
                return (EntityLiving) e;
            }
            return null;
        }
        Biome b = w.getBiome(at);
        List<Biome.SpawnListEntry> list = b.getSpawnableList(net.minecraft.entity.EnumCreatureType.MONSTER);
        if (list.isEmpty()) {
            return null;
        }
        Biome.SpawnListEntry en = WeightedRandom.getRandomItem(w.rand, list);
        try {
            EntityLiving m = en.newInstance(w);
            return m.isNonBoss() ? m : null;
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ события

    /** Якорь, на чьей земле точка, с владельцем (или null). */
    static TileAnchor at(World w, BlockPos p) {
        return AnchorNet.isNetWorld(w) ? TileAnchor.covering(w, p) : null;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || !AnchorNet.isNetWorld(victim.world)) {
            return;
        }
        Entity src = event.getSource().getTrueSource();
        if (src == null) {
            return;
        }
        TileAnchor a = at(victim.world, victim.getPosition());
        if (a == null || a.getOwner() == null) {
            return;
        }
        UUID o = a.getOwner();
        boolean ours = o.equals(victim.getUniqueID()) || Servants.isServantOf(victim, o);
        if (ours && enemy(src, o) && (a.hasFx(AnchorFx.BASTION_GUARD) || a.hasFx(AnchorFx.CITADEL_FORCE))) {
            event.setAmount(event.getAmount() * 0.85F);
        }
        if (!ours && a.hasFx(AnchorFx.CITADEL_FORCE) && Servants.isServantOf(src, o)) {
            event.setAmount(event.getAmount() * 1.15F);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !(e instanceof IMob) || Servants.isServant(e)) {
            return;
        }
        TileAnchor a = at(e.world, e.getPosition());
        if (a != null && a.isCaptured() && a.net(AnchorSpec.NEST) > 0) {
            a.addXp(Math.max(1, (int) Math.round(a.net(AnchorSpec.NEST))), false);
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote) {
            return;
        }
        if (e.getEntityData().hasKey(TAG_FIELD)) {
            TileAnchor a = AnchorClusters.at(e.world, BlockPos.fromLong(e.getEntityData().getLong(TAG_FIELD)));
            if (a != null && a.getSpec() == AnchorSpec.HARVEST) {
                event.getDrops().removeIf(it -> {
                    ItemStack rest = insert(a, it.getItem(), AnchorSpec.HARVEST.storage);
                    if (rest.isEmpty()) {
                        return true;
                    }
                    it.setItem(rest);
                    return false;
                });
            }
            return;
        }
        Entity killer = event.getSource().getTrueSource();
        if (!(killer instanceof EntityPlayer) || event.getDrops().isEmpty()) {
            return;
        }
        TileAnchor a = at(e.world, e.getPosition());
        if (a != null && killer.getUniqueID().equals(a.getOwner()) && a.net(AnchorSpec.HARVEST) > 0
                && e.world.rand.nextDouble() < 0.1 * a.net(AnchorSpec.HARVEST)) {
            EntityItem pick = event.getDrops().get(e.world.rand.nextInt(event.getDrops().size()));
            event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, pick.getItem().copy()));
        }
    }

    @SubscribeEvent
    public void onXp(LivingExperienceDropEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !e.getEntityData().hasKey(TAG_FIELD)) {
            return;
        }
        TileAnchor a = AnchorClusters.at(e.world, BlockPos.fromLong(e.getEntityData().getLong(TAG_FIELD)));
        if (a != null && a.getSpec() == AnchorSpec.HARVEST) {
            a.fieldXp += event.getDroppedExperience();
            a.markDirty();
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public void onSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getWorld().isRemote || !(event.getEntityLiving() instanceof IMob) || !AnchorNet.isNetWorld(event.getWorld())
                || event.isSpawner()) {
            return;
        }
        TileAnchor a = at(event.getWorld(), new BlockPos(event.getX(), event.getY(), event.getZ()));
        if (a != null && a.getSpec() == AnchorSpec.CITADEL && a.stage()) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer p = event.player;
        if (event.phase != TickEvent.Phase.END || p.world.isRemote || p.ticksExisted % 20 != 7) {
            return;
        }
        // Тропа: скорость на земле сети
        TileAnchor own = AnchorNet.ownLand(p);
        double f = own == null ? 0 : own.net(AnchorSpec.PATH);
        IAttributeInstance sp = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        AttributeModifier m = sp.getModifier(PATH_SPEED);
        double want = 0.15 * f;
        if (m != null && Math.abs(m.getAmount() - want) > 1e-6) {
            sp.removeModifier(PATH_SPEED);
            m = null;
        }
        if (m == null && want > 0) {
            sp.applyModifier(new AttributeModifier(PATH_SPEED, "chaosshard_path", want, 1).setSaved(false));
        }
        // сопровождающие Гнезда уходят, если Гнезда больше нет
        if (p.ticksExisted % 200 == 7) {
            AnchorData d = AnchorData.get(p.world);
            for (EntityLiving e : p.world.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(64),
                    x -> x.getEntityData().getBoolean(TAG_ESCORT) && Servants.isServantOf(x, p.getUniqueID()))) {
                AnchorData.Rec r = d.rec(BlockPos.fromLong(e.getEntityData().getLong(TAG_NEST)));
                boolean ok = r != null && (r.spec == AnchorSpec.NEST.ordinal()
                        || java.util.Arrays.stream(r.borrow).anyMatch(b -> b == AnchorFx.NEST_ESCORT.ordinal()));
                if (!ok) {
                    e.setDead();
                }
            }
        }
    }

    // ------------------------------------------------------------------ Пульс: заклинания

    public static final SpellMods.Hook PULSE = new SpellMods.Hook() {
        private TileAnchor land(EntityPlayer p) {
            return p.world.isRemote ? null : AnchorNet.ownLand(p);
        }

        @Override
        public double cost(EntityPlayer p, Spell s) {
            TileAnchor a = land(p);
            return a == null ? 1.0 : Math.max(0.5, 1.0 - 0.1 * a.net(AnchorSpec.PULSE));
        }

        @Override
        public double cooldown(EntityPlayer p, Spell s) {
            TileAnchor a = land(p);
            return a == null ? 1.0 : Math.max(0.5, 1.0 - 0.15 * a.netStage(AnchorSpec.PULSE));
        }

        @Override
        public int maxEnergy(EntityPlayer p) {
            TileAnchor a = land(p);
            return a == null ? 0 : (int) Math.round(20 * a.net(AnchorSpec.PULSE));
        }
    };

    // ------------------------------------------------------------------ Тропа: перенос

    /** Перенести игрока к Якорю сети. */
    public static boolean travel(EntityPlayerMP p, TileAnchor from, BlockPos to) {
        if (from.net(AnchorSpec.PATH) <= 0 || !p.getUniqueID().equals(from.getOwner())) {
            return false;
        }
        AnchorData.Rec r = AnchorData.get(p.world).rec(to);
        if (r == null || !p.getUniqueID().equals(r.owner) || to.equals(from.getPos())) {
            return false;
        }
        boolean free = from.netStage(AnchorSpec.PATH) > 0;
        int cost = 30;
        if (!free && !p.isCreative()) {
            if (ru.death.chaosshard.spell.ChaosEnergy.get(p) < cost) {
                p.sendStatusMessage(Breach.grimoire("chaosshard.anchor.spec.path.no_energy", cost), true);
                return false;
            }
            ru.death.chaosshard.spell.ChaosEnergy.spend(p, cost);
        }
        List<EntityLiving> company = free ? Servants.near(p, 8) : java.util.Collections.emptyList();
        BlockPos at = p.world.getHeight(to.add(2, 0, 0));
        ((WorldServer) p.world).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 40, 0.4, 0.8, 0.4, 0.5);
        p.closeScreen();
        p.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        for (EntityLiving e : company) {
            e.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
        }
        ((WorldServer) p.world).spawnParticle(EnumParticleTypes.SPELL_WITCH, p.posX, p.posY + 1, p.posZ, 40, 0.4, 0.8, 0.4, 0.05);
        return true;
    }

    /** Жатва: отдать игроку опыт, накопленный в колонне. */
    public static int takeXp(EntityPlayerMP p, TileAnchor a) {
        int xp = a.fieldXp;
        if (xp <= 0) {
            return 0;
        }
        a.fieldXp = 0;
        a.markDirty();
        p.addExperience(xp);
        return xp;
    }

    private AnchorSpecs() {
    }

    public static AnchorSpecs events() {
        return new AnchorSpecs();
    }
}
