package ru.death.chaosshard.servant.work;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.IGrowable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.servant.ServantGear;
import ru.death.chaosshard.servant.ServantUpgrade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Лесоруб: рубит деревья вокруг сундука и сажает саженцы. Рубит только настоящие деревья — со «своей»
 * (природной) листвой, так что постройки из брёвен не трогает.
 */
public class WorkLumberjack extends EntityAIBase {

    private final EntityCreature m;
    private BlockPos base;
    /** Порода срубаемого дерева (мета саженца; -1 — неизвестна) и клетки основания ствола. */
    private int kind = -1;
    private final List<BlockPos> bases = new ArrayList<>();
    private final List<BlockPos> logs = new ArrayList<>();
    private final int[] stuck = {0};
    private int t;
    /** Найденные обходом основания деревьев и саженцы (обход идёт по чанку за тик — зона большая). */
    private final java.util.LinkedHashSet<BlockPos> found = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<BlockPos> saplings = new java.util.LinkedHashSet<>();
    private int scanIdx;

    public WorkLumberjack(EntityCreature m) {
        this.m = m;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return Work.chest(m) != null && m.getAttackTarget() == null && Work.alive(m) && !Work.waitsForRoom(m);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    /** Зона вырубки вокруг сундука: 120 блоков, с «Широкой вырубкой» — 200. */
    public static final int RADIUS = 120, RADIUS_WIDE = 200;

    private int radius() {
        return ServantUpgrade.has(m, ServantUpgrade.WOOD_WIDE) ? RADIUS_WIDE : RADIUS;
    }

    /**
     * Шаг обхода зоны: два чанка за вызов. По карте высот сверху вниз через листву и воздух до бревна,
     * под которым земля, — основание дерева; саженцы на пути — в список для «Лесника». Только прогруженные чанки.
     */
    private void scanStep(BlockPos c, int r) {
        World w = m.world;
        int cr = (r >> 4) + 1, side = cr * 2 + 1, total = side * side;
        for (int k = 0; k < 2; k++) {
            int i = scanIdx++ % total;
            int cx = (c.getX() >> 4) - cr + i % side, cz = (c.getZ() >> 4) - cr + i / side;
            if (!w.isBlockLoaded(new BlockPos(cx << 4, 0, cz << 4))) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int bx = (cx << 4) + x, bz = (cz << 4) + z;
                    double dx = bx - c.getX(), dz = bz - c.getZ();
                    if (dx * dx + dz * dz > (double) r * r) {
                        continue;
                    }
                    int top = w.getHeight(bx, bz);
                    BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(bx, top, bz);
                    for (int y = top; y > Math.max(1, top - 40); y--) {
                        p.setY(y);
                        IBlockState st = w.getBlockState(p);
                        if (isLog(w, p, st)) {
                            if (ground(w.getBlockState(p.down()))) {
                                found.add(p.toImmutable());
                                break;
                            }
                            continue;
                        }
                        if (st.getBlock() instanceof BlockSapling) {
                            saplings.add(p.toImmutable());
                            break;
                        }
                        Material mat = st.getMaterial();
                        if (mat == Material.AIR || mat == Material.LEAVES || mat == Material.VINE || mat == Material.PLANTS
                                || mat == Material.SNOW || mat == Material.FIRE || st.getBlock().isReplaceable(w, p)) {
                            continue;
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void updateTask() {
        t++;
        World w = m.world;
        BlockPos c = Work.chest(m);
        int r = radius();
        scanStep(c, r);
        if (t % 100 == 0) {
            gather(c, r);
        }
        if (ServantUpgrade.has(m, ServantUpgrade.WOOD_FORESTER) && t % 200 == 0) {
            growSapling(c, r);
        }
        if (base == null) {
            if (t % 40 == 0) {
                findTree(c, r);
                stuck[0] = 0;
                if (base != null) {
                    Work.claim(m, base);
                } else {
                    Work.release(m);
                }
            }
            if (base == null) {
                if (t % 60 == 0 && m.getDistanceSq(c) > 9) {
                    m.getNavigator().tryMoveToXYZ(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.9);
                }
                return;
            }
        }
        if (!Work.approach(m, base, 3.0, stuck)) {
            return;
        }
        m.getLookHelper().setLookPosition(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5, 30, 30);
        int speed = Math.max(3, 10 - ServantGear.tier(m));
        if (ServantUpgrade.has(m, ServantUpgrade.WOOD_SHARP)) {
            speed = Math.max(2, speed / 2);
        }
        if (t % speed != 0) {
            return;
        }
        m.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        while (!logs.isEmpty()) {
            BlockPos p = logs.remove(logs.size() - 1);
            IBlockState s = w.getBlockState(p);
            if (isLog(w, p, s)) {
                chop(w, p, s);
                return;
            }
        }
        // дерево срублено
        if (ServantUpgrade.has(m, ServantUpgrade.WOOD_LEAVES)) {
            stripLeaves(w, base);
        }
        replant(w, base);
        base = null;
    }

    static boolean isLog(World w, BlockPos p, IBlockState s) {
        return s.getBlock().isWood(w, p) || s.getMaterial() == Material.WOOD && s.getBlock().getClass().getSimpleName().contains("Log");
    }

    private static boolean ground(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.MYCELIUM || s.getMaterial() == Material.GROUND || s.getMaterial() == Material.GRASS;
    }

    private static boolean naturalLeaves(IBlockState s) {
        if (!(s.getBlock() instanceof BlockLeaves)) {
            return false;
        }
        return !s.getPropertyKeys().contains(BlockLeaves.DECAYABLE) || s.getValue(BlockLeaves.DECAYABLE);
    }

    /** Ближайшее к сундуку проверенное дерево из найденных обходом (не больше 12 проверок за раз). */
    private void findTree(BlockPos c, int r) {
        World w = m.world;
        List<BlockPos> cand = new ArrayList<>(found);
        cand.sort(Comparator.comparingDouble(m::getDistanceSq));
        int tries = 0;
        for (BlockPos p : cand) {
            if (tries++ >= 12) {
                break;
            }
            IBlockState s = w.getBlockState(p);
            if (!w.isBlockLoaded(p) || !isLog(w, p, s) || !ground(w.getBlockState(p.down())) || p.distanceSq(c) > (double) r * r) {
                found.remove(p);
                continue;
            }
            if (Work.taken(m, p)) {
                continue;
            }
            List<BlockPos> tree = tree(w, p);
            if (tree.isEmpty()) {
                found.remove(p);
                continue;
            }
            found.remove(p);
            logs.clear();
            logs.addAll(tree);
            kind = woodKind(s);
            bases.clear();
            int minY = tree.get(0).getY();
            for (BlockPos q : tree) {
                if (q.getY() == minY && ground(w.getBlockState(q.down()))) {
                    bases.add(q);
                }
            }
            base = p;
            return;
        }
        base = null;
    }

    /** Брёвна дерева от основания; пусто, если это не дерево (нет природной листвы). */
    public static List<BlockPos> tree(World w, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        int leaves = 0;
        while (!q.isEmpty() && out.size() < 160) {
            BlockPos p = q.poll();
            out.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.add(dx, dy, dz);
                        if (seen.contains(n) || Math.abs(n.getX() - start.getX()) > 8 || Math.abs(n.getZ() - start.getZ()) > 8) {
                            continue;
                        }
                        IBlockState s = w.getBlockState(n);
                        if (isLog(w, n, s)) {
                            seen.add(n);
                            q.add(n);
                        } else if (naturalLeaves(s)) {
                            seen.add(n);
                            leaves++;
                        }
                    }
                }
            }
        }
        if (leaves < 3 && !burnt(w, out)) {
            return new ArrayList<>();
        }
        // рубим сверху вниз (список с конца)
        out.sort(Comparator.comparingInt(BlockPos::getY));
        return out;
    }

    /**
     * Обгоревшее дерево (листва сгорела): одиночный ствол до 40 брёвен, верх открыт небу, и ни одно бревно
     * не примыкает к постройке — рядом только воздух, листва, растения, огонь, снег и другие брёвна.
     */
    private static boolean burnt(World w, List<BlockPos> logs) {
        if (logs.size() < 2 || logs.size() > 40) {
            return false;
        }
        BlockPos top = logs.get(0);
        for (BlockPos q : logs) {
            if (q.getY() > top.getY()) {
                top = q;
            }
        }
        if (!w.canSeeSky(top.up())) {
            return false;
        }
        for (BlockPos q : logs) {
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                BlockPos n = q.offset(f);
                IBlockState st = w.getBlockState(n);
                if (isLog(w, n, st)) {
                    continue;
                }
                Material mat = st.getMaterial();
                boolean natural = mat == Material.AIR || mat == Material.LEAVES || mat == Material.PLANTS || mat == Material.VINE
                        || mat == Material.FIRE || mat == Material.SNOW || mat == Material.CRAFTED_SNOW || st.getBlock().isReplaceable(w, n)
                        || ground(st) && q.getY() == logs.get(0).getY();
                if (!natural) {
                    return false;
                }
            }
        }
        return true;
    }

    private void chop(World w, BlockPos p, IBlockState s) {
        NonNullList<ItemStack> drops = NonNullList.create();
        s.getBlock().getDrops(drops, w, p, s, 0);
        w.playEvent(2001, p, Block.getStateId(s));
        w.setBlockToAir(p);
        boolean coal = ServantUpgrade.has(m, ServantUpgrade.WOOD_CHARCOAL);
        for (ItemStack d : drops) {
            if (coal && m.getRNG().nextInt(5) == 0) {
                d = new ItemStack(Items.COAL, d.getCount(), 1);
            }
            Work.deposit(m, d);
        }
    }

    private void stripLeaves(World w, BlockPos b) {
        for (BlockPos p : BlockPos.getAllInBoxMutable(b.add(-4, 0, -4), b.add(4, 16, 4))) {
            IBlockState s = w.getBlockState(p);
            if (naturalLeaves(s)) {
                NonNullList<ItemStack> drops = NonNullList.create();
                s.getBlock().getDrops(drops, w, p, s, 0);
                w.setBlockToAir(p);
                for (ItemStack d : drops) {
                    Work.deposit(m, d);
                }
            }
        }
    }

    /** Порода по бревну: мета саженца ванильной породы, -1 — модное дерево. */
    private static int woodKind(IBlockState s) {
        if (s.getBlock() == Blocks.LOG) {
            return s.getValue(net.minecraft.block.BlockOldLog.VARIANT).getMetadata();
        }
        if (s.getBlock() == Blocks.LOG2) {
            return s.getValue(net.minecraft.block.BlockNewLog.VARIANT).getMetadata();
        }
        return -1;
    }

    private static boolean isSapling(ItemStack s) {
        if (!(s.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block b = ((ItemBlock) s.getItem()).getBlock();
        return b instanceof BlockSapling || b.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("sapling");
    }

    private boolean sameKind(ItemStack s) {
        if (!isSapling(s)) {
            return false;
        }
        if (kind < 0) {
            return true;
        }
        return ((ItemBlock) s.getItem()).getBlock() == Blocks.SAPLING && s.getMetadata() == kind;
    }

    /**
     * Посадить на место срубленного дерева саженец той же породы (из сундука; нет такого — любой).
     * Тёмный дуб, ель и джунгли с толстым стволом — квадратом 2×2, если в сундуке хватает саженцев.
     */
    private void replant(World w, BlockPos b) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos q : bases.isEmpty() ? java.util.Collections.singletonList(b) : bases) {
            if (w.isAirBlock(q) && ground(w.getBlockState(q.down()))) {
                spots.add(q);
            }
        }
        if (spots.isEmpty()) {
            return;
        }
        boolean big = spots.size() >= 4 && (kind == 1 || kind == 3 || kind == 5) && Work.count(m, this::sameKind) >= 4;
        if (!big) {
            spots = spots.subList(0, 1);
        } else {
            spots = spots.subList(0, 4);
        }
        for (BlockPos q : spots) {
            // одиночный тёмный дуб не вырастет — тогда лучше другая порода
            ItemStack sap = !big && kind == 5 ? Work.take(m, x -> isSapling(x) && !(x.getItem() == Item.getItemFromBlock(Blocks.SAPLING) && x.getMetadata() == 5))
                    : Work.take(m, this::sameKind);
            if (sap.isEmpty() && !big) {
                sap = Work.take(m, WorkLumberjack::isSapling);
            }
            if (sap.isEmpty()) {
                return;
            }
            Block block = ((ItemBlock) sap.getItem()).getBlock();
            w.setBlockState(q, block.getStateFromMeta(sap.getMetadata()), 3);
            ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
        }
    }

    /** Подобрать саженцы, яблоки, палки и брёвна вокруг — в сундук. */
    private void gather(BlockPos c, int r) {
        for (EntityItem it : m.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(c).grow(r, 8, r),
                x -> x.isEntityAlive() && !x.cannotPickup() && x.getThrower() == null && woodish(x.getItem()))) {
            Work.deposit(m, it.getItem().copy());
            it.setDead();
        }
    }

    private static boolean woodish(ItemStack s) {
        if (s.getItem() == Items.APPLE || s.getItem() == Items.STICK) {
            return true;
        }
        if (s.getItem() instanceof ItemBlock) {
            Block b = ((ItemBlock) s.getItem()).getBlock();
            return isSapling(s) || b.getDefaultState().getMaterial() == Material.WOOD && b.getClass().getSimpleName().contains("Log");
        }
        return false;
    }

    private void growSapling(BlockPos c, int r) {
        World w = m.world;
        for (BlockPos p : new ArrayList<>(saplings)) {
            if (!w.isBlockLoaded(p)) {
                continue;
            }
            IBlockState s = w.getBlockState(p);
            if (!(s.getBlock() instanceof BlockSapling)) {
                saplings.remove(p);
                continue;
            }
            if (((IGrowable) s.getBlock()).canGrow(w, p, s, false)) {
                ((IGrowable) s.getBlock()).grow(w, m.getRNG(), p.toImmutable(), s);
                ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
                return;
            }
        }
    }
}
