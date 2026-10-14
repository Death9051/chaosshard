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
    /** Опыт Якоря (осквернение, пассивный прирост, кровь, перелив). */
    private int corrupted;
    /** Сколько блоков земли реально осквернено — от этого зависит захват края. */
    private int land;
    private boolean captured;
    private double xpCarry;
    private double bloodCarry;
    private int passiveXpTimer;

    // ------------------------------------------------------------------ специализация
    private int spec = -1;
    /** Кэш сетевых сил специализаций (обновляется раз в секунду): [спец] и [спец + ступень]. */
    private final double[] netF = new double[AnchorSpec.values().length];
    private final double[] netS = new double[AnchorSpec.values().length];
    /** Хранилище Жилы и Жатвы. */
    public final net.minecraftforge.items.ItemStackHandler storage = new net.minecraftforge.items.ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };
    /** Образец моба для поля Жатвы (2-я ступень). */
    public final net.minecraftforge.items.ItemStackHandler sample = new net.minecraftforge.items.ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, net.minecraft.item.ItemStack stack) {
            return stack.getItem() instanceof net.minecraft.item.ItemMonsterPlacer;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };
    /** Опыт мобов, павших на поле Жатвы. */
    public int fieldXp;
    /** Запас энергии Горна (RF). */
    public int forgeEnergy;
    int specTimer;
    int veinTimer;

    public AnchorSpec getSpec() {
        return AnchorSpec.of(spec);
    }

    // ------------------------------------------------------------------ Альфа
    public static final int ALPHA_MAX_LEVEL = 10;
    /** Опыт Альфы для уровней 2..10 и потолок (последний). */
    public static final int[] ALPHA_T = {1000, 2500, 4500, 7000, 10000, 14000, 19000, 25000, 32000, 40000};
    private boolean alpha;
    private int alphaXp;
    private double alphaCarry;
    /** Кэш: уровень старшей бодрствующей Альфы сети, сколько Альф бодрствует, бодрствует ли эта. */
    private int netAlpha;
    private int netAlphas;
    private boolean alphaAwake;
    /** Кэш: сила печатей Альф сети по видам. */
    private final double[] alphaSealF = new double[ItemAnchorSeal.Type.values().length];
    /** Заимствованные эффекты (AnchorFx), −1 — пусто. */
    private int[] borrow = new int[0];
    /** На клиенте: открытых мест под печати и уровень Альфы. */
    private int clientSlots = -1;
    private int clientAlpha;

    public boolean isAlpha() {
        return alpha;
    }

    public int getAlphaXp() {
        return alphaXp;
    }

    public int alphaLevel() {
        if (!alpha) {
            return 0;
        }
        if (world != null && world.isRemote) {
            return Math.max(1, clientAlpha);
        }
        int l = 1;
        for (int i = 0; i < ALPHA_MAX_LEVEL - 1; i++) {
            if (alphaXp >= ALPHA_T[i]) {
                l = i + 2;
            }
        }
        return l;
    }

    /** Альфа бодрствует (одна на каждые 10 Якорей сети). */
    public boolean alphaAwake() {
        return alphaAwake;
    }

    public int netAlpha() {
        return netAlpha;
    }

    /** Сколько заимствований может взять Якорь: по числу бодрствующих Альф. */
    public int borrowSlots() {
        return alpha ? 0 : netAlphas;
    }

    public int[] getBorrow() {
        return borrow.clone();
    }

    /** Есть ли у Якоря эффект: своя специализация или заимствованный. */
    public boolean hasFx(AnchorFx fx) {
        if (AnchorFx.own(getSpec(), fx)) {
            return true;
        }
        int n = Math.min(borrow.length, borrowSlots());
        for (int i = 0; i < n; i++) {
            if (borrow[i] == fx.ordinal()) {
                return true;
            }
        }
        return false;
    }

    /** Назначить заимствование в место slot (−1 — очистить). Проверка числа доноров. */
    public boolean setBorrow(int slot, AnchorFx fx) {
        if (slot < 0 || slot >= borrowSlots() || alpha) {
            return false;
        }
        if (fx != null) {
            if (fx.spec == getSpec()) {
                return false;
            }
            int donors = AnchorNet.count(world, owner, fx.spec, false);
            int used = AnchorNet.borrowers(world, owner, fx.spec, pos);
            for (int i = 0; i < borrow.length; i++) {
                AnchorFx b = AnchorFx.of(borrow[i]);
                if (i != slot && b != null && b.spec == fx.spec) {
                    used++;
                }
            }
            if (used >= donors) {
                return false;
            }
        }
        if (borrow.length < borrowSlots()) {
            int[] n = new int[borrowSlots()];
            java.util.Arrays.fill(n, -1);
            System.arraycopy(borrow, 0, n, 0, borrow.length);
            borrow = n;
        }
        borrow[slot] = fx == null ? -1 : fx.ordinal();
        markDirty();
        syncRec();
        return true;
    }

    /** Доноров стало меньше, чем заимствований, — лишние сбрасываются (сначала у Якорей с большими координатами). */
    private void checkBorrow() {
        boolean changedB = false;
        for (int i = 0; i < borrow.length; i++) {
            AnchorFx fx = AnchorFx.of(borrow[i]);
            if (fx == null) {
                continue;
            }
            boolean drop = i >= netAlphas || fx.spec == getSpec() || alpha;
            if (!drop) {
                int donors = AnchorNet.count(world, owner, fx.spec, false);
                // место в очереди: заимствования этой специализации у Якорей «раньше» нас
                int before = 0;
                AnchorData d = AnchorData.get(world);
                for (BlockPos p : AnchorNet.members(world, owner)) {
                    if (p.toLong() >= pos.toLong()) {
                        continue;
                    }
                    for (int b : d.rec(p).borrow) {
                        AnchorFx o = AnchorFx.of(b);
                        if (o != null && o.spec == fx.spec) {
                            before++;
                        }
                    }
                }
                for (int j = 0; j < i; j++) {
                    AnchorFx o = AnchorFx.of(borrow[j]);
                    if (o != null && o.spec == fx.spec) {
                        before++;
                    }
                }
                drop = before >= donors;
            }
            if (drop) {
                borrow[i] = -1;
                changedB = true;
            }
        }
        if (changedB) {
            markDirty();
            syncRec();
        }
    }

    /** Высота колонны над основанием. */
    public int columnHeight() {
        return alpha ? 4 + alphaLevel() * 7 / 10 : level() - 1;
    }

    /**
     * Эволюция в Альфу (ритуал): Якорь 5-го уровня, площадка 3×3 вокруг основания свободна и стоит на твёрдом,
     * Альф в сети меньше, чем Якорей / 10. Возвращает ключ ошибки или null.
     */
    public String evolve() {
        if (alpha) {
            return "chaosshard.anchor.alpha.already";
        }
        if (level() < maxLevel()) {
            return "chaosshard.anchor.alpha.level";
        }
        if (AnchorNet.isNetWorld(world) && owner != null
                && AnchorNet.alphas(world, owner).size() >= AnchorNet.alphaAllowed(world, owner)) {
            return "chaosshard.anchor.alpha.count";
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos p = pos.add(dx, 0, dz);
                net.minecraft.block.state.IBlockState st = world.getBlockState(p);
                if (!(st.getBlock() instanceof BlockAnchorRim) && !world.isAirBlock(p) && !st.getBlock().isReplaceable(world, p)
                        || !world.getBlockState(p.down()).isSideSolid(world, p.down(), net.minecraft.util.EnumFacing.UP)) {
                    return "chaosshard.anchor.alpha.space";
                }
            }
        }
        if (spec >= 0) {
            AnchorSpec was = getSpec();
            spec = -1;
            dropStorage();
            if (was == AnchorSpec.NEST) {
                AnchorSpecs.dismissNest(this);
            }
        }
        borrow = new int[0];
        alpha = true;
        alphaXp = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    world.setBlockState(pos.add(dx, 0, dz), ru.death.chaosshard.init.ModBlocks.ANCHOR_RIM.getDefaultState(), 3);
                }
            }
        }
        growColumn();
        syncRec();
        refreshNet();
        changed();
        world.playSound(null, pos, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.BLOCKS, 0.7F, 0.8F);
        ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendToAllAround(
                new ru.death.chaosshard.network.PacketHeartBolt(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        pos.getX() + 0.5, pos.getY() + 24, pos.getZ() + 0.5, 0xE0A0FF),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 128));
        return null;
    }

    private void dropStorage() {
        for (int i = 0; i < storage.getSlots(); i++) {
            net.minecraft.item.ItemStack st = storage.getStackInSlot(i);
            if (!st.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, st.copy());
                storage.setStackInSlot(i, net.minecraft.item.ItemStack.EMPTY);
            }
        }
        if (!sample.getStackInSlot(0).isEmpty()) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, sample.getStackInSlot(0).copy());
            sample.setStackInSlot(0, net.minecraft.item.ItemStack.EMPTY);
        }
    }

    /** Опыт Альфы: свой — на четверть, перелив и кровь — полностью. */
    private void addAlphaXp(int n, boolean full) {
        double add = (full ? n : n * 0.25) + alphaCarry;
        int whole = (int) Math.floor(add);
        alphaCarry = add - whole;
        if (whole <= 0) {
            return;
        }
        int before = alphaLevel();
        alphaXp += whole;
        int max = ALPHA_T[ALPHA_T.length - 1];
        if (alphaXp > max) {
            int surplus = alphaXp - max;
            alphaXp = max;
            AnchorNet.overflow(world, owner, pos, surplus);
        }
        markDirty();
        int after = alphaLevel();
        if (after != before) {
            EntityPlayerMP p = ownerOnline();
            for (int l = before + 1; l <= after; l++) {
                if (p != null) {
                    ThreadManager.addPoints(p, 100);
                    p.sendMessage(Breach.grimoire("chaosshard.anchor.alpha.level_up", pos.getX(), pos.getZ(), l));
                    if (l >= ALPHA_MAX_LEVEL) {
                        ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_ALPHA_MAX);
                    }
                }
            }
            growColumn();
            changed();
        }
        syncRec();
    }

    /** Сетевая сила специализации у сети этого Якоря (кэш). */
    public double net(AnchorSpec s) {
        return netF[s.ordinal()];
    }

    public double netStage(AnchorSpec s) {
        return netS[s.ordinal()];
    }

    /** Вторая ступень своей специализации открыта. */
    public boolean stage() {
        return spec >= 0 && level() >= AnchorSpec.STAGE_LEVEL;
    }

    /** Выбрать специализацию (окно). */
    public boolean chooseSpec(AnchorSpec s) {
        if (s == null || spec >= 0 || alpha || level() < AnchorSpec.CHOOSE_LEVEL) {
            return false;
        }
        spec = s.ordinal();
        syncRec();
        refreshNet();
        changed();
        EntityPlayerMP p = ownerOnline();
        if (p != null && level() >= AnchorSpec.STAGE_LEVEL) {
            ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_STAGE);
        }
        return true;
    }

    /** Сбросить специализацию (ритуал «Перекройка»): −25% опыта, хранилище высыпается. */
    public boolean resetSpec() {
        if (spec < 0) {
            return false;
        }
        AnchorSpec was = getSpec();
        spec = -1;
        corrupted = (int) (corrupted * 0.75);
        shownLevel = level();
        trimColumn();
        dropStorage();
        if (was == AnchorSpec.NEST) {
            AnchorSpecs.dismissNest(this);
        }
        syncRec();
        refreshNet();
        changed();
        return true;
    }

    /** Обновить кэш сетевых сил. */
    public void refreshNet() {
        boolean net = AnchorNet.isNetWorld(world) && owner != null;
        netAlpha = net ? AnchorNet.alphaLevel(world, owner) : 0;
        java.util.List<BlockPos> awake = net ? AnchorNet.awakeAlphas(world, owner) : java.util.Collections.emptyList();
        netAlphas = awake.size();
        alphaAwake = alpha && (!net || awake.contains(pos));
        for (ItemAnchorSeal.Type t : ItemAnchorSeal.Type.values()) {
            alphaSealF[t.ordinal()] = net ? AnchorNet.alphaSeals(world, owner, t.ordinal(), pos) : 0;
        }
        if (net) {
            checkBorrow();
        }
        for (AnchorSpec s : AnchorSpec.values()) {
            if (AnchorNet.isNetWorld(world) && owner != null) {
                netF[s.ordinal()] = AnchorNet.factor(world, owner, s, false);
                netS[s.ordinal()] = AnchorNet.factor(world, owner, s, true);
            } else {
                // вне сети работает только своя специализация
                netF[s.ordinal()] = spec == s.ordinal() ? 1 : 0;
                netS[s.ordinal()] = spec == s.ordinal() && level() >= AnchorSpec.STAGE_LEVEL ? 1 : 0;
            }
        }
    }
    /** Бак крови: кровь постепенно перерабатывается в опыт. */
    public final net.minecraftforge.fluids.FluidTank blood = new net.minecraftforge.fluids.FluidTank(ChaosConfig.anchor.bloodTank) {
        @Override
        public boolean canFillFluidType(net.minecraftforge.fluids.FluidStack fluid) {
            return isBlood(fluid);
        }

        @Override
        protected void onContentsChanged() {
            markDirty();
        }
    };

    {
        blood.setCanDrain(false);
    }

    public static boolean isBlood(net.minecraftforge.fluids.FluidStack f) {
        return f != null && f.getFluid() != null && f.getFluid().getName().toLowerCase(java.util.Locale.ROOT).contains("blood");
    }
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
        int warn = net(AnchorSpec.WATCH) > 0 ? WARN * 3 : WARN;
        if (raidTimer <= warn && !raidWarned) {
            if (!near) {
                raidTimer = warn;
                return;
            }
            raidWarned = true;
            TileAnchor target = nearestTo(c, o);
            raidTarget = target.pos.toLong();
            raidAngle = world.rand.nextDouble() * Math.PI * 2;
            if (order) {
                ru.death.chaosshard.act5.OrderRaids.herald(o, "chaosshard.order.raid.warn");
            } else {
                ru.death.chaosshard.garrot.GarrotRaids.say(o, "chaosshard.garrot.warn");
            }
            o.sendMessage(Breach.grimoire("chaosshard.raid.warn", target.pos.getX(), target.pos.getZ(),
                    new net.minecraft.util.text.TextComponentTranslation("chaosshard.dir." + compass(raidAngle))));
            if (net(AnchorSpec.WATCH) > 0) {
                int lvl = AnchorClusters.maxLevel(c);
                o.sendMessage(Breach.grimoire("chaosshard.anchor.spec.watch.raid", lvl, lvl >= 4 ? 2 : 1, warn / 1200));
            }
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
        return (int) ((SIEGE + 900 * (Math.max(1, level()) - 1)) * (1 + 0.25 * net(AnchorSpec.BASTION)));
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
        double frac = 0.2 - 0.1 * Math.min(1.0, net(AnchorSpec.BASTION));
        int loss = Math.max(150, (int) (corrupted * frac));
        corrupted = Math.max(0, corrupted - loss);
        shownLevel = level();
        syncRec();
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
        addXp(n, false);
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
        addXp(50 * lvl, false);
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
                int size = AnchorNet.size(world, a.getOwner());
                double m = AnchorNet.landMultiplier(size, a.netAlpha) + a.power(ItemAnchorSeal.Type.WELL);
                if (a.hasFx(AnchorFx.PULSE_REGEN)) {
                    m *= 1.5;   // Пульс: на своей земле энергия в полтора раза быстрее
                }
                best = Math.max(best, m);
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
        return base + (int) Math.round(6 * power(ItemAnchorSeal.Type.REACH) + 6 * netStage(AnchorSpec.ROOTS));
    }

    public int radius() {
        return Math.min(radiusCap(), ChaosConfig.anchor.startRadius + corrupted / ChaosConfig.anchor.blocksPerRadius
                + (int) Math.round(6 * power(ItemAnchorSeal.Type.REACH) + 6 * netStage(AnchorSpec.ROOTS)));
    }

    // ------------------------------------------------------------------ печати

    /** Всего мест под печати (открыты — по уровню). */
    public static final int MAX_SEALS = 12;

    /** Печати Якоря: ставятся и снимаются в окне. Работают только в открытых местах. */
    public final net.minecraftforge.items.ItemStackHandler sealInv = new net.minecraftforge.items.ItemStackHandler(MAX_SEALS) {
        @Override
        public boolean isItemValid(int slot, net.minecraft.item.ItemStack stack) {
            if (!(stack.getItem() instanceof ItemAnchorSeal) || slot >= sealSlots()) {
                return false;
            }
            ItemAnchorSeal.Type t = ((ItemAnchorSeal) stack.getItem()).type;
            if (t.sign) {
                // знак — не больше одного на Якорь
                for (int i = 0; i < getSlots(); i++) {
                    net.minecraft.item.ItemStack o = getStackInSlot(i);
                    if (i != slot && o.getItem() instanceof ItemAnchorSeal && ((ItemAnchorSeal) o.getItem()).type == t) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
            net.minecraft.item.ItemStack st = getStackInSlot(slot);
            if (world != null && !world.isRemote && st.getItem() instanceof ItemAnchorSeal && ((ItemAnchorSeal) st.getItem()).type.sign) {
                EntityPlayerMP p = ownerOnline();
                if (p != null) {
                    ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_SIGN);
                }
            }
        }
    };

    /** Открытых мест под печати: по уровню. */
    public int sealSlots() {
        if (world != null && world.isRemote && clientSlots >= 0) {
            return clientSlots;
        }
        int base = alpha ? 5 + (alphaLevel() + 1) / 2 : level();
        return Math.min(MAX_SEALS, base + (netAlpha >= 4 ? 1 : 0));
    }

    public java.util.List<ItemAnchorSeal.Type> getSeals() {
        java.util.List<ItemAnchorSeal.Type> out = new java.util.ArrayList<>();
        int open = sealSlots();
        for (int i = 0; i < open; i++) {
            net.minecraft.item.ItemStack st = sealInv.getStackInSlot(i);
            if (st.getItem() instanceof ItemAnchorSeal) {
                out.add(((ItemAnchorSeal) st.getItem()).type);
            }
        }
        return out;
    }

    /** Сколько печатей этого вида стоит в открытых местах. */
    public int seals(ItemAnchorSeal.Type type) {
        int n = 0;
        for (ItemAnchorSeal.Type t : getSeals()) {
            if (t == type) {
                n++;
            }
        }
        return n;
    }

    /**
     * Сила печати: силовые одинаковые складываются по убывающей; знак — 0 или 1.
     * Плюс печати Альф сети (10% × уровень Альфы за печать).
     */
    public double power(ItemAnchorSeal.Type type) {
        int n = seals(type);
        double alphaF = alphaSealF[type.ordinal()];
        if (type.sign) {
            return Math.max(n > 0 ? 1.0 : 0.0, Math.min(1.0, alphaF));
        }
        return AnchorNet.stack(n, netAlpha >= 8) + alphaF;
    }

    /** Навесить печать в первое свободное открытое место; false — места нет. */
    public boolean addSeal(ItemAnchorSeal.Type type) {
        int open = sealSlots();
        net.minecraft.item.ItemStack st = new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.SEALS.get(type));
        for (int i = 0; i < open; i++) {
            if (sealInv.getStackInSlot(i).isEmpty() && sealInv.isItemValid(i, st)) {
                sealInv.setStackInSlot(i, st);
                return true;
            }
        }
        return false;
    }

    public ItemAnchorSeal.Type removeSeal() {
        for (int i = MAX_SEALS - 1; i >= 0; i--) {
            net.minecraft.item.ItemStack st = sealInv.getStackInSlot(i);
            if (st.getItem() instanceof ItemAnchorSeal) {
                sealInv.setStackInSlot(i, net.minecraft.item.ItemStack.EMPTY);
                return ((ItemAnchorSeal) st.getItem()).type;
            }
        }
        return null;
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
        int height = columnHeight();
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
        int height = columnHeight();
        for (int i = height + 1; i <= 16; i++) {
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

    /** Уровень вырос; transfer — опытом из сети (перелив/запас): тогда Нити вполовину. */
    private void onLevelUp(int lvl, boolean transfer) {
        int[] rw = ChaosConfig.anchor.levelReward;
        // Нить за 2-й уровень выдаётся при захвате края — по реально осквернённой земле
        int reward = lvl >= 3 && lvl - 1 < rw.length ? rw[lvl - 1] : 0;
        if (transfer) {
            reward /= 2;
        }
        EntityPlayerMP p = ownerOnline();
        if (p != null) {
            if (reward > 0) {
                ThreadManager.addPoints(p, reward);
            }
            p.sendMessage(Breach.grimoire(reward > 0 ? "chaosshard.anchor.level_up" : "chaosshard.anchor.level_up_quiet",
                    pos.getX(), pos.getZ(), lvl, radiusCap(), reward));
            if (lvl >= maxLevel()) {
                ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_MAX);
            }
            if (lvl >= AnchorSpec.STAGE_LEVEL && spec >= 0) {
                ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_STAGE);
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
                    * (1 + 0.5 * power(ItemAnchorSeal.Type.FURY)) * (hasFx(AnchorFx.ROOTS_FURY) ? 1.5 : 1.0))
                    + (int) Math.round(hum.working() * ChaosConfig.tech.humAttempts * (1 + net(AnchorSpec.FORGE)));
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
        if (wt % 20 == (pos.toLong() & 15)) {
            tickNet();
        }
        AnchorSpecs.tick(this);
        AnchorSigns.tick(this);
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
                && (passiveTimer += 1 + (int) Math.round(power(ItemAnchorSeal.Type.HARVEST))) >= ChaosConfig.anchor.passiveInterval) {
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

    /** Скверна осквернила n блоков: и земля, и опыт. */
    private void addCorrupted(int n) {
        land += n;
        addXp(n, false);
        checkCapture();
    }

    public int getLand() {
        return land;
    }

    public static int maxXp() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        return t[t.length - 1];
    }

    /** Добавить опыт. transfer — пришёл из сети. Излишек сверх 5-го уровня уходит в сеть. */
    public void addXp(int n, boolean transfer) {
        addXp(n, transfer, false);
    }

    /** full — для Альфы засчитать полностью (кровь); перелив (transfer) засчитывается полностью всегда. */
    public void addXp(int n, boolean transfer, boolean full) {
        if (n <= 0 || world == null || world.isRemote) {
            return;
        }
        if (alpha) {
            addAlphaXp(n, transfer || full);
            return;
        }
        int before = level();
        corrupted += n;
        int max = maxXp();
        if (corrupted > max) {
            int surplus = corrupted - max;
            corrupted = max;
            AnchorNet.overflow(world, owner, pos, surplus);
        }
        markDirty();
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l, transfer);
        }
        if (after != shownLevel) {
            shownLevel = after;
            changed();
        }
        syncRec();
    }

    /** Раз в секунду: перелив из сети, запас, кровь, пассивный прирост, сводка для сети. */
    private void tickNet() {
        refreshNet();
        if (AnchorNet.isNetWorld(world) && owner != null) {
            AnchorData d = AnchorData.get(world);
            AnchorData.Rec r = d.rec(pos);
            if (r != null && r.inbox > 0) {
                int in = r.inbox;
                r.inbox = 0;
                d.markDirty();
                addXp(in, true);
            }
            int need = alpha ? ALPHA_T[ALPHA_T.length - 1] - alphaXp : maxXp() - corrupted;
            if (need > 0) {
                int got = AnchorNet.takeReserve(world, owner, need);
                if (got > 0) {
                    addXp(got, true);
                }
            }
        }
        // кровь: ведро за bloodTicksPerBucket, Кровавые печати ускоряют
        int amount = blood.getFluidAmount();
        if (amount > 0) {
            double speed = 1.0 + ChaosConfig.anchor.bloodSealSpeed * power(ItemAnchorSeal.Type.BLOOD);
            double want = 1000.0 * 20 / Math.max(1, ChaosConfig.anchor.bloodTicksPerBucket) * speed + bloodCarry;
            int mb = (int) Math.min(amount, Math.floor(want));
            bloodCarry = mb < amount ? want - Math.floor(want) : 0;
            if (mb > 0) {
                blood.drainInternal(mb, true);
                xpCarry += mb * ChaosConfig.anchor.bloodXpPerBucket / 1000.0;
                int x = (int) Math.floor(xpCarry);
                xpCarry -= x;
                addXp(x, false, true);
            }
        }
        if (captured && ChaosConfig.anchor.passiveXpPerLevel > 0) {
            passiveXpTimer += 20;
            if (passiveXpTimer >= ChaosConfig.anchor.passiveXpInterval) {
                passiveXpTimer = 0;
                double alphaBoost = netAlpha >= 6 ? 1.5 : 1.0;
                addXp((int) Math.round((level() + alphaLevel()) * ChaosConfig.anchor.passiveXpPerLevel
                        * (1 + 0.5 * net(AnchorSpec.ROOTS)) * alphaBoost), false);
            }
        }
        if (world.getTotalWorldTime() % 200 < 20) {
            syncRec();
        }
    }

    /** Обновить сводку Якоря для сети. */
    public void syncRec() {
        if (world == null || world.isRemote || owner == null || !AnchorNet.isNetWorld(world)) {
            return;
        }
        AnchorData d = AnchorData.get(world);
        if (!d.anchors.contains(pos)) {
            return;
        }
        AnchorData.Rec r = d.recOrNew(pos);
        int lvl = level();
        int[] sc = new int[ItemAnchorSeal.Type.values().length];
        for (ItemAnchorSeal.Type t : getSeals()) {
            sc[t.ordinal()]++;
        }
        int al = alphaLevel();
        if (!owner.equals(r.owner) || r.level != lvl || r.xp != corrupted || r.land != land || r.captured != captured || r.spec != spec
                || r.alpha != al || !java.util.Arrays.equals(r.seals, sc) || !java.util.Arrays.equals(r.borrow, borrow)) {
            r.spec = spec;
            r.alpha = al;
            r.seals = sc;
            r.borrow = borrow.clone();
            r.owner = owner;
            r.level = lvl;
            r.xp = corrupted;
            r.land = land;
            r.captured = captured;
            d.markDirty();
        }
    }

    /** Якорь сети ближе всех к игроку. */
    private TileAnchor nearestTo(java.util.List<TileAnchor> c, EntityPlayerMP o) {
        TileAnchor best = this;
        double bd = Double.MAX_VALUE;
        for (TileAnchor t : c) {
            double d = o.getDistanceSq(t.pos);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    /** Для отладки: сразу выставить число осквернённых блоков (уровни засчитываются). */
    public void setCorrupted(int n) {
        int before = level();
        corrupted = Math.max(0, Math.min(maxXp(), n));
        land = Math.max(land, corrupted);
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l, false);
        }
        checkCapture();
        growColumn();
        changed();
        syncRec();
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
                if (captured) {
                    Corruption.flora(world, top.up(), rand);
                }
            }
        } else {
            int y = pos.getY() - 6 + rand.nextInt(18);
            BlockPos deep = new BlockPos(x, y, z);
            // в осквернённом камне вызревает тёмная руда (EvilCraft)
            changed = DarkCorruption.darkOre(world, deep, rand, 1 + net(AnchorSpec.VEIN)) || Corruption.corrupt(world, deep);
        }
        return changed;
    }

    private void checkCapture() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        if (captured || land < (t.length > 1 ? t[1] : 0)) {
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
        tag.setInteger("land", land);
        tag.setInteger("spec", spec);
        tag.setBoolean("alpha", alpha);
        tag.setInteger("alphaXp", alphaXp);
        tag.setDouble("alphaCarry", alphaCarry);
        tag.setIntArray("borrow", borrow);
        tag.setTag("storage", storage.serializeNBT());
        tag.setTag("sample", sample.serializeNBT());
        tag.setInteger("fieldXp", fieldXp);
        tag.setInteger("forgeEnergy", forgeEnergy);
        tag.setInteger("veinTimer", veinTimer);
        tag.setDouble("xpCarry", xpCarry);
        tag.setInteger("passiveXp", passiveXpTimer);
        tag.setTag("blood", blood.writeToNBT(new NBTTagCompound()));
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
        land = tag.hasKey("land") ? tag.getInteger("land") : corrupted;
        spec = tag.hasKey("spec") ? tag.getInteger("spec") : -1;
        alpha = tag.getBoolean("alpha");
        alphaXp = tag.getInteger("alphaXp");
        alphaCarry = tag.getDouble("alphaCarry");
        borrow = tag.getIntArray("borrow");
        if (tag.hasKey("storage")) {
            storage.deserializeNBT(tag.getCompoundTag("storage"));
        }
        if (tag.hasKey("sample")) {
            sample.deserializeNBT(tag.getCompoundTag("sample"));
        }
        fieldXp = tag.getInteger("fieldXp");
        forgeEnergy = tag.getInteger("forgeEnergy");
        veinTimer = tag.getInteger("veinTimer");
        xpCarry = tag.getDouble("xpCarry");
        passiveXpTimer = tag.getInteger("passiveXp");
        blood.setFluid(null);
        blood.readFromNBT(tag.getCompoundTag("blood"));
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

    /** Печати по местам: ordinal или −1 (и для сохранения, и для клиента). */
    private void writeSeals(NBTTagCompound tag) {
        int[] arr = new int[MAX_SEALS];
        for (int i = 0; i < MAX_SEALS; i++) {
            net.minecraft.item.ItemStack st = sealInv.getStackInSlot(i);
            arr[i] = st.getItem() instanceof ItemAnchorSeal ? ((ItemAnchorSeal) st.getItem()).type.ordinal() : -1;
        }
        tag.setIntArray("sealSlots", arr);
    }

    private void readSeals(NBTTagCompound tag) {
        ItemAnchorSeal.Type[] types = ItemAnchorSeal.Type.values();
        for (int i = 0; i < MAX_SEALS; i++) {
            sealInv.setStackInSlot(i, net.minecraft.item.ItemStack.EMPTY);
        }
        if (tag.hasKey("sealSlots")) {
            int[] arr = tag.getIntArray("sealSlots");
            for (int i = 0; i < Math.min(arr.length, MAX_SEALS); i++) {
                if (arr[i] >= 0 && arr[i] < types.length) {
                    sealInv.setStackInSlot(i, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.SEALS.get(types[arr[i]])));
                }
            }
            return;
        }
        // старое сохранение: список печатей подряд
        int i = 0;
        for (int o : tag.getIntArray("seals")) {
            if (o >= 0 && o < types.length && i < MAX_SEALS) {
                sealInv.setStackInSlot(i++, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.SEALS.get(types[o])));
            }
        }
    }

    // ------------------------------------------------------------------ клиент: уровень и печати для отрисовки

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("corrupted", corrupted);
        tag.setInteger("spec", spec);
        tag.setBoolean("alpha", alpha);
        tag.setInteger("alphaLvl", alphaLevel());
        tag.setInteger("slots", sealSlots());
        writeSeals(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        corrupted = tag.getInteger("corrupted");
        spec = tag.hasKey("spec") ? tag.getInteger("spec") : -1;
        alpha = tag.getBoolean("alpha");
        clientAlpha = tag.getInteger("alphaLvl");
        clientSlots = tag.hasKey("slots") ? tag.getInteger("slots") : -1;
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
        return new net.minecraft.util.math.AxisAlignedBB(pos.add(-2, 0, -2), pos.add(3, 15, 3));
    }

    /** Горн отдаёт энергию трубам и кабелям; принимать не умеет. */
    private final net.minecraftforge.energy.IEnergyStorage forgeOut = new net.minecraftforge.energy.IEnergyStorage() {
        @Override
        public int receiveEnergy(int max, boolean sim) {
            return 0;
        }

        @Override
        public int extractEnergy(int max, boolean sim) {
            int e = Math.min(max, forgeEnergy);
            if (!sim) {
                forgeEnergy -= e;
            }
            return e;
        }

        @Override
        public int getEnergyStored() {
            return forgeEnergy;
        }

        @Override
        public int getMaxEnergyStored() {
            return AnchorSpecs.FORGE_BUFFER;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap, @javax.annotation.Nullable net.minecraft.util.EnumFacing f) {
        if (cap == net.minecraftforge.energy.CapabilityEnergy.ENERGY) {
            return getSpec() == AnchorSpec.FORGE;
        }
        return cap == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(cap, f);
    }

    @Override
    @javax.annotation.Nullable
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @javax.annotation.Nullable net.minecraft.util.EnumFacing f) {
        if (cap == net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(blood);
        }
        if (cap == net.minecraftforge.energy.CapabilityEnergy.ENERGY && getSpec() == AnchorSpec.FORGE) {
            return net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(forgeOut);
        }
        return super.getCapability(cap, f);
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
