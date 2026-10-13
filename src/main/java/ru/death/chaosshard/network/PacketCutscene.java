package ru.death.chaosshard.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.cutscene.Cutscene;

/** Сервер → клиент: начать катсцену (вся сцена в пакете) или остановить её. */
public class PacketCutscene implements IMessage {

    public Cutscene scene;

    public PacketCutscene() {
    }

    public static PacketCutscene start(Cutscene c) {
        PacketCutscene p = new PacketCutscene();
        p.scene = c;
        return p;
    }

    public static PacketCutscene stop() {
        return new PacketCutscene();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        scene = buf.readBoolean() ? Cutscene.read(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(scene != null);
        if (scene != null) {
            scene.write(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketCutscene, IMessage> {
        @Override
        public IMessage onMessage(PacketCutscene m, MessageContext ctx) {
            ChaosShard.proxy.handleCutscene(m);
            return null;
        }
    }
}
