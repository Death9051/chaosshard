package ru.death.chaosshard.compat.astral;

import hellfirepvp.astralsorcery.common.data.research.ResearchManager;
import net.minecraft.entity.player.EntityPlayerMP;

/** Перелив Нити в опыт перков Astral Sorcery. Класс грузится только при установленном Astral. */
public final class AstralOverflow {

    public static void give(EntityPlayerMP player, int exp) {
        ResearchManager.modifyExp(player, exp);
    }

    public static double exp(EntityPlayerMP player) {
        return ResearchManager.getProgress(player, net.minecraftforge.fml.relauncher.Side.SERVER).getPerkExp();
    }

    /** Главное созвездие настройки ("aevitas", "discidia", ...) или null; "chaos" — Связь с Хаосом. */
    public static String attuned(net.minecraft.entity.player.EntityPlayer p) {
        if (!p.world.isRemote && ru.death.chaosshard.stars.ChaosLink.linked(p)) {
            return "chaos";
        }
        hellfirepvp.astralsorcery.common.data.research.PlayerProgress pr = ResearchManager.getProgress(p,
                p.world.isRemote ? net.minecraftforge.fml.relauncher.Side.CLIENT : net.minecraftforge.fml.relauncher.Side.SERVER);
        if (pr == null || pr.getAttunedConstellation() == null) {
            return null;
        }
        return pr.getAttunedConstellation().getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }

    private AstralOverflow() {
    }
}
