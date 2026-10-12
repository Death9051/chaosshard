package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.servant.BeastData;
import ru.death.chaosshard.servant.Beasts;

/** Клиент → сервер: взять перк зверя (action 0), сбросить перки (1), переименовать (2), позвать (3). */
public class PacketBeastAction implements IMessage {

    private int beast, action, perk;
    private String text = "";

    public PacketBeastAction() {
    }

    public PacketBeastAction(BeastData.Beast beast, String name) {
        this(beast, 2, 0);
        this.text = name;
    }

    public PacketBeastAction(BeastData.Beast beast, int action, int perk) {
        this.beast = beast.ordinal();
        this.action = action;
        this.perk = perk;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        beast = buf.readByte();
        action = buf.readByte();
        perk = buf.readByte();
        text = net.minecraftforge.fml.common.network.ByteBufUtils.readUTF8String(buf);
        if (text.length() > 64) {
            text = text.substring(0, 64);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(beast);
        buf.writeByte(action);
        buf.writeByte(perk);
        net.minecraftforge.fml.common.network.ByteBufUtils.writeUTF8String(buf, text == null ? "" : text);
    }

    public static class Handler implements IMessageHandler<PacketBeastAction, IMessage> {
        @Override
        public IMessage onMessage(PacketBeastAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                BeastData.Beast[] all = BeastData.Beast.values();
                if (m.beast < 0 || m.beast >= all.length) {
                    return;
                }
                if (m.action == 0) {
                    Beasts.buyPerk(p, all[m.beast], m.perk);
                } else if (m.action == 1) {
                    Beasts.resetPerks(p, all[m.beast]);
                } else if (m.action == 3) {
                    Beasts.recall(p, all[m.beast]);
                } else if (m.action == 2) {
                    Beasts.rename(p, all[m.beast], m.text);
                }
            });
            return null;
        }
    }
}
