package ru.death.chaosshard.spell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketEnergy;
import ru.death.chaosshard.thread.ThreadBonus;

/**
 * Энергия Хаоса — то, на что Гримуар читает заклинания.
 * Запас растёт с Нитью, восстанавливается сама (в Преддверии быстрее). После смерти — полная.
 */
public class ChaosEnergy {

    private static final String TAG = "chaosshard_energy";
    private static final String TAG_SENT = "chaosshard_energy_sent";

    public static int max(EntityPlayer player) {
        int core = ru.death.chaosshard.compat.body.Body.hasCore(player) ? ChaosConfig.body.coreEnergy : 0;
        int perks = (ru.death.chaosshard.stars.Perks.spark(player) ? 10 : 0) + (ru.death.chaosshard.stars.Perks.well(player) ? 30 : 0);
        return ChaosConfig.magic.energyBase + ChaosConfig.magic.energyPerLevel * ThreadBonus.level(player) + core + perks
                + SpellMods.maxEnergy(player);
    }

    public static float get(EntityPlayer player) {
        NBTTagCompound d = player.getEntityData();
        return d.hasKey(TAG) ? Math.min(d.getFloat(TAG), max(player)) : max(player);
    }

    public static void set(EntityPlayerMP player, float value) {
        float v = Math.max(0, Math.min(value, max(player)));
        player.getEntityData().setFloat(TAG, v);
        sync(player);
    }

    public static void spend(EntityPlayerMP player, int amount) {
        set(player, get(player) - amount);
    }

    public static void sync(EntityPlayerMP player) {
        if (player.connection == null) {
            return;   // подставной игрок модов
        }
        int e = (int) get(player);
        int m = max(player);
        player.getEntityData().setInteger(TAG_SENT, e * 1000 + m);
        ChaosNetwork.CHANNEL.sendTo(new PacketEnergy(e, m), player);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || !(event.player instanceof EntityPlayerMP)
                || event.player.ticksExisted % 20 != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ru.death.chaosshard.compat.body.Body.tick(player);
        float now = get(player);
        int max = max(player);
        if (now < max) {
            double regen = ChaosConfig.magic.regenPerSecond;
            if (AntechamberDimension.is(player.world)) {
                regen *= ChaosConfig.magic.antechamberRegenMultiplier;
            } else {
                regen *= ru.death.chaosshard.anchor.AnchorNet.regen(player);
            }
            if (ru.death.chaosshard.compat.body.Body.hasCore(player)) {
                regen *= 1.5;
            }
            regen *= ru.death.chaosshard.lair.TileLair.energyMultiplier(player);
            if (ru.death.chaosshard.stars.Perks.night(player)) {
                regen *= 1.25;
            }
            if (ru.death.chaosshard.hero.TrophyData.has(player, ru.death.chaosshard.hero.Lightbearer.VELMOR)) {
                regen *= 1.25;   // Посох Вельмора в Логове: тайное знание
            }
            player.getEntityData().setFloat(TAG, (float) Math.min(max, now + regen));
        }
        int key = (int) get(player) * 1000 + max;
        if (player.getEntityData().getInteger(TAG_SENT) != key) {
            sync(player);
        }
    }

    /** Голод Скверны (перк): убийство возвращает 5 энергии. */
    @SubscribeEvent
    public void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getTrueSource() instanceof EntityPlayerMP
                && !ru.death.chaosshard.anchor.AnchorSpecs.isAnchorHit(event.getSource())) {
            EntityPlayerMP p = (EntityPlayerMP) event.getSource().getTrueSource();
            if (ru.death.chaosshard.stars.Perks.hunger(p)) {
                set(p, get(p) + 5);
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            event.player.getEntityData().removeTag(TAG);
            sync((EntityPlayerMP) event.player);
        }
    }
}
