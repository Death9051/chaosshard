package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: показать титры (финал Акта 5). */
public class PacketCredits implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketCredits, IMessage> {
        @Override
        public IMessage onMessage(PacketCredits m, MessageContext ctx) {
            ChaosShard.proxy.showCredits();
            return null;
        }
    }
}
