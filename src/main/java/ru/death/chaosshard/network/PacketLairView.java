package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер → клиент: что показывает окно логова. */
public class PacketLairView implements IMessage {

    private NBTTagCompound tag = new NBTTagCompound();

    public PacketLairView() {
    }

    public PacketLairView(NBTTagCompound tag) {
        this.tag = tag;
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

    public static class Handler implements IMessageHandler<PacketLairView, IMessage> {
        @Override
        public IMessage onMessage(PacketLairView m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> ru.death.chaosshard.lair.client.GuiLair.view = m.tag);
            return null;
        }
    }
}
