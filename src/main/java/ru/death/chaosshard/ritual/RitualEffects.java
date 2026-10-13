package ru.death.chaosshard.ritual;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.IMob;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;

import java.util.List;
import java.util.Random;

/** Действие ритуалов на месте — общее для Чаши (старые запущенные ритуалы) и сфер. */
public final class RitualEffects {

    public static final int GROWTH_RADIUS = 8, WARD_RADIUS = 32, STORM_RADIUS = 24, ANCHOR_REACH = 48;

    /** Один тик длительного ритуала в точке pos. */
    public static void tick(World world, BlockPos pos, Ritual r, int age) {
        switch (r) {
            case GROWTH:
                if (age % 10 == 0) {
                    grow(world, pos, 4);
                }
                break;
            case WARD:
                if (age % 20 == 0) {
                    RitualEvents.ward(world, pos, world.getTotalWorldTime() + 40);
                }
                break;
            case STORM:
                if (age % 40 == 0) {
                    strike(world, pos);
                }
                break;
            default:
                break;
        }
    }

    static void grow(World world, BlockPos pos, int tries) {
        Random rand = world.rand;
        int d = GROWTH_RADIUS * 2 + 1;
        for (int i = 0; i < tries; i++) {
            BlockPos p = pos.add(rand.nextInt(d) - GROWTH_RADIUS, rand.nextInt(5) - 2, rand.nextInt(d) - GROWTH_RADIUS);
            IBlockState s = world.getBlockState(p);
            Block b = s.getBlock();
            if (b instanceof IGrowable && b != Blocks.GRASS && b != Blocks.TALLGRASS && b != Blocks.DOUBLE_PLANT) {
                IGrowable g = (IGrowable) b;
                if (g.canGrow(world, p, s, false) && g.canUseBonemeal(world, rand, p, s)) {
                    g.grow(world, rand, p, s);
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, p.getX() + 0.5, p.getY() + 0.5,
                            p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
    }

    static void strike(World world, BlockPos pos) {
        List<EntityLivingBase> mobs = world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(pos).grow(STORM_RADIUS),
                e -> e instanceof IMob && e.isEntityAlive());
        if (mobs.isEmpty()) {
            return;
        }
        EntityLivingBase t = mobs.get(world.rand.nextInt(mobs.size()));
        world.addWeatherEffect(new EntityLightningBolt(world, t.posX, t.posY, t.posZ, true));
        t.attackEntityFrom(DamageSource.LIGHTNING_BOLT, 10.0F);
        t.setFire(4);
    }

    /** Ближайший Якорь в пределах ANCHOR_REACH от точки. */
    public static TileAnchor nearestAnchor(World world, BlockPos pos) {
        BlockPos near = AnchorData.get(world).nearest(pos, null);
        if (near == null || near.distanceSq(pos) > ANCHOR_REACH * ANCHOR_REACH) {
            return null;
        }
        TileEntity te = world.getTileEntity(near);
        return te instanceof TileAnchor ? (TileAnchor) te : null;
    }

    private RitualEffects() {
    }
}
