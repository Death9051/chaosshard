package ru.death.chaosshard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.command.CommandChaosShard;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.thread.ThreadCapability;

/**
 * «Осколок Хаоса» — мод-ядро одноимённой сборки.
 *
 * Модули первой вехи:
 *  - Преддверие: своё измерение с вечной звёздной ночью;
 *  - Нить: шкала связи Осколка с Лордом, этапы Game Stages;
 *  - Звёзды: созвездие Хаоса и правила неба Astral Sorcery по мирам.
 */
@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:forge@[14.23.5.2847,);after:mixinbooter;after:astralsorcery;after:gamestages;after:tconstruct;after:cyberware;after:metamorph"
)
public class ChaosShard {

    public static final String MODID = Tags.MOD_ID;
    public static final Logger LOG = LogManager.getLogger("ChaosShard");
    public static final ru.death.chaosshard.structure.OrderStructures STRUCTURES = new ru.death.chaosshard.structure.OrderStructures();

    /** Станции добытчиков держат чанки. */
    public static final ru.death.chaosshard.servant.work.WorkChunks WORK_CHUNKS = new ru.death.chaosshard.servant.work.WorkChunks();

    @Mod.Instance(Tags.MOD_ID)
    public static ChaosShard INSTANCE;

    @SidedProxy(clientSide = "ru.death.chaosshard.client.ClientProxy", serverSide = "ru.death.chaosshard.CommonProxy")
    public static CommonProxy proxy;

    /** Astral Sorcery установлен — модуль Звёзды активен. */
    public static boolean astralLoaded;

    public ChaosShard() {
        astralLoaded = Loader.isModLoaded("astralsorcery");
        if (astralLoaded) {
            // Astral Sorcery регистрирует созвездия в своём preInit через событие на шине Forge.
            // Подписываемся заранее, в конструкторе, чтобы не зависеть от порядка загрузки модов.
            ru.death.chaosshard.stars.AstralCompat.subscribeEarly();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ThreadCapability.register();
        ChaosNetwork.init();
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, new ru.death.chaosshard.heart.HeartGuiHandler());
        AntechamberDimension.register();
        ru.death.chaosshard.trial.TrialDimension.register();
        ru.death.chaosshard.act5.RiftDimension.register();
        int eid = 0;
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_hare"),
                ru.death.chaosshard.servant.EntityChaosBeast.Hare.class, "chaosshard.servant_hare", eid++, this, 64, 3, true, 0x8a7a66, 0x6a2fb0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_tiger"),
                ru.death.chaosshard.servant.EntityChaosBeast.Tiger.class, "chaosshard.servant_tiger", eid++, this, 80, 3, true, 0xd07020, 0x1a1020);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_cat"),
                ru.death.chaosshard.servant.EntityChaosBeast.Cat.class, "chaosshard.servant_cat", eid++, this, 64, 3, true, 0x303038, 0xb070ff);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_husk"),
                ru.death.chaosshard.servant.entity.ServantEntities.Husk.class, "chaosshard.servant_husk", eid++, this, 64, 3, true, 0x6b5a3a, 0x6a2fb0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_bone"),
                ru.death.chaosshard.servant.entity.ServantEntities.Bone.class, "chaosshard.servant_bone", eid++, this, 64, 3, true, 0x1c1a22, 0x8040d0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_executioner"),
                ru.death.chaosshard.servant.entity.ServantEntities.Executioner.class, "chaosshard.servant_executioner", eid++, this, 64, 3, true, 0x3a3040, 0xb070ff);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_reaper"),
                ru.death.chaosshard.servant.entity.ServantEntities.Reaper.class, "chaosshard.servant_reaper", eid++, this, 64, 3, true, 0x8a7a3a, 0x6a2fb0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_lumberjack"),
                ru.death.chaosshard.servant.entity.ServantEntities.Lumberjack.class, "chaosshard.servant_lumberjack", eid++, this, 64, 3, true, 0x5a4026, 0x6a2fb0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_miner"),
                ru.death.chaosshard.servant.entity.ServantEntities.Miner.class, "chaosshard.servant_miner", eid++, this, 64, 3, true, 0x505058, 0x6a2fb0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "order_knight"),
                ru.death.chaosshard.structure.EntityOrderKnight.class, "chaosshard.order_knight", eid++, this, 80, 3, true, 0xe8e6de, 0xd8b048);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "hero"),
                ru.death.chaosshard.hero.EntityHero.class, "chaosshard.hero", eid++, this, 96, 3, true, 0xf4f0e0, 0xe8b830);
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.hero.Pillars());
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "garrot"),
                ru.death.chaosshard.garrot.EntityGarrot.class, "chaosshard.garrot", eid++, this, 96, 3, true, 0x1a1720, 0x8a1422);
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.garrot.GarrotDuel());
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "king"),
                ru.death.chaosshard.act5.EntityKing.class, "chaosshard.king", eid++, this, 96, 3, true, 0xe8c050, 0x9a1a22);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "archon"),
                ru.death.chaosshard.act5.EntityArchon.class, "chaosshard.archon", eid++, this, 128, 3, true, 0xf4f2fa, 0x8a5ad8);
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.cutscene.CutsceneManager());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.Resync());
        MinecraftForge.EVENT_BUS.register(STRUCTURES);
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.thread.ThreadEvents());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.antechamber.AntechamberEvents());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.breach.BreachRitual());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.breach.BreachHints());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.heart.HeartDrops());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.breach.BreachEvents());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.quest.Milestones());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.order.Shrines());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.garrot.GarrotRaids());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.ritual.RitualEvents());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.spell.ChaosEnergy());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.spell.SpellData());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.spell.SpellAccel());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.hero.TrophyPassives());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.trial.TrialManager());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.Servants());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.Beasts());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.AltarAscension());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.ritual.AltarRituals());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.challenge.ChallengeManager());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.ServantEffects());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.work.Work());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.anchor.DarkCorruption());
        MinecraftForge.EVENT_BUS.register(ru.death.chaosshard.anchor.AnchorSpecs.events());
        MinecraftForge.EVENT_BUS.register(ru.death.chaosshard.anchor.AnchorSigns.events());
        ru.death.chaosshard.spell.SpellMods.register(ru.death.chaosshard.anchor.AnchorSpecs.PULSE);
        MinecraftForge.EVENT_BUS.register(WORK_CHUNKS);
        ru.death.chaosshard.compat.body.Body.init();
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(new ru.death.chaosshard.order.ShrineGen(), 20);
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(STRUCTURES, 30);
        if (astralLoaded) {
            MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.stars.PerkBridge());
        }
        if (Loader.isModLoaded("tconstruct") && ChaosConfig.compat.tinkers) {
            ru.death.chaosshard.compat.tinkers.TinkersCompat.preInit();
        }
        if (Loader.isModLoaded("champions")) {
            ru.death.chaosshard.compat.champions.ChampionsCompat.init();
        }
        proxy.preInit(event);
        if (astralLoaded && !Loader.isModLoaded("mixinbooter")) {
            LOG.error("Astral Sorcery есть, а MixinBooter нет: правила неба Преддверия и Кладовок работать не будут.");
        }
        LOG.info("Осколок собран. Модуль Звёзд: {}", astralLoaded ? "активен" : "выключен (нет Astral Sorcery)");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(this, WORK_CHUNKS);
        net.minecraftforge.oredict.OreDictionary.registerOre("dustChaos", ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE);
        net.minecraftforge.oredict.OreDictionary.registerOre("ingotDistortedStarmetal", ru.death.chaosshard.init.ModItems.DISTORTED_STARMETAL);
        if (Loader.isModLoaded("tconstruct") && ChaosConfig.compat.tinkers) {
            ru.death.chaosshard.compat.tinkers.TinkersCompat.init();
        }
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    /** -Dchaosshard.autotest=true: сразу после старта сервера — автотест, затем выключение (для CI). */
    @Mod.EventHandler
    public void serverStarted(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) {
        if (Boolean.getBoolean("chaosshard.autotest")) {
            ru.death.chaosshard.debug.AutoTest.start(net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance(), true);
        }
    }

    /** Мир вот-вот сохранится и закроется: вернуть на место то, что временно вынуто из него (разрывы Разлома, Обвал). */
    @Mod.EventHandler
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        try {
            ru.death.chaosshard.act5.Rift.restoreOnStop();
            ru.death.chaosshard.trial.TrialManager.restoreCollapse();
        } catch (Throwable e) {
            LOG.warn("Не удалось вернуть блоки перед остановкой", e);
        }
    }

    /** Мир закрыт: сбросить всё, что живёт в памяти между тиками, — иначе оно перетекает в следующий мир одиночной игры. */
    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        reset(ru.death.chaosshard.servant.Beasts::clearState);
        reset(ru.death.chaosshard.servant.Servants::clearState);
        reset(ru.death.chaosshard.servant.AltarAscension::clearState);
        reset(ru.death.chaosshard.servant.work.WorkChunks::clearState);
        reset(ru.death.chaosshard.servant.work.Work::clearState);
        reset(ru.death.chaosshard.servant.Resync::clearState);
        reset(ru.death.chaosshard.challenge.ChallengeManager::clearState);
        reset(ru.death.chaosshard.anchor.AnchorSigns::clearState);
        reset(ru.death.chaosshard.garrot.GarrotDuel::clearState);
        reset(ru.death.chaosshard.garrot.GarrotRaids::clearState);
        reset(ru.death.chaosshard.breach.BreachHints::clearState);
        reset(ru.death.chaosshard.breach.BreachRitual::clearState);
        reset(ru.death.chaosshard.hero.TrophyPassives::clearState);
        reset(ru.death.chaosshard.spell.SpellAccel::clearState);
        reset(ru.death.chaosshard.order.Shrines::clearState);
        reset(ru.death.chaosshard.cutscene.CutsceneManager::clearState);
        reset(ru.death.chaosshard.ritual.RitualEvents::clearState);
        reset(ru.death.chaosshard.ritual.AltarRituals::clearState);
        reset(ru.death.chaosshard.trial.TrialManager::clearState);
        if (astralLoaded) {
            reset(ru.death.chaosshard.stars.ChaosLink::clearState);
        }
        reset(ru.death.chaosshard.anchor.AnchorView::clear);
    }

    /** Сброс не должен ронять остановку сервера: ошибка — в журнал, дальше. */
    private static void reset(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOG.warn("Сброс состояния при остановке не удался", t);
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandChaosShard());
    }
}
