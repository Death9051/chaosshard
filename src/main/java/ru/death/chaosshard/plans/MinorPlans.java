package ru.death.chaosshard.plans;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;

/**
 * Малые планы Осколка: Незер, Край и карманы Dimensional Doors.
 * — Незер переплавляет: огненные твари роняют Эссенцию Хаоса.
 * — У Края Завеса тоньше всего: эндермены и шалкеры роняют Осколки Завесы.
 * — В карманах DimDoors Осколки мира падают вдвое чаще.
 * — Иссушитель и Дракон: Нить и строка в Гримуаре.
 * — Ритуал «Нюх Нити» называет ближайшую крепость Незера, город Края или крепость с порталом.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class MinorPlans {

    public static boolean isNether(World w) {
        return w.provider.getDimension() == -1;
    }

    public static boolean isEnd(World w) {
        return w.provider.getDimension() == 1;
    }

    /** Карманы и Лимб Dimensional Doors. */
    public static boolean isDimDoors(World w) {
        return w.provider.getClass().getName().contains("dimdoors");
    }

    /** Множитель шанса Осколка мира в этом мире. */
    public static double shardMultiplier(World w) {
        return isDimDoors(w) ? ChaosConfig.plans.pocketShardMultiplier : 1.0;
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        float bonus = 1 + event.getLootingLevel() * 0.5F;
        if (isNether(e.world) && (e instanceof EntityBlaze || e instanceof EntityWitherSkeleton || e instanceof EntityGhast
                || e instanceof EntityMagmaCube)) {
            if (e.world.rand.nextFloat() < ChaosConfig.plans.netherEssenceChance * bonus) {
                event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, new ItemStack(ModItems.CHAOS_ESSENCE)));
            }
        } else if (isEnd(e.world) && (e instanceof EntityEnderman || e instanceof EntityShulker)) {
            if (e.world.rand.nextFloat() < ChaosConfig.plans.endVeilChance * bonus) {
                event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, new ItemStack(ModItems.VEIL_SHARD)));
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !(e instanceof EntityWither || e instanceof EntityDragon)) {
            return;
        }
        boolean dragon = e instanceof EntityDragon;
        // Дракона добивают издалека — в счёт все Осколки рядом
        for (EntityPlayerMP p : e.world.getEntitiesWithinAABB(EntityPlayerMP.class, new AxisAlignedBB(e.getPosition()).grow(dragon ? 160 : 64))) {
            slain(p, dragon);
        }
    }

    public static void slain(EntityPlayerMP p, boolean dragon) {
        int nit = dragon ? ChaosConfig.plans.dragonThread : ChaosConfig.plans.witherThread;
        ThreadManager.addPoints(p, nit);
        Milestones.grant(p, dragon ? Milestones.DRAGON_SLAIN : Milestones.WITHER_SLAIN);
        p.sendMessage(Breach.grimoire(dragon ? "chaosshard.plans.dragon" : "chaosshard.plans.wither", nit));
    }

    /** «Нюх Нити»: ближайшая постройка, ведущая дальше. false — ничего не нашлось (сфера цела). */
    public static boolean scent(EntityPlayerMP p, World w, BlockPos at) {
        String what = isNether(w) ? "Fortress" : isEnd(w) ? "EndCity" : "Stronghold";
        BlockPos found = null;
        try {
            found = ((WorldServer) w).findNearestStructure(what, at, false);
        } catch (Exception ignored) {
            // мир без такой постройки
        }
        if (found == null) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.ritual.scent.none"), true);
            return false;
        }
        int dist = (int) Math.sqrt(found.distanceSq(at.getX(), found.getY(), at.getZ()));
        p.sendMessage(Breach.grimoire("chaosshard.ritual.scent." + what.toLowerCase(), found.getX(), found.getZ(), dist));
        return true;
    }

    private MinorPlans() {
    }
}
