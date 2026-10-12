package ru.death.chaosshard.servant.work;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockStem;
import net.minecraft.block.IGrowable;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;
import ru.death.chaosshard.servant.ServantGear;
import ru.death.chaosshard.servant.ServantUpgrade;

import java.util.ArrayList;
import java.util.List;

/** Жнец: собирает спелый урожай вокруг сундука и пересаживает. */
public class WorkReaper extends EntityAIBase {

    private final EntityCreature m;
    private BlockPos target;
    private final int[] stuck = {0};
    private int t;

    public WorkReaper(EntityCreature m) {
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
        return ServantUpgrade.has(m, ServantUpgrade.REAPER_WIDE) ? 16 : 8;
    }

    @Override
    public void updateTask() {
        t++;
        BlockPos c = Work.chest(m);
        World w = m.world;
        int r = radius();
        if (t % 200 == 0) {
            extras(c, r);
        }
        if (ServantUpgrade.has(m, ServantUpgrade.REAPER_PICKUP) && t % 40 == 0) {
            for (EntityItem it : w.getEntitiesWithinAABB(EntityItem.class, new net.minecraft.util.math.AxisAlignedBB(c).grow(r, 4, r),
                    x -> x.isEntityAlive() && !x.cannotPickup())) {
                Work.deposit(m, it.getItem().copy());
                it.setDead();
            }
        }
        if (target == null) {
            if (t % 40 == 0) {
                target = find(c, r);
                stuck[0] = 0;
                if (target != null) {
                    Work.claim(m, target);
                } else {
                    Work.release(m);
                }
            }
            if (target == null) {
                if (t % 60 == 0 && m.getDistanceSq(c) > 9) {
                    m.getNavigator().tryMoveToXYZ(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.9);
                }
                return;
            }
        }
        if (!mature(w, target, w.getBlockState(target))) {
            target = null;
            return;
        }
        if (Work.approach(m, target, 2.5, stuck)) {
            m.getLookHelper().setLookPosition(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 30, 30);
            if (t % Math.max(4, 12 - 2 * ServantGear.tier(m)) == 0) {
                m.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                harvest(w, target);
                target = null;
            }
        }
    }

    private BlockPos find(BlockPos c, int r) {
        World w = m.world;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -3, -r), c.add(r, 3, r))) {
            IBlockState s = w.getBlockState(p);
            if (mature(w, p, s) && !Work.taken(m, p)) {
                double d = m.getDistanceSq(p);
                if (d < bd) {
                    bd = d;
                    best = p.toImmutable();
                }
            }
        }
        return best;
    }

    static boolean mature(World w, BlockPos p, IBlockState s) {
        Block b = s.getBlock();
        if (b instanceof BlockCrops) {
            return ((BlockCrops) b).isMaxAge(s);
        }
        if (b == Blocks.NETHER_WART) {
            return s.getValue(BlockNetherWart.AGE) >= 3;
        }
        if (b == Blocks.COCOA) {
            return s.getValue(BlockCocoa.AGE) >= 2;
        }
        if (b == Blocks.REEDS) {
            return w.getBlockState(p.down()).getBlock() == Blocks.REEDS && w.getBlockState(p.down(2)).getBlock() != Blocks.REEDS;
        }
        if (b == Blocks.PUMPKIN || b == Blocks.MELON_BLOCK) {
            for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
                if (w.getBlockState(p.offset(f)).getBlock() instanceof BlockStem) {
                    return true;
                }
            }
        }
        return false;
    }

    private void harvest(World w, BlockPos p) {
        IBlockState s = w.getBlockState(p);
        Block b = s.getBlock();
        NonNullList<ItemStack> drops = NonNullList.create();
        w.playEvent(2001, p, Block.getStateId(s));
        if (b == Blocks.REEDS) {
            int n = 0;
            BlockPos top = p;
            while (w.getBlockState(top.up()).getBlock() == Blocks.REEDS) {
                top = top.up();
            }
            for (BlockPos q = top; q.getY() >= p.getY(); q = q.down()) {
                w.setBlockState(q, Blocks.AIR.getDefaultState(), 2);
                n++;
            }
            drops.add(new ItemStack(net.minecraft.init.Items.REEDS, n));
        } else {
            b.getDrops(drops, w, p, s, 0);
            if (b instanceof BlockCrops || b == Blocks.NETHER_WART || b == Blocks.COCOA) {
                Item seed = b.getItem(w, p, s).getItem();
                boolean planted = false;
                for (ItemStack d : drops) {
                    if (d.getItem() == seed && (b != Blocks.COCOA || d.getMetadata() == 3)) {
                        d.shrink(1);
                        planted = true;
                        break;
                    }
                }
                if (planted) {
                    IBlockState young = b instanceof BlockCrops ? ((BlockCrops) b).withAge(0)
                            : b == Blocks.COCOA ? s.withProperty(BlockCocoa.AGE, 0) : b.getDefaultState();
                    w.setBlockState(p, young, 2);
                } else {
                    w.setBlockToAir(p);
                }
            } else {
                w.setBlockToAir(p);
            }
        }
        boolean bounty = ServantUpgrade.has(m, ServantUpgrade.REAPER_BOUNTY) && m.getRNG().nextInt(4) == 0;
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack d : drops) {
            if (!d.isEmpty()) {
                if (bounty) {
                    extra.add(d.copy());
                }
                Work.deposit(m, d);
            }
        }
        for (ItemStack d : extra) {
            Work.deposit(m, d);
        }
        if (bounty) {
            ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
        }
    }

    /** Раз в 10 секунд: подкормка мукой и пахота (улучшения). */
    private void extras(BlockPos c, int r) {
        World w = m.world;
        java.util.Random rand = m.getRNG();
        if (ServantUpgrade.has(m, ServantUpgrade.REAPER_MEAL)) {
            for (int i = 0; i < 40; i++) {
                BlockPos p = c.add(rand.nextInt(2 * r + 1) - r, rand.nextInt(7) - 3, rand.nextInt(2 * r + 1) - r);
                IBlockState s = w.getBlockState(p);
                if (s.getBlock() instanceof IGrowable && !(s.getBlock() == Blocks.GRASS) && !(s.getBlock() == Blocks.TALLGRASS)
                        && ((IGrowable) s.getBlock()).canGrow(w, p, s, false)) {
                    ((IGrowable) s.getBlock()).grow(w, rand, p, s);
                    ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
                    break;
                }
            }
        }
        if (ServantUpgrade.has(m, ServantUpgrade.REAPER_PLOW)) {
            int done = 0;
            for (BlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -2, -r), c.add(r, 2, r))) {
                if (done >= 4) {
                    break;
                }
                Block b = w.getBlockState(p).getBlock();
                if ((b == Blocks.DIRT || b == Blocks.GRASS) && w.isAirBlock(p.up()) && nearWater(w, p)) {
                    ItemStack seed = Work.take(m, s -> s.getItem() instanceof IPlantable
                            && ((IPlantable) s.getItem()).getPlantType(w, p) == EnumPlantType.Crop);
                    if (seed.isEmpty()) {
                        break;
                    }
                    BlockPos q = p.toImmutable();
                    w.setBlockState(q, Blocks.FARMLAND.getDefaultState(), 3);
                    w.setBlockState(q.up(), ((IPlantable) seed.getItem()).getPlant(w, q.up()), 3);
                    done++;
                }
            }
        }
    }

    private static boolean nearWater(World w, BlockPos p) {
        for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-4, 0, -4), p.add(4, 0, 4))) {
            if (w.getBlockState(q).getMaterial() == Material.WATER) {
                return true;
            }
        }
        return false;
    }
}
