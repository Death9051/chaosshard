package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: насколько истончена Нить (святая земля). */
public class PacketThinned implements IMessage {

    private int penalty;

    public PacketThinned() {
    }

    public PacketThinned(int penalty) {
        this.penalty = penalty;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        penalty = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(penalty);
    }

    public static class Handler implements IMessageHandler<PacketThinned, IMessage> {
        @Override
        public IMessage onMessage(PacketThinned message, MessageContext ctx) {
            ChaosShard.proxy.handleThinned(message.penalty);
            return null;
        }
    }
}
