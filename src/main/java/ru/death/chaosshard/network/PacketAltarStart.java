package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;

/** Клиент → сервер: кнопка «Начать» в окне Алтаря Прорыва (как ПКМ Гримуаром по Алтарю). */
public class PacketAltarStart implements IMessage {

    private long pos;

    public PacketAltarStart() {
    }

    public PacketAltarStart(BlockPos pos) {
        this.pos = pos.toLong();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
    }

    static boolean hasGrimoire(EntityPlayerMP p) {
        if (p.isCreative() || p.getHeldItemOffhand().getItem() == ModItems.GRIMOIRE) {
            return true;
        }
        for (ItemStack s : p.inventory.mainInventory) {
            if (s.getItem() == ModItems.GRIMOIRE) {
                return true;
            }
        }
        return false;
    }

    public static class Handler implements IMessageHandler<PacketAltarStart, IMessage> {
        @Override
        public IMessage onMessage(PacketAltarStart m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                BlockPos at = BlockPos.fromLong(m.pos);
                if (p.getDistanceSq(at) > 8 * 8 || p.world.getBlockState(at).getBlock() != ModBlocks.BREACH_ALTAR || !hasGrimoire(p)) {
                    return;
                }
                ru.death.chaosshard.ritual.AltarRituals.tryStart(p, at);
            });
            return null;
        }
    }
}
