package ru.death.chaosshard.lair;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketLairView;

/** Окно логова без мест: сервер раз в секунду шлёт, что логово даёт и что нужно для роста. */
public class ContainerLair extends Container {

    public final TileLair tile;
    private final EntityPlayer player;
    private int tick;

    public ContainerLair(EntityPlayer player, TileLair tile) {
        this.player = player;
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return !tile.isInvalid() && p.getDistanceSq(tile.getPos()) <= 100;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (player instanceof EntityPlayerMP && tick++ % 20 == 0) {
            sendView();
        }
    }

    public void sendView() {
        ChaosNetwork.CHANNEL.sendTo(new PacketLairView(BlockLair.view(tile.getWorld(), tile.getPos(), tile, (EntityPlayerMP) player)),
                (EntityPlayerMP) player);
    }
}
