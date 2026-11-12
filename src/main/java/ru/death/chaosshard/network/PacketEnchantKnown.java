package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.enchant.EnchantKnowledge;

import java.util.HashSet;
import java.util.Set;

/** Сервер → клиент: изученные зачарования (для окна Стола Хаоса). */
public class PacketEnchantKnown implements IMessage {

    private Set<String> ids = new HashSet<>();

    public PacketEnchantKnown() {
    }

    public PacketEnchantKnown(Set<String> ids) {
        this.ids = new HashSet<>(ids);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readShort();
        ids = new HashSet<>();
        for (int i = 0; i < n; i++) {
            ids.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(ids.size());
        for (String s : ids) {
            ByteBufUtils.writeUTF8String(buf, s);
        }
    }

    public static class Handler implements IMessageHandler<PacketEnchantKnown, IMessage> {
        @Override
        public IMessage onMessage(PacketEnchantKnown m, MessageContext ctx) {
            EnchantKnowledge.receive(m.ids);
            return null;
        }
    }
}
