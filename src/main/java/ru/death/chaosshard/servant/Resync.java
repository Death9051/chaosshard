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

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) {
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
