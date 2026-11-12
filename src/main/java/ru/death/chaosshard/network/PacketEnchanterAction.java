package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.enchant.ContainerChaosEnchanter;

/** Клиент → сервер: действие в окне Стола Хаоса. */
public class PacketEnchanterAction implements IMessage {

    public static final int ENCHANT = 0;
    public static final int RESEARCH = 1;
    public static final int LEARN_ITEM = 2;
    public static final int CANCEL = 3;

    private int action;
    private String ench = "";
    private int level;

    public PacketEnchanterAction() {
    }

    public PacketEnchanterAction(int action, String ench, int level) {
        this.action = action;
        this.ench = ench == null ? "" : ench;
        this.level = level;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        ench = ByteBufUtils.readUTF8String(buf);
        level = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, ench);
        buf.writeShort(level);
    }

    public static class Handler implements IMessageHandler<PacketEnchanterAction, IMessage> {
        @Override
        public IMessage onMessage(PacketEnchanterAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (p.openContainer instanceof ContainerChaosEnchanter) {
                    ((ContainerChaosEnchanter) p.openContainer).act(p, m.action, m.ench, m.level);
                }
            });
            return null;
        }
    }
}
