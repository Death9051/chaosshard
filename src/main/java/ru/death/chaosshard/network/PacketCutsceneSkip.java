package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.cutscene.CutsceneManager;

/** Клиент → сервер: пропустить катсцену. */
public class PacketCutsceneSkip implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketCutsceneSkip, IMessage> {
        @Override
        public IMessage onMessage(PacketCutsceneSkip m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> CutsceneManager.skip(p));
            return null;
        }
    }
}
