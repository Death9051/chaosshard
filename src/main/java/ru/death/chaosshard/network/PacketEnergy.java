package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: энергия Хаоса и её запас. */
public class PacketEnergy implements IMessage {

    private int energy, max;

    public PacketEnergy() {
    }

    public PacketEnergy(int energy, int max) {
        this.energy = energy;
        this.max = max;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        energy = buf.readShort();
        max = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(energy);
        buf.writeShort(max);
    }

    public static class Handler implements IMessageHandler<PacketEnergy, IMessage> {
        @Override
        public IMessage onMessage(PacketEnergy m, MessageContext ctx) {
            ChaosShard.proxy.handleEnergy(m.energy, m.max);
            return null;
        }
    }
}
