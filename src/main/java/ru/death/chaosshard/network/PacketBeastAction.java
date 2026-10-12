package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.servant.BeastData;
import ru.death.chaosshard.servant.Beasts;

/** Клиент → сервер: взять перк зверя (action 0) или сбросить перки (action 1). */
public class PacketBeastAction implements IMessage {

    private int beast, action, perk;

    public PacketBeastAction() {
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
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(beast);
        buf.writeByte(action);
        buf.writeByte(perk);
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
                }
            });
            return null;
        }
    }
}
