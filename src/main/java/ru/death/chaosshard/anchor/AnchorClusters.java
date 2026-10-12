package ru.death.chaosshard.anchor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.servant.Servants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Кластеры Якорей: Якоря одного хозяина, чьи земли касаются (расстояние ≤ сумма радиусов), — одна сеть.
 * Сеть даёт: общий гарнизон (бойцы всех Якорей сети приходят на помощь туда, где набег), бонус энергии
 * (+25% за каждый Якорь сверх первого, до +100%), один набег на всю сеть вместо отдельного на каждый Якорь.
 */
public final class AnchorClusters {

    public static TileAnchor at(World w, BlockPos p) {
        if (p == null || !w.isBlockLoaded(p)) {
            return null;
        }
        TileEntity te = w.getTileEntity(p);
        return te instanceof TileAnchor ? (TileAnchor) te : null;
    }

    /** Сеть, в которую входит Якорь (включая его самого). */
    public static List<TileAnchor> cluster(World w, TileAnchor start) {
        List<TileAnchor> out = new ArrayList<>();
        if (start == null) {
            return out;
        }
        List<TileAnchor> all = new ArrayList<>();
        for (BlockPos p : AnchorData.get(w).anchors) {
            TileAnchor a = at(w, p);
            if (a != null && a.getOwner() != null && a.getOwner().equals(start.getOwner())) {
                all.add(a);
            }
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<TileAnchor> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start.getPos());
        while (!q.isEmpty()) {
            TileAnchor a = q.poll();
            out.add(a);
            for (TileAnchor b : all) {
                if (!seen.contains(b.getPos()) && touch(a, b)) {
                    seen.add(b.getPos());
                    q.add(b);
                }
            }
        }
        return out;
    }

    public static boolean touch(TileAnchor a, TileAnchor b) {
        double dx = a.getPos().getX() - b.getPos().getX(), dz = a.getPos().getZ() - b.getPos().getZ();
        double r = a.radius() + b.radius();
        return dx * dx + dz * dz <= r * r;
    }

    /** Главный Якорь сети: он ведёт отсчёт набегов за всю сеть. */
    public static boolean isLead(List<TileAnchor> c, TileAnchor a) {
        long best = Long.MAX_VALUE;
        for (TileAnchor t : c) {
            best = Math.min(best, t.getPos().toLong());
        }
        return a.getPos().toLong() == best;
    }

    public static int maxLevel(List<TileAnchor> c) {
        int m = 1;
        for (TileAnchor t : c) {
            m = Math.max(m, t.level());
        }
        return m;
    }

    /** Предел гарнизона сети: сумма уровней её Якорей. */
    public static int garrisonLimit(List<TileAnchor> c) {
        int n = 0;
        for (TileAnchor t : c) {
            n += t.level();
        }
        return n;
    }

    public static boolean contains(List<TileAnchor> c, BlockPos p) {
        for (TileAnchor t : c) {
            if (t.getPos().equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** Бойцы гарнизона сети (загруженные). */
    public static List<EntityLiving> garrison(WorldServer w, List<TileAnchor> c) {
        List<EntityLiving> out = new ArrayList<>();
        if (c.isEmpty()) {
            return out;
        }
        UUID owner = c.get(0).getOwner();
        for (Entity e : w.loadedEntityList) {
            if (e instanceof EntityLiving && e.isEntityAlive() && owner != null && Servants.isServantOf(e, owner)
                    && e.getEntityData().hasKey(Servants.TAG_ANCHOR)
                    && contains(c, BlockPos.fromLong(e.getEntityData().getLong(Servants.TAG_ANCHOR)))) {
                out.add((EntityLiving) e);
            }
        }
        return out;
    }

    private AnchorClusters() {
    }
}
