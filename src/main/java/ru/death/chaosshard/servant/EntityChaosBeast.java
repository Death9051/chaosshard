package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Уникальные слуги: Заяц, Тигр, Кошка. По одному каждого у Осколка.
 *  Заяц — быстрый добытчик: собирает предметы вокруг себя и переносит владельцу в инвентарь.
 *  Тигр — лучший боец: много здоровья и сильный удар.
 *  Кошка — отпугивает криперов и приносит владельцу удачу.
 */
public abstract class EntityChaosBeast extends EntityCreature {

    public EntityChaosBeast(World world) {
        super(world);
    }

    protected abstract double hp();

    protected abstract double speed();

    protected abstract double damage();

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp());
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(speed());
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(damage());
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24);
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this),
                (float) getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue());
        if (hit) {
            applyEnchantments(this, target);
        }
        return hit;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!world.isRemote && ticksExisted % 40 == 0) {
            ability();
        }
    }

    protected void ability() {
    }

    protected EntityPlayer owner() {
        return Servants.owner(this);
    }

    // ------------------------------------------------------------------ виды

    public static class Hare extends EntityChaosBeast {
        public Hare(World w) {
            super(w);
            setSize(0.5F, 0.7F);
        }

        protected double hp() { return 20; }
        protected double speed() { return 0.42; }
        protected double damage() { return 2; }

        /** Добытчик: подбирает предметы в 8 блоках и переносит владельцу. */
        @Override
        protected void ability() {
            EntityPlayer o = owner();
            if (o == null || o.world != world || o.getDistanceSq(this) > 64 * 64) {
                return;
            }
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, getEntityBoundingBox().grow(8),
                    e -> e.isEntityAlive() && !e.cannotPickup());
            for (EntityItem it : items) {
                ((net.minecraft.world.WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, it.posX, it.posY, it.posZ, 6, 0.1, 0.1, 0.1, 0.2);
                ItemHandlerHelper.giveItemToPlayer(o, it.getItem().copy());
                it.setDead();
            }
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_RABBIT_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_RABBIT_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_RABBIT_DEATH; }
    }

    public static class Tiger extends EntityChaosBeast {
        public Tiger(World w) {
            super(w);
            setSize(1.1F, 1.3F);
            stepHeight = 1.0F;
        }

        protected double hp() { return 80; }
        protected double speed() { return 0.33; }
        protected double damage() { return 9; }

        @Override
        protected void applyEntityAttributes() {
            super.applyEntityAttributes();
            getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.7);
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_POLAR_BEAR_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_POLAR_BEAR_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_POLAR_BEAR_DEATH; }
        @Override
        protected float getSoundPitch() { return 0.7F; }
    }

    public static class Cat extends EntityChaosBeast {
        public Cat(World w) {
            super(w);
            setSize(0.6F, 0.7F);
        }

        protected double hp() { return 24; }
        protected double speed() { return 0.36; }
        protected double damage() { return 4; }

        /** Криперы рядом пугаются и пятятся; владелец рядом — с удачей. */
        @Override
        protected void ability() {
            for (EntityCreeper c : world.getEntitiesWithinAABB(EntityCreeper.class, getEntityBoundingBox().grow(12))) {
                c.setAttackTarget(null);
                c.setCreeperState(-1);
                Vec3d away = c.getPositionVector().subtract(getPositionVector()).normalize().scale(8);
                c.getNavigator().tryMoveToXYZ(c.posX + away.x, c.posY, c.posZ + away.z, 1.3);
            }
            EntityPlayer o = owner();
            if (o != null && o.world == world && o.getDistanceSq(this) < 16 * 16) {
                o.addPotionEffect(new PotionEffect(MobEffects.LUCK, 100, 0, true, false));
            }
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_CAT_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_CAT_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_CAT_DEATH; }
    }
}
