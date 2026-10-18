package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.ritual.Ritual;
import ru.death.chaosshard.ritual.TileRitualBowl;

/** Клиент → сервер: сложить подношения ритуала в ближайшую Чашу (кнопка на странице ритуала в Гримуаре). */
public class PacketRitualFill implements IMessage {

    /** Как далеко ищется Чаша (с запасом к клиентской проверке). */
    public static final int REACH = 7;

    private int ritual;

    public PacketRitualFill() {
    }

    public PacketRitualFill(Ritual r) {
        this.ritual = r.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ritual = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(ritual);
    }

    /** Ближайшая к игроку Чаша в REACH блоках или null. */
    public static TileRitualBowl nearestBowl(EntityPlayer player) {
        BlockPos p = new BlockPos(player);
        TileRitualBowl best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-REACH, -3, -REACH), p.add(REACH, 3, REACH))) {
            if (player.world.getBlockState(q).getBlock() == ModBlocks.RITUAL_BOWL) {
                TileEntity te = player.world.getTileEntity(q);
                double d = q.distanceSq(p);
                if (te instanceof TileRitualBowl && d < bestD) {
                    best = (TileRitualBowl) te;
                    bestD = d;
                }
            }
        }
        return best;
    }

    public static class Handler implements IMessageHandler<PacketRitualFill, IMessage> {
        @Override
        public IMessage onMessage(PacketRitualFill msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Ritual r = Ritual.byIndex(msg.ritual);
                if (r == null || r.isAltar() || !r.available()) {
                    return;
                }
                TileRitualBowl bowl = nearestBowl(player);
                if (bowl == null) {
                    player.sendStatusMessage(Breach.grimoire("chaosshard.magic.fill.no_bowl"), true);
                    return;
                }
                bowl.fill(player, r);
            });
            return null;
        }
    }
}
