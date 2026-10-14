package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellCaster;
import ru.death.chaosshard.spell.SpellData;

/** Клиент → сервер: купить перк, сменить форму, прочитать заклинание клавишей. */
public class PacketSpellAction implements IMessage {

    public static final int BUY = 0, FORM = 1, CAST = 2;

    private int action, spell, arg;

    public PacketSpellAction() {
    }

    public PacketSpellAction(int action, Spell spell, int arg) {
        this.action = action;
        this.spell = spell == null ? 0 : spell.ordinal();
        this.arg = arg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        spell = buf.readByte();
        arg = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeByte(spell);
        buf.writeByte(arg);
    }

    public static class Handler implements IMessageHandler<PacketSpellAction, IMessage> {
        @Override
        public IMessage onMessage(PacketSpellAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                Spell s = Spell.byIndex(m.spell);
                switch (m.action) {
                    case BUY:
                        SpellData.buy(p, s, m.arg);
                        break;
                    case FORM:
                        SpellData.setForm(p, s, m.arg);
                        break;
                    case CAST:
                        SpellCaster.castKey(p);
                        break;
                    default:
                        break;
                }
            });
            return null;
        }
    }
}
