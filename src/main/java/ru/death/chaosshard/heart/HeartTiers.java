package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import ru.death.chaosshard.ChaosConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ступени Сердца Острова: что на нём нарастает и чем его прокачивать.
 * Всё читается из конфига строками вида:
 *   руда:   "ore:oreIron*10"  или  "minecraft:coal_ore*30"  или  "astralsorcery:blockcustomore:1*2"
 *   плата:  "ore:ingotIron*8" или  "chaosshard:veil_shard*4"
 */
public final class HeartTiers {

    public static int maxTier() {
        return ChaosConfig.heart.tierOres.length;
    }

    // ------------------------------------------------------------------ наросты

    public static final class Growth {
        public final IBlockState state;
        public final int weight;

        public Growth(IBlockState state, int weight) {
            this.state = state;
            this.weight = weight;
        }
    }

    /** Пул наростов ступени (1..max). Все ступени до текущей тоже входят в пул. */
    public static List<Growth> pool(int tier) {
        List<Growth> out = new ArrayList<>();
        String[] tiers = ChaosConfig.heart.tierOres;
        for (int t = 0; t < Math.min(tier, tiers.length); t++) {
            for (String entry : tiers[t].split(",")) {
                Growth g = parseGrowth(entry.trim());
                if (g != null) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** Вес руды в общем пуле (для «редкости»); не найдена — большой вес. */
    public static int weightOf(IBlockState state) {
        for (Growth g : pool(maxTier())) {
            if (g.state == state) {
                return g.weight;
            }
        }
        return Integer.MAX_VALUE;
    }

    public static IBlockState pick(List<Growth> pool, Random rand) {
        int total = 0;
        for (Growth g : pool) {
            total += g.weight;
        }
        if (total <= 0) {
            return null;
        }
        int r = rand.nextInt(total);
        for (Growth g : pool) {
            r -= g.weight;
            if (r < 0) {
                return g.state;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Growth parseGrowth(String entry) {
        if (entry.isEmpty()) {
            return null;
        }
        int weight = 1;
        int star = entry.lastIndexOf('*');
        if (star > 0) {
            try {
                weight = Integer.parseInt(entry.substring(star + 1).trim());
            } catch (NumberFormatException ignored) {
            }
            entry = entry.substring(0, star).trim();
        }
        if (entry.startsWith("ore:")) {
            for (ItemStack s : OreDictionary.getOres(entry.substring(4))) {
                if (s.getItem() instanceof ItemBlock) {
                    Block b = ((ItemBlock) s.getItem()).getBlock();
                    int meta = s.getMetadata() == OreDictionary.WILDCARD_VALUE ? 0 : s.getMetadata();
                    return new Growth(b.getStateFromMeta(meta), weight);
                }
            }
            return null;
        }
        String[] p = entry.split(":");
        if (p.length < 2) {
            return null;
        }
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(p[0], p[1]));
        if (b == null || b == net.minecraft.init.Blocks.AIR) {
            return null;
        }
        int meta = p.length > 2 ? parseInt(p[2]) : 0;
        return new Growth(b.getStateFromMeta(meta), weight);
    }

    // ------------------------------------------------------------------ плата за ступень

    public static final class Cost {
        public final String ore;
        public final ItemStack stack;
        public final int count;

        Cost(String ore, ItemStack stack, int count) {
            this.ore = ore;
            this.stack = stack;
            this.count = count;
        }

        public boolean matches(ItemStack s) {
            if (s.isEmpty()) {
                return false;
            }
            if (ore != null) {
                for (int id : OreDictionary.getOreIDs(s)) {
                    if (OreDictionary.getOreName(id).equals(ore)) {
                        return true;
                    }
                }
                return false;
            }
            return s.getItem() == stack.getItem()
                    && (!stack.getHasSubtypes() || s.getMetadata() == stack.getMetadata());
        }

        public String displayName() {
            if (ore != null) {
                List<ItemStack> ores = OreDictionary.getOres(ore);
                return ores.isEmpty() ? ore : ores.get(0).getDisplayName();
            }
            return stack.getDisplayName();
        }
    }

    /** Плата за переход на ступень target (2..max). */
    public static List<Cost> costFor(int target) {
        List<Cost> out = new ArrayList<>();
        String[] costs = ChaosConfig.heart.upgradeCosts;
        int index = target - 2;
        if (index < 0 || index >= costs.length) {
            return out;
        }
        for (String entry : costs[index].split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int count = 1;
            int star = entry.lastIndexOf('*');
            if (star > 0) {
                count = parseInt(entry.substring(star + 1).trim());
                entry = entry.substring(0, star).trim();
            }
            if (entry.startsWith("ore:")) {
                out.add(new Cost(entry.substring(4), ItemStack.EMPTY, count));
            } else {
                String[] p = entry.split(":");
                Item item = p.length >= 2 ? ForgeRegistries.ITEMS.getValue(new ResourceLocation(p[0], p[1])) : null;
                if (item != null) {
                    out.add(new Cost(null, new ItemStack(item, 1, p.length > 2 ? parseInt(p[2]) : 0), count));
                }
            }
        }
        return out;
    }

    /** Нужный уровень Нити для ступени target. */
    public static int threadFor(int target) {
        int[] req = ChaosConfig.heart.threadForTier;
        int index = target - 2;
        return index >= 0 && index < req.length ? req[index] : 0;
    }

    public static int count(EntityPlayer player, Cost cost) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) {
            if (cost.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    public static void take(EntityPlayer player, Cost cost) {
        int left = cost.count;
        for (ItemStack s : player.inventory.mainInventory) {
            if (left <= 0) {
                break;
            }
            if (cost.matches(s)) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private HeartTiers() {
    }
}
