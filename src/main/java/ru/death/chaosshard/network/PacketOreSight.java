package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: «Взгляд сквозь камень» — что подсветить и сколько тиков. */
public class PacketOreSight implements IMessage {

    private long[] pos;
    private byte[] tier;
    private int ticks;

    public PacketOreSight() {
    }

    public PacketOreSight(long[] pos, byte[] tier, int ticks) {
        this.pos = pos;
        this.tier = tier;
        this.ticks = ticks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ticks = buf.readShort();
        int n = Math.min(buf.readShort(), 4096);
        pos = new long[n];
        tier = new byte[n];
        for (int i = 0; i < n; i++) {
            pos[i] = buf.readLong();
            tier[i] = buf.readByte();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(ticks);
        int n = Math.min(pos.length, 4096);
        buf.writeShort(n);
        for (int i = 0; i < n; i++) {
            buf.writeLong(pos[i]);
            buf.writeByte(tier[i]);
        }
    }

    public static class Handler implements IMessageHandler<PacketOreSight, IMessage> {
        @Override
        public IMessage onMessage(PacketOreSight m, MessageContext ctx) {
            ChaosShard.proxy.handleOreSight(m.pos, m.tier, m.ticks);
            return null;
        }
    }
}
