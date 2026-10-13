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
            case PULL:
                if (age % 4 == 0) {
                    pull(world, pos);
                }
                break;
            case BLOOD_MOON:
                if (age % 20 == 0) {
                    AltarRituals.keepMoon(world, pos, world.getTotalWorldTime() + 60);
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

    public static final int PULL_RADIUS = 12;

    /** Тяга: вещи и сферы опыта плывут к сфере; дошедшие вещи падают в сундук под ней, если он есть. */
    static void pull(World world, BlockPos pos) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(PULL_RADIUS);
        TileEntity below = world.getTileEntity(pos.down());
        net.minecraftforge.items.IItemHandler inv = below == null ? null
                : below.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, net.minecraft.util.EnumFacing.UP);
        for (net.minecraft.entity.Entity e : world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, box,
                x -> x instanceof net.minecraft.entity.item.EntityItem || x instanceof net.minecraft.entity.item.EntityXPOrb)) {
            double dx = cx - e.posX, dy = cy - e.posY, dz = cz - e.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1.2 && e instanceof net.minecraft.entity.item.EntityItem && inv != null) {
                net.minecraft.entity.item.EntityItem ei = (net.minecraft.entity.item.EntityItem) e;
                net.minecraft.item.ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inv, ei.getItem().copy(), false);
                if (rest.isEmpty()) {
                    ei.setDead();
                } else {
                    ei.setItem(rest);
                }
                continue;
            }
            if (d > 0.6) {
                double sp = 0.12;
                e.motionX = dx / d * sp;
                e.motionY = dy / d * sp + 0.03;
                e.motionZ = dz / d * sp;
                e.velocityChanged = true;
            }
        }
    }

    /**
     * Мгновенная сфера: применить в точке pos. false — применить нельзя (сообщение уже отправлено), сфера не тратится.
     */
    public static boolean use(net.minecraft.entity.player.EntityPlayerMP p, World world, BlockPos pos, Ritual r) {
        switch (r) {
            case CORRUPTION: {
                TileAnchor a = nearestAnchor(world, pos);
                if (a == null) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.no_anchor"), true);
                    return false;
                }
                p.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.corruption.done", a.forceSpread(400)));
                return true;
            }
            case DUSK: {
                if (!world.provider.hasSkyLight() || world.provider.doesWaterVaporize()
                        || ru.death.chaosshard.antechamber.AntechamberDimension.is(world)) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.no_sky"), true);
                    return false;
                }
                long t = world.getWorldTime();
                long day = t % 24000;
                // день → закат (13 000), ночь → рассвет (следующие 0)
                world.setWorldTime(day < 12500 ? t - day + 13000 : t - day + 24000);
                return true;
            }
            case BREED: {
                int n = 0;
                for (net.minecraft.entity.passive.EntityAnimal a : world.getEntitiesWithinAABB(net.minecraft.entity.passive.EntityAnimal.class,
                        new AxisAlignedBB(pos).grow(12), x -> x.isEntityAlive() && x.getGrowingAge() == 0 && !x.isInLove())) {
                    a.setInLove(p);
                    n++;
                }
                if (n == 0) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.breed.none"), true);
                    return false;
                }
                p.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.breed.done", n));
                return true;
            }
            case SIGHT:
                AltarRituals.sight(p, pos);
                return true;
            case RECALL: {
                if (ru.death.chaosshard.servant.Beasts.countInThread(p) == 0) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.altar.recall.none"), true);
                    return false;
                }
                p.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.altar.recall.done",
                        ru.death.chaosshard.servant.Beasts.pullFromThread(p)));
                return true;
            }
            case GREAT_SURGE: {
                List<TileAnchor> mine = AltarRituals.myAnchors(p, pos);
                if (mine.isEmpty()) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.altar.surge.none"), true);
                    return false;
                }
                int total = 0;
                for (TileAnchor a : mine) {
                    total += a.forceSpread(250);
                }
                p.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.altar.surge.done", mine.size(), total));
                return true;
            }
            case HOLY_DEFILE: {
                BlockPos stone = ru.death.chaosshard.order.Shrines.nearest(world, pos, 64);
                if (stone == null) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.holy_defile.none"), true);
                    return false;
                }
                return ru.death.chaosshard.order.Shrines.desecrate(world, stone, p,
                        new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE));
            }
            case FALLEN_FEATHER: {
                int n = 0;
                for (net.minecraft.entity.EntityLiving e : world.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class,
                        new AxisAlignedBB(pos).grow(32), x -> ru.death.chaosshard.servant.Servants.isServantOf(x, p.getUniqueID()))) {
                    e.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.SPEED, 6000, 1));
                    e.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.RESISTANCE, 6000, 0));
                    e.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.REGENERATION, 6000, 0));
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, e.posX, e.posY + e.height, e.posZ, 12, 0.3, 0.3, 0.3, 0.02);
                    n++;
                }
                if (n == 0) {
                    p.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.fallen_feather.none"), true);
                    return false;
                }
                p.sendMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.ritual.fallen_feather.done", n));
                return true;
            }
            default: {
                ru.death.chaosshard.hero.Lightbearer h = ru.death.chaosshard.hero.Lightbearer.byOmen(r);
                return h != null && ru.death.chaosshard.hero.Pillars.omen(p, world, pos, h);
            }
        }
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
