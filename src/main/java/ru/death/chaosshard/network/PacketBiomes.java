package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/**
 * Сервер → клиент: новые биомы чанка (Скверна перекрасила землю). Раньше для этого чанк отправлялся заново
 * целиком — клиент заменял его, и существа, стоявшие в нём, переставали рисоваться (видно и слышно — только после
 * перезахода). Этот пакет меняет лишь биомы, чанк остаётся тем же.
 */
public class PacketBiomes implements IMessage {

    private int x, z;
    private byte[] biomes;

    public PacketBiomes() {
    }

    public PacketBiomes(int x, int z, byte[] biomes) {
        this.x = x;
        this.z = z;
        this.biomes = biomes.clone();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        z = buf.readInt();
        biomes = new byte[256];
        buf.readBytes(biomes);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeBytes(biomes, 0, 256);
    }

    public static class Handler implements IMessageHandler<PacketBiomes, IMessage> {
        @Override
        public IMessage onMessage(PacketBiomes m, MessageContext ctx) {
            ChaosShard.proxy.handleBiomes(m.x, m.z, m.biomes);
            return null;
        }
    }
}
