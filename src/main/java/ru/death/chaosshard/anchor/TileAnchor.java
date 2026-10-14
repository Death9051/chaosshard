package ru.death.chaosshard.anchor;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Якорь Хаоса. Пускает Скверну кругом, который растёт по мере осквернения.
 * Набрав порог, край считается захваченным: владелец получает Нить разом и потом понемногу.
 */
public class TileAnchor extends TileEntity implements ITickable {

    private UUID owner;
    private String ownerName = "";
    private int corrupted;
    private boolean captured;
    private int timer;
    private int passiveTimer;
    /** Захват уже засчитан владельцу (для квестов). */
    private boolean counted;
    /** Тики до следующего набега Мрачной Армии (−1 — ещё не назначен). */
    private int raidTimer = -1;

    /** За сколько тиков до набега Гаррот предупреждает (барабаны, сторона света). */
    public static final int WARN = 1200;
    /** Сколько тиков отряд должен простоять у Якоря, чтобы Якорь потерял силу. */
    public static final int SIEGE = 1200;
    /** Пауза между набегами по уровню сети (доля от raidInterval). */
    private static final double[] INTERVAL = {1.0, 1.0, 0.85, 0.67, 0.33};

    private boolean raidWarned;
    private double raidAngle;
    private long raidTarget;
    private int siege;
    private long lastSiege;

    public void setRaidTimer(int t) {
        raidTimer = t;
        raidWarned = false;
        markDirty();
    }

    public int raidTimer() {
        return raidTimer;
    }

    public UUID getOwner() {
        return owner;
    }

    /**
     * Раз в секунду. Отсчёт ведёт главный Якорь сети: один набег на всю сеть. За минуту — предупреждение
     * и барабаны с той стороны, откуда придут. Набег приходит, только если хозяин рядом с сетью.
     */
    private void tickArmy() {
        if (siege > 0 && world.getTotalWorldTime() - lastSiege > 200) {
            siege = Math.max(0, siege - 20);
        }
        if (!ru.death.chaosshard.ChaosConfig.order.raids) {
            return;
        }
        // Гаррот склонился — набегов нет, пока в бой не пойдёт сам Орден (Акт 5)
        boolean order = ru.death.chaosshard.garrot.GarrotData.get(world).submitted;
        if (order && !ru.death.chaosshard.act5.OrderRaids.active(world)) {
            return;
        }
        java.util.List<TileAnchor> c = AnchorClusters.cluster(world, this);
        if (!AnchorClusters.isLead(c, this)) {
            return;
        }
        if (raidTimer < 0) {
            raidTimer = ru.death.chaosshard.ChaosConfig.order.firstRaidDelay;
            markDirty();
        }
        raidTimer = Math.max(0, raidTimer - 20);
        EntityPlayerMP o = ownerOnline();
        boolean near = false;
        if (o != null && o.world == world) {
            for (TileAnchor t : c) {
                if (o.getDistanceSq(t.pos) < 96 * 96) {
                    near = true;
                }
            }
        }
        if (raidTimer <= WARN && !raidWarned) {
            if (!near) {
                raidTimer = WARN;
                return;
            }
            raidWarned = true;
            TileAnchor target = c.get(world.rand.nextInt(c.size()));
            raidTarget = target.pos.toLong();
            raidAngle = world.rand.nextDouble() * Math.PI * 2;
            if (order) {
                ru.death.chaosshard.act5.OrderRaids.herald(o, "chaosshard.order.raid.warn");
            } else {
                ru.death.chaosshard.garrot.GarrotRaids.say(o, "chaosshard.garrot.warn");
            }
            o.sendMessage(Breach.grimoire("chaosshard.raid.warn", target.pos.getX(), target.pos.getZ(),
                    new net.minecraft.util.text.TextComponentTranslation("chaosshard.dir." + compass(raidAngle))));
            markDirty();
        }
        if (raidWarned && raidTimer > 0) {
            if (near && (raidTimer / 20) % 3 == 0) {
                BlockPos t = BlockPos.fromLong(raidTarget);
                BlockPos drum = new BlockPos(t.getX() + Math.cos(raidAngle) * 24, o.posY, t.getZ() + Math.sin(raidAngle) * 24);
                // Гаррот бьёт в барабаны, Орден звонит в колокола
                net.minecraft.util.SoundEvent beat = order ? SoundEvents.BLOCK_NOTE_BELL : SoundEvents.BLOCK_NOTE_BASEDRUM;
                world.playSound(null, drum, beat, SoundCategory.HOSTILE, 3.0F, 0.5F);
                world.playSound(null, drum, beat, SoundCategory.HOSTILE, 3.0F, 0.6F);
            }
            return;
        }
        if (raidTimer > 0) {
            return;
        }
        if (!near) {
            raidTimer = 200;
            return;
        }
        TileAnchor target = AnchorClusters.at(world, BlockPos.fromLong(raidTarget));
        if (target == null || !AnchorClusters.contains(c, target.pos)) {
            target = this;
        }
        int lvl = AnchorClusters.maxLevel(c);
        if (order) {
            ru.death.chaosshard.act5.OrderRaids.spawn((WorldServer) world, target.pos, o, lvl, raidAngle);
        } else {
            ru.death.chaosshard.garrot.GarrotRaids.spawn((WorldServer) world, target.pos, o, lvl, raidAngle);
        }
        target.siege = 0;
        // гарнизон всей сети — на помощь
        for (net.minecraft.entity.EntityLiving g : AnchorClusters.garrison((WorldServer) world, c)) {
            if (g.getDistanceSq(target.pos) > 16 * 16) {
                BlockPos at = world.getHeight(target.pos.add(world.rand.nextInt(7) - 3, 0, world.rand.nextInt(7) - 3));
                ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, g.posX, g.posY + 1, g.posZ, 20, 0.3, 0.5, 0.3, 0.5);
                g.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, g.posX, g.posY + 1, g.posZ, 20, 0.3, 0.5, 0.3, 0.05);
            }
        }
        raidWarned = false;
        raidTimer = (int) (ru.death.chaosshard.ChaosConfig.order.raidInterval * INTERVAL[Math.min(lvl, INTERVAL.length) - 1]
                * (0.75 + 0.5 * world.rand.nextDouble()));
        markDirty();
    }

    /** 0..7: север, северо-восток, восток, … — откуда идут (угол в мире: +x восток, +z юг). */
    private static int compass(double ang) {
        double deg = Math.toDegrees(Math.atan2(Math.sin(ang), Math.cos(ang)));   // 0 = восток, 90 = юг
        int idx = (int) Math.round(((deg + 90) % 360 + 360) % 360 / 45.0) % 8;    // 0 = север
        return idx;
    }

    /** Сколько армия должна простоять у Якоря, чтобы он пал: минута на 1-м уровне, +45 секунд за каждый следующий. */
    public int siegeLimit() {
        return SIEGE + 900 * (Math.max(1, level()) - 1);
    }

    /** Солдат Мрачной Армии у Якоря (раз в секунду от каждого — считается раз в секунду). */
    public void siegeTick(EntityPlayerMP warn) {
        long now = world.getTotalWorldTime();
        if (now - lastSiege < 20) {
            return;
        }
        lastSiege = now;
        int limit = siegeLimit();
        int before = siege * 5 / limit;
        siege += 20;
        if (siege * 5 / limit != before && siege < limit) {
            EntityPlayerMP o = ownerOnline();
            if (o != null) {
                o.sendStatusMessage(Breach.grimoire("chaosshard.raid.siege", siege * 100 / limit), true);
            }
        }
        if (siege >= limit) {
            siege = 0;
            fall();
        }
    }

    /** Отряд выстоял у Якоря: Якорь теряет силу (может откатиться на уровень). Армия уходит довольная. */
    private void fall() {
        int before = level();
        int loss = Math.max(150, corrupted / 5);
        corrupted = Math.max(0, corrupted - loss);
        shownLevel = level();
        trimColumn();
        changed();
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.BLOCKS, 0.8F, 0.6F);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 1, 1.5, 1, 0.02);
        EntityPlayerMP o = ownerOnline();
        if (o != null) {
            ru.death.chaosshard.garrot.GarrotRaids.say(o, "chaosshard.garrot.won");
            o.sendMessage(Breach.grimoire(level() < before ? "chaosshard.raid.lost_level" : "chaosshard.raid.lost",
                    pos.getX(), pos.getZ(), loss, level()));
        }
        ru.death.chaosshard.garrot.GarrotRaids.retreat(world);
    }

    /** Скверна напиталась (Кровавая печать и пр.). */
    public void feed(int n) {
        if (n > 0) {
            addCorrupted(n);
        }
    }

    /** Якорь, на чьей земле стоит точка (ближайший из подходящих), или null. */
    public static TileAnchor covering(net.minecraft.world.World world, BlockPos at) {
        TileAnchor best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : AnchorData.get(world).anchors) {
            if (!world.isBlockLoaded(p)) {
                continue;
            }
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileAnchor)) {
                continue;
            }
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            double d = dx * dx + dz * dz;
            int r = ((TileAnchor) te).radius();
            if (d <= (double) r * r && d < bd) {
                bd = d;
                best = (TileAnchor) te;
            }
        }
        return best;
    }

    /** Набег отбит: Якорь крепнет. */
    public void repelled(int lvl) {
        addCorrupted(50 * lvl);
    }

    /** Для отладки: набег прямо сейчас. */
    public void forceRaid(EntityPlayerMP player) {
        java.util.List<TileAnchor> c = AnchorClusters.cluster(world, this);
        ru.death.chaosshard.garrot.GarrotRaids.spawn((WorldServer) world, pos, player, AnchorClusters.maxLevel(c), world.rand.nextDouble() * Math.PI * 2);
        siege = 0;
    }

    /** Дуги между Якорями сети (главный рисует раз в 10 секунд). */
    private void clusterArcs() {
        java.util.List<TileAnchor> c = AnchorClusters.cluster(world, this);
        if (c.size() < 2 || !AnchorClusters.isLead(c, this)) {
            return;
        }
        for (int i = 0; i < c.size(); i++) {
            for (int j = i + 1; j < c.size(); j++) {
                TileAnchor a = c.get(i), b = c.get(j);
                if (!AnchorClusters.touch(a, b)) {
                    continue;
                }
                BlockPos ta = a.pos.up(Math.max(0, a.level() - 1)), tb = b.pos.up(Math.max(0, b.level() - 1));
                if (ta.distanceSq(tb) > 128 * 128) {
                    continue;
                }
                ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendToAllAround(
                        new ru.death.chaosshard.network.PacketHeartBolt(ta.getX() + 0.5, ta.getY() + 1.2, ta.getZ() + 0.5,
                                tb.getX() + 0.5, tb.getY() + 1.2, tb.getZ() + 0.5, 0x8A50D0),
                        new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(world.provider.getDimension(),
                                (ta.getX() + tb.getX()) / 2.0, ta.getY(), (ta.getZ() + tb.getZ()) / 2.0, 128));
            }
        }
    }

    private final Set<Long> dirtyChunks = new HashSet<>();

    public void setOwner(EntityPlayerMP player) {
        owner = player.getUniqueID();
        ownerName = player.getName();
        markDirty();
    }

    public int getCorrupted() {
        return corrupted;
    }

    public boolean isCaptured() {
        return captured;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Множитель восстановления энергии Хаоса для игрока в этой точке: на земле любого Якоря — energyRegenMultiplier,
     * и ещё +1 за каждую печать «Родник» на этом Якоре. Вне Якорей — 1.
     */
    public static double energyMultiplier(net.minecraft.world.World world, BlockPos at) {
        double best = 1.0;
        for (BlockPos p : AnchorData.get(world).anchors) {
            if (!world.isBlockLoaded(p)) {
                continue;
            }
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileAnchor)) {
                continue;
            }
            TileAnchor a = (TileAnchor) te;
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            int r = a.radius();
            if (dx * dx + dz * dz <= (double) r * r) {
                int size = AnchorClusters.cluster(world, a).size();
                double cluster = Math.min(1.0, 0.25 * (size - 1));
                best = Math.max(best, ChaosConfig.anchor.energyRegenMultiplier + a.seals(ItemAnchorSeal.Type.WELL) + cluster);
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ уровни

    /** Уровень (1..5) по числу осквернённых блоков. */
    public int level() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        int lvl = 1;
        for (int i = 1; i < t.length; i++) {
            if (corrupted >= t[i]) {
                lvl = i + 1;
            }
        }
        return lvl;
    }

    /** Якорь ещё растёт — руны горят (клиент знает число осквернённых блоков). */
    public boolean growing() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        return corrupted < t[t.length - 1];
    }

    /** Горят ли руны колонны над основанием base. */
    public static boolean litAt(net.minecraft.world.IBlockAccess world, BlockPos base) {
        TileEntity te = base == null ? null : world.getTileEntity(base);
        return te instanceof TileAnchor && ((TileAnchor) te).growing();
    }

    public static int maxLevel() {
        return ChaosConfig.anchor.levelThresholds.length;
    }

    /** Сколько осквернить до следующего уровня (−1 — уровень последний). */
    public int nextThreshold() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        int l = level();
        return l < t.length ? t[l] : -1;
    }

    public int radiusCap() {
        int[] r = ChaosConfig.anchor.levelRadius;
        int base = r.length == 0 ? 32 : r[Math.min(level(), r.length) - 1];
        return base + 6 * seals(ItemAnchorSeal.Type.REACH);
    }

    public int radius() {
        return Math.min(radiusCap(), ChaosConfig.anchor.startRadius + corrupted / ChaosConfig.anchor.blocksPerRadius
                + 6 * seals(ItemAnchorSeal.Type.REACH));
    }

    // ------------------------------------------------------------------ печати

    private final java.util.List<ItemAnchorSeal.Type> seals = new java.util.ArrayList<>();

    public java.util.List<ItemAnchorSeal.Type> getSeals() {
        return seals;
    }

    public int seals(ItemAnchorSeal.Type type) {
        int n = 0;
        for (ItemAnchorSeal.Type t : seals) {
            if (t == type) {
                n++;
            }
        }
        return n;
    }

    /** Навесить печать; false — нет места (печатей не больше уровня). */
    public boolean addSeal(ItemAnchorSeal.Type type) {
        if (seals.size() >= level()) {
            return false;
        }
        seals.add(type);
        changed();
        return true;
    }

    public ItemAnchorSeal.Type removeSeal() {
        if (seals.isEmpty()) {
            return null;
        }
        ItemAnchorSeal.Type t = seals.remove(seals.size() - 1);
        changed();
        return t;
    }

    private int shownLevel = 0;

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    /** Колонна: основание + (уровень − 1) сегментов, верхний — остриё. Препятствия (кроме мягких блоков) останавливают рост. */
    public void growColumn() {
        int lvl = level();
        net.minecraft.block.state.IBlockState base = world.getBlockState(pos);
        if (base.getBlock() instanceof BlockAnchor) {
            boolean grown = lvl >= 2;
            if (base.getValue(BlockAnchor.GROWN) != grown) {
                world.setBlockState(pos, base.withProperty(BlockAnchor.GROWN, grown), 2);
            }
        }
        int height = lvl - 1;
        for (int i = 1; i <= height; i++) {
            BlockPos p = pos.up(i);
            boolean top = i == height;
            net.minecraft.block.state.IBlockState want = ru.death.chaosshard.init.ModBlocks.ANCHOR_PART.getDefaultState()
                    .withProperty(BlockAnchorPart.TOP, top);
            net.minecraft.block.state.IBlockState have = world.getBlockState(p);
            if (have == want) {
                continue;
            }
            boolean ours = have.getBlock() instanceof BlockAnchorPart;
            boolean soft = world.isAirBlock(p) || have.getBlock().isReplaceable(world, p) || have.getMaterial() == net.minecraft.block.material.Material.LEAVES
                    || have.getMaterial() == net.minecraft.block.material.Material.PLANTS || have.getMaterial() == net.minecraft.block.material.Material.VINE;
            if (!ours && !soft) {
                break;
            }
            world.setBlockState(p, want, 3);
            if (!ours) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        20, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** Убрать сегменты колонны выше уровня (после поражения в набеге). */
    private void trimColumn() {
        int height = level() - 1;
        for (int i = height + 1; i <= maxLevel() + 1; i++) {
            BlockPos p = pos.up(i);
            if (world.getBlockState(p).getBlock() instanceof BlockAnchorPart) {
                world.setBlockToAir(p);
            }
        }
        if (height >= 1) {
            BlockPos top = pos.up(height);
            if (world.getBlockState(top).getBlock() instanceof BlockAnchorPart) {
                world.setBlockState(top, ru.death.chaosshard.init.ModBlocks.ANCHOR_PART.getDefaultState().withProperty(BlockAnchorPart.TOP, true), 3);
            }
        }
        net.minecraft.block.state.IBlockState base = world.getBlockState(pos);
        if (base.getBlock() instanceof BlockAnchor && level() < 2 && base.getValue(BlockAnchor.GROWN)) {
            world.setBlockState(pos, base.withProperty(BlockAnchor.GROWN, false), 2);
        }
    }

    private void onLevelUp(int lvl) {
        int[] rw = ChaosConfig.anchor.levelReward;
        int reward = lvl - 1 < rw.length ? rw[lvl - 1] : 0;
        EntityPlayerMP p = ownerOnline();
        if (lvl == 2) {
            checkCapture();
        } else if (p != null) {
            if (reward > 0) {
                ThreadManager.addPoints(p, reward);
            }
            p.sendMessage(Breach.grimoire("chaosshard.anchor.level_up", pos.getX(), pos.getZ(), lvl, radiusCap(), reward));
            if (lvl >= maxLevel()) {
                ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_MAX);
            }
        }
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.5F, 0.6F + 0.1F * lvl);
        growColumn();
        BlockPos tip = pos.up(Math.max(0, lvl - 1));
        ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendToAllAround(
                new ru.death.chaosshard.network.PacketHeartBolt(tip.getX() + 0.5, tip.getY() + 0.9, tip.getZ() + 0.5,
                        tip.getX() + 0.5, tip.getY() + 8, tip.getZ() + 0.5, 0xB070FF),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 96));
        changed();
    }

    /** Путевой камень уже вырос (Waystones). */
    private boolean waystone;

    /** На нужном уровне Якорь выращивает рядом путевой камень Waystones. */
    public boolean growWaystone() {
        if (waystone || ChaosConfig.compat.anchorWaystoneLevel <= 0 || level() < ChaosConfig.compat.anchorWaystoneLevel || !ru.death.chaosshard.compat.life.ChaosWaystones.available()) {
            return false;
        }
        waystone = true;
        markDirty();
        if (ru.death.chaosshard.compat.life.ChaosWaystones.nearby(world, pos, 8)) {
            return false;
        }
        boolean ok = ru.death.chaosshard.compat.life.ChaosWaystones.place(world, pos, "Якорь " + pos.getX() + " " + pos.getZ(), 3, 7);
        EntityPlayerMP o = ownerOnline();
        if (ok && o != null) {
            o.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.anchor.waystone"));
            ru.death.chaosshard.quest.Milestones.grant(o, ru.death.chaosshard.quest.Milestones.ANCHOR_WAYSTONE);
        }
        return ok;
    }

    /** Гул чужих машин рядом: каждая работающая — лишние попытки Скверны. Не сохраняется. */
    public final ru.death.chaosshard.tech.MachineHum hum = new ru.death.chaosshard.tech.MachineHum();

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (world.getTotalWorldTime() % ChaosConfig.tech.humInterval == (pos.toLong() & 31)) {
            int was = hum.working();
            if (hum.sample(world, pos) > 0 && was == 0) {
                EntityPlayerMP o = ownerOnline();
                if (o != null) {
                    ru.death.chaosshard.quest.Milestones.grant(o, ru.death.chaosshard.quest.Milestones.ANCHOR_HUM);
                }
            }
        }
        if (++timer >= ChaosConfig.anchor.spreadInterval) {
            timer = 0;
            int done = 0;
            int attempts = (int) Math.round((ChaosConfig.anchor.attemptsPerSpread + (level() - 1) * ChaosConfig.anchor.attemptsPerLevel)
                    * (1 + 0.5 * seals(ItemAnchorSeal.Type.FURY))) + hum.working() * ChaosConfig.tech.humAttempts;
            for (int i = 0; i < attempts; i++) {
                if (spreadOnce(world.rand)) {
                    done++;
                }
            }
            if (done > 0) {
                addCorrupted(done);
            }
        }
        long wt = world.getTotalWorldTime();
        if (wt % 200 == 100) {
            growColumn();
            growWaystone();
        }
        if (wt % 200 == 150 && captured) {
            clusterArcs();
        }
        if (timer % 20 == 5 && captured) {
            tickArmy();
        }
        if (timer == 11 && world.getTotalWorldTime() % 200 < 40) {
            EntityPlayerMP o = ownerOnline();
            ru.death.chaosshard.village.VillageFate.checkAnchor(world, pos, radius(), o);
        }
        if (captured && !counted && timer % 20 == 0) {
            EntityPlayerMP owner = ownerOnline();
            if (owner != null) {
                ru.death.chaosshard.quest.Milestones.onCapture(owner);
                counted = true;
                markDirty();
            }
        }
        if (wt % 200 == 0 && !dirtyChunks.isEmpty()) {
            resendChunks();
        }
        if (captured && ChaosConfig.anchor.passiveThread > 0
                && (passiveTimer += 1 + seals(ItemAnchorSeal.Type.HARVEST)) >= ChaosConfig.anchor.passiveInterval) {
            passiveTimer = 0;
            EntityPlayerMP p = ownerOnline();
            if (p != null) {
                ThreadManager.addPoints(p, ChaosConfig.anchor.passiveThread);
            }
        }
    }

    /** Для отладки: n попыток подряд. */
    public int forceSpread(int n) {
        int done = 0;
        for (int i = 0; i < n; i++) {
            if (spreadOnce(world.rand)) {
                done++;
            }
        }
        addCorrupted(done);
        resendChunks();
        return done;
    }

    private void addCorrupted(int n) {
        int before = level();
        corrupted += n;
        markDirty();
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l);
        }
        if (after != shownLevel) {
            shownLevel = after;
            changed();
        }
    }

    /** Для отладки: сразу выставить число осквернённых блоков (уровни засчитываются). */
    public void setCorrupted(int n) {
        int before = level();
        corrupted = Math.max(0, n);
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l);
        }
        growColumn();
        changed();
    }

    /** Одна попытка: поверхность случайного столбца в круге либо случайный блок в объёме (деревья, камень). */
    public boolean spreadOnce(Random rand) {
        int r = radius();
        double ang = rand.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(rand.nextDouble()) * r;
        int x = pos.getX() + (int) Math.round(Math.cos(ang) * dist);
        int z = pos.getZ() + (int) Math.round(Math.sin(ang) * dist);
        if (!world.isBlockLoaded(new BlockPos(x, 0, z))) {
            return false;
        }
        // Святая земля и деревни под покровительством Скверне не по зубам
        if (ru.death.chaosshard.order.Shrines.protects(world, x, z)) {
            return false;
        }
        BlockPos sigil = ru.death.chaosshard.village.VillageFate.protector(world, x, z);
        if (sigil != null) {
            ru.death.chaosshard.village.VillageFate.repelled(world, sigil);
            return false;
        }
        boolean changed = false;
        if (rand.nextBoolean()) {
            BlockPos top = world.getHeight(new BlockPos(x, 0, z)).down();
            // Скверна будит здешнюю тьму: стоячая вода темнеет в кровь (EvilCraft)
            if (DarkCorruption.waterToBlood(world, top, rand)) {
                return true;
            }
            for (int d = 0; d < 3; d++) {
                changed |= Corruption.corrupt(world, top.down(d));
            }
            // растения сверху
            changed |= Corruption.corrupt(world, top.up());
            if (changed && Corruption.corruptBiome(world, top)) {
                dirtyChunks.add(ChunkPos.asLong(x >> 4, z >> 4));
            }
            if (changed) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, x + 0.5, top.getY() + 1.2, z + 0.5,
                        4, 0.3, 0.2, 0.3, 0.01);
            }
        } else {
            int y = pos.getY() - 6 + rand.nextInt(18);
            BlockPos deep = new BlockPos(x, y, z);
            // в осквернённом камне вызревает тёмная руда (EvilCraft)
            changed = DarkCorruption.darkOre(world, deep, rand) || Corruption.corrupt(world, deep);
        }
        return changed;
    }

    private void checkCapture() {
        if (captured || level() < 2) {
            return;
        }
        captured = true;
        markDirty();
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.6F, 0.7F);
        EntityPlayerMP p = ownerOnline();
        if (p != null) {
            int reward = ChaosConfig.anchor.levelReward.length > 1 ? ChaosConfig.anchor.levelReward[1] : 0;
            ThreadManager.addPoints(p, reward);
            p.sendMessage(Breach.grimoire("chaosshard.anchor.captured", pos.getX(), pos.getZ(), reward));
        }
    }

    private EntityPlayerMP ownerOnline() {
        if (owner == null || world.getMinecraftServer() == null) {
            return null;
        }
        return ru.death.chaosshard.debug.TestHooks.player(world.getMinecraftServer(), owner);
    }

    /** Отправить игрокам новые биомы, чтобы цвет травы и неба обновился. */
    private void resendChunks() {
        Corruption.resendChunks((WorldServer) world, dirtyChunks);
        dirtyChunks.clear();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (owner != null) {
            tag.setUniqueId("owner", owner);
        }
        tag.setString("ownerName", ownerName);
        tag.setInteger("corrupted", corrupted);
        tag.setBoolean("captured", captured);
        tag.setBoolean("waystone", waystone);
        tag.setInteger("passive", passiveTimer);
        tag.setBoolean("counted", counted);
        tag.setInteger("raidTimer", raidTimer);
        tag.setBoolean("raidWarned", raidWarned);
        tag.setDouble("raidAngle", raidAngle);
        tag.setLong("raidTarget", raidTarget);
        tag.setInteger("siege", siege);
        writeSeals(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
        ownerName = tag.getString("ownerName");
        corrupted = tag.getInteger("corrupted");
        captured = tag.getBoolean("captured");
        waystone = tag.getBoolean("waystone");
        passiveTimer = tag.getInteger("passive");
        counted = tag.getBoolean("counted");
        raidTimer = tag.hasKey("raidTimer") ? tag.getInteger("raidTimer") : -1;
        raidWarned = tag.getBoolean("raidWarned");
        raidAngle = tag.getDouble("raidAngle");
        raidTarget = tag.getLong("raidTarget");
        siege = tag.getInteger("siege");
        readSeals(tag);
    }

    private void writeSeals(NBTTagCompound tag) {
        int[] arr = new int[seals.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = seals.get(i).ordinal();
        }
        tag.setIntArray("seals", arr);
    }

    private void readSeals(NBTTagCompound tag) {
        seals.clear();
        for (int o : tag.getIntArray("seals")) {
            if (o >= 0 && o < ItemAnchorSeal.Type.values().length) {
                seals.add(ItemAnchorSeal.Type.values()[o]);
            }
        }
    }

    // ------------------------------------------------------------------ клиент: уровень и печати для отрисовки

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("corrupted", corrupted);
        writeSeals(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        corrupted = tag.getInteger("corrupted");
        readSeals(tag);
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos.add(-2, 0, -2), pos.add(3, 7, 3));
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
