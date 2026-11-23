package ru.death.chaosshard.hero;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketPillarMarks;

/**
 * Метки Опор для карты (JourneyMap): какие Опоры названы Знамением и ещё стоят.
 * Сервер шлёт весь список целиком — при входе, после Знамения, когда Опора встала и когда пала.
 */
public class PillarMarks {

    public static NBTTagCompound list(World world) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("dim", Breach.luchezariyaDim());
        NBTTagList l = new NBTTagList();
        HeroData data = HeroData.get(world);
        for (Lightbearer h : Lightbearer.values()) {
            HeroData.Pillar pl = data.of(h);
            if (pl.state != HeroData.PLANNED && pl.state != HeroData.STANDING) {
                continue;
            }
            NBTTagCompound m = new NBTTagCompound();
            m.setString("id", h.id());
            m.setInteger("x", pl.x);
            m.setInteger("y", pl.y > 0 ? pl.y : 80);
            m.setInteger("z", pl.z);
            l.appendTag(m);
        }
        t.setTag("marks", l);
        return t;
    }

    public static void sync(EntityPlayerMP p) {
        ChaosNetwork.CHANNEL.sendTo(new PacketPillarMarks(list(p.world)), p);
    }

    public static void syncAll() {
        net.minecraft.server.MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (srv == null) {
            return;
        }
        for (EntityPlayerMP p : srv.getPlayerList().getPlayers()) {
            sync(p);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) e.player);
        }
    }
}
