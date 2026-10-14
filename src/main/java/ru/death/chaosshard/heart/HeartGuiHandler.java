package ru.death.chaosshard.heart;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

public class HeartGuiHandler implements IGuiHandler {

    public static final int HEART = 0;
    public static final int HARVESTER = 1;
    public static final int ANCHOR = 2;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (id == HARVESTER && te instanceof TileHarvester) {
            return new ContainerHarvester(player.inventory, (TileHarvester) te);
        }
        if (id == ANCHOR && te instanceof ru.death.chaosshard.anchor.TileAnchor) {
            return new ru.death.chaosshard.anchor.ContainerAnchor(player, (ru.death.chaosshard.anchor.TileAnchor) te);
        }
        return id == HEART && te instanceof TileHeart ? new ContainerHeart(player.inventory, (TileHeart) te) : null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (id == HARVESTER && te instanceof TileHarvester) {
            return new ru.death.chaosshard.heart.client.GuiHarvester(new ContainerHarvester(player.inventory, (TileHarvester) te));
        }
        if (id == ANCHOR && te instanceof ru.death.chaosshard.anchor.TileAnchor) {
            return new ru.death.chaosshard.anchor.client.GuiAnchor(
                    new ru.death.chaosshard.anchor.ContainerAnchor(player, (ru.death.chaosshard.anchor.TileAnchor) te));
        }
        return id == HEART && te instanceof TileHeart
                ? new ru.death.chaosshard.heart.client.GuiHeart(new ContainerHeart(player.inventory, (TileHeart) te)) : null;
    }
}
