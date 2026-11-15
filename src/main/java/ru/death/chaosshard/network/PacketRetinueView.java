package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер → клиент: свита для Гримуара (список и предел). */
public class PacketRetinueView implements IMessage {

    private NBTTagCompound tag = new NBTTagCompound();

    public PacketRetinueView() {
    }

    public PacketRetinueView(NBTTagList list, int limit) {
        tag.setTag("list", list);
        tag.setInteger("limit", limit);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound t = ByteBufUtils.readTag(buf);
        tag = t == null ? new NBTTagCompound() : t;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketRetinueView, IMessage> {
        @Override
        public IMessage onMessage(PacketRetinueView m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> ru.death.chaosshard.client.magic.ClientRetinue.set(m.tag));
            return null;
        }
    }
}
