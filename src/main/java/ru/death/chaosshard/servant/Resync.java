package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Иногда клиент не получает пакет появления существа (сервер его знает, игрок — нет: слышны шаги, но ни модели,
 * ни хитбокса; после перезахода всё на месте). Через несколько тиков после появления — и после прыжков «по Нити» —
 * существо заново отдаётся всем, кто рядом.
 */
public class Resync {

    private static final List<Object[]> QUEUE = new ArrayList<>();

    public static void later(Entity e, int ticks) {
        if (e != null && !e.world.isRemote) {
            QUEUE.add(new Object[]{e, ticks});
        }
    }

    public static void now(Entity e) {
        if (e == null || e.isDead || !(e.world instanceof WorldServer)) {
            return;
        }
        net.minecraft.entity.EntityTracker t = ((WorldServer) e.world).getEntityTracker();
        t.untrack(e);
        t.track(e);
    }

    @SuppressWarnings("unchecked")
    public static net.minecraft.entity.EntityTrackerEntry entry(Entity e) {
        try {
            net.minecraft.util.IntHashMap<net.minecraft.entity.EntityTrackerEntry> map =
                    net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.entity.EntityTracker.class,
                            ((WorldServer) e.world).getEntityTracker(), "field_72794_c", "trackedEntityHashTable");
            return map.lookup(e.getEntityId());
        } catch (Exception ex) {
            return null;
        }
    }

    /** Нужен ли этому существу присмотр: слуги, звери, армия Гаррота. */
    static boolean watched(Entity e) {
        return Servants.isServant(e) || e.getTags().contains(ru.death.chaosshard.garrot.GarrotRaids.TAG_ARMY);
    }

    /** Проверить и при необходимости починить: игрок рядом должен видеть существо. Возвращает, что сделано (или null). */
    public static String repair(Entity e, net.minecraft.entity.player.EntityPlayerMP p) {
        WorldServer ws = (WorldServer) e.world;
        net.minecraft.entity.EntityTrackerEntry en = entry(e);
        if (en == null) {
            ws.getEntityTracker().track(e);
            return "не отслеживался — взят на учёт";
        }
        if (!en.trackingPlayers.contains(p)) {
            boolean watching = ws.getPlayerChunkMap().isPlayerWatchingChunk(p, e.chunkCoordX, e.chunkCoordZ);
            en.updatePlayerEntity(p);
            if (en.trackingPlayers.contains(p)) {
                return "не был отправлен игроку — отправлен";
            }
            if (!watching) {
                int cx = net.minecraft.util.math.MathHelper.floor(e.posX) >> 4, cz = net.minecraft.util.math.MathHelper.floor(e.posZ) >> 4;
                return "чанк сущности (" + e.chunkCoordX + "," + e.chunkCoordZ + ") не виден игроку; по координатам чанк (" + cx + "," + cz + ")"
                        + (e.addedToChunk ? "" : ", вне чанка");
            }
            return "не отправлен: игрок вне дальности";
        }
        return null;
    }

    private int scan;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++scan % 100 == 0) {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
            for (WorldServer w : server.worlds) {
                if (w.playerEntities.isEmpty()) {
                    continue;
                }
                for (Entity e : new ArrayList<>(w.loadedEntityList)) {
                    if (!e.isEntityAlive() || !watched(e)) {
                        continue;
                    }
                    for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities) {
                        if (pl instanceof net.minecraft.entity.player.EntityPlayerMP && pl.getDistanceSq(e) < 48 * 48) {
                            String r = repair(e, (net.minecraft.entity.player.EntityPlayerMP) pl);
                            if (r != null) {
                                ru.death.chaosshard.ChaosShard.LOG.info("Видимость {} ({}): {}", e.getName(), e.getPosition(), r);
                            }
                        }
                    }
                }
            }
        }
        if (QUEUE.isEmpty()) {
            return;
        }
        for (Iterator<Object[]> it = QUEUE.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            int left = (Integer) o[1] - 1;
            if (left <= 0) {
                it.remove();
                now((Entity) o[0]);
            } else {
                o[1] = left;
            }
        }
    }
}
