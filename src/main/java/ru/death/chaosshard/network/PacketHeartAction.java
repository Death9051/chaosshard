package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.heart.ContainerHeart;
import ru.death.chaosshard.heart.HeartActions;

/** Клиент → сервер: кнопка в окне Сердца (0 — пробудить, 1 — вкл/выкл руду). */
public class PacketHeartAction implements IMessage {

    public static final int UPGRADE = 0;
    public static final int TOGGLE = 1;

    private BlockPos pos;
    private int action;
    private String arg;

    public PacketHeartAction() {
    }

    public PacketHeartAction(BlockPos pos, int action, String arg) {
        this.pos = pos;
        this.action = action;
        this.arg = arg == null ? "" : arg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        action = buf.readByte();
        arg = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, arg);
    }

    public static class Handler implements IMessageHandler<PacketHeartAction, IMessage> {
        @Override
        public IMessage onMessage(PacketHeartAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerHeart)) {
                    return;
                }
                ContainerHeart c = (ContainerHeart) player.openContainer;
                if (!c.heart.getPos().equals(msg.pos) || !c.canInteractWith(player)) {
                    return;
                }
                if (msg.action == UPGRADE) {
                    HeartActions.upgrade(player, c.heart);
                } else if (msg.action == TOGGLE && msg.arg.length() < 200) {
                    c.heart.toggle(msg.arg);
                }
            });
            return null;
        }
    }
}
