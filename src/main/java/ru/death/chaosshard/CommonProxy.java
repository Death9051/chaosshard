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

    /** Истончение Нити (святая земля) пришло на клиент. */
    public void handleThinned(int penalty) {
    }

    /** Уровень Нити, известный клиенту. На сервере 0. */
    public int clientThreadLevel() {
        return 0;
    }

    /** Открыть книгу квестов (только клиент). */
    public void openGrimoire() {
    }

    /** Дуга от Сердца (только клиент). */
    public void handleHeartBolt(ru.death.chaosshard.network.PacketHeartBolt bolt) {
    }
}
