package ru.death.chaosshard.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import ru.death.chaosshard.Tags;

public final class ChaosNetwork {

    public static SimpleNetworkWrapper CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);
        CHANNEL.registerMessage(PacketThreadSync.Handler.class, PacketThreadSync.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(PacketHeartAction.Handler.class, PacketHeartAction.class, 1, Side.SERVER);
    }

    private ChaosNetwork() {
    }
}
