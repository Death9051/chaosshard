package ru.death.chaosshard.ritual;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.BlockRitualSpire;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.breach.BreachRitual;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketHeartBolt;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.servant.Beasts;
import ru.death.chaosshard.structure.OrderStructureData;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Старшие ритуалы на Алтаре Прорыва: подношения бросают на Алтарь, четыре шпиля целы,
 * ПКМ Гримуаром. Шпили пять секунд бьют дугами в подношения — и ритуал свершается.
 * Если на Алтаре нет подношений старшего ритуала, ПКМ Гримуаром — это Вознесение зверя.
 */
public class AltarRituals {

    public static final int DURATION = 100;
    public static final int MOON_RADIUS = 64, SIGHT_RADIUS = 96, SURGE_REACH = 256;

    private static final Map<UUID, Running> RUNNING = new HashMap<>();
    /** Кровавые луны: измерение → (позиция Алтаря → до какого времени мира). */
    private static final Map<Integer, Map<Long, Long>> MOONS = new HashMap<>();

    private static final class Running {
        final BlockPos altar;
        final int dim;
        final Ritual ritual;
        final List<ItemStack> paid;
        int ticks;

        Running(BlockPos altar, int dim, Ritual ritual, List<ItemStack> paid) {
            this.altar = altar;
            this.dim = dim;
            this.ritual = ritual;
            this.paid = paid;
        }
    }

    public static boolean isRunning(EntityPlayer p) {
        return RUNNING.containsKey(p.getUniqueID());
    }

    /** Подношения, брошенные на Алтарь (над ним и вплотную). */
    static List<EntityItem> offered(World world, BlockPos altar) {
        return world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(altar).grow(1.5, 1.0, 1.5).expand(0, 1, 0),
                Entity::isEntityAlive);
    }

    /** ПКМ Гримуаром по Алтарю. */
    public static void tryStart(EntityPlayerMP p, BlockPos altar) {
        if (isRunning(p) || BreachRitual.isRunning(p)) {
            return;
        }
        List<EntityItem> items = offered(p.world, altar);
        List<ItemStack> stacks = new ArrayList<>();
        for (EntityItem ei : items) {
            stacks.add(ei.getItem());
        }
        Ritual r = stacks.isEmpty() ? null : Ritual.findAltar(stacks);
        if (r == null) {
            if (!stacks.isEmpty() && !hasBeastNear(p, altar)) {
                p.sendMessage(Breach.grimoire("chaosshard.altar.unknown"));
                return;
            }
            ru.death.chaosshard.servant.AltarAscension.tryStart(p, altar);
            return;
        }
        for (BlockPos s : BreachRitual.pillars(altar)) {
            if (!BlockRitualSpire.isWhole(p.world, s)) {
                p.sendMessage(Breach.grimoire("chaosshard.altar.no_spires"));
                return;
            }
        }
        if (ThreadBonus.level(p) < r.minThread && !p.isCreative()) {
            p.sendMessage(Breach.grimoire("chaosshard.ritual.thin", r.minThread));
            return;
        }
        List<ItemStack> paid = new ArrayList<>();
        for (EntityItem ei : items) {
            paid.add(ei.getItem().copy());
            ei.setDead();
        }
        RUNNING.put(p.getUniqueID(), new Running(altar, p.dimension, r, paid));
        p.sendMessage(Breach.grimoire("chaosshard.altar.ritual.start", new TextComponentTranslation(r.key())));
        p.world.playSound(null, altar, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.6F, 1.1F);
    }

    private static boolean hasBeastNear(EntityPlayerMP p, BlockPos altar) {
        return !p.world.getEntitiesWithinAABB(ru.death.chaosshard.servant.EntityChaosBeast.class, new AxisAlignedBB(altar).grow(8),
                x -> x.isEntityAlive() && ru.death.chaosshard.servant.Servants.isServantOf(x, p.getUniqueID())).isEmpty();
    }

    // ------------------------------------------------------------------ ход ритуала

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNNING.isEmpty() || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        tick((EntityPlayerMP) event.player);
    }

    /** Один тик идущего старшего ритуала игрока (вынесено, чтобы автотест мог крутить его за подставного игрока). */
    public static void tick(EntityPlayerMP p) {
        Running r = RUNNING.get(p.getUniqueID());
        if (r == null) {
            return;
        }
        WorldServer ws = (WorldServer) p.world;
        if (p.dimension != r.dim || p.isDead || p.getDistanceSq(r.altar) > 16 * 16
                || ws.getBlockState(r.altar).getBlock() != ModBlocks.BREACH_ALTAR) {
            RUNNING.remove(p.getUniqueID());
            if (p.dimension == r.dim) {
                for (ItemStack s : r.paid) {
                    net.minecraft.inventory.InventoryHelper.spawnItemStack(ws, r.altar.getX() + 0.5, r.altar.getY() + 1.2, r.altar.getZ() + 0.5, s.copy());
                }
            }
            p.sendMessage(Breach.grimoire("chaosshard.altar.ritual.broken"));
            return;
        }
        r.ticks++;
        double cx = r.altar.getX() + 0.5, cy = r.altar.getY() + 1.4 + r.ticks / (double) DURATION * 0.8, cz = r.altar.getZ() + 0.5;
        double angle = r.ticks * 0.35;
        ws.spawnParticle(EnumParticleTypes.PORTAL, cx + Math.cos(angle) * 1.1, cy, cz + Math.sin(angle) * 1.1, 6, 0.05, 0.05, 0.05, 0.3);
        ws.spawnParticle(r.ritual == Ritual.BLOOD_MOON ? EnumParticleTypes.REDSTONE : EnumParticleTypes.SPELL_WITCH,
                cx, cy, cz, 4, 0.25, 0.25, 0.25, 0.0);
        if (r.ticks % 10 == 0) {
            int color = r.ritual == Ritual.BLOOD_MOON ? 0xD02030 : r.ticks > DURATION * 2 / 3 ? 0xFFD27A : 0xB070FF;
            for (BlockPos s : BreachRitual.pillars(r.altar)) {
                ChaosNetwork.CHANNEL.sendToAllAround(new PacketHeartBolt(s.getX() + 0.5, s.getY() + 1.95, s.getZ() + 0.5, cx, cy, cz, color),
                        new NetworkRegistry.TargetPoint(p.dimension, cx, cy, cz, 64));
            }
            ws.playSound(null, r.altar, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.BLOCKS, 0.15F, 1.6F);
        }
        if (r.ticks >= DURATION) {
            RUNNING.remove(p.getUniqueID());
            ws.spawnParticle(EnumParticleTypes.END_ROD, cx, cy, cz, 60, 0.4, 0.4, 0.4, 0.15);
            // старший ритуал запечатывается в сферу — её применяют там, где нужно
            EntityItem ei = new EntityItem(ws, cx, cy, cz, ItemRitualSphere.of(r.ritual, r.ritual.duration));
            ei.motionX = ei.motionY = ei.motionZ = 0;
            ei.setNoPickupDelay();
            ws.spawnEntity(ei);
            ws.playSound(null, r.altar, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 0.6F);
            p.sendMessage(Breach.grimoire("chaosshard.ritual.sphere.made", new TextComponentTranslation(r.ritual.key())));
            Milestones.grant(p, Milestones.HIGH_RITUAL);
        }
    }

    public static List<TileAnchor> myAnchors(EntityPlayerMP p, BlockPos altar) {
        List<TileAnchor> out = new ArrayList<>();
        for (BlockPos a : AnchorData.get(p.world).anchors) {
            if (a.distanceSq(altar) > (double) SURGE_REACH * SURGE_REACH) {
                continue;
            }
            TileEntity te = p.world.getTileEntity(a);
            if (te instanceof TileAnchor && p.getUniqueID().equals(((TileAnchor) te).getOwner())) {
                out.add((TileAnchor) te);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Взор Нити

    public static void sight(EntityPlayerMP p, BlockPos at) {
        int glowing = 0;
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(at).grow(SIGHT_RADIUS),
                x -> x instanceof IMob && x.isEntityAlive())) {
            e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 1200, 0, false, false));
            glowing++;
        }
        p.sendMessage(Breach.grimoire("chaosshard.altar.sight.mobs", glowing));
        // ближайшие постройки Ордена и святыни
        List<long[]> found = new ArrayList<>();   // dist², x, z, kind(0 постройка / 1 святыня), name index
        List<String> names = new ArrayList<>();
        for (OrderStructureData.Entry e : OrderStructureData.get(p.world).entries) {
            if (e.state == OrderStructureData.FAILED) {
                continue;
            }
            long dx = e.x - at.getX(), dz = e.z - at.getZ();
            found.add(new long[]{dx * dx + dz * dz, e.x, e.z, 0, names.size()});
            names.add("chaosshard.structure." + e.name);
        }
        for (BlockPos s : ru.death.chaosshard.order.ShrineData.get(p.world).shrines) {
            long dx = s.getX() - at.getX(), dz = s.getZ() - at.getZ();
            found.add(new long[]{dx * dx + dz * dz, s.getX(), s.getZ(), 1, names.size()});
            names.add("chaosshard.structure.shrine");
        }
        found.sort((a, b) -> Long.compare(a[0], b[0]));
        if (found.isEmpty()) {
            p.sendMessage(Breach.grimoire("chaosshard.altar.sight.none"));
            return;
        }
        p.sendMessage(Breach.grimoire("chaosshard.altar.sight.head"));
        for (int i = 0; i < Math.min(5, found.size()); i++) {
            long[] f = found.get(i);
            int dist = (int) Math.sqrt(f[0]);
            p.sendMessage(new TextComponentString(" §7• ").appendSibling(new TextComponentTranslation(names.get((int) f[4])))
                    .appendSibling(new TextComponentString("§7 — " + f[1] + " " + f[2] + " (" + dist + " бл., "
                            + side(f[1] - at.getX(), f[2] - at.getZ()) + ")")));
        }
    }

    static String side(long dx, long dz) {
        String ns = dz < -Math.abs(dx) / 2 ? "С" : dz > Math.abs(dx) / 2 ? "Ю" : "";
        String ew = dx > Math.abs(dz) / 2 ? "В" : dx < -Math.abs(dz) / 2 ? "З" : "";
        String s = ns + ew;
        return s.isEmpty() ? "здесь" : s;
    }

    // ------------------------------------------------------------------ Кровавая луна

    /** Сфера Кровавой луны держит луну, пока стоит. */
    public static void keepMoon(World world, BlockPos pos, long until) {
        MOONS.computeIfAbsent(world.provider.getDimension(), k -> new HashMap<>()).put(pos.toLong(), until);
    }

    public static boolean moonAt(World world, BlockPos pos) {
        Map<Long, Long> m = MOONS.get(world.provider.getDimension());
        if (m == null) {
            return false;
        }
        long now = world.getTotalWorldTime();
        for (Map.Entry<Long, Long> e : m.entrySet()) {
            if (e.getValue() > now && BlockPos.fromLong(e.getKey()).distanceSq(pos) <= (double) MOON_RADIUS * MOON_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static final String[] MOON_MOBS = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk",
            "minecraft:stray", "minecraft:zombie_villager", "minecraft:witch"};

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || MOONS.isEmpty()) {
            return;
        }
        World w = event.world;
        Map<Long, Long> m = MOONS.get(w.provider.getDimension());
        if (m == null || m.isEmpty()) {
            return;
        }
        long now = w.getTotalWorldTime();
        Iterator<Map.Entry<Long, Long>> it = m.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            BlockPos altar = BlockPos.fromLong(e.getKey());
            if (e.getValue() <= now) {
                it.remove();
                w.playSound(null, altar, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.5F, 0.5F);
                for (EntityPlayer pl : w.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(altar).grow(MOON_RADIUS))) {
                    pl.sendMessage(Breach.grimoire("chaosshard.altar.moon.end"));
                }
                continue;
            }
            if (now % 300 != 0) {
                continue;
            }
            for (EntityPlayer pl : w.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(altar).grow(MOON_RADIUS))) {
                for (int i = 0; i < 2; i++) {
                    spawnMoonMob((WorldServer) w, pl);
                }
            }
        }
    }

    public static void spawnMoonMob(WorldServer w, EntityPlayer near) {
        for (int tries = 0; tries < 8; tries++) {
            double a = w.rand.nextDouble() * Math.PI * 2;
            int r = 10 + w.rand.nextInt(7);
            BlockPos top = w.getTopSolidOrLiquidBlock(new BlockPos(near.posX + Math.cos(a) * r, 0, near.posZ + Math.sin(a) * r));
            if (w.getBlockState(top.down()).getMaterial().isLiquid() || Math.abs(top.getY() - near.posY) > 8) {
                continue;
            }
            Entity e = EntityList.createEntityByIDFromName(new ResourceLocation(MOON_MOBS[w.rand.nextInt(MOON_MOBS.length)]), w);
            if (!(e instanceof EntityLiving)) {
                return;
            }
            EntityLiving m = (EntityLiving) e;
            m.setLocationAndAngles(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, w.rand.nextFloat() * 360, 0);
            m.onInitialSpawn(w.getDifficultyForLocation(top), null);
            m.addTag("chaosshard_moon");
            w.spawnEntity(m);
            w.spawnParticle(EnumParticleTypes.REDSTONE, m.posX, m.posY + 1, m.posZ, 20, 0.3, 0.6, 0.3, 0.0);
            return;
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (MOONS.isEmpty() || e.world.isRemote || !(e instanceof IMob) || !(event.getSource().getTrueSource() instanceof EntityPlayer)
                || !moonAt(e.world, e.getPosition())) {
            return;
        }
        if (e.world.rand.nextFloat() < 0.3F) {
            event.getDrops().add(new EntityItem(e.world, e.posX, e.posY + 0.5, e.posZ, new ItemStack(ModItems.CHAOS_ESSENCE)));
        }
    }

    @SubscribeEvent
    public void onXp(LivingExperienceDropEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!MOONS.isEmpty() && !e.world.isRemote && e instanceof IMob && moonAt(e.world, e.getPosition())) {
            event.setDroppedExperience(event.getDroppedExperience() * 2);
        }
    }
}
