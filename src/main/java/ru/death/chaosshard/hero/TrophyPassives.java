package ru.death.chaosshard.hero;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.servant.Servants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Пассивки трофеев Светочей, стоящих в Логове хозяина.
 *  Каэль — первый удар по новой цели слепит её на 2 с.
 *  Ирена — слуги и звери рядом помалу лечатся.
 *  Вельмор — энергия Хаоса восстанавливается на 25% быстрее (см. ChaosEnergy).
 *  Лиан — скорость ходьбы +10%.
 *  Элис — жители торгуют дешевле (на четверть, при первом разговоре).
 */
public final class TrophyPassives {

    private static final UUID LIAN_SPEED = UUID.fromString("6b2c7d1e-4a7f-4c5e-9f33-1a2b3c4d5e6f");
    public static final String TAG_ELIS = "chaosshard_elis_discount";
    private static final Map<UUID, Integer> LAST_TARGET = new HashMap<>();

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase t = event.getEntityLiving();
        if (t.world.isRemote || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)
                || event.getSource().getTrueSource() != event.getSource().getImmediateSource()) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.getSource().getTrueSource();
        if (!TrophyData.has(p, Lightbearer.KAEL)) {
            return;
        }
        Integer last = LAST_TARGET.put(p.getUniqueID(), t.getEntityId());
        if (last == null || last != t.getEntityId()) {
            t.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 40, 0));
            t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 0));
            ((WorldServer) t.world).spawnParticle(EnumParticleTypes.END_ROD, t.posX, t.posY + t.height, t.posZ, 10, 0.3, 0.2, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || event.player.ticksExisted % 100 != 0) {
            return;
        }
        EntityPlayer p = event.player;
        lian(p, TrophyData.has(p, Lightbearer.LIAN));
        if (TrophyData.has(p, Lightbearer.IRENA)) {
            for (EntityLiving e : Servants.near(p, 48)) {
                if (e.getHealth() < e.getMaxHealth()) {
                    e.heal(2.0F);
                    ((WorldServer) p.world).spawnParticle(EnumParticleTypes.HEART, e.posX, e.posY + e.height + 0.2, e.posZ, 1, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }

    /** Лютня Лиана: +10% к скорости ходьбы, пока трофей в Логове. */
    public static void lian(EntityPlayer p, boolean on) {
        IAttributeInstance a = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        boolean has = a.getModifier(LIAN_SPEED) != null;
        if (on && !has) {
            a.applyModifier(new AttributeModifier(LIAN_SPEED, "chaosshard_lian", 0.1, 1).setSaved(false));
        } else if (!on && has) {
            a.removeModifier(LIAN_SPEED);
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote || !(event.getTarget() instanceof EntityVillager)) {
            return;
        }
        EntityPlayer p = event.getEntityPlayer();
        if (TrophyData.has(p, Lightbearer.ELIS)) {
            discount((EntityVillager) event.getTarget(), p);
        }
    }

    /** Перстень Элис: цены жителя на четверть ниже (один раз на жителя). */
    public static boolean discount(EntityVillager v, EntityPlayer p) {
        if (v.getEntityData().getBoolean(TAG_ELIS)) {
            return false;
        }
        MerchantRecipeList list = v.getRecipes(p);
        if (list == null) {
            return false;
        }
        for (MerchantRecipe r : list) {
            ItemStack buy = r.getItemToBuy();
            if (buy.getCount() > 1) {
                buy.setCount(Math.max(1, (int) Math.ceil(buy.getCount() * 0.75)));
            }
        }
        v.getEntityData().setBoolean(TAG_ELIS, true);
        return true;
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        LAST_TARGET.clear();
    }
}
