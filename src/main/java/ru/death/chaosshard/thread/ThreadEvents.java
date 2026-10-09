package ru.death.chaosshard.thread;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Нить живёт на игроке, переживает смерть и синхронизируется с клиентом.
 */
public class ThreadEvents {

    @SubscribeEvent
    public void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(ThreadCapability.KEY, new ThreadCapability.Provider());
        }
    }

    /** Нить — часть самого конструкта, смерть её не рвёт. */
    @SubscribeEvent
    public void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        IThread oldThread = ThreadCapability.get(event.getOriginal());
        IThread newThread = ThreadCapability.get(event.getEntityPlayer());
        if (oldThread != null && newThread != null) {
            newThread.setPoints(oldThread.getPoints());
            newThread.setAnnouncedLevel(oldThread.getAnnouncedLevel());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            // Этапы Game Stages хранятся самим Game Stages, повторно не выдаём,
            // чтобы не засорять чат. Ручная повторная выдача: /chaosshard nit resync
            ThreadManager.initialize(player);
            ThreadManager.sync(player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ThreadManager.sync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ThreadManager.sync((EntityPlayerMP) event.player);
        }
    }
}
