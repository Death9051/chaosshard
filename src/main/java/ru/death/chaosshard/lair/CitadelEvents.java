package ru.death.chaosshard.lair;

import net.minecraft.entity.monster.IMob;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;

/** Цитадель — святилище Хаоса: в её стенах враждебные твари сами не появляются. */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class CitadelEvents {

    @SubscribeEvent
    public static void onSpawn(LivingSpawnEvent.CheckSpawn event) {
        World w = event.getWorld();
        if (w.isRemote || !(event.getEntityLiving() instanceof IMob) || event.isSpawner()) {
            return;
        }
        if (inCitadel(w, event.getX(), event.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    public static boolean inCitadel(World w, double x, double z) {
        int r = TileLair.RADIUS[2];
        for (BlockPos c : LairData.get(w).citadelsIn(w.provider.getDimension())) {
            double dx = c.getX() + 0.5 - x, dz = c.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= (double) r * r) {
                return true;
            }
        }
        return false;
    }

    private CitadelEvents() {
    }
}
