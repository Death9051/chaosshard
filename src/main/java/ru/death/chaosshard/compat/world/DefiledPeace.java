package ru.death.chaosshard.compat.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.challenge.ChallengeManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Твари Скверны (Defiled Lands) нейтральны к Осколку: сами не нападают. Ударишь одну — она и её сородичи в 16 блоках
 * будут мстить тебе полминуты (как свиные зомби). Боссы Defiled Lands (Разрушитель, Скорбящий) и бойцы Вызова — враги как прежде.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class DefiledPeace {

    static final String PROVOKED = "chaosshard_provoked";
    /** Сколько тиков твари помнят обиду. */
    public static final int GRUDGE = 600;
    static final double CALL_RADIUS = 16;
    private static final Set<String> BOSSES = new HashSet<>(Arrays.asList("the_destroyer", "the_mourner"));

    /** Нейтральная тварь Скверны. */
    public static boolean neutral(Entity e) {
        if (!(e instanceof EntityLiving)) {
            return false;
        }
        ResourceLocation id = EntityList.getKey(e);
        return id != null && "defiledlands".equals(id.getNamespace()) && !BOSSES.contains(id.getPath())
                && !e.getEntityData().hasUniqueId(ChallengeManager.TAG);
    }

    /** Этот игрок её задел (или её сородича рядом) недавно. */
    public static boolean provokedBy(EntityLivingBase mob, EntityPlayer p) {
        if (mob.getRevengeTarget() == p) {
            return true;
        }
        NBTTagCompound t = mob.getEntityData().getCompoundTag(PROVOKED);
        return t.hasUniqueId("By") && t.getUniqueId("By").equals(p.getUniqueID())
                && mob.world.getTotalWorldTime() - t.getLong("At") < GRUDGE;
    }

    static void provoke(EntityLivingBase mob, EntityPlayer p) {
        NBTTagCompound t = new NBTTagCompound();
        t.setUniqueId("By", p.getUniqueID());
        t.setLong("At", mob.world.getTotalWorldTime());
        mob.getEntityData().setTag(PROVOKED, t);
    }

    /** Сама не выбирает Осколка целью. */
    @SubscribeEvent
    public static void onTarget(LivingSetAttackTargetEvent event) {
        if (!(event.getTarget() instanceof EntityPlayer) || event.getEntityLiving().world.isRemote || !neutral(event.getEntityLiving())) {
            return;
        }
        if (!provokedBy(event.getEntityLiving(), (EntityPlayer) event.getTarget())) {
            ((EntityLiving) event.getEntityLiving()).setAttackTarget(null);
        }
    }

    /** Не ранит без причины (слизни бьют касанием и без цели); удар Осколка будит обиду у неё и у соседей. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) {
            return;
        }
        Entity src = event.getSource().getTrueSource();
        if (victim instanceof EntityPlayer && neutral(src) && !provokedBy((EntityLivingBase) src, (EntityPlayer) victim)) {
            event.setCanceled(true);
            if (((EntityLiving) src).getAttackTarget() == victim) {
                ((EntityLiving) src).setAttackTarget(null);
            }
            return;
        }
        if (src instanceof EntityPlayer && neutral(victim)) {
            EntityPlayer p = (EntityPlayer) src;
            provoke(victim, p);
            for (EntityLiving e : victim.world.getEntitiesWithinAABB(EntityLiving.class, victim.getEntityBoundingBox().grow(CALL_RADIUS),
                    DefiledPeace::neutral)) {
                provoke(e, p);
                if (e.getAttackTarget() == null) {
                    e.setAttackTarget(p);
                }
            }
        }
    }

    private DefiledPeace() {
    }
}
