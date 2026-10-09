package ru.death.chaosshard;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Настройки мода. Файл: config/chaosshard.cfg
 */
@Config(modid = Tags.MOD_ID, name = "chaosshard")
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ChaosConfig {

    @Config.Comment("Преддверие — стартовое измерение Осколка")
    @Config.LangKey("chaosshard.config.antechamber")
    public static final AntechamberCfg antechamber = new AntechamberCfg();

    @Config.Comment("Нить — связь Осколка с Лордом")
    @Config.LangKey("chaosshard.config.thread")
    public static final ThreadCfg thread = new ThreadCfg();

    @Config.Comment("Звёзды — связка с Astral Sorcery")
    @Config.LangKey("chaosshard.config.stars")
    public static final StarsCfg stars = new StarsCfg();

    public static final class AntechamberCfg {
        @Config.Comment("ID измерения Преддверия. Меняйте только на новом мире.")
        @Config.RequiresMcRestart
        public int dimensionId = 66;

        @Config.Comment("Высота поверхности острова. Алтари Astral Sorcery собирают полный свет с Y >= 120.")
        @Config.RangeInt(min = 64, max = 240)
        @Config.RequiresWorldRestart
        public int islandSurfaceY = 130;

        @Config.Comment("Радиус острова в блоках")
        @Config.RangeInt(min = 8, max = 200)
        @Config.RequiresWorldRestart
        public int islandRadius = 48;

        @Config.Comment("При первом входе в мир переносить игрока в Преддверие")
        public boolean teleportOnFirstJoin = true;

        @Config.Comment("Если у игрока нет кровати, после смерти тело Осколка собирается заново в Преддверии")
        public boolean respawnInAntechamber = true;
    }

    public static final class ThreadCfg {
        @Config.Comment({"Сколько очков Нити нужно для каждого уровня, начиная с 1-го.",
                "Длина списка = максимальный уровень."})
        public int[] pointsPerLevel = {100, 300, 700, 1500, 3000};

        @Config.Comment("Префикс этапов Game Stages: уровень 3 даст этап <префикс>3")
        public String stagePrefix = "nit_";

        @Config.Comment({"Команда выдачи этапа. {player} и {stage} подставляются.",
                "Пустая строка — не выдавать этапы."})
        public String stageAddCommand = "gamestage add {player} {stage}";

        @Config.Comment("Команда снятия этапа (когда уровень понижен командой)")
        public String stageRemoveCommand = "gamestage remove {player} {stage}";

        @Config.Comment("Показывать шкалу Нити на экране (при приседании и несколько секунд после изменения)")
        public boolean showHud = true;
    }

    public static final class StarsCfg {
        @Config.Comment("Включить правила неба по мирам (нужен Astral Sorcery)")
        @Config.RequiresMcRestart
        public boolean enabled = true;

        @Config.Comment("Измерения Лучезарии: здесь небо Astral Sorcery работает как обычно")
        public int[] luchezariyaDimensions = {0};

        @Config.Comment("В Преддверии видны все созвездия сразу, с полной силой")
        public boolean antechamberAllConstellations = true;

        @Config.Comment("В Преддверии максимальная концентрация звёздного света (ночь, место, сила созвездий)")
        public boolean antechamberMaxStarlight = true;

        @Config.Comment("Во всех прочих мирах с небом видно только созвездие Хаоса")
        public boolean chaosOnlyElsewhere = true;

        @Config.Comment("Рисовать созвездие Хаоса поверх родного неба в прочих мирах (Кладовки и т.п.)")
        public boolean drawChaosInOtherSkies = true;

        @Config.Comment("Добавить на небо дополнительные места для созвездий (в Astral их всего 15)")
        @Config.RequiresMcRestart
        public boolean extraSkyPositions = true;
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Tags.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        }
    }

    private ChaosConfig() {
    }
}
