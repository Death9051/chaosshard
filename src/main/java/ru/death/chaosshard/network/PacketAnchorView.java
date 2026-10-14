package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: окно Якоря — состояние Якоря и сети. */
public class PacketAnchorView implements IMessage {

    private NBTTagCompound data;

    public PacketAnchorView() {
    }

    public PacketAnchorView(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<PacketAnchorView, IMessage> {
        @Override
        public IMessage onMessage(PacketAnchorView m, MessageContext ctx) {
            ChaosShard.proxy.handleAnchorView(m.data == null ? new NBTTagCompound() : m.data);
            return null;
        }
    }
}
