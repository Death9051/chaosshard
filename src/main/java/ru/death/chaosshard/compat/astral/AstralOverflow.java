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

    private AstralOverflow() {
    }
}
