package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellCaster;

/** Клиент → сервер: выбрать заклинание Гримуара (из колеса или окна магии). */
public class PacketSelectSpell implements IMessage {

    private int spell;

    public PacketSelectSpell() {
    }

    public PacketSelectSpell(Spell spell) {
        this.spell = spell.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        spell = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(spell);
    }

    public static class Handler implements IMessageHandler<PacketSelectSpell, IMessage> {
        @Override
        public IMessage onMessage(PacketSelectSpell msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = player.getHeldItemMainhand();
                if (stack.getItem() != ModItems.GRIMOIRE) {
                    stack = player.getHeldItemOffhand();
                }
                if (stack.getItem() != ModItems.GRIMOIRE) {
                    for (ItemStack s : player.inventory.mainInventory) {
                        if (s.getItem() == ModItems.GRIMOIRE) {
                            stack = s;
                            break;
                        }
                    }
                }
                if (stack.getItem() != ModItems.GRIMOIRE) {
                    return;
                }
                SpellCaster.select(player, stack, Spell.byIndex(msg.spell));
            });
            return null;
        }
    }
}
