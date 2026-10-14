package ru.death.chaosshard.anchor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сеть Якорей: все Якоря одного владельца в Лучезарии — одна сеть, где бы они ни стояли.
 * Считается по сводкам {@link AnchorData.Rec}, поэтому видит и Якоря в незагруженных чанках.
 */
public final class AnchorNet {

    public static boolean isNetWorld(World w) {
        return w != null && w.provider.getDimension() == Breach.luchezariyaDim();
    }

    /** Мир Лучезарии (или null, если не загружен). */
    public static WorldServer luch() {
        return DimensionManager.getWorld(Breach.luchezariyaDim());
    }

    /** Позиции Якорей сети владельца. */
    public static List<BlockPos> members(World w, UUID owner) {
        List<BlockPos> out = new ArrayList<>();
        if (owner == null || !isNetWorld(w)) {
            return out;
        }
        AnchorData d = AnchorData.get(w);
        for (BlockPos p : d.anchors) {
            AnchorData.Rec r = d.rec(p);
            if (r != null && owner.equals(r.owner)) {
                out.add(p);
            }
        }
        return out;
    }

    public static int size(World w, UUID owner) {
        return members(w, owner).size();
    }

    /** Множитель восстановления энергии на земле Якоря сети такого размера (без печатей). */
    public static double landMultiplier(int size) {
        double m = ChaosConfig.anchor.energyRegenMultiplier + ChaosConfig.anchor.networkEnergyStep * Math.max(0, size - 1);
        return Math.min(ChaosConfig.anchor.networkEnergyMax, m);
    }

    /** Вне осквернённой земли — половина прибавки сети. */
    public static double outsideMultiplier(int size) {
        if (size <= 0) {
            return 1.0;
        }
        return 1.0 + (landMultiplier(size) - 1.0) / 2.0;
    }

    /** Восстановление энергии игрока: на земле Якоря — полный бонус сети этого Якоря, вне — половина своей сети. */
    public static double regen(EntityPlayer p) {
        double land = TileAnchor.energyMultiplier(p.world, p.getPosition());
        if (land > 1.0) {
            return land;
        }
        WorldServer l = luch();
        return l == null ? 1.0 : outsideMultiplier(size(l, p.getUniqueID()));
    }

    /** Сумма уровней всех Якорей сети (предел гарнизона). */
    public static int levelSum(World w, UUID owner) {
        int n = 0;
        AnchorData d = AnchorData.get(w);
        for (BlockPos p : members(w, owner)) {
            n += Math.max(1, d.rec(p).level);
        }
        // Бастион: +2 места за каждый (по убывающей), Цитадель: +1
        n += (int) Math.round(2 * factor(w, owner, AnchorSpec.BASTION, false) + factor(w, owner, AnchorSpec.CITADEL, false));
        return n;
    }

    public static int reserve(World w, UUID owner) {
        Integer r = owner == null ? null : AnchorData.get(w).reserve.get(owner);
        return r == null ? 0 : r;
    }

    /**
     * Излишек опыта Якоря на 5-м уровне: поровну всем растущим Якорям сети (с коэффициентом),
     * а если таких нет — в запас сети.
     */
    public static void overflow(World w, UUID owner, BlockPos from, int amount) {
        if (amount <= 0 || owner == null || !isNetWorld(w)) {
            return;
        }
        AnchorData d = AnchorData.get(w);
        double share = Math.min(1.0, ChaosConfig.anchor.overflowShare + 0.25 * factor(w, owner, AnchorSpec.ROOTS, false));
        int give = (int) Math.floor(amount * share);
        if (give <= 0) {
            return;
        }
        List<AnchorData.Rec> growing = new ArrayList<>();
        for (BlockPos p : members(w, owner)) {
            AnchorData.Rec r = d.rec(p);
            if (!p.equals(from) && r.level < TileAnchor.maxLevel()) {
                growing.add(r);
            }
        }
        if (growing.isEmpty()) {
            d.reserve.merge(owner, give, Integer::sum);
        } else {
            int each = give / growing.size();
            int rest = give - each * growing.size();
            for (AnchorData.Rec r : growing) {
                r.inbox += each;
            }
            if (rest > 0) {
                d.reserve.merge(owner, rest, Integer::sum);
            }
        }
        d.markDirty();
    }

    /** Взять из запаса сети не больше need. */
    public static int takeReserve(World w, UUID owner, int need) {
        if (need <= 0 || owner == null) {
            return 0;
        }
        AnchorData d = AnchorData.get(w);
        int have = reserve(w, owner);
        int got = Math.min(have, need);
        if (got > 0) {
            if (have - got <= 0) {
                d.reserve.remove(owner);
            } else {
                d.reserve.put(owner, have - got);
            }
            d.markDirty();
        }
        return got;
    }

    /** Сколько Якорей сети с этой специализацией (stage — только достигшие 5-го уровня). */
    public static int count(World w, UUID owner, AnchorSpec spec, boolean stage) {
        if (w == null || owner == null || !isNetWorld(w)) {
            return 0;
        }
        AnchorData d = AnchorData.get(w);
        int n = 0;
        for (BlockPos p : d.anchors) {
            AnchorData.Rec r = d.rec(p);
            if (r != null && owner.equals(r.owner) && r.spec == spec.ordinal()
                    && (!stage || r.level >= AnchorSpec.STAGE_LEVEL)) {
                n++;
            }
        }
        return n;
    }

    /** Сила сетевого эффекта: одинаковые складываются по убывающей — 1 + 1/2 + 1/4 + … */
    public static double stack(int n) {
        double f = 0, part = 1;
        for (int i = 0; i < n; i++) {
            f += part;
            part *= 0.5;
        }
        return f;
    }

    /** Сила сетевого эффекта специализации для владельца (в Лучезарии). */
    public static double factor(UUID owner, AnchorSpec spec, boolean stage) {
        WorldServer l = luch();
        return l == null ? 0 : stack(count(l, owner, spec, stage));
    }

    public static double factor(World w, UUID owner, AnchorSpec spec, boolean stage) {
        return stack(count(w, owner, spec, stage));
    }

    /** Якорь игрока, на чьей земле он стоит (или null). */
    public static TileAnchor ownLand(EntityPlayer p) {
        if (p == null || !isNetWorld(p.world)) {
            return null;
        }
        TileAnchor a = TileAnchor.covering(p.world, p.getPosition());
        return a != null && p.getUniqueID().equals(a.getOwner()) ? a : null;
    }

    private AnchorNet() {
    }
}
