package ru.death.chaosshard.stars.client;

import hellfirepvp.astralsorcery.client.util.mappings.ClientConstellationPositionMapping;
import hellfirepvp.astralsorcery.common.data.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.stars.SkyRules;

/**
 * Клиент: решает, где Astral Sorcery рисует своё небо.
 *
 * Списки миров в Astral открытые (public static) и сбрасываются при перечитывании
 * его конфига, поэтому раз в секунду проверяем текущий мир и при нужде дописываем его.
 *  - Преддверие: полное звёздное небо Astral Sorcery;
 *  - Лучезария: как настроено в самом Astral (по умолчанию верхний мир уже там);
 *  - прочие миры с обычным небом: «слабый» режим — родное небо остаётся,
 *    поверх рисуются только активные созвездия, то есть только Хаос.
 */
public class AstralSkyClient {

    private int ticks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++this.ticks % 20 != 0) {
            return;
        }
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return;
        }
        int dim = world.provider.getDimension();
        switch (SkyRules.classify(world)) {
            case ANTECHAMBER:
                addOnce(Config.constellationSkyDimWhitelist, dim);
                Config.weakSkyRendersWhitelist.remove(Integer.valueOf(dim));
                break;
            case CHAOS_ONLY:
                if (ChaosConfig.stars.drawChaosInOtherSkies && world.provider.isSurfaceWorld()) {
                    addOnce(Config.constellationSkyDimWhitelist, dim);
                    addOnce(Config.weakSkyRendersWhitelist, dim);
                }
                break;
            default:
                break;
        }
    }

    private static void addOnce(java.util.List<Integer> list, int dim) {
        if (!list.contains(dim)) {
            list.add(dim);
        }
    }

    /**
     * В Astral 15 мест на небе, а созвездий вместе с Хаосом 17.
     * Добавляем ещё четыре места в свободных участках неба.
     */
    public static void addExtraSkyPositions() {
        ClientConstellationPositionMapping.availablePositions.add(ClientConstellationPositionMapping.RenderPosition.createRenderInfoFor(0.55, -0.9, 0.45, 22));
        ClientConstellationPositionMapping.availablePositions.add(ClientConstellationPositionMapping.RenderPosition.createRenderInfoFor(-0.6, -0.8, -0.55, 22));
        ClientConstellationPositionMapping.availablePositions.add(ClientConstellationPositionMapping.RenderPosition.createRenderInfoFor(0.65, -0.7, -0.4, 18));
        ClientConstellationPositionMapping.availablePositions.add(ClientConstellationPositionMapping.RenderPosition.createRenderInfoFor(-0.25, -1.2, -0.7, 26));
        ChaosShard.LOG.info("Добавлены места на небе: теперь их {}", ClientConstellationPositionMapping.availablePositions.size());
    }
}
