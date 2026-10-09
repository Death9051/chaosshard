package ru.death.chaosshard.command;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.stars.SkyRules;
import ru.death.chaosshard.thread.IThread;
import ru.death.chaosshard.thread.ThreadCapability;
import ru.death.chaosshard.thread.ThreadManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

/**
 * Полный отчёт для отладки: /oh debug → файл chaosshard-debug.txt в папке игры.
 * Каждый раздел защищён try/catch: ошибка в одном не ломает весь отчёт.
 */
public final class DebugReport {

    public static final String FILE_NAME = "chaosshard-debug.txt";

    private final StringBuilder out = new StringBuilder();
    private int problems;

    public static DebugReport build(MinecraftServer server, EntityPlayerMP player) {
        DebugReport r = new DebugReport();
        r.line("=== Осколок Хаоса — отладочный отчёт ===");
        r.line("Время: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        r.section("Версии", () -> r.versions());
        r.section("Конфиг", () -> r.config());
        r.section("Игрок и мир", () -> r.playerAndWorld(player));
        r.section("Нить", () -> r.thread(player));
        r.section("Game Stages", () -> r.stages(player));
        r.section("Флаги игрока", () -> r.flags(player));
        r.section("Измерения", () -> r.dimensions(server));
        r.section("Better Questing", () -> r.questing());
        r.section("Astral Sorcery", () -> r.astral(player));
        r.line("");
        r.line("Итог: найдено проблем — " + r.problems);
        return r;
    }

    public int problems() {
        return problems;
    }

    public File write() throws Exception {
        File file = new File(FILE_NAME).getAbsoluteFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(out.toString());
        }
        return file;
    }

    // ------------------------------------------------------------------ разделы

    private void versions() {
        line("chaosshard: " + Tags.VERSION);
        for (String id : new String[]{"forge", "mixinbooter", "astralsorcery", "baubles", "gamestages", "bookshelf",
                "crafttweaker", "betterquesting", "bq_standard", "mystcraft", "cyberware", "metamorph", "universaltweaks", "vintagefix"}) {
            ModContainer mod = Loader.instance().getIndexedModList().get(id);
            line(String.format("  %-16s %s", id, mod == null ? "НЕТ" : mod.getVersion()));
        }
        if (ChaosShard.astralLoaded && !Loader.isModLoaded("mixinbooter")) {
            problem("Astral есть, MixinBooter нет — правила неба не работают");
        }
    }

    private void config() {
        line("antechamber.dimensionId = " + ChaosConfig.antechamber.dimensionId);
        line("antechamber.islandSurfaceY = " + ChaosConfig.antechamber.islandSurfaceY + ", radius = " + ChaosConfig.antechamber.islandRadius);
        line("antechamber.teleportOnFirstJoin = " + ChaosConfig.antechamber.teleportOnFirstJoin
                + ", respawnInAntechamber = " + ChaosConfig.antechamber.respawnInAntechamber);
        line("thread.pointsPerLevel = " + Arrays.toString(ChaosConfig.thread.pointsPerLevel)
                + ", stagePrefix = " + ChaosConfig.thread.stagePrefix);
        line("thread.stageAddCommand = " + ChaosConfig.thread.stageAddCommand);
        line("stars.enabled = " + ChaosConfig.stars.enabled + ", luchezariya = " + Arrays.toString(ChaosConfig.stars.luchezariyaDimensions));
        line("stars.allConst/maxLight/chaosOnly/drawOther/extraPos = " + ChaosConfig.stars.antechamberAllConstellations + "/"
                + ChaosConfig.stars.antechamberMaxStarlight + "/" + ChaosConfig.stars.chaosOnlyElsewhere + "/"
                + ChaosConfig.stars.drawChaosInOtherSkies + "/" + ChaosConfig.stars.extraSkyPositions);
    }

    private void playerAndWorld(EntityPlayerMP player) {
        World w = player.world;
        BlockPos pos = player.getPosition();
        line("Игрок: " + player.getName() + " @ " + pos + ", измерение " + player.dimension);
        line("Провайдер: " + w.provider.getClass().getName());
        line("Правила неба: " + SkyRules.classify(w));
        line("Время мира: " + w.getWorldTime() + ", угол неба: " + w.getCelestialAngle(1.0F)
                + ", дождь: " + w.getRainStrength(1.0F) + ", гроза: " + w.getThunderStrength(1.0F));
        line("Видит небо: " + w.canSeeSky(pos.up()) + ", isSurfaceWorld: " + w.provider.isSurfaceWorld()
                + ", hasSkyLight: " + w.provider.hasSkyLight());
        line("Свет неба / блоков у ног: " + w.getLightFromNeighbors(pos));
        if (AntechamberDimension.is(w)) {
            BlockPos spawn = AntechamberDimension.spawnPoint();
            line("Точка появления Преддверия: " + spawn + ", под ней: " + w.getBlockState(spawn.down()).getBlock().getRegistryName());
            if (w.isAirBlock(spawn.down())) {
                problem("под точкой появления Преддверия пусто");
            }
            if (w.getRainStrength(1.0F) > 0.01F) {
                problem("в Преддверии идёт дождь");
            }
        }
        line("Кровать (здесь): " + player.getBedLocation(player.dimension));
    }

    private void thread(EntityPlayerMP player) {
        IThread t = ThreadCapability.get(player);
        if (t == null) {
            problem("у игрока нет capability Нити");
            return;
        }
        int level = ThreadManager.levelFor(t.getPoints());
        line("Очки: " + t.getPoints() + ", уровень: " + level + ", объявленный уровень: " + t.getAnnouncedLevel()
                + ", до следующего: " + ThreadManager.nextThreshold(level));
        if (t.getAnnouncedLevel() != level) {
            problem("объявленный уровень не совпадает с текущим (этапы могли не выдаться)");
        }
    }

    private void stages(EntityPlayerMP player) throws Exception {
        if (!Loader.isModLoaded("gamestages")) {
            line("Game Stages не установлен");
            return;
        }
        Class<?> helper = Class.forName("net.darkhax.gamestages.GameStageHelper");
        Object data = helper.getMethod("getPlayerData", EntityPlayer.class).invoke(null, player);
        if (data == null) {
            problem("Game Stages не вернул данные игрока");
            return;
        }
        Collection<?> stages = (Collection<?>) data.getClass().getMethod("getStages").invoke(data);
        line("Этапы: " + stages);
        int level = ThreadManager.levelFor(ThreadManager.getPoints(player));
        for (int l = 0; l <= level; l++) {
            String stage = ChaosConfig.thread.stagePrefix + l;
            if (!stages.contains(stage)) {
                problem("нет этапа " + stage);
            }
        }
    }

    private void flags(EntityPlayerMP player) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        line("chaosshard_arrived = " + persisted.getBoolean("chaosshard_arrived")
                + ", chaosshard_grimoire_given = " + persisted.getBoolean("chaosshard_grimoire_given")
                + ", прорыв = " + persisted.getBoolean("chaosshard_breached"));
        line("config breach: нить >= " + ChaosConfig.breach.threadLevel + ", осколок Завесы " + ChaosConfig.breach.veilShardChance
                + ", обрывок памяти " + ChaosConfig.breach.memoryFragmentChance);
        boolean hasGrimoire = false;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (player.inventory.getStackInSlot(i).getItem() == ModItems.GRIMOIRE) {
                hasGrimoire = true;
            }
        }
        line("Гримуар в инвентаре: " + hasGrimoire);
    }

    private void dimensions(MinecraftServer server) {
        line("Преддверие зарегистрировано: " + DimensionManager.isDimensionRegistered(AntechamberDimension.id())
                + ", тип: " + AntechamberDimension.TYPE);
        for (Integer id : DimensionManager.getIDs()) {
            World w = DimensionManager.getWorld(id);
            line(String.format("  %4d %-12s %s", id, w == null ? "-" : SkyRules.classify(w).toString(),
                    w == null ? "не загружен" : w.provider.getClass().getSimpleName()));
        }
    }

    private void questing() throws Exception {
        if (!Loader.isModLoaded("betterquesting")) {
            line("Better Questing не установлен");
            return;
        }
        Object quests = Class.forName("betterquesting.questing.QuestDatabase").getField("INSTANCE").get(null);
        Object lines = Class.forName("betterquesting.questing.QuestLineDatabase").getField("INSTANCE").get(null);
        int q = (Integer) quests.getClass().getMethod("size").invoke(quests);
        int l = (Integer) lines.getClass().getMethod("size").invoke(lines);
        line("Квестов в мире: " + q + ", глав: " + l);
        File def = new File("config/betterquesting/DefaultQuests.json");
        line("DefaultQuests.json: " + (def.exists() ? def.length() + " байт" : "НЕТ"));
        if (q == 0) {
            problem("в мире нет квестов — выполните /bq_admin default load");
        }
    }

    private void astral(EntityPlayerMP player) throws Exception {
        if (!ChaosShard.astralLoaded) {
            line("Astral Sorcery не установлен");
            return;
        }
        Class<?> compat = Class.forName("ru.death.chaosshard.stars.AstralCompat");
        Object chaos = compat.getField("CHAOS").get(null);
        line("Созвездие Хаоса: " + (chaos == null ? "НЕ зарегистрировано" : "зарегистрировано"));
        if (chaos == null && ChaosConfig.stars.enabled) {
            problem("созвездие Хаоса не зарегистрировано");
        }
        line("Миксины: WorldSkyHandler=" + mixed("hellfirepvp.astralsorcery.common.constellation.distribution.WorldSkyHandler")
                + ", ConstellationSkyHandler=" + mixed("hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler")
                + ", SkyCollectionHelper=" + mixed("hellfirepvp.astralsorcery.common.util.SkyCollectionHelper"));
        line(String.valueOf(compat.getMethod("debugSky", EntityPlayerMP.class).invoke(null, player)));
    }

    // ------------------------------------------------------------------ служебное

    private static String mixed(String className) {
        try {
            for (Method m : Class.forName(className).getDeclaredMethods()) {
                if (m.getName().contains("chaosshard$")) {
                    return "да";
                }
            }
            return "НЕТ";
        } catch (Throwable t) {
            return "ошибка: " + t;
        }
    }

    private interface Part {
        void run() throws Exception;
    }

    private void section(String title, Part part) {
        line("");
        line("--- " + title + " ---");
        try {
            part.run();
        } catch (Throwable t) {
            problem("раздел упал: " + t);
        }
    }

    private void line(String s) {
        out.append(s).append('\n');
    }

    private void problem(String s) {
        problems++;
        line("!! ПРОБЛЕМА: " + s);
    }
}
