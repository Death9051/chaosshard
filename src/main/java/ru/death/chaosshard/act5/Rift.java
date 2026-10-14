package ru.death.chaosshard.act5;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.structure.EntityOrderKnight;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Разлом и Архонт (Акт 5). Врата за троном ведут в Разлом тех, у кого Ключ Разлома.
 * Пилоны бьют приёмами своих героев, пока целы; когда разбиты все — Архонт уязвим.
 * Если в Разломе никого не осталось, бой начинается заново: пилоны встают, Архонт исцелён.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class Rift {

    public static final int PYLON_EVERY = 120, INTRO_RANGE = 22, RESET_AFTER = 600;

    /** Состояние боя — в мире Разлома. */
    public static class Data extends WorldSavedData {
        public static final String NAME = "chaosshard_rift";
        public boolean defeated, introShown;
        public int nextPylon, emptyTicks;

        public Data() {
            super(NAME);
        }

        public Data(String n) {
            super(n);
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            defeated = t.getBoolean("Defeated");
            introShown = t.getBoolean("Intro");
            nextPylon = t.getInteger("NextPylon");
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound t) {
            t.setBoolean("Defeated", defeated);
            t.setBoolean("Intro", introShown);
            t.setInteger("NextPylon", nextPylon);
            return t;
        }
    }

    public static Data data(World w) {
        Data d = (Data) w.getPerWorldStorage().getOrLoadData(Data.class, Data.NAME);
        if (d == null) {
            d = new Data();
            w.getPerWorldStorage().setData(Data.NAME, d);
        }
        return d;
    }

    // ------------------------------------------------------------------ врата

    /** Врата за троном (после падения Короля). */
    public static void openGate(WorldServer lu) {
        CapitalData d = CapitalData.get(lu);
        if (d.rift == null) {
            return;
        }
        for (int dy = 0; dy < 3; dy++) {
            lu.setBlockState(d.rift.up(dy), ModBlocks.RIFT_GATE.getDefaultState(), 3);
        }
        d.riftOpen = true;
        d.markDirty();
        lu.playSound(null, d.rift, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 3.0F, 0.6F);
    }

    static boolean hasKey(EntityPlayer p) {
        if (p.isCreative()) {
            return true;
        }
        for (ItemStack s : p.inventory.mainInventory) {
            if (s.getItem() == ModItems.RIFT_KEY) {
                return true;
            }
        }
        return p.inventory.offHandInventory.get(0).getItem() == ModItems.RIFT_KEY;
    }

    /** Войти в Разлом (с Ключом). */
    public static boolean enter(EntityPlayerMP p) {
        if (!hasKey(p)) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.rift.no_key"), true);
            return false;
        }
        WorldServer rw = p.getServer().getWorld(RiftDimension.id());
        if (rw != null && data(rw).defeated) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.rift.quiet"), true);
            return false;
        }
        ChaosTeleporter.toPos(p, RiftDimension.id(), RiftDimension.arrival());
        Milestones.grant(p, Milestones.RIFT_ENTERED);
        return true;
    }

    // ------------------------------------------------------------------ пилоны

    public static boolean pylonIntact(World w, int i) {
        return w.getBlockState(RiftDimension.pylon(i)).getBlock() == ModBlocks.RIFT_PYLON;
    }

    public static int intactPylons(World w) {
        int n = 0;
        for (int i = 0; i < 5; i++) {
            if (pylonIntact(w, i)) {
                n++;
            }
        }
        return n;
    }

    public static void pylonBroken(WorldServer w, int hero) {
        if (!RiftDimension.is(w)) {
            return;
        }
        say(w, "chaosshard.archon.pylon." + hero);
        for (EntityPlayerMP p : players(w)) {
            Milestones.grant(p, Milestones.PYLON_BROKEN);
        }
        if (intactPylons(w) == 0) {
            EntityArchon a = archon(w);
            if (a != null) {
                a.unshield();
            }
        }
    }

    static void restorePylons(WorldServer w) {
        for (int i = 0; i < 5; i++) {
            if (!pylonIntact(w, i)) {
                w.setBlockState(RiftDimension.pylon(i), ModBlocks.RIFT_PYLON.getStateFromMeta(i), 3);
            }
        }
    }

    /** Приём героя из пилона i против ближайшего врага. */
    public static void pylonStrike(WorldServer w, int i) {
        BlockPos p = RiftDimension.pylon(i);
        Vec3d at = new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        EntityPlayer t = w.getClosestPlayer(at.x, at.y, at.z, 64, false);
        w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, at.x, at.y, at.z, 40, 0.3, 0.3, 0.3, 0.2);
        switch (i) {
            case 0: // Каэль: вспышка — слепнут те, кто смотрит на пилон
                for (EntityPlayer pl : players(w)) {
                    Vec3d look = pl.getLook(1.0F);
                    Vec3d to = at.subtract(pl.getPositionEyes(1.0F)).normalize();
                    if (look.dotProduct(to) > 0.7 && pl.getDistanceSq(p) < 40 * 40) {
                        pl.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0));
                    }
                }
                w.playSound(null, p, SoundEvents.ENTITY_FIREWORK_TWINKLE, SoundCategory.HOSTILE, 3.0F, 1.0F);
                break;
            case 1: // Ирена: святой свет у пилона жжёт тех, кто рядом
                for (EntityLivingBase e : w.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(p).grow(7, 12, 7),
                        x -> x instanceof EntityPlayer || ru.death.chaosshard.servant.Servants.isServant(x))) {
                    e.attackEntityFrom(DamageSource.MAGIC, 6.0F);
                }
                w.spawnParticle(EnumParticleTypes.END_ROD, at.x, RiftDimension.FLOOR + 1.2, at.z, 80, 4, 0.2, 4, 0.02);
                w.playSound(null, p, SoundEvents.BLOCK_NOTE_BELL, SoundCategory.HOSTILE, 2.0F, 1.4F);
                break;
            case 2: // Вельмор: огненные шары
                if (t != null) {
                    for (int k = 0; k < 3; k++) {
                        net.minecraft.entity.projectile.EntitySmallFireball f = new net.minecraft.entity.projectile.EntitySmallFireball(w,
                                at.x, at.y, at.z, t.posX - at.x + w.rand.nextGaussian(), t.posY + 1 - at.y, t.posZ - at.z + w.rand.nextGaussian());
                        w.spawnEntity(f);
                    }
                    w.playSound(null, p, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0F, 1.0F);
                }
                break;
            case 3: // Лиан: песнь — всех тянет к земле
                for (EntityPlayer pl : players(w)) {
                    pl.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1));
                    pl.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 80, 1));
                }
                w.playSound(null, p, SoundEvents.BLOCK_NOTE_HARP, SoundCategory.HOSTILE, 3.0F, 0.6F);
                break;
            default: // Элис: королевская стража
                for (int k = 0; k < 2; k++) {
                    EntityOrderKnight g = new EntityOrderKnight(w);
                    g.setup(EntityOrderKnight.GUARD);
                    g.setLocationAndAngles(p.getX() + 0.5 + w.rand.nextInt(5) - 2, RiftDimension.FLOOR + 1, p.getZ() + 0.5 + w.rand.nextInt(5) - 2, 0, 0);
                    g.addTag(EntityKing.TAG_ROYAL);
                    w.spawnEntity(g);
                    if (t != null) {
                        g.setAttackTarget(t);
                    }
                }
                w.playSound(null, p, SoundEvents.BLOCK_NOTE_BASS, SoundCategory.HOSTILE, 3.0F, 0.5F);
                break;
        }
    }

    // ------------------------------------------------------------------ разрывы арены

    /** Разрыв: сектор края арены через TEAR_WARN тиков уходит в пустоту на TEAR_HOLD тиков. */
    static final class Tear {
        final double angle;
        int warn;
        int hold;
        final List<Object[]> saved = new ArrayList<>();

        Tear(double angle) {
            this.angle = angle;
            this.warn = EntityArchon.TEAR_WARN;
            this.hold = EntityArchon.TEAR_HOLD;
        }
    }

    public static int tearsActive() {
        return TEARS.size();
    }

    static final List<Tear> TEARS = new ArrayList<>();

    public static void tear(WorldServer w) {
        TEARS.add(new Tear(w.rand.nextDouble() * Math.PI * 2));
        w.playSound(null, RiftDimension.center(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 3.0F, 0.5F);
    }

    static boolean inSector(int x, int z, double angle) {
        double r = Math.sqrt(x * x + z * z);
        if (r < 17 || r > RiftDimension.ARENA_R + 2) {
            return false;
        }
        double a = Math.atan2(z, x) - angle;
        a = Math.atan2(Math.sin(a), Math.cos(a));
        return Math.abs(a) < Math.toRadians(30);
    }

    public static void tickTears(WorldServer w) {
        int R = RiftDimension.ARENA_R + 2;
        for (int i = TEARS.size() - 1; i >= 0; i--) {
            Tear t = TEARS.get(i);
            if (t.warn > 0) {
                t.warn--;
                if (t.warn % 5 == 0) {
                    for (int k = 0; k < 30; k++) {
                        double a = t.angle + (w.rand.nextDouble() - 0.5) * Math.toRadians(60);
                        double r = 17 + w.rand.nextDouble() * (RiftDimension.ARENA_R - 17);
                        w.spawnParticle(EnumParticleTypes.PORTAL, Math.cos(a) * r, RiftDimension.FLOOR + 1.1, Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.3);
                    }
                }
                if (t.warn == 0) {
                    for (int x = -R; x <= R; x++) {
                        for (int z = -R; z <= R; z++) {
                            if (!inSector(x, z, t.angle)) {
                                continue;
                            }
                            for (int y = RiftDimension.FLOOR - 12; y <= RiftDimension.FLOOR + 1; y++) {
                                BlockPos p = new BlockPos(x, y, z);
                                IBlockState s = w.getBlockState(p);
                                if (!w.isAirBlock(p) && s.getBlock() != ModBlocks.RIFT_PYLON) {
                                    t.saved.add(new Object[]{p, s});
                                    w.setBlockState(p, net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
                                }
                            }
                        }
                    }
                }
            } else if (--t.hold <= 0) {
                restore(w, t);
                TEARS.remove(i);
            }
        }
    }

    static void restore(WorldServer w, Tear t) {
        for (Object[] o : t.saved) {
            if (w.isAirBlock((BlockPos) o[0])) {
                w.setBlockState((BlockPos) o[0], (IBlockState) o[1], 2);
            }
        }
        t.saved.clear();
    }

    /** Сервер останавливается: края арены — на место до сохранения мира (разрывы живут только в памяти). */
    public static void restoreOnStop() {
        WorldServer w = DimensionManager.getWorld(RiftDimension.id());
        if (w != null) {
            restoreAllTears(w);
        }
        TEARS.clear();
    }

    static void restoreAllTears(WorldServer w) {
        for (Tear t : TEARS) {
            restore(w, t);
        }
        TEARS.clear();
    }

    // ------------------------------------------------------------------ Архонт и ход боя

    public static List<EntityPlayerMP> players(WorldServer w) {
        List<EntityPlayerMP> out = new ArrayList<>();
        for (EntityPlayer p : w.playerEntities) {
            if (p instanceof EntityPlayerMP && !p.isSpectator() && p.isEntityAlive()) {
                out.add((EntityPlayerMP) p);
            }
        }
        return out;
    }

    public static EntityArchon archon(WorldServer w) {
        List<EntityArchon> l = w.getEntities(EntityArchon.class, EntityLivingBase::isEntityAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    public static EntityArchon spawnArchon(WorldServer w) {
        EntityArchon a = archon(w);
        if (a != null) {
            return a;
        }
        a = new EntityArchon(w);
        a.setup();
        BlockPos c = RiftDimension.center();
        a.setLocationAndAngles(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 180, 0);
        a.scripted = !data(w).introShown;
        w.spawnEntity(a);
        return a;
    }

    public static void say(WorldServer w, String key) {
        TextComponentTranslation name = new TextComponentTranslation("entity.chaosshard.archon.name");
        name.getStyle().setColor(TextFormatting.LIGHT_PURPLE).setBold(true);
        TextComponentTranslation line = new TextComponentTranslation(key);
        line.getStyle().setColor(TextFormatting.WHITE);
        for (EntityPlayer p : w.playerEntities) {
            p.sendMessage(new TextComponentTranslation("").appendSibling(name).appendText(": ").appendSibling(line));
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer) || !RiftDimension.is(event.world)) {
            return;
        }
        WorldServer w = (WorldServer) event.world;
        Data d = data(w);
        tickTears(w);
        if (d.defeated) {
            return;
        }
        List<EntityPlayerMP> ps = players(w);
        EntityArchon a = archon(w);
        if (ps.isEmpty()) {
            // все ушли или пали — бой начинается заново
            if (a != null && (a.phase() > 1 || a.getHealth() < a.getMaxHealth() || intactPylons(w) < 5)) {
                if (++d.emptyTicks >= RESET_AFTER) {
                    d.emptyTicks = 0;
                    reset(w, a);
                }
            }
            return;
        }
        d.emptyTicks = 0;
        long t = w.getTotalWorldTime();
        if (a == null && t % 20 == 0) {
            a = spawnArchon(w);
        }
        if (a == null) {
            return;
        }
        if (!d.introShown && t % 10 == 0) {
            for (EntityPlayerMP p : ps) {
                if (!(p instanceof net.minecraftforge.common.util.FakePlayer) && p.getDistanceSq(a) < INTRO_RANGE * INTRO_RANGE
                        && !ru.death.chaosshard.cutscene.CutsceneManager.playing(p)) {
                    d.introShown = true;
                    d.markDirty();
                    a.scripted = true;
                    ru.death.chaosshard.cutscene.CutsceneManager.play(p, ArchonScenes.intro(w, a, p), new ArchonScenes.Intro(a));
                    break;
                }
            }
        }
        if (a.phase() == 1 && !a.scripted && t % PYLON_EVERY == 0 && intactPylons(w) > 0) {
            for (int k = 0; k < 5; k++) {
                int i = (d.nextPylon + k) % 5;
                if (pylonIntact(w, i)) {
                    pylonStrike(w, i);
                    d.nextPylon = (i + 1) % 5;
                    break;
                }
            }
        }
        if (a.phase() == 1 && intactPylons(w) == 0) {
            a.unshield();
        }
    }

    /** Начать бой заново. */
    public static void reset(WorldServer w, EntityArchon a) {
        restoreAllTears(w);
        restorePylons(w);
        if (a != null) {
            a.setHealth(a.getMaxHealth());
            a.setPhase(1);
            BlockPos c = RiftDimension.center();
            a.setPositionAndUpdate(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            a.setAttackTarget(null);
        }
        for (EntityOrderKnight k : w.getEntities(EntityOrderKnight.class, EntityLivingBase::isEntityAlive)) {
            k.setDead();
        }
    }

    /** Архонт пал: Завеса падает. Финальная сцена, титры, дорога домой. */
    public static void victory(WorldServer w) {
        Data d = data(w);
        d.defeated = true;
        d.markDirty();
        restoreAllTears(w);
        for (EntityOrderKnight k : w.getEntities(EntityOrderKnight.class, EntityLivingBase::isEntityAlive)) {
            k.setDead();
        }
        for (EntityPlayerMP p : players(w)) {
            ThreadManager.addPoints(p, ChaosConfig.order.archonReward);
            Milestones.grant(p, Milestones.ARCHON_SLAIN);
            Milestones.grant(p, Milestones.GAME_COMPLETE);
            if (p instanceof net.minecraftforge.common.util.FakePlayer) {
                continue;
            }
            ru.death.chaosshard.cutscene.CutsceneManager.play(p, ArchonScenes.ending(w, p), new ArchonScenes.Ending());
        }
    }

    /** После финала: домой, в Лучезарию, и титры. */
    public static void home(EntityPlayerMP p) {
        WorldServer lu = p.getServer().getWorld(Breach.luchezariyaDim());
        if (lu != null) {
            ChaosTeleporter.toWorldSpawn(p, Breach.luchezariyaDim());
        }
        ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendTo(new ru.death.chaosshard.network.PacketCredits(), p);
    }

    /** Сброс для отладки и автотеста. */
    public static void forget(WorldServer w) {
        Data d = data(w);
        d.defeated = false;
        d.introShown = false;
        d.emptyTicks = 0;
        d.markDirty();
        restoreAllTears(w);
        restorePylons(w);
        EntityArchon a = archon(w);
        if (a != null) {
            a.setDead();
        }
    }

    public static WorldServer riftWorld() {
        WorldServer w = DimensionManager.getWorld(RiftDimension.id());
        if (w == null) {
            DimensionManager.initDimension(RiftDimension.id());
            w = DimensionManager.getWorld(RiftDimension.id());
        }
        return w;
    }

    private Rift() {
    }
}
