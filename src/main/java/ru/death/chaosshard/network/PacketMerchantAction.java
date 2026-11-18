package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.merchant.EntityMerchant;
import ru.death.chaosshard.merchant.Merchant;

/** Клиент → сервер: действие в окне торговца. */
public class PacketMerchantAction implements IMessage {

    public static final int DEPOSIT = 0, BUY = 1, SELL = 2, SELL_ALL = 3, REFRESH = 4;
    private int entity, action, arg;
    private String key = "";

    public PacketMerchantAction() {
    }

    public PacketMerchantAction(int entity, int action, int arg, String key) {
        this.entity = entity;
        this.action = action;
        this.arg = arg;
        this.key = key == null ? "" : key;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entity = buf.readInt();
        action = buf.readByte();
        arg = buf.readInt();
        key = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity);
        buf.writeByte(action);
        buf.writeInt(arg);
        ByteBufUtils.writeUTF8String(buf, key);
    }

    public static class Handler implements IMessageHandler<PacketMerchantAction, IMessage> {
        @Override
        public IMessage onMessage(PacketMerchantAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                Entity e = p.world.getEntityByID(m.entity);
                if (!(e instanceof EntityMerchant) || !e.isEntityAlive() || p.getDistanceSq(e) > 10 * 10) {
                    return;
                }
                EntityMerchant mer = (EntityMerchant) e;
                boolean ok = false;
                switch (m.action) {
                    case DEPOSIT:
                        ok = Merchant.deposit(p) > 0;
                        break;
                    case BUY:
                        int times = Math.max(1, Math.min(16, m.arg >> 8));
                        for (int i = 0; i < times; i++) {
                            if (!Merchant.buy(p, m.arg & 0xFF)) {
                                break;
                            }
                            ok = true;
                        }
                        break;
                    case SELL:
                        ok = !m.key.isEmpty() && Merchant.sell(p, m.key) > 0;
                        break;
                    case SELL_ALL:
                        ok = Merchant.sell(p, null) > 0;
                        break;
                    default:
                        break;
                }
                if (ok) {
                    p.world.playSound(null, mer.getPosition(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.6F, 0.7F);
                }
                Merchant.open(p, mer, false);
            });
            return null;
        }
    }
}
