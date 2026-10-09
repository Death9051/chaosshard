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
import ru.death.chaosshard.thread.ThreadManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Нить и древо перков Astral Sorcery.
 *
 *  1. Очки перков от Нити. Опытом весь круг древа не набрать (уровень 30 — это ~790 000 опыта),
 *     поэтому Нить доплачивает свободными очками: с каждым уровнем — свою долю,
 *     на максимальной Нити хватает на всё древо. Очки — «жетоны» Astral с именами chaosshard-nit-N;
 *     при понижении Нити лишние жетоны отзываются.
 *  2. Опыт перков растёт с уровнем Нити (событие PostProcessModded, тип expgain).
 *  3. Сила всех перков растёт с уровнем Нити — это миксин в PlayerAttributeMap.
 */
public final class PerkBridge {

    public static final String TOKEN_PREFIX = "chaosshard-nit-";
    private static final int CHECK_EVERY_TICKS = 40;

    /** Сколько очков должна дать Нить на этом уровне. */
    public static int tokensFor(int threadLevel) {
        if (!ChaosConfig.perks.allPerksAtMaxThread) {
            return 0;
        }
        int total = Math.max(0, perksToBuy() - ChaosConfig.perks.levelsFromExp);
        int max = ThreadManager.maxLevel();
        if (max <= 0 || total == 0) {
            return 0;
        }
        int level = Math.max(0, Math.min(threadLevel, max));
        // Округление вверх: на последнем уровне выходит ровно total
        return (total * level + max - 1) / max;
    }

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

    /** Привести жетоны игрока к уровню Нити. Возвращает изменение числа жетонов. */
    public static int syncTokens(EntityPlayerMP player) {
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        if (progress == null || !progress.isValid()) {
            return 0;
        }
        int want = tokensFor(ThreadManager.getLevel(player));
        List<String> have = new ArrayList<>();
        for (String token : progress.getFreePointTokens()) {
            if (token.startsWith(TOKEN_PREFIX)) {
                have.add(token);
            }
        }
        int changed = 0;
        for (String token : have) {
            int index = indexOf(token);
            if (index < 0 || index >= want) {
                if (ResearchManager.revokeFreePoint(player, token)) {
                    changed--;
                }
            }
        }
        for (int i = 0; i < want; i++) {
            String token = TOKEN_PREFIX + i;
            if (!have.contains(token) && ResearchManager.grantFreePerkPoint(player, token)) {
                changed++;
            }
        }
        if (changed != 0) {
            ChaosShard.LOG.info("Нить: очки перков {} изменены на {} (теперь {})", player.getName(), changed, want);
        }
        return changed;
    }

    public static int ownedTokens(EntityPlayer player) {
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        int n = 0;
        if (progress != null) {
            for (String token : progress.getFreePointTokens()) {
                if (token.startsWith(TOKEN_PREFIX)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int indexOf(String token) {
        try {
            return Integer.parseInt(token.substring(TOKEN_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ события

    /** Раз в 2 секунды сверяем жетоны: Astral загружает прогресс игрока не сразу после входа. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER
                || !(event.player instanceof EntityPlayerMP)
                || event.player.ticksExisted % CHECK_EVERY_TICKS != 0) {
            return;
        }
        syncTokens((EntityPlayerMP) event.player);
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
        int level = ThreadManager.getLevel(player);
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        StringBuilder b = new StringBuilder();
        b.append("Перков в древе (без корней): ").append(perksToBuy())
                .append(", ожидается уровней от опыта: ").append(ChaosConfig.perks.levelsFromExp).append('\n');
        b.append("Очков от Нити: должно быть ").append(tokensFor(level)).append(", есть ").append(ownedTokens(player))
                .append(", на макс. Нити будет ").append(tokensFor(ThreadManager.maxLevel())).append('\n');
        if (progress != null && progress.isValid()) {
            b.append("Опыт перков: ").append((long) progress.getPerkExp())
                    .append(", свободных очков сейчас: ").append(progress.getAvailablePerkPoints(player))
                    .append(", взято перков: ").append(progress.getAppliedPerks().size()).append('\n');
        } else {
            b.append("Прогресс Astral игрока ещё не загружен\n");
        }
        b.append(String.format("Сила перков от Нити: x%.2f, опыт перков от Нити: x%.2f",
                ThreadBonus.perkEffect(player), ThreadBonus.expGain(player)));
        return b.toString();
    }
}
