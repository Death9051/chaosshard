package ru.death.chaosshard.thread;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketThreadSync;

/**
 * Вся логика Нити на сервере: очки, уровни, этапы Game Stages, сообщения Гримуара.
 *
 * Внешние источники Нити (Якоря Хаоса, ритуалы, квесты) вызывают {@link #addPoints}
 * или команду: /chaosshard nit add <игрок> <очки>
 */
public final class ThreadManager {

    /** Уровень по числу очков: 0, если не набран первый порог. */
    public static int levelFor(int points) {
        int[] thresholds = ChaosConfig.thread.pointsPerLevel;
        int level = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (points >= thresholds[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return level;
    }

    public static int maxLevel() {
        return ChaosConfig.thread.pointsPerLevel.length;
    }

    /** Порог следующего уровня или -1, если уровень максимальный. */
    public static int nextThreshold(int level) {
        int[] thresholds = ChaosConfig.thread.pointsPerLevel;
        return level < thresholds.length ? thresholds[level] : -1;
    }

    public static int getPoints(EntityPlayerMP player) {
        IThread thread = ThreadCapability.get(player);
        return thread == null ? 0 : thread.getPoints();
    }

    public static int getLevel(EntityPlayerMP player) {
        return levelFor(getPoints(player));
    }

    /** Подставной игрок модов (Жатва бьёт мобов от его имени): Нити у него нет и слать ему некуда. */
    static boolean ghost(EntityPlayerMP player) {
        return player == null || player.connection == null;
    }

    public static void addPoints(EntityPlayerMP player, int delta) {
        if (ghost(player)) {
            return;
        }
        // Приёмник Нити (имплант Хаоса): прибавка Нити на 25% больше
        if (delta > 0 && ru.death.chaosshard.compat.body.Body.hasReceiver(player)) {
            delta = (int) Math.ceil(delta * 1.25);
        }
        int before = getPoints(player);
        int after = before + delta;
        int cap = cap();
        if (delta > 0 && after > cap) {
            // Нить полна: избыток не копится, а переливается в зверей и звёзды
            int keep = Math.max(cap, before);
            int over = after - keep;
            after = keep;
            if (over > 0) {
                overflow(player, over);
            }
        }
        setPoints(player, after);
    }

    /** Последний порог: выше Нить не поднимается. */
    public static int cap() {
        int[] t = ChaosConfig.thread.pointsPerLevel;
        return t.length == 0 ? Integer.MAX_VALUE : t[t.length - 1];
    }

    /** Перелив избытка Нити в прокачку. Возвращает {опыт каждому зверю, число зверей, опыт перков}. */
    /** Сколько Нити перелилось за всё время работы сервера (для автотеста). */
    public static long overflowed;

    public static int[] overflow(EntityPlayerMP player, int over) {
        overflowed += over;
        int beastXp = over * Math.max(0, ChaosConfig.thread.overflowBeastXp);
        int beasts = beastXp > 0 ? ru.death.chaosshard.servant.Beasts.overflowXp(player, beastXp) : 0;
        int astral = 0;
        if (ChaosShard.astralLoaded && ChaosConfig.thread.overflowAstralExp > 0) {
            astral = over * ChaosConfig.thread.overflowAstralExp;
            ru.death.chaosshard.compat.astral.AstralOverflow.give(player, astral);
        }
        if (beasts > 0 || astral > 0) {
            player.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.thread.overflow",
                    over, beasts > 0 ? beastXp : 0, astral), true);
        }
        return new int[]{beasts > 0 ? beastXp : 0, beasts, astral};
    }

    public static void setPoints(EntityPlayerMP player, int points) {
        IThread thread = ThreadCapability.get(player);
        if (thread == null) {
            return;
        }
        thread.setPoints(points);
        applyLevel(player, thread, true);
        sync(player);
    }

    /**
     * Приводит выданные этапы к текущему уровню.
     * @param announce сообщать ли игроку о новых уровнях
     */
    public static void applyLevel(EntityPlayerMP player, IThread thread, boolean announce) {
        int level = levelFor(thread.getPoints());
        int announced = thread.getAnnouncedLevel();
        if (level > announced) {
            for (int l = announced + 1; l <= level; l++) {
                runStageCommand(player, ChaosConfig.thread.stageAddCommand, l);
                if (announce && l >= 1) {
                    TextComponentTranslation msg = new TextComponentTranslation("chaosshard.thread.levelup." + Math.min(l, 5), l);
                    msg.getStyle().setColor(TextFormatting.DARK_PURPLE).setItalic(true);
                    player.sendMessage(msg);
                }
            }
        } else if (level < announced) {
            for (int l = announced; l > level; l--) {
                runStageCommand(player, ChaosConfig.thread.stageRemoveCommand, l);
            }
        }
        thread.setAnnouncedLevel(level);
    }

    /**
     * Первый вход: выдать этап 0 (<префикс>0). Он нужен, чтобы Astral Sorcery
     * ограничивал уровень перков с самого начала: без единого этапа с лимитом
     * Astral даёт полный потолок.
     */
    public static void initialize(EntityPlayerMP player) {
        IThread thread = ThreadCapability.get(player);
        if (thread != null && thread.getAnnouncedLevel() < 0) {
            applyLevel(player, thread, true);
        }
    }

    /** Повторно выдать все этапы до текущего уровня (на случай, если Game Stages поставили позже). */
    public static void reapplyStages(EntityPlayerMP player) {
        IThread thread = ThreadCapability.get(player);
        if (thread == null) {
            return;
        }
        int level = levelFor(thread.getPoints());
        for (int l = 0; l <= level; l++) {
            runStageCommand(player, ChaosConfig.thread.stageAddCommand, l);
        }
        thread.setAnnouncedLevel(level);
    }

    private static void runStageCommand(EntityPlayerMP player, String template, int level) {
        if (template == null || template.trim().isEmpty()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String command = template
                .replace("{player}", player.getName())
                .replace("{stage}", ChaosConfig.thread.stagePrefix + level);
        try {
            server.getCommandManager().executeCommand(server, command);
        } catch (Exception e) {
            ChaosShard.LOG.warn("Не удалось выполнить команду этапа Нити: {}", command, e);
        }
    }

    public static void sync(EntityPlayerMP player) {
        if (ghost(player)) {
            return;
        }
        ChaosNetwork.CHANNEL.sendTo(new PacketThreadSync(getPoints(player)), player);
    }

    private ThreadManager() {
    }
}
