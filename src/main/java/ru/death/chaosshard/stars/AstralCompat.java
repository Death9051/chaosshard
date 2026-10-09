package ru.death.chaosshard.stars;

import hellfirepvp.astralsorcery.common.constellation.IConstellation;
import hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler;
import hellfirepvp.astralsorcery.common.constellation.distribution.WorldSkyHandler;
import hellfirepvp.astralsorcery.common.constellation.effect.ConstellationEffectRegistry;
import hellfirepvp.astralsorcery.common.event.APIRegistryEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;

import javax.annotation.Nullable;

/**
 * Всё, что напрямую трогает классы Astral Sorcery (кроме миксинов).
 * Загружается только если Astral Sorcery установлен.
 */
public final class AstralCompat {

    /** Созвездие Хаоса; null, пока Astral Sorcery его не зарегистрировал. */
    @Nullable
    public static ChaosConstellation CHAOS;

    public static void subscribeEarly() {
        MinecraftForge.EVENT_BUS.register(new AstralCompat());
    }

    @SubscribeEvent
    public void onConstellationRegister(APIRegistryEvent.ConstellationRegister event) {
        if (!ChaosConfig.stars.enabled) {
            ChaosShard.LOG.info("Модуль Звёзд выключен в конфиге — созвездие Хаоса не регистрируется.");
            return;
        }
        CHAOS = new ChaosConstellation();
        event.registerConstellation(CHAOS);
        ChaosShard.LOG.info("Созвездие Хаоса зарегистрировано: {}", CHAOS.getUnlocalizedName());
    }

    /**
     * Ритуальный эффект Хаоса. Событие API принимает только «главные» созвездия,
     * поэтому пишем в реестр эффектов напрямую — он принимает и «яркие».
     */
    @SubscribeEvent
    public void onEffectRegister(APIRegistryEvent.ConstellationEffectRegister event) {
        if (CHAOS != null) {
            ConstellationEffectRegistry.registerFromAPI(CHAOS, CEffectChaos::new);
        }
    }

    /** Текст для команды /chaosshard sky: какие созвездия сейчас активны в мире. */
    public static ITextComponent describeActive(World world) {
        WorldSkyHandler handler = ConstellationSkyHandler.getInstance().getWorldHandler(world);
        if (handler == null) {
            return new TextComponentTranslation("chaosshard.sky.no_handler");
        }
        TextComponentString out = new TextComponentString("");
        boolean first = true;
        for (IConstellation c : handler.getActiveConstellations()) {
            if (!first) {
                out.appendText(", ");
            }
            first = false;
            out.appendSibling(new TextComponentTranslation(c.getUnlocalizedName()));
            Float distribution = handler.getCurrentDistribution(c, f -> f);
            out.appendText(String.format(" (%.2f)", distribution == null ? 0F : distribution));
        }
        if (first) {
            out.appendSibling(new TextComponentTranslation("chaosshard.sky.empty"));
        }
        return out;
    }

    public static boolean isChaos(IConstellation constellation) {
        return CHAOS != null && CHAOS == constellation;
    }

    private AstralCompat() {
    }
}
