package ru.death.chaosshard.thread;

import net.minecraft.entity.player.EntityPlayer;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;

/**
 * Бонусы, которые Нить даёт сама по себе. Работает на обеих сторонах:
 * на клиенте берёт уровень из синхронизированного кэша.
 */
public final class ThreadBonus {

    public static int level(EntityPlayer player) {
        if (player == null || player.world == null) {
            return 0;
        }
        if (player.world.isRemote) {
            return ChaosShard.proxy.clientThreadLevel();
        }
        IThread thread = ThreadCapability.get(player);
        int raw = thread == null ? 0 : ThreadManager.levelFor(thread.getPoints());
        // Святая земля истончает Нить
        return Math.max(0, raw - ru.death.chaosshard.order.Shrines.penalty(player.getUniqueID()));
    }

    /** Множитель силы всех перков Astral Sorcery: 1 + бонус × уровень. */
    public static float perkEffect(EntityPlayer player) {
        return (float) (1.0 + ChaosConfig.perks.perkEffectPerLevel * level(player));
    }

    /** Множитель опыта перков. */
    public static double expGain(EntityPlayer player) {
        return 1.0 + ChaosConfig.perks.expGainPerLevel * level(player);
    }

    private ThreadBonus() {
    }
}
