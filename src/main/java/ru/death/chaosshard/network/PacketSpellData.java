package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;

/** Сервер → клиент: прокачка заклинаний (перки и формы). */
public class PacketSpellData implements IMessage {

    private NBTTagCompound data;

    public PacketSpellData() {
    }

    public PacketSpellData(NBTTagCompound data) {
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

    public static class Handler implements IMessageHandler<PacketSpellData, IMessage> {
        @Override
        public IMessage onMessage(PacketSpellData m, MessageContext ctx) {
            ChaosShard.proxy.handleSpellData(m.data == null ? new NBTTagCompound() : m.data);
            return null;
        }
    }
}
