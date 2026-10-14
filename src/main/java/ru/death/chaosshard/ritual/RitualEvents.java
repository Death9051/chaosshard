package ru.death.chaosshard.ritual;

import net.minecraft.entity.monster.IMob;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Оберег: пока идёт ритуал, враждебные твари не появляются рядом с Чашей. */
public class RitualEvents {

    private static final int RADIUS = 32;
    /** Измерение → (позиция чаши → до какого времени мира действует). */
    private static final Map<Integer, Map<Long, Long>> WARDS = new HashMap<>();

    static void ward(World world, BlockPos pos, long until) {
        WARDS.computeIfAbsent(world.provider.getDimension(), k -> new HashMap<>()).put(pos.toLong(), until);
    }

    @SubscribeEvent
    public void onSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!(event.getEntityLiving() instanceof IMob) || event.isSpawner()) {
            return;
        }
        World world = event.getWorld();
        Map<Long, Long> wards = WARDS.get(world.provider.getDimension());
        if (wards == null || wards.isEmpty()) {
            return;
        }
        long now = world.getTotalWorldTime();
        Iterator<Map.Entry<Long, Long>> it = wards.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() < now) {
                it.remove();
                continue;
            }
            BlockPos p = BlockPos.fromLong(e.getKey());
            double dx = p.getX() - event.getX(), dz = p.getZ() - event.getZ();
            if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        WARDS.clear();
    }
}
