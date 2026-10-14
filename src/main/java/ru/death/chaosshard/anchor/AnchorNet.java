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
        return landMultiplier(size, 0);
    }

    /** То же; Альфа 2-го уровня даёт +0,25 за каждый Якорь сверх 7 — сверх предела ×5. */
    public static double landMultiplier(int size, int alphaLvl) {
        double m = ChaosConfig.anchor.energyRegenMultiplier + ChaosConfig.anchor.networkEnergyStep * Math.max(0, size - 1);
        m = Math.min(ChaosConfig.anchor.networkEnergyMax, m);
        if (alphaLvl >= 2) {
            m += 0.25 * Math.max(0, size - 7);
        }
        return m;
    }

    /** Вне осквернённой земли — половина прибавки сети. */
    public static double outsideMultiplier(int size) {
        return outsideMultiplier(size, 0);
    }

    public static double outsideMultiplier(int size, int alphaLvl) {
        if (size <= 0) {
            return 1.0;
        }
        return 1.0 + (landMultiplier(size, alphaLvl) - 1.0) / 2.0;
    }

    // ------------------------------------------------------------------ Альфы

    /** Альфы сети: сначала старшие. Бодрствуют первые size/10. */
    public static List<BlockPos> alphas(World w, UUID owner) {
        List<BlockPos> out = new ArrayList<>();
        AnchorData d = AnchorData.get(w);
        for (BlockPos p : members(w, owner)) {
            if (d.rec(p).alpha > 0) {
                out.add(p);
            }
        }
        out.sort((a, b) -> {
            int c = Integer.compare(d.rec(b).alpha, d.rec(a).alpha);
            return c != 0 ? c : Long.compare(a.toLong(), b.toLong());
        });
        return out;
    }

    /** Сколько Альф может быть в сети: одна на каждые 10 Якорей. */
    public static int alphaAllowed(World w, UUID owner) {
        return size(w, owner) / 10;
    }

    public static List<BlockPos> awakeAlphas(World w, UUID owner) {
        List<BlockPos> all = alphas(w, owner);
        int n = Math.min(all.size(), alphaAllowed(w, owner));
        return new ArrayList<>(all.subList(0, n));
    }

    /** Уровень старшей бодрствующей Альфы (0 — нет). */
    public static int alphaLevel(World w, UUID owner) {
        List<BlockPos> a = awakeAlphas(w, owner);
        return a.isEmpty() ? 0 : AnchorData.get(w).rec(a.get(0)).alpha;
    }

    /** Сила печатей Альф сети этого вида (кроме самой себя): число × 10% × уровень Альфы. */
    public static double alphaSeals(World w, UUID owner, int type, BlockPos except) {
        double f = 0;
        AnchorData d = AnchorData.get(w);
        for (BlockPos p : awakeAlphas(w, owner)) {
            AnchorData.Rec r = d.rec(p);
            if (p.equals(except) || type >= r.seals.length) {
                continue;
            }
            f += r.seals[type] * 0.1 * r.alpha;
        }
        return f;
    }

    /** Восстановление энергии игрока: на земле Якоря — полный бонус сети этого Якоря, вне — половина своей сети. */
    public static double regen(EntityPlayer p) {
        double land = TileAnchor.energyMultiplier(p.world, p.getPosition());
        if (land > 1.0) {
            return land;
        }
        WorldServer l = luch();
        return l == null ? 1.0 : outsideMultiplier(size(l, p.getUniqueID()), alphaLevel(l, p.getUniqueID()));
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
        List<AnchorData.Rec> alphaGrow = new ArrayList<>();
        for (BlockPos p : members(w, owner)) {
            AnchorData.Rec r = d.rec(p);
            if (p.equals(from)) {
                continue;
            }
            if (r.alpha > 0) {
                if (r.alpha < TileAnchor.ALPHA_MAX_LEVEL) {
                    alphaGrow.add(r);
                }
            } else if (r.level < TileAnchor.maxLevel()) {
                growing.add(r);
            }
        }
        if (growing.isEmpty() && alphaGrow.isEmpty()) {
            d.reserve.merge(owner, give, Integer::sum);
        } else {
            // Альфа берёт без коэффициента и двойную долю
            int weight = growing.size() + 2 * alphaGrow.size();
            int each = give / weight;
            int rest = give - each * growing.size() - (alphaGrow.isEmpty() ? 0 : (give / weight) * 2 * alphaGrow.size());
            for (AnchorData.Rec r : growing) {
                r.inbox += each;
            }
            int alphaEach = (int) Math.floor((double) amount * 2 / weight);
            for (AnchorData.Rec r : alphaGrow) {
                r.inbox += alphaEach;
            }
            // остаток от деления не теряется — в запас сети
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
        return stack(n, false);
    }

    /** soft — Альфа 8-го уровня: 1 + 0,75 + 0,5 + 0,25. */
    public static double stack(int n, boolean soft) {
        double f = 0, part = 1;
        for (int i = 0; i < n; i++) {
            f += part;
            part = soft ? Math.max(0, part - 0.25) : part * 0.5;
        }
        return f;
    }

    /** Сила сетевого эффекта специализации для владельца (в Лучезарии). */
    public static double factor(UUID owner, AnchorSpec spec, boolean stage) {
        WorldServer l = luch();
        return l == null ? 0 : factor(l, owner, spec, stage);
    }

    public static double factor(World w, UUID owner, AnchorSpec spec, boolean stage) {
        return factor(w, owner, spec, stage, alphaLevel(w, owner));
    }

    /** То же, когда уровень Альфы сети уже известен (al). */
    public static double factor(World w, UUID owner, AnchorSpec spec, boolean stage, int al) {
        double f = stack(count(w, owner, spec, stage), al >= 8);
        return stage && al >= 10 ? f * 1.5 : f;
    }

    /** Сколько Якорей сети заимствуют эффекты этой специализации. */
    public static int borrowers(World w, UUID owner, AnchorSpec spec, BlockPos except) {
        int n = 0;
        AnchorData d = AnchorData.get(w);
        for (BlockPos p : members(w, owner)) {
            if (p.equals(except)) {
                continue;
            }
            for (int b : d.rec(p).borrow) {
                AnchorFx fx = AnchorFx.of(b);
                if (fx != null && fx.spec == spec) {
                    n++;
                }
            }
        }
        return n;
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
