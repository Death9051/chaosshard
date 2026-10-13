package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.hero.ItemHeroEcho;
import ru.death.chaosshard.hero.Lightbearer;
import slimeknights.tconstruct.library.modifiers.ModifierTrait;

/**
 * Отголоски Светочей — модификаторы Tinkers' Construct. Каждый — приём павшего героя.
 * Один отголосок — один модификатор; на инструмент только один уровень каждого.
 */
public final class EchoModifiers {

    static final ModifierTrait[] ALL = new ModifierTrait[Lightbearer.values().length];

    /** Общая часть: веха за первый инструмент с отголоском. */
    abstract static class Echo extends ModifierTrait {
        Echo(Lightbearer h, int color) {
            super("chaosshard_echo_" + h.id(), color, 1, 1);
        }

        @Override
        public void onUpdate(ItemStack tool, World world, Entity entity, int slot, boolean selected) {
            if (!world.isRemote && entity instanceof EntityPlayerMP && world.getTotalWorldTime() % 40 == 0) {
                ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) entity, "echo_applied");
            }
            if (selected && !world.isRemote && entity instanceof EntityPlayer && world.getTotalWorldTime() % 20 == 0) {
                held((EntityPlayer) entity);
            }
        }

        /** Раз в секунду, пока инструмент в руке. */
        void held(EntityPlayer p) {
        }
    }

    public static void preInit() {
        // Каэль: удар иногда ослепляет вспышкой
        ALL[0] = new Echo(Lightbearer.KAEL, 0xFFD060) {
            @Override
            public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float dmg, boolean crit, boolean hit) {
                if (hit && !target.world.isRemote && target.world.rand.nextFloat() < 0.2F) {
                    target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0));
                    target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 1));
                    ((WorldServer) target.world).spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, target.posX, target.posY + target.height / 2,
                            target.posZ, 20, 0.3, 0.3, 0.3, 0.1);
                }
            }
        };
        // Ирена: добивающий удар лечит
        ALL[1] = new Echo(Lightbearer.IRENA, 0x9AD4FF) {
            @Override
            public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float dmg, boolean crit, boolean hit) {
                if (hit && !target.world.isRemote && target.getHealth() <= 0) {
                    player.heal(4.0F);
                    ((WorldServer) player.world).spawnParticle(EnumParticleTypes.HEART, player.posX, player.posY + 2, player.posZ, 3, 0.3, 0.2, 0.3, 0);
                }
            }
        };
        // Вельмор: удар поджигает
        ALL[2] = new Echo(Lightbearer.VELMOR, 0xC080FF) {
            @Override
            public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float dmg, boolean crit, boolean hit) {
                if (hit && !target.world.isRemote && target.world.rand.nextFloat() < 0.3F) {
                    target.setFire(4);
                }
            }
        };
        // Лиан: в руке — песня в ногах (Скорость I)
        ALL[3] = new Echo(Lightbearer.LIAN, 0xFFA040) {
            @Override
            void held(EntityPlayer p) {
                p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 50, 0, true, false));
            }
        };
        // Элис: в руке — королевская выучка (Сопротивление I)
        ALL[4] = new Echo(Lightbearer.ELIS, 0xE04040) {
            @Override
            void held(EntityPlayer p) {
                p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 50, 0, true, false));
            }
        };
    }

    public static void init() {
        for (Lightbearer h : Lightbearer.values()) {
            if (ALL[h.ordinal()] != null) {
                ALL[h.ordinal()].addItem(ItemHeroEcho.of(h), 1, 1);
            }
        }
    }

    private EchoModifiers() {
    }
}
