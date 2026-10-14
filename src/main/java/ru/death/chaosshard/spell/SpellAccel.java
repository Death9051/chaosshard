package ru.death.chaosshard.spell;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ITickable;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Росток: ускорение времени для блока — растения, печи, машины.
 * Машина (тикающий тайл) получает лишние обновления каждый тик, растение — частые «случайные» тики.
 * Свои блоки мода и спавнеры не ускоряются.
 */
public final class SpellAccel {

    private static final class Entry {
        final int dim;
        final BlockPos pos;
        int left;
        final int extra;
        final float chance;

        Entry(int dim, BlockPos pos, int left, int extra, float chance) {
            this.dim = dim;
            this.pos = pos;
            this.left = left;
            this.extra = extra;
            this.chance = chance;
        }
    }

    private static final Map<String, Entry> ACTIVE = new HashMap<>();

    /** Можно ли ускорить блок. */
    public static boolean accelerable(World w, BlockPos pos) {
        TileEntity te = w.getTileEntity(pos);
        if (te != null) {
            return te instanceof ITickable && !(te instanceof TileEntityMobSpawner)
                    && !te.getClass().getName().startsWith("ru.death.chaosshard");
        }
        IBlockState s = w.getBlockState(pos);
        return !s.getBlock().isAir(s, w, pos) && s.getBlock().getTickRandomly();
    }

    /** Ускорить блок на ticks тиков: extra лишних обновлений машине, chance — шанс случайного тика растению за тик. */
    public static boolean add(World w, BlockPos pos, int ticks, int extra, float chance) {
        if (!accelerable(w, pos)) {
            return false;
        }
        int dim = w.provider.getDimension();
        ACTIVE.put(dim + ":" + pos.toLong(), new Entry(dim, pos.toImmutable(), ticks, extra, chance));
        return true;
    }

    public static int active() {
        return ACTIVE.size();
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || ACTIVE.isEmpty()) {
            return;
        }
        World w = event.world;
        int dim = w.provider.getDimension();
        Iterator<Entry> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.dim != dim) {
                continue;
            }
            if (--e.left <= 0 || !w.isBlockLoaded(e.pos)) {
                it.remove();
                continue;
            }
            TileEntity te = w.getTileEntity(e.pos);
            if (te instanceof ITickable && !te.isInvalid()) {
                for (int i = 0; i < e.extra; i++) {
                    ((ITickable) te).update();
                }
            } else {
                IBlockState s = w.getBlockState(e.pos);
                if (!s.getBlock().getTickRandomly()) {
                    it.remove();
                    continue;
                }
                if (w.rand.nextFloat() < e.chance) {
                    s.getBlock().randomTick(w, e.pos, s, w.rand);
                }
            }
            if (e.left % 10 == 0 && w instanceof WorldServer) {
                ((WorldServer) w).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, e.pos.getX() + 0.5, e.pos.getY() + 0.8,
                        e.pos.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }
}
