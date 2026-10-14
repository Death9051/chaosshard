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
        CHANNEL.registerMessage(PacketThinned.Handler.class, PacketThinned.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(PacketSelectSpell.Handler.class, PacketSelectSpell.class, 3, Side.SERVER);
        CHANNEL.registerMessage(PacketHeartBolt.Handler.class, PacketHeartBolt.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(PacketEnergy.Handler.class, PacketEnergy.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(PacketBeasts.Handler.class, PacketBeasts.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(PacketBeastAction.Handler.class, PacketBeastAction.class, 7, Side.SERVER);
        CHANNEL.registerMessage(PacketBiomes.Handler.class, PacketBiomes.class, 8, Side.CLIENT);
        CHANNEL.registerMessage(PacketCutscene.Handler.class, PacketCutscene.class, 9, Side.CLIENT);
        CHANNEL.registerMessage(PacketCutsceneSkip.Handler.class, PacketCutsceneSkip.class, 10, Side.SERVER);
        CHANNEL.registerMessage(PacketCredits.Handler.class, PacketCredits.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(PacketSpellData.Handler.class, PacketSpellData.class, 12, Side.CLIENT);
        CHANNEL.registerMessage(PacketSpellAction.Handler.class, PacketSpellAction.class, 13, Side.SERVER);
        CHANNEL.registerMessage(PacketOreSight.Handler.class, PacketOreSight.class, 14, Side.CLIENT);
        CHANNEL.registerMessage(PacketAnchorView.Handler.class, PacketAnchorView.class, 15, Side.CLIENT);
    }

    private ChaosNetwork() {
    }
}
