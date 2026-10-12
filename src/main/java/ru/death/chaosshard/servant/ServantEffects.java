package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static ru.death.chaosshard.servant.ServantUpgrade.*;

/** Действие улучшений бойцов и периодическое обновление вида слуг по Нити хозяина. */
public class ServantEffects {

    private static boolean sweeping;

    private static boolean common(Entity e) {
        return e instanceof EntityLiving && !(e instanceof EntityChaosBeast) && Servants.isServant(e);
    }

    @SubscribeEvent
    public void onUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e.ticksExisted % 20 != 5 || !common(e)) {
            return;
        }
        EntityLiving m = (EntityLiving) e;
        WorldServer ws = (WorldServer) m.world;
        int sec = m.ticksExisted / 20;
        // раз в 10 секунд: ступень Нити хозяина могла смениться
        if (sec % 10 == 0) {
            EntityPlayer o = Servants.owner(m);
            if (o != null && Math.min(5, ru.death.chaosshard.thread.ThreadBonus.level(o)) != ServantGear.tier(m)) {
                ServantGear.refresh(m, o);
            }
        }
        // гарнизон: после боя — домой, к своему Якорю
        if (sec % 10 == 0 && m.getEntityData().hasKey(Servants.TAG_ANCHOR) && m.getAttackTarget() == null) {
            net.minecraft.util.math.BlockPos home = net.minecraft.util.math.BlockPos.fromLong(m.getEntityData().getLong(Servants.TAG_ANCHOR));
            if (m.getDistanceSq(home) > 24 * 24 && m.world.isBlockLoaded(home)) {
                if (ru.death.chaosshard.anchor.AnchorClusters.at(m.world, home) == null) {
                    m.getEntityData().removeTag(Servants.TAG_ANCHOR);   // Якоря больше нет
                } else {
                    net.minecraft.util.math.BlockPos at = m.world.getHeight(home.add(2, 0, 2));
                    ws.spawnParticle(EnumParticleTypes.PORTAL, m.posX, m.posY + 1, m.posZ, 20, 0.3, 0.5, 0.3, 0.5);
                    m.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                }
            }
        }
        // станция добытчика держит чанки, пока он работает
        if (sec % 5 == 0 && m.getEntityData().hasKey(Servants.TAG_CHEST)) {
            Servants.Kind k = Servants.kindOf(m);
            ru.death.chaosshard.servant.work.MineData.Mine mine = k == Servants.Kind.MINER
                    ? ru.death.chaosshard.servant.work.MineData.get(m.world).existing(ru.death.chaosshard.servant.work.Work.chest(m)) : null;
            if (mine == null || !mine.done()) {
                ru.death.chaosshard.servant.work.WorkChunks.touch(m, m.getPosition());
            }
        }
        if (ServantGear.tier(m) >= 4 && sec % 2 == 0) {
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, m.posX, m.posY + m.height * 0.6, m.posZ, 2, 0.25, 0.4, 0.25, 0.0);
        }
        if (has(m, BONE_SHIELD)) {
            m.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 0, true, false));
        }
        if (has(m, HUSK_SAND)) {
            m.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
        }
        // Приманка: твари вокруг бросаются на Скорлупу
        if (has(m, HUSK_BAIT) && sec % 10 == 0) {
            for (EntityLiving t : ws.getEntitiesWithinAABB(EntityLiving.class, m.getEntityBoundingBox().grow(12),
                    x -> x instanceof IMob && !Servants.isServant(x))) {
                t.setAttackTarget(m);
            }
            ws.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY, m.posX, m.posY + m.height + 0.3, m.posZ, 2, 0.2, 0.1, 0.2, 0.0);
        }
        // Боевой клич: в бою — Сила слугам рядом, раз в 30 секунд
        if (has(m, EXEC_CRY) && m.getAttackTarget() != null && m.world.getTotalWorldTime() >= m.getEntityData().getLong("chaosshard_cry")) {
            m.getEntityData().setLong("chaosshard_cry", m.world.getTotalWorldTime() + 600);
            java.util.UUID o = m.getEntityData().getUniqueId(Servants.TAG_OWNER);
            for (EntityLiving s : ws.getEntitiesWithinAABB(EntityLiving.class, m.getEntityBoundingBox().grow(8),
                    x -> Servants.isServantOf(x, o))) {
                s.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 200, 0));
            }
            m.world.playSound(null, m.getPosition(), SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.0F, 0.7F);
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Entity src = event.getSource().getTrueSource();
        EntityLivingBase t = event.getEntityLiving();
        if (t.world.isRemote || !common(src) || event.getSource().getImmediateSource() != src) {
            return;
        }
        EntityLiving m = (EntityLiving) src;
        float amount = event.getAmount();
        if (has(m, EXEC_EXECUTE) && t.getHealth() < t.getMaxHealth() * 0.3F) {
            amount *= 2;
            ((WorldServer) t.world).spawnParticle(EnumParticleTypes.CRIT, t.posX, t.posY + 1, t.posZ, 15, 0.3, 0.4, 0.3, 0.2);
        }
        event.setAmount(amount);
        if (has(m, HUSK_BITE)) {
            t.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 100, 1));
            t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 0));
        }
        if (has(m, BONE_WITHER)) {
            t.addPotionEffect(new PotionEffect(MobEffects.WITHER, 60, 0));
        }
        if (has(m, BONE_FLAME)) {
            t.setFire(4);
        }
        if (has(m, EXEC_THIRST)) {
            m.heal(amount * 0.2F);
        }
        if (has(m, BONE_SWEEP) && !sweeping) {
            sweeping = true;
            try {
                for (EntityLiving o : t.world.getEntitiesWithinAABB(EntityLiving.class, t.getEntityBoundingBox().grow(2),
                        x -> x != t && x instanceof IMob && !Servants.isServant(x))) {
                    o.attackEntityFrom(DamageSource.causeMobDamage(m), amount * 0.5F);
                }
                ((WorldServer) t.world).spawnParticle(EnumParticleTypes.SWEEP_ATTACK, t.posX, t.posY + 1, t.posZ, 1, 0.0, 0.0, 0.0, 0.0);
            } finally {
                sweeping = false;
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !common(e)) {
            return;
        }
        EntityLiving m = (EntityLiving) e;
        // Взрыв гнили: облако яда, блоки целы
        if (has(m, HUSK_BLAST)) {
            WorldServer ws = (WorldServer) m.world;
            for (EntityLiving t : ws.getEntitiesWithinAABB(EntityLiving.class, m.getEntityBoundingBox().grow(3),
                    x -> x instanceof IMob && !Servants.isServant(x))) {
                t.attackEntityFrom(DamageSource.causeMobDamage(m), 6);
                t.addPotionEffect(new PotionEffect(MobEffects.POISON, 100, 1));
            }
            ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, m.posX, m.posY + 1, m.posZ, 2, 0.5, 0.5, 0.5, 0);
            ws.spawnParticle(EnumParticleTypes.SPELL_MOB, m.posX, m.posY + 1, m.posZ, 60, 1.5, 0.8, 1.5, 0);
            m.world.playSound(null, m.getPosition(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.8F, 1.3F);
        }
    }
}
