package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.lair.BlockLair;
import ru.death.chaosshard.lair.ContainerLair;
import ru.death.chaosshard.lair.TileLair;

/** Клиент → сервер: кнопка в окне логова. 0 — возрождаться здесь, 1 — вырастить. */
public class PacketLairAction implements IMessage {

    public static final int SPAWN = 0, GROW = 1;
    private int action;

    public PacketLairAction() {
    }

    public PacketLairAction(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<PacketLairAction, IMessage> {
        @Override
        public IMessage onMessage(PacketLairAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (!(p.openContainer instanceof ContainerLair)) {
                    return;
                }
                ContainerLair c = (ContainerLair) p.openContainer;
                TileLair t = c.tile;
                if (t.isInvalid() || t.getOwner() == null || !t.getOwner().equals(p.getUniqueID()) || !c.canInteractWith(p)) {
                    return;
                }
                if (m.action == SPAWN) {
                    BlockLair.setSpawn(t.getWorld(), t.getPos(), p);
                } else if (m.action == GROW) {
                    BlockLair.tryUpgrade(t.getWorld(), t.getPos(), t, p);
                }
                c.sendView();
            });
            return null;
        }
    }
}
