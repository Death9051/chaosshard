package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: маленькая молния-дуга от Сердца к новому наросту. */
public class PacketHeartBolt implements IMessage {

    public float x1, y1, z1, x2, y2, z2;
    public int color;

    public PacketHeartBolt() {
    }

    public PacketHeartBolt(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        this.x1 = (float) x1;
        this.y1 = (float) y1;
        this.z1 = (float) z1;
        this.x2 = (float) x2;
        this.y2 = (float) y2;
        this.z2 = (float) z2;
        this.color = color;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x1 = buf.readFloat();
        y1 = buf.readFloat();
        z1 = buf.readFloat();
        x2 = buf.readFloat();
        y2 = buf.readFloat();
        z2 = buf.readFloat();
        color = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(x1);
        buf.writeFloat(y1);
        buf.writeFloat(z1);
        buf.writeFloat(x2);
        buf.writeFloat(y2);
        buf.writeFloat(z2);
        buf.writeInt(color);
    }

    public static class Handler implements IMessageHandler<PacketHeartBolt, IMessage> {
        @Override
        public IMessage onMessage(PacketHeartBolt message, MessageContext ctx) {
            ChaosShard.proxy.handleHeartBolt(message);
            return null;
        }
    }
}
