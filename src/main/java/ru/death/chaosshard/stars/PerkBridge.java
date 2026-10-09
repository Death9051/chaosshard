package ru.death.chaosshard.stars;

import hellfirepvp.astralsorcery.common.constellation.perk.AbstractPerk;
import hellfirepvp.astralsorcery.common.constellation.perk.attribute.AttributeTypeRegistry;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.PerkTree;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.PerkTreePoint;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.root.RootPerk;
import hellfirepvp.astralsorcery.common.data.research.PlayerProgress;
import hellfirepvp.astralsorcery.common.data.research.ResearchManager;
import hellfirepvp.astralsorcery.common.event.AttributeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.ArrayList;
import java.util.List;

/**
 * Нить и древо перков Astral Sorcery.
 *
 *  1. Потолок уровня и пологая кривая опыта — миксин в PerkLevelManager (вместо очков от Нити).
 *  2. Опыт перков растёт с уровнем Нити (событие PostProcessModded, тип expgain).
 *  3. Сила всех перков растёт с уровнем Нити — это миксин в PlayerAttributeMap.
 */
public final class PerkBridge {

    public static final String TOKEN_PREFIX = "chaosshard-nit-";
    private static final int CHECK_EVERY_TICKS = 40;

    /** Потолок уровня, который действует в Astral (ставит миксин при чтении конфига). */
    public static volatile int appliedLevelCap = -1;

    /** Перков, за которые надо платить очками: всё древо без корней (корень даётся бесплатно при настройке). */
    public static int perksToBuy() {
        int count = 0;
        for (PerkTreePoint<?> point : PerkTree.PERK_TREE.getPerkPoints()) {
            AbstractPerk perk = point.getPerk();
            if (perk != null && !(perk instanceof RootPerk)) {
                count++;
            }
        }
        return count;
    }

    /** Убрать свободные очки, которые выдавала Нить в версии 0.1.3 (больше не выдаются). */
    public static void revokeOldTokens(EntityPlayerMP player) {
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        if (progress == null || !progress.isValid()) {
            return;
        }
        List<String> old = new ArrayList<>();
        for (String token : progress.getFreePointTokens()) {
            if (token.startsWith(TOKEN_PREFIX)) {
                old.add(token);
            }
        }
        for (String token : old) {
            ResearchManager.revokeFreePoint(player, token);
        }
        if (!old.isEmpty()) {
            ChaosShard.LOG.info("Сняты старые очки перков от Нити у {}: {}", player.getName(), old.size());
        }
    }

    /** Полный опыт для уровня по нашей кривой. */
    public static long totalExpFor(int level) {
        long total = 0;
        for (int i = 1; i <= level; i++) {
            total += ChaosConfig.perks.expCurveBase + (long) ChaosConfig.perks.expCurveStep * i;
        }
        return total;
    }

    // ------------------------------------------------------------------ события

    /** Раз в 2 секунды: Astral загружает прогресс игрока не сразу после входа. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER
                || !(event.player instanceof EntityPlayerMP)
                || event.player.ticksExisted % CHECK_EVERY_TICKS != 0) {
            return;
        }
        revokeOldTokens((EntityPlayerMP) event.player);
    }

    /** Опыт перков. Высокий приоритет — чтобы ограничитель Astral отработал уже после нас. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onModded(AttributeEvent.PostProcessModded event) {
        if (event.getPlayer() != null
                && AttributeTypeRegistry.ATTR_TYPE_INC_PERK_EXP.equals(event.getType().getTypeString())) {
            event.setValue(event.getValue() * ThreadBonus.expGain(event.getPlayer()));
        }
    }

    public static String debug(EntityPlayerMP player) {
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        StringBuilder b = new StringBuilder();
        b.append("Перков в древе (без корней): ").append(perksToBuy())
                .append(", потолок уровня Astral: ").append(appliedLevelCap)
                .append(", потолок игрока: ").append(hellfirepvp.astralsorcery.common.constellation.perk.PerkLevelManager.getLevelCapFor(player)).append('\n');
        b.append("Кривая: ур.10 = ").append(totalExpFor(10)).append(", ур.30 = ").append(totalExpFor(30))
                .append(", ур.100 = ").append(totalExpFor(100)).append(", ур.320 = ").append(totalExpFor(320)).append(" опыта\n");
        if (progress != null && progress.isValid()) {
            b.append("Опыт перков: ").append((long) progress.getPerkExp())
                    .append(", уровень: ").append(hellfirepvp.astralsorcery.common.constellation.perk.PerkLevelManager.INSTANCE.getLevel(progress.getPerkExp(), player))
                    .append(", свободных очков: ").append(progress.getAvailablePerkPoints(player))
                    .append(", взято перков: ").append(progress.getAppliedPerks().size()).append('\n');
        } else {
            b.append("Прогресс Astral игрока ещё не загружен\n");
        }
        b.append(String.format("Сила перков от Нити: x%.2f, опыт перков от Нити: x%.2f",
                ThreadBonus.perkEffect(player), ThreadBonus.expGain(player)));
        return b.toString();
    }
}
