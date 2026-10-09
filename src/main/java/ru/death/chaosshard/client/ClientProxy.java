package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.CommonProxy;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new ThreadHud());
        if (ChaosShard.astralLoaded && ChaosConfig.stars.enabled) {
            MinecraftForge.EVENT_BUS.register(new ru.death.chaosshard.stars.client.AstralSkyClient());
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        if (ChaosShard.astralLoaded && ChaosConfig.stars.enabled && ChaosConfig.stars.extraSkyPositions) {
            ru.death.chaosshard.stars.client.AstralSkyClient.addExtraSkyPositions();
        }
    }

    @Override
    public void openGrimoire() {
        ru.death.chaosshard.compat.BetterQuestingCompat.openQuestBook();
    }

    @Override
    public void handleThreadSync(int points) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientThreadCache.update(points));
    }
}
