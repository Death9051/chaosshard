package ru.death.chaosshard.quest;

import hellfirepvp.astralsorcery.common.data.research.PlayerProgress;
import hellfirepvp.astralsorcery.common.data.research.ResearchManager;
import hellfirepvp.astralsorcery.common.tile.TileRitualPedestal;
import hellfirepvp.astralsorcery.common.tile.TileStarlightInfuser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import ru.death.chaosshard.init.ModItems;

/** Вехи звёздной ветки (только при Astral Sorcery). */
final class AstralMilestones {

    static final String CHAOS = "star_chaos";
    static final String PERK = "star_perk";
    static final String RITUAL = "star_ritual";
    static final String PURIFY = "star_purify";

    static void check(EntityPlayerMP player) {
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        if (progress != null) {
            if (progress.hasConstellationDiscovered("astralsorcery.constellation.chaos")) {
                Milestones.grant(player, CHAOS);
            }
            // Корневой перк даётся при настройке — первый выбранный идёт вторым
            if (progress.getAppliedPerks().size() >= 2) {
                Milestones.grant(player, PERK);
            }
        }
        boolean needRitual = !Milestones.has(player, RITUAL);
        boolean needPurify = !Milestones.has(player, PURIFY);
        if (!needRitual && !needPurify) {
            return;
        }
        for (TileEntity te : player.world.loadedTileEntityList) {
            if (needRitual && te instanceof TileRitualPedestal) {
                TileRitualPedestal p = (TileRitualPedestal) te;
                if (p.isWorking() && player.getUniqueID().equals(p.getOwnerUUID())) {
                    Milestones.grant(player, RITUAL);
                    needRitual = false;
                }
            } else if (needPurify && te instanceof TileStarlightInfuser
                    && te.getPos().distanceSq(player.getPosition()) < 16 * 16) {
                ItemStack in = ((TileStarlightInfuser) te).getInputStack();
                if (!in.isEmpty() && in.getItem() == ModItems.CHAOS_ESSENCE) {
                    Milestones.grant(player, PURIFY);
                    needPurify = false;
                }
            }
        }
    }

    private AstralMilestones() {
    }
}
