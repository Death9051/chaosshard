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

    @Config.Comment("Прорыв сквозь Завесу")
    @Config.LangKey("chaosshard.config.breach")
    public static final BreachCfg breach = new BreachCfg();

    @Config.Comment("Связки с другими модами")
    @Config.LangKey("chaosshard.config.compat")
    public static final CompatCfg compat = new CompatCfg();

    @Config.Comment("Перки Astral Sorcery и Нить")
    @Config.LangKey("chaosshard.config.perks")
    public static final PerksCfg perks = new PerksCfg();

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

        @Config.Comment({"Освещение Преддверия: небо ночное, но земля освещена как днём",
                "(растения растут, мобы на поверхности не появляются)."})
        public boolean ambientLight = true;

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

    public static final class CompatCfg {
        @Config.Comment("Tinkers' Construct: материал «Хаосит» со свойством «Нить Хаоса»")
        @Config.RequiresMcRestart
        public boolean tinkers = true;
    }

    public static final class BreachCfg {
        @Config.Comment("Уровень Нити, нужный для ритуала Прорыва")
        @Config.RangeInt(min = 0, max = 10)
        public int threadLevel = 2;

        @Config.Comment("Сколько очков Нити даёт первый Прорыв")
        public int threadReward = 100;

        @Config.Comment("Этап Game Stages, который выдаётся после первого Прорыва (пусто — не выдавать)")
        public String stage = "proryv";

        @Config.Comment("Шанс Осколка Завесы из корки Преддверия")
        @Config.RangeDouble(min = 0, max = 1)
        public double veilShardChance = 0.08;

        @Config.Comment("Шанс Обрывка памяти с монстра в чужих мирах (Кладовки и пр.)")
        @Config.RangeDouble(min = 0, max = 1)
        public double memoryFragmentChance = 0.10;
    }

    public static final class PerksCfg {
        @Config.Comment({"Потолок уровня перков Astral Sorcery (в самом Astral не больше 100).",
                "320 хватает на всё древо. 0 — оставить значение из конфига Astral.",
                "Потолок для каждого уровня Нити задаётся в scripts/chaosshard_stars.zs."})
        @Config.RangeInt(min = 0, max = 1000)
        @Config.RequiresMcRestart
        public int levelCap = 320;

        @Config.Comment({"Кривая опыта: уровень N стоит (база + шаг × N) опыта.",
                "В Astral было 150 + 2^(N/2+3) — к 30-му уровню это ~790 000 опыта."})
        @Config.RangeInt(min = 1, max = 100000)
        public int expCurveBase = 100;

        @Config.RangeInt(min = 0, max = 100000)
        public int expCurveStep = 5;

        @Config.Comment("Усиление всех перков за уровень Нити (0.05 = +5% за уровень, на 5-м уровне +25%)")
        @Config.RangeDouble(min = 0, max = 1)
        public double perkEffectPerLevel = 0.05;

        @Config.Comment("Прибавка опыта перков за уровень Нити (0.5 = +50% за уровень, на 5-м уровне x3.5)")
        @Config.RangeDouble(min = 0, max = 5)
        public double expGainPerLevel = 0.5;
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
