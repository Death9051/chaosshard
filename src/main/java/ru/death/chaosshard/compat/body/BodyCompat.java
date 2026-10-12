package ru.death.chaosshard.compat.body;

import flaxbeard.cyberware.api.CyberwareAPI;
import flaxbeard.cyberware.api.CyberwareSurgeryEvent;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.UUID;

/**
 * Тело Осколка поверх Robotic Parts: импланты Хаоса и стабильность конструкта (толерантность), растущая с Нитью.
 * Класс трогается только если мод cyberware загружен (см. Body).
 */
public final class BodyCompat {

    private static final UUID TOLERANCE_ID = UUID.fromString("6c1f2f3e-4d2a-4c6b-9d1e-0a7c5b3e9f11");
    public static ItemChaosImplant IMPLANT;

    static void registerItems() {
        IMPLANT = new ItemChaosImplant();
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register(new BodyCompat.Events());
    }

    static boolean has(EntityPlayer player, int meta) {
        if (IMPLANT == null || player == null || !CyberwareAPI.hasCapability(player)) {
            return false;
        }
        return CyberwareAPI.isCyberwareInstalled(player, new ItemStack(IMPLANT, 1, meta));
    }

    /** Стабильность конструкта: + tolerancePerLevel за каждый уровень Нити. */
    static void updateTolerance(EntityPlayerMP player) {
        IAttributeInstance inst = player.getEntityAttribute(CyberwareAPI.TOLERANCE_ATTR);
        if (inst == null) {
            return;
        }
        double want = ChaosConfig.body.tolerancePerLevel * ThreadBonus.level(player);
        AttributeModifier cur = inst.getModifier(TOLERANCE_ID);
        if (cur != null && cur.getAmount() == want) {
            return;
        }
        if (cur != null) {
            inst.removeModifier(TOLERANCE_ID);
        }
        if (want > 0) {
            inst.applyModifier(new AttributeModifier(TOLERANCE_ID, "chaosshard thread stability", want, 0).setSaved(false));
        }
    }

    /** Панцирь Скверны: на осквернённой земле — регенерация и сопротивление. */
    static void tickCarapace(EntityPlayerMP player) {
        if (!has(player, ItemChaosImplant.CARAPACE)) {
            return;
        }
        if (ru.death.chaosshard.anchor.TileAnchor.energyMultiplier(player.world, player.getPosition()) > 1.0) {
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 60, 0, true, false));
            player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 60, 0, true, false));
        }
    }

    public static final class Events {
        @SubscribeEvent
        public void onSurgery(CyberwareSurgeryEvent.Post event) {
            if (!(event.getEntity() instanceof EntityPlayerMP)) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) event.getEntity();
            Milestones.grant(p, Milestones.IMPLANT);
            if (has(p, ItemChaosImplant.CORE)) {
                Milestones.grant(p, Milestones.CHAOS_CORE);
            }
        }
    }

    private BodyCompat() {
    }
}
