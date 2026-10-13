package ru.death.chaosshard.act5;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.hero.HeroData;
import ru.death.chaosshard.hero.Lightbearer;
import ru.death.chaosshard.hero.Pillars;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.structure.bp.Blueprint;
import ru.death.chaosshard.structure.BlueprintPlacer;
import ru.death.chaosshard.structure.EntityOrderKnight;
import ru.death.chaosshard.structure.OrderStructureData;
import ru.death.chaosshard.structure.OrderStructures;
import ru.death.chaosshard.structure.TileOrderBeacon;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.List;

/**
 * Столица и Король (Акт 5).
 * Знамение Короны (старший ритуал на трёх Печатях Ордена) ставит столицу в нетронутых землях.
 * Пока горит хоть один маяк Света на башнях, защитники сильнее, а вход во дворец закрыт барьером.
 * Маяк гасят Эссенцией Хаоса (ПКМ), когда рядом нет стражи. Погасли все — барьер падает, Король встаёт с трона.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class Crown {

    public static final String STRUCTURE = "capital";
    public static final int INTRO_RANGE = 14;

    // ------------------------------------------------------------------ Знамение

    public static boolean omen(EntityPlayerMP p, World world, BlockPos at) {
        if (world.provider.getDimension() != Breach.luchezariyaDim()) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.omen.wrong_world"), true);
            return false;
        }
        if (HeroData.get(world).fallenCount() < Lightbearer.values().length) {
            p.sendMessage(Breach.grimoire("chaosshard.crown.early"));
            return false;
        }
        CapitalData d = CapitalData.get(world);
        if (d.state != CapitalData.NONE) {
            p.sendMessage(Breach.grimoire("chaosshard.crown.already", d.x, d.z, (int) Math.sqrt(at.distanceSq(d.x, at.getY(), d.z))));
            return false;
        }
        WorldServer ws = (WorldServer) world;
        int[] range = ChaosConfig.order.omenDistance;
        int near = range.length > 0 ? range[0] : 400, far = range.length > 1 ? Math.max(near, range[1]) : near * 6;
        int[] spot = Pillars.findSpot(ws, at, p.rotationYaw, STRUCTURE, near, far, 128);
        if (spot == null) {
            p.sendMessage(Breach.grimoire("chaosshard.omen.nowhere"));
            return false;
        }
        OrderStructureData sd = OrderStructureData.get(world);
        OrderStructureData.Entry e = OrderStructures.plan(sd, STRUCTURE, spot[0], spot[1], world.rand.nextInt(4), world.rand.nextLong());
        d.state = CapitalData.PLANNED;
        d.entry = e.id;
        d.x = spot[0];
        d.z = spot[1];
        d.markDirty();
        ws.addWeatherEffect(new EntityLightningBolt(world, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, true));
        p.sendMessage(Breach.grimoire("chaosshard.crown.found", d.x, d.z, (int) Math.sqrt(at.distanceSq(d.x, at.getY(), d.z))));
        Milestones.grant(p, Milestones.CROWN_OMEN);
        ChaosShard.LOG.info("Знамение Короны: столица будет у {} {}", d.x, d.z);
        return true;
    }

    /** Столица встала (вызывает OrderStructures.place). */
    public static void placed(WorldServer ws, OrderStructureData.Entry e, Blueprint b) {
        CapitalData d = CapitalData.get(ws);
        BlockPos o = e.origin();
        d.entry = e.id;
        d.x = o.getX();
        d.y = o.getY();
        d.z = o.getZ();
        d.beacons.clear();
        d.beacons.addAll(e.beams);
        d.barrier.clear();
        for (Blueprint.Mark m : b.marks) {
            BlockPos p = BlueprintPlacer.at(o, m.x, m.y, m.z, e.rot);
            if ("barrier".equals(m.kind)) {
                d.barrier.add(p);
            } else if ("king".equals(m.kind)) {
                d.king = p;
            } else if ("rift".equals(m.kind)) {
                d.rift = p;
            }
        }
        if (d.state < CapitalData.STANDING) {
            d.state = CapitalData.STANDING;
        }
        d.markDirty();
        ChaosShard.LOG.info("Столица встала у {} {} {}: маяков {}, барьер {}", d.x, d.y, d.z, d.beacons.size(), d.barrier.size());
    }

    /** Сколько маяков ещё горит. */
    public static int lit(World w) {
        int n = 0;
        for (BlockPos p : CapitalData.get(w).beacons) {
            TileEntity te = w.isBlockLoaded(p) ? w.getTileEntity(p) : null;
            if (te instanceof TileOrderBeacon && ((TileOrderBeacon) te).lit) {
                n++;
            }
        }
        return n;
    }

    static boolean isCapitalBeacon(World w, BlockPos p) {
        return w.provider.getDimension() == Breach.luchezariyaDim() && CapitalData.get(w).beacons.contains(p);
    }

    /** Стража рядом с маяком, которая не даёт его погасить. */
    public static List<EntityOrderKnight> keepers(World w, BlockPos p) {
        return w.getEntitiesWithinAABB(EntityOrderKnight.class, new AxisAlignedBB(p).grow(ChaosConfig.order.beaconGuardRadius),
                k -> k.isEntityAlive() && !ru.death.chaosshard.servant.Servants.isServant(k));
    }

    /** Погасить маяк. 0 — погас, 1 — стража рядом, 2 — мало Эссенции, 3 — уже не горит. */
    public static int snuff(EntityPlayerMP p, BlockPos pos, ItemStack held) {
        World w = p.world;
        TileEntity te = w.getTileEntity(pos);
        if (!(te instanceof TileOrderBeacon) || !((TileOrderBeacon) te).lit) {
            return 3;
        }
        List<EntityOrderKnight> ks = keepers(w, pos);
        if (!ks.isEmpty()) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.crown.beacon_guarded", ks.size()), true);
            return 1;
        }
        int need = ChaosConfig.order.beaconEssence;
        if (!p.isCreative() && (held.getItem() != ModItems.CHAOS_ESSENCE || held.getCount() < need)) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.crown.beacon_need", need), true);
            return 2;
        }
        if (!p.isCreative()) {
            held.shrink(need);
        }
        ((TileOrderBeacon) te).setLit(false);
        WorldServer ws = (WorldServer) w;
        ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 0.4, 1.5, 0.4, 0.05);
        ws.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 80, 0.5, 0.5, 0.5, 1.0);
        w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 3.0F, 0.5F);
        int left = lit(w);
        p.sendMessage(Breach.grimoire("chaosshard.crown.beacon_out", CapitalData.get(w).beacons.size() - left, CapitalData.get(w).beacons.size()));
        Milestones.grant(p, Milestones.BEACON_OUT);
        if (left == 0) {
            open(ws);
        }
        return 0;
    }

    /** Барьер пал: Король встаёт с трона. */
    public static void open(WorldServer ws) {
        CapitalData d = CapitalData.get(ws);
        if (d.state >= CapitalData.OPEN) {
            return;
        }
        for (BlockPos p : d.barrier) {
            if (ws.getBlockState(p).getBlock() == ModBlocks.CAPITAL_BARRIER) {
                ws.setBlockToAir(p);
                ws.spawnParticle(EnumParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            }
        }
        d.state = CapitalData.OPEN;
        d.markDirty();
        ws.playSound(null, d.center(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 4.0F, 0.5F);
        spawnKing(ws);
        for (EntityPlayer pl : ws.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(d.center()).grow(96, 64, 96))) {
            pl.sendMessage(Breach.grimoire("chaosshard.crown.open"));
            if (pl instanceof EntityPlayerMP) {
                Milestones.grant((EntityPlayerMP) pl, Milestones.CAPITAL_OPEN);
            }
        }
    }

    /** Король на месте перед троном (если ещё не стоит). */
    public static EntityKing spawnKing(WorldServer ws) {
        CapitalData d = CapitalData.get(ws);
        if (d.king == null) {
            return null;
        }
        EntityKing k = kingOf(ws);
        if (k != null) {
            return k;
        }
        k = new EntityKing(ws);
        k.setup();
        k.setLocationAndAngles(d.king.getX() + 0.5, d.king.getY(), d.king.getZ() + 0.5, 180, 0);
        k.setHomePosAndDistance(d.king, 14);
        k.scripted = !d.introShown;
        ws.spawnEntity(k);
        d.kingSpawned = true;
        d.markDirty();
        return k;
    }

    public static EntityKing kingOf(WorldServer ws) {
        CapitalData d = CapitalData.get(ws);
        if (d.king == null) {
            return null;
        }
        List<EntityKing> l = ws.getEntitiesWithinAABB(EntityKing.class, new AxisAlignedBB(d.king).grow(48, 32, 48), EntityLivingBase::isEntityAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Король пал: Ключ Разлома, Нить; за троном откроется Разлом. */
    public static void kingFell(WorldServer ws, BlockPos at, EntityPlayerMP killer) {
        CapitalData d = CapitalData.get(ws);
        d.state = CapitalData.FALLEN;
        d.markDirty();
        EntityItem key = new EntityItem(ws, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, new ItemStack(ModItems.RIFT_KEY));
        key.setEntityInvulnerable(true);
        key.setNoDespawn();
        ws.spawnEntity(key);
        EntityItem crown = new EntityItem(ws, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, new ItemStack(ModItems.ROYAL_CROWN));
        crown.setEntityInvulnerable(true);
        crown.setNoDespawn();
        ws.spawnEntity(crown);
        ru.death.chaosshard.act5.Rift.openGate(ws);
        for (EntityPlayerMP p : ws.getEntitiesWithinAABB(EntityPlayerMP.class, new AxisAlignedBB(at).grow(64))) {
            ThreadManager.addPoints(p, ChaosConfig.order.kingReward);
            Milestones.grant(p, Milestones.KING_SLAIN);
            p.sendMessage(Breach.grimoire("chaosshard.crown.king_fell", ChaosConfig.order.kingReward));
            if (!(p instanceof net.minecraftforge.common.util.FakePlayer)) {
                ru.death.chaosshard.cutscene.CutsceneManager.play(p, KingScenes.fall(ws, at, p), KingScenes.NONE);
            }
        }
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        World w = event.getWorld();
        if (w.isRemote || event.getHand() != net.minecraft.util.EnumHand.MAIN_HAND
                || w.getBlockState(event.getPos()).getBlock() != ModBlocks.ORDER_BEACON || !isCapitalBeacon(w, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        snuff((EntityPlayerMP) event.getEntityPlayer(), event.getPos(), event.getEntityPlayer().getHeldItemMainhand());
    }

    /** Маяки столицы не ломаются, пока горят. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        World w = event.getWorld();
        if (!w.isRemote && event.getState().getBlock() == ModBlocks.ORDER_BEACON && isCapitalBeacon(w, event.getPos())
                && !event.getPlayer().isCreative()) {
            TileEntity te = w.getTileEntity(event.getPos());
            if (te instanceof TileOrderBeacon && ((TileOrderBeacon) te).lit) {
                event.setCanceled(true);
                event.getPlayer().sendStatusMessage(Breach.grimoire("chaosshard.crown.beacon_hard"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)
                || event.world.provider.getDimension() != Breach.luchezariyaDim()) {
            return;
        }
        WorldServer ws = (WorldServer) event.world;
        CapitalData d = CapitalData.get(ws);
        if (d.state < CapitalData.STANDING || d.state == CapitalData.FALLEN || !ws.isBlockLoaded(d.center())) {
            return;
        }
        long t = ws.getTotalWorldTime();
        if (d.state == CapitalData.STANDING && t % 40 == 0) {
            int n = lit(ws);
            if (n > 0) {
                int r = ru.death.chaosshard.structure.bp.Capital.H + 8;
                for (EntityOrderKnight k : ws.getEntitiesWithinAABB(EntityOrderKnight.class, new AxisAlignedBB(d.center()).grow(r, 32, r),
                        x -> x.isEntityAlive() && !ru.death.chaosshard.servant.Servants.isServant(x))) {
                    k.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, n >= 3 ? 1 : 0, true, false));
                    if (n >= 2) {
                        k.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 60, 0, true, false));
                    }
                    if (n >= 4) {
                        k.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 0, true, false));
                    }
                }
            }
        }
        if (d.state == CapitalData.OPEN && !d.introShown && t % 10 == 0) {
            EntityKing k = kingOf(ws);
            if (k == null) {
                k = spawnKing(ws);
            }
            if (k != null) {
                for (EntityPlayer p : ws.playerEntities) {
                    if (p instanceof EntityPlayerMP && !(p instanceof net.minecraftforge.common.util.FakePlayer) && !p.isSpectator()
                            && p.getDistanceSq(k) < INTRO_RANGE * INTRO_RANGE && !ru.death.chaosshard.cutscene.CutsceneManager.playing(p)) {
                        d.introShown = true;
                        d.markDirty();
                        intro((EntityPlayerMP) p, k);
                        break;
                    }
                }
            }
        }
    }

    /** Показать появление Короля. */
    public static void intro(EntityPlayerMP p, EntityKing king) {
        double dx = king.posX - p.posX, dz = king.posZ - p.posZ, dy = king.posY + 1.6 - (p.posY + p.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.connection.setPlayerLocation(p.posX, p.posY, p.posZ, yaw, pitch);
        p.rotationYaw = p.rotationYawHead = yaw;
        p.rotationPitch = pitch;
        king.scripted = true;
        ru.death.chaosshard.cutscene.CutsceneManager.play(p, KingScenes.intro((WorldServer) p.world, king, p), new KingScenes.Intro(king));
    }

    private Crown() {
    }
}
