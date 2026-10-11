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

    @Config.Comment("Сердце Острова — наросты руды в центре Преддверия")
    @Config.LangKey("chaosshard.config.heart")
    public static final HeartCfg heart = new HeartCfg();

    @Config.Comment("Якоря Хаоса и Скверна")
    @Config.LangKey("chaosshard.config.anchor")
    public static final AnchorCfg anchor = new AnchorCfg();

    @Config.Comment("Связки с другими модами")
    @Config.LangKey("chaosshard.config.compat")
    public static final CompatCfg compat = new CompatCfg();

    @Config.Comment("Перки Astral Sorcery и Нить")
    @Config.LangKey("chaosshard.config.perks")
    public static final PerksCfg perks = new PerksCfg();

    @Config.Comment("Акт 3: святыни Ордена, деревни, Мрачная Армия Гаррота")
    @Config.LangKey("chaosshard.config.order")
    public static final OrderCfg order = new OrderCfg();

    public static final class OrderCfg {
        @Config.Comment("Шанс святыни Ордена на чанк Лучезарии (1 из N)")
        @Config.RangeInt(min = 0, max = 100000)
        public int shrineRarity = 220;

        @Config.Comment("Радиус святой земли вокруг святыни: Нить истончается, Скверна не ползёт")
        @Config.RangeInt(min = 4, max = 128)
        public int shrineRadius = 32;

        @Config.Comment("На сколько уровней истончается Нить на святой земле")
        @Config.RangeInt(min = 0, max = 5)
        public int shrinePenalty = 2;

        @Config.Comment("Уровень Нити, нужный, чтобы осквернить святыню (Эссенцией Хаоса)")
        public int desecrateThread = 2;

        @Config.Comment("Очки Нити за осквернение святыни")
        public int desecrateReward = 60;

        @Config.Comment("Как часто Знак покровителя собирает дань (тики, 24000 = день)")
        public int tributeInterval = 24000;

        @Config.Comment("Очки Нити владельцу за каждую дань")
        public int tributeThread = 8;

        @Config.Comment("Очки Нити за деревню, поглощённую Скверной")
        public int fallenVillageReward = 60;

        @Config.Comment("Набеги Мрачной Армии на захваченные края")
        public boolean raids = true;

        @Config.Comment("Через сколько тиков после захвата края приходит первый набег")
        public int firstRaidDelay = 2400;

        @Config.Comment("Пауза между набегами на один край (тики)")
        public int raidInterval = 72000;

        @Config.Comment("Очки Нити за убитого капитана Мрачной Армии")
        public int captainReward = 40;
    }

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

    public static final class HeartCfg {
        @Config.Comment("Ставить Сердце Острова в Преддверии")
        public boolean enabled = true;

        @Config.Comment("Наросты при добыче выпадают блоком руды (как с «Шёлковым касанием»), а не осколками")
        public boolean dropOreBlocks = true;

        @Config.Comment("Шанс за каждый модуль «Богатство», что вместо руды вырастет Жила Завесы (даёт Осколки Завесы)")
        @Config.RangeDouble(min = 0, max = 0.25)
        public float veinChancePerModule = 0.10F;

        @Config.Comment("Редкие наросты (и Жила Завесы) появляются с тонкой дугой из Сердца")
        public boolean lightning = true;

        @Config.Comment("Руда считается редкой, если её вес в пуле не больше этого числа")
        public int lightningMaxWeight = 4;

        @Config.Comment({"Что нарастает на каждой ступени (ступень включает и все предыдущие).",
                "Формат: ore:<оредикт>*вес или modid:блок[:мета]*вес, через запятую. Несуществующее пропускается."})
        public String[] tierOres = {
                "minecraft:coal_ore*30,ore:oreCopper*12,minecraft:iron_ore*6",
                "minecraft:iron_ore*14,ore:oreTin*10,ore:oreLead*6,ore:oreOsmium*8,minecraft:quartz_ore*4",
                "minecraft:gold_ore*8,minecraft:redstone_ore*10,minecraft:lapis_ore*6,ore:oreSilver*5,ore:oreNickel*4,"
                        + "astralsorcery:blockcustomsandore*6,astralsorcery:blockcustomore*3",
                "minecraft:diamond_ore*4,minecraft:emerald_ore*3,ore:orePlatinum*2,ore:oreAluminum*4,ore:oreIridium*2,"
                        + "astralsorcery:blockcustomore:1*3"
        };

        @Config.Comment({"Плата за переход на ступени 2, 3, 4 (берётся из инвентаря при Shift+ПКМ по Сердцу).",
                "Формат: ore:<оредикт>*кол-во или modid:предмет[:мета]*кол-во, через запятую."})
        public String[] upgradeCosts = {
                "ore:ingotIron*8,ore:ingotCopper*8,chaosshard:veil_shard*4",
                "ore:ingotIron*16,ore:ingotTin*8,chaosshard:memory_fragment*4,chaosshard:veil_shard*8",
                "ore:ingotGold*16,ore:dustRedstone*32,ore:gemLapis*16,chaosshard:memory_fragment*8"
        };

        @Config.Comment("Уровень Нити для ступеней 2, 3, 4")
        public int[] threadForTier = {1, 2, 3};

        @Config.Comment("Как часто (в тиках) нарастает руда на ступенях 1..4")
        public int[] growthInterval = {600, 400, 300, 200};

        @Config.Comment("Сколько наростов может быть на Сердце одновременно, ступени 1..4")
        public int[] maxGrowths = {8, 12, 18, 24};
    }

    public static final class AnchorCfg {
        @Config.Comment("Уровень Нити, нужный, чтобы вбить Якорь")
        public int threadLevel = 2;

        @Config.Comment("Минимальное расстояние между Якорями (блоков)")
        public int minDistance = 96;

        @Config.Comment("Как часто Скверна делает шаг (тиков) и сколько попыток за шаг")
        public int spreadInterval = 40;
        public int attemptsPerSpread = 4;

        @Config.Comment("Начальный и наибольший радиус Скверны; +1 к радиусу за каждые N осквернённых блоков")
        public int startRadius = 4;
        public int maxRadius = 32;
        public int blocksPerRadius = 25;

        @Config.Comment("Сколько блоков осквернить, чтобы край считался захваченным, и награда Нитью")
        public int captureThreshold = 300;
        public int captureReward = 150;

        @Config.Comment("Захваченный край понемногу питает Нить владельца: очков и интервал (тиков)")
        public int passiveThread = 5;
        public int passiveInterval = 24000;

        @Config.Comment("Менять биом осквернённой земли на биомы Defiled Lands")
        public boolean changeBiomes = true;

        @Config.Comment({"Что во что превращает Скверна: откуда=куда. Если целевого блока нет (нет мода) —",
                "трава, земля и песок становятся коркой Преддверия, камень и гравий — хаоситом."})
        public String[] conversions = {
                "minecraft:grass=defiledlands:grass_defiled",
                "minecraft:dirt=defiledlands:dirt_defiled",
                "minecraft:stone=defiledlands:stone_defiled",
                "minecraft:sand=defiledlands:sand_defiled",
                "minecraft:gravel=defiledlands:gravel_defiled",
                "minecraft:sandstone=defiledlands:sandstone_defiled",
                "minecraft:log=defiledlands:tenebra_log",
                "minecraft:log2=defiledlands:tenebra_log",
                "minecraft:leaves=defiledlands:tenebra_leaves",
                "minecraft:leaves2=defiledlands:tenebra_leaves",
                "minecraft:tallgrass=defiledlands:vilespine",
                "minecraft:red_flower=minecraft:air",
                "minecraft:yellow_flower=minecraft:air",
                "minecraft:double_plant=minecraft:air"
        };
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
            ru.death.chaosshard.anchor.Corruption.reset();
        }
    }

    private ChaosConfig() {
    }
}
