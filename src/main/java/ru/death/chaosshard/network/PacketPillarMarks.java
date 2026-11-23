package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер → клиент: стоящие Опоры, названные Знамением. Клиент ставит по ним метки JourneyMap (если он есть). */
public class PacketPillarMarks implements IMessage {

    private NBTTagCompound tag = new NBTTagCompound();

    public PacketPillarMarks() {
    }

    public PacketPillarMarks(NBTTagCompound tag) {
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

    public static class Handler implements IMessageHandler<PacketPillarMarks, IMessage> {
        @Override
        public IMessage onMessage(PacketPillarMarks m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                ru.death.chaosshard.compat.journeymap.PillarMarksClient.set(m.tag);
                if (Loader.isModLoaded("journeymap")) {
                    ru.death.chaosshard.compat.journeymap.JmPlugin.refresh();
                }
            });
            return null;
        }
    }
}
