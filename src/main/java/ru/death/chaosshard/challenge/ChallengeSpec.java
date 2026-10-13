package ru.death.chaosshard.challenge;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import ru.death.chaosshard.init.ModItems;

import java.util.List;

/**
 * Состав Вызова, записанный в сфере: кто придёт, ступень (0 — бесконечный), модификаторы.
 * Подношения на Алтарь: 4 Эссенции Хаоса и 2 Осколка Завесы (основа), знак стороны,
 * 1–5 Осколков мира (ступень) или Звезда Незера (бесконечный), по желанию — предметы модификаторов.
 */
public class ChallengeSpec {

    public static final int BASE_ESSENCE = 4, BASE_VEIL = 2;

    public ChallengeFaction faction = ChallengeFaction.ORDER;
    /** 1..5 — ступень; 0 — бесконечный. */
    public int tier = 1;
    public int mods;

    public boolean endless() {
        return tier <= 0;
    }

    /** Множитель награды: +25% за модификатор. */
    public double rewardMul() {
        return 1.0 + 0.25 * ChallengeMod.count(mods);
    }

    /** Волн на ступени (последняя — с боссом); у бесконечного — без предела. */
    public int waves() {
        return endless() ? Integer.MAX_VALUE : tier + 2;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Faction", faction.ordinal());
        t.setInteger("Tier", tier);
        t.setInteger("Mods", mods);
        return t;
    }

    public static ChallengeSpec read(NBTTagCompound t) {
        ChallengeSpec s = new ChallengeSpec();
        if (t == null) {
            return s;
        }
        s.faction = ChallengeFaction.values()[Math.max(0, Math.min(ChallengeFaction.values().length - 1, t.getInteger("Faction")))];
        s.tier = Math.max(0, Math.min(5, t.getInteger("Tier")));
        s.mods = t.getInteger("Mods");
        return s;
    }

    /** Разобрать подношения; null — это не Вызов (или лишнее/не хватает). */
    public static ChallengeSpec parse(List<ItemStack> stacks) {
        int essence = 0, veil = 0, worldShards = 0, stars = 0;
        int[] faction = new int[ChallengeFaction.values().length];
        int mods = 0;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() == ModItems.CHAOS_ESSENCE) {
                essence += s.getCount();
                continue;
            }
            if (s.getItem() == ModItems.VEIL_SHARD) {
                veil += s.getCount();
                continue;
            }
            if (s.getItem() == ModItems.WORLD_SHARD) {
                worldShards += s.getCount();
                continue;
            }
            if (s.getItem() == Items.NETHER_STAR) {
                stars += s.getCount();
                continue;
            }
            boolean known = false;
            for (ChallengeFaction f : ChallengeFaction.values()) {
                if (f.isMarker(s)) {
                    faction[f.ordinal()] += s.getCount();
                    known = true;
                    break;
                }
            }
            if (known) {
                continue;
            }
            for (ChallengeMod m : ChallengeMod.values()) {
                if (m.is(s)) {
                    mods |= m.bit();
                    known = true;
                    break;
                }
            }
            if (!known) {
                return null;   // лишнее на Алтаре
            }
        }
        if (essence != BASE_ESSENCE || veil != BASE_VEIL) {
            return null;
        }
        ChallengeFaction chosen = null;
        for (ChallengeFaction f : ChallengeFaction.values()) {
            if (faction[f.ordinal()] > 0) {
                if (chosen != null || faction[f.ordinal()] != f.markerCount) {
                    return null;   // две стороны сразу или не то количество знака
                }
                chosen = f;
            }
        }
        if (chosen == null) {
            return null;
        }
        ChallengeSpec spec = new ChallengeSpec();
        spec.faction = chosen;
        spec.mods = mods;
        if (stars == 1 && worldShards == 0) {
            spec.tier = 0;
        } else if (stars == 0 && worldShards >= 1 && worldShards <= 5) {
            spec.tier = worldShards;
        } else {
            return null;
        }
        return spec;
    }
}
