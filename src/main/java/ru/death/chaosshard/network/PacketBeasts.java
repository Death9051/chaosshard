package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: уровни, опыт и перки зверей игрока (для окна Гримуара). */
public class PacketBeasts implements IMessage {

    private NBTTagCompound tag;

    public PacketBeasts() {
    }

    public PacketBeasts(NBTTagCompound tag) {
        this.tag = tag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tag = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketBeasts, IMessage> {
        @Override
        public IMessage onMessage(PacketBeasts m, MessageContext ctx) {
            ChaosShard.proxy.handleBeasts(m.tag == null ? new NBTTagCompound() : m.tag);
            return null;
        }
    }
}
