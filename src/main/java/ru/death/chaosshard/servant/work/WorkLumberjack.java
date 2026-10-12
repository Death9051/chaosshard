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
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
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
    private final List<BlockPos> logs = new ArrayList<>();
    private final int[] stuck = {0};
    private int t;

    public WorkLumberjack(EntityCreature m) {
        this.m = m;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return Work.chest(m) != null && m.getAttackTarget() == null && Work.alive(m);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    private int radius() {
        return ServantUpgrade.has(m, ServantUpgrade.WOOD_WIDE) ? 24 : 12;
    }

    @Override
    public void updateTask() {
        t++;
        World w = m.world;
        BlockPos c = Work.chest(m);
        int r = radius();
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

    private void findTree(BlockPos c, int r) {
        World w = m.world;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -3, -r), c.add(r, 6, r))) {
            IBlockState s = w.getBlockState(p);
            if (isLog(w, p, s) && ground(w.getBlockState(p.down()))) {
                double d = m.getDistanceSq(p);
                if (d < bd) {
                    List<BlockPos> tree = tree(w, p.toImmutable());
                    if (!tree.isEmpty()) {
                        bd = d;
                        best = p.toImmutable();
                        logs.clear();
                        logs.addAll(tree);
                    }
                }
            }
        }
        base = best;
    }

    /** Брёвна дерева от основания; пусто, если это не дерево (нет природной листвы). */
    private static List<BlockPos> tree(World w, BlockPos start) {
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
        if (leaves < 3) {
            return new ArrayList<>();
        }
        // рубим сверху вниз (список с конца)
        out.sort(Comparator.comparingInt(BlockPos::getY));
        return out;
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

    private void replant(World w, BlockPos b) {
        if (!w.isAirBlock(b) || !ground(w.getBlockState(b.down()))) {
            return;
        }
        ItemStack sap = Work.take(m, s -> s.getItem() instanceof ItemBlock && ((ItemBlock) s.getItem()).getBlock() instanceof BlockSapling);
        if (sap.isEmpty()) {
            return;
        }
        Block block = ((ItemBlock) sap.getItem()).getBlock();
        w.setBlockState(b, block.getStateFromMeta(sap.getMetadata()), 3);
        ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
    }

    /** Подобрать саженцы, яблоки, палки и брёвна вокруг — в сундук. */
    private void gather(BlockPos c, int r) {
        for (EntityItem it : m.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(c).grow(r, 8, r),
                x -> x.isEntityAlive() && !x.cannotPickup() && woodish(x.getItem()))) {
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
            return b instanceof BlockSapling || b.getDefaultState().getMaterial() == Material.WOOD && b.getClass().getSimpleName().contains("Log");
        }
        return false;
    }

    private void growSapling(BlockPos c, int r) {
        World w = m.world;
        for (BlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -3, -r), c.add(r, 6, r))) {
            IBlockState s = w.getBlockState(p);
            if (s.getBlock() instanceof BlockSapling && ((IGrowable) s.getBlock()).canGrow(w, p, s, false)) {
                ((IGrowable) s.getBlock()).grow(w, m.getRNG(), p.toImmutable(), s);
                ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
                return;
            }
        }
    }
}
