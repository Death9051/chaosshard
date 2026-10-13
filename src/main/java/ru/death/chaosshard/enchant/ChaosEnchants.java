package ru.death.chaosshard.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;

/**
 * Зачарования Хаоса. Выпадают в обычном столе зачарований и в книгах; Apotheosis поднимает их потолок сам.
 * «Погибель Порядка» — урон по рыцарям Ордена, Светочам и войску Гаррота.
 * «Жатва осколков» — Осколки мира падают чаще (считается в BreachEvents).
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ChaosEnchants {

    public static Enchantment BANE_OF_ORDER, SHARD_HARVEST;

    static final EntityEquipmentSlot[] HAND = {EntityEquipmentSlot.MAINHAND};

    static class Simple extends Enchantment {
        private final int max, minBase, perLevel;

        Simple(String name, Rarity rarity, int max, int minBase, int perLevel) {
            super(rarity, EnumEnchantmentType.WEAPON, HAND);
            this.max = max;
            this.minBase = minBase;
            this.perLevel = perLevel;
            setRegistryName(Tags.MOD_ID, name);
            setName(Tags.MOD_ID + "." + name);
        }

        @Override
        public int getMaxLevel() {
            return max;
        }

        @Override
        public int getMinEnchantability(int level) {
            return minBase + (level - 1) * perLevel;
        }

        @Override
        public int getMaxEnchantability(int level) {
            return getMinEnchantability(level) + 20;
        }
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Enchantment> event) {
        BANE_OF_ORDER = new Simple("bane_of_order", Enchantment.Rarity.UNCOMMON, 5, 5, 8) {
            @Override
            public boolean canApplyTogether(Enchantment other) {
                // как «Небесная кара»: с другими «уронами по виду» не совмещается
                return super.canApplyTogether(other) && !(other instanceof net.minecraft.enchantment.EnchantmentDamage);
            }
        };
        SHARD_HARVEST = new Simple("shard_harvest", Enchantment.Rarity.RARE, 3, 15, 9);
        event.getRegistry().registerAll(BANE_OF_ORDER, SHARD_HARVEST);
    }

    /** Свои враги: Орден Света, Светочи, Гаррот и его тени. */
    public static boolean isOrder(Entity e) {
        return e instanceof ru.death.chaosshard.structure.EntityOrderKnight || e instanceof ru.death.chaosshard.hero.EntityHero
                || e instanceof ru.death.chaosshard.garrot.EntityGarrot;
    }

    public static int level(Enchantment ench, EntityLivingBase holder) {
        if (ench == null || holder == null) {
            return 0;
        }
        ItemStack held = holder.getHeldItemMainhand();
        return held.isEmpty() ? 0 : EnchantmentHelper.getEnchantmentLevel(ench, held);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityLivingBase) || !isOrder(event.getEntityLiving())) {
            return;
        }
        int lvl = level(BANE_OF_ORDER, (EntityLivingBase) event.getSource().getTrueSource());
        if (lvl > 0) {
            event.setAmount(event.getAmount() + 2.5F * lvl);
            if (event.getSource().getTrueSource() instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ru.death.chaosshard.quest.Milestones.grant((net.minecraft.entity.player.EntityPlayerMP) event.getSource().getTrueSource(), "bane_of_order");
            }
        }
    }

    private ChaosEnchants() {
    }
}
