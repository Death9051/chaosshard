package ru.death.chaosshard.servant.entity;

import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityVindicator;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Собственные существа свиты. Повадки — от ванильных родичей, но без их «дикой» натуры:
 * ничего не роняют, не зовут подкрепление, не рождаются детьми и не превращаются.
 */
public final class ServantEntities {

    private ServantEntities() {
    }

    static void tame(net.minecraft.entity.EntityLiving m) {
        for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
            if (s.getSlotType() == EntityEquipmentSlot.Type.ARMOR) {
                m.setItemStackToSlot(s, ItemStack.EMPTY);
            }
            m.setDropChance(s, 0F);
        }
        for (IAttributeInstance a : new IAttributeInstance[]{m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH),
                m.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE),
                m.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE)}) {
            if (a != null) {
                a.removeAllModifiers();
            }
        }
    }

    /** Скорлупа Хаоса и добытчики. */
    public static class Husk extends EntityHusk {
        public Husk(World world) {
            super(world);
        }

        @Nullable
        @Override
        public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
            IEntityLivingData d = super.onInitialSpawn(difficulty, data);
            setChild(false);
            dismountRidingEntity();
            removePassengers();
            IAttributeInstance r = getEntityAttribute(SPAWN_REINFORCEMENTS_CHANCE);
            r.removeAllModifiers();
            r.setBaseValue(0);
            tame(this);
            return d;
        }

        @Override
        public void setChild(boolean child) {
            super.setChild(false);
        }

        @Override
        protected boolean shouldBurnInDay() {
            return false;
        }

        @Nullable
        @Override
        protected ResourceLocation getLootTable() {
            return null;
        }

        @Override
        protected void dropFewItems(boolean recentlyHit, int looting) {
        }

        @Override
        protected boolean canDespawn() {
            return false;
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            // подкрепление не зовём: шанс уже обнулён, но атрибут могут поднять чужие моды
            return super.attackEntityFrom(source, amount);
        }
    }

    public static class Reaper extends Husk {
        public Reaper(World world) {
            super(world);
        }
    }

    public static class Lumberjack extends Husk {
        public Lumberjack(World world) {
            super(world);
        }
    }

    public static class Miner extends Husk {
        public Miner(World world) {
            super(world);
        }
    }

    /** Костяной страж. */
    public static class Bone extends EntityWitherSkeleton {
        public Bone(World world) {
            super(world);
        }

        @Nullable
        @Override
        public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
            IEntityLivingData d = super.onInitialSpawn(difficulty, data);
            tame(this);
            return d;
        }

        /** Ванильный скелет при смене оружия сам добавляет себе ИИ боя — у слуги ИИ ставит хозяин. */
        @Override
        public void setCombatTask() {
        }

        @Nullable
        @Override
        protected ResourceLocation getLootTable() {
            return null;
        }

        @Override
        protected void dropFewItems(boolean recentlyHit, int looting) {
        }

        @Override
        protected boolean canDespawn() {
            return false;
        }
    }

    /** Палач Хаоса. */
    public static class Executioner extends EntityVindicator {
        public Executioner(World world) {
            super(world);
        }

        @Nullable
        @Override
        public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
            IEntityLivingData d = super.onInitialSpawn(difficulty, data);
            tame(this);
            return d;
        }

        @Nullable
        @Override
        protected ResourceLocation getLootTable() {
            return null;
        }

        @Override
        protected void dropFewItems(boolean recentlyHit, int looting) {
        }

        @Override
        protected boolean canDespawn() {
            return false;
        }
    }
}
