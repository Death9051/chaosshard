package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.servant.Retinue;

import java.util.UUID;

/**
 * Клиент → сервер: свита в Гримуаре. action −1 — только прислать список; 0 — позвать, 1 — отозвать, 2 — вернуть,
 * 3 — отозвать всех, 4 — вернуть всех. В ответ всегда свежий список ({@link PacketRetinueView}).
 */
public class PacketRetinueAction implements IMessage {

    private int action;
    private UUID id;

    public PacketRetinueAction() {
    }

    public PacketRetinueAction(int action, UUID id) {
        this.action = action;
        this.id = id;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        if (buf.readBoolean()) {
            id = new UUID(buf.readLong(), buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeBoolean(id != null);
        if (id != null) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
        }
    }

    public static class Handler implements IMessageHandler<PacketRetinueAction, IMessage> {
        @Override
        public IMessage onMessage(PacketRetinueAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (m.action >= 0) {
                    Retinue.act(p, m.action, m.id);
                }
                ChaosNetwork.CHANNEL.sendTo(new PacketRetinueView(Retinue.view(p), ru.death.chaosshard.servant.Servants.limit(p)), p);
            });
            return null;
        }
    }
}
