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
        int eid = 0;
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_hare"),
                ru.death.chaosshard.servant.EntityChaosBeast.Hare.class, "chaosshard.servant_hare", eid++, this, 64, 3, true, 0x8a7a66, 0x6a2fb0);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_tiger"),
                ru.death.chaosshard.servant.EntityChaosBeast.Tiger.class, "chaosshard.servant_tiger", eid++, this, 80, 3, true, 0xd07020, 0x1a1020);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "servant_cat"),
                ru.death.chaosshard.servant.EntityChaosBeast.Cat.class, "chaosshard.servant_cat", eid++, this, 64, 3, true, 0x303038, 0xb070ff);
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
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.trial.TrialManager());
        MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.servant.Servants());
        ru.death.chaosshard.compat.body.Body.init();
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(new ru.death.chaosshard.order.ShrineGen(), 20);
        if (astralLoaded) {
            MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.stars.PerkBridge());
        }
        if (Loader.isModLoaded("tconstruct") && ChaosConfig.compat.tinkers) {
            ru.death.chaosshard.compat.tinkers.TinkersCompat.preInit();
        }
        proxy.preInit(event);
        if (astralLoaded && !Loader.isModLoaded("mixinbooter")) {
            LOG.error("Astral Sorcery есть, а MixinBooter нет: правила неба Преддверия и Кладовок работать не будут.");
        }
        LOG.info("Осколок собран. Модуль Звёзд: {}", astralLoaded ? "активен" : "выключен (нет Astral Sorcery)");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
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

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandChaosShard());
    }
}
