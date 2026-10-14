package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.anchor.AnchorSpec;
import ru.death.chaosshard.anchor.AnchorSpecs;
import ru.death.chaosshard.anchor.ContainerAnchor;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.Breach;

/** Клиент → сервер: действие в окне Якоря (выбор специализации, перенос, взгляд, опыт Жатвы). */
public class PacketAnchorAction implements IMessage {

    public static final int SELECT = 0;
    public static final int TRAVEL = 1;
    public static final int SCRY = 2;
    public static final int TAKE_XP = 3;

    private int action;
    private int arg;
    private long pos;

    public PacketAnchorAction() {
    }

    public PacketAnchorAction(int action, int arg, BlockPos pos) {
        this.action = action;
        this.arg = arg;
        this.pos = pos == null ? 0 : pos.toLong();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        arg = buf.readInt();
        pos = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(arg);
        buf.writeLong(pos);
    }

    public static class Handler implements IMessageHandler<PacketAnchorAction, IMessage> {
        @Override
        public IMessage onMessage(PacketAnchorAction m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> handle(p, m));
            return null;
        }
    }

    static void handle(EntityPlayerMP p, PacketAnchorAction m) {
        if (!(p.openContainer instanceof ContainerAnchor)) {
            return;
        }
        ContainerAnchor c = (ContainerAnchor) p.openContainer;
        TileAnchor a = c.tile;
        boolean mine = ContainerAnchor.canEdit(p, a);
        switch (m.action) {
            case SELECT:
                if (mine && a.chooseSpec(AnchorSpec.of(m.arg))) {
                    p.sendMessage(Breach.grimoire("chaosshard.anchor.spec.chosen",
                            new net.minecraft.util.text.TextComponentTranslation("chaosshard.anchor.spec." + a.getSpec().key())));
                    ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_SPEC);
                }
                break;
            case TRAVEL:
                if (mine) {
                    AnchorSpecs.travel(p, a, BlockPos.fromLong(m.pos));
                }
                break;
            case SCRY:
                c.scry = m.arg < 0 ? null : BlockPos.fromLong(m.pos);
                break;
            case TAKE_XP:
                if (mine) {
                    AnchorSpecs.takeXp(p, a);
                }
                break;
            default:
                break;
        }
    }
}
