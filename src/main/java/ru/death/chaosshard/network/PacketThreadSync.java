package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/**
 * Сервер → клиент: текущие очки Нити.
 */
public class PacketThreadSync implements IMessage {

    private int points;

    public PacketThreadSync() {
    }

    public PacketThreadSync(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.points = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.points);
    }

    public static class Handler implements IMessageHandler<PacketThreadSync, IMessage> {
        @Override
        public IMessage onMessage(PacketThreadSync message, MessageContext ctx) {
            ChaosShard.proxy.handleThreadSync(message.getPoints());
            return null;
        }
    }
}
