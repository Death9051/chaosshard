package ru.death.chaosshard;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    /** Пакет синхронизации Нити пришёл на клиент. На сервере ничего не делает. */
    public void handleThreadSync(int points) {
    }

    /** Открыть книгу квестов (только клиент). */
    public void openGrimoire() {
    }
}
