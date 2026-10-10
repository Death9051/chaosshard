package ru.death.chaosshard.anchor;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.init.ModBlocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Скверна: во что превращаются блоки и биомы вокруг Якоря.
 * Правила из конфига «откуда=куда» (modid:блок). Если Defiled Lands нет — материя Хаоса.
 */
public final class Corruption {

    private static Map<Block, IBlockState> rules;

    public static Map<Block, IBlockState> rules() {
        if (rules == null) {
            rules = new HashMap<>();
            for (String r : ChaosConfig.anchor.conversions) {
                String[] p = r.split("=");
                if (p.length != 2) {
                    continue;
                }
                Block from = block(p[0].trim());
                Block to = block(p[1].trim());
                if (from == null) {
                    continue;
                }
                if (to == null) {
                    to = fallback(from);
                }
                if (to != null) {
                    rules.put(from, to.getDefaultState());
                }
            }
        }
        return rules;
    }

    /** Сколько правил нашлось (для самопроверки). */
    public static int[] resolved() {
        int ok = 0, total = ChaosConfig.anchor.conversions.length;
        for (String r : ChaosConfig.anchor.conversions) {
            String[] p = r.split("=");
            if (p.length == 2 && block(p[0].trim()) != null && block(p[1].trim()) != null) {
                ok++;
            }
        }
        return new int[]{ok, total};
    }

    private static Block fallback(Block from) {
        if (from == Blocks.GRASS || from == Blocks.SAND || from == Blocks.DIRT) {
            return ModBlocks.CHAOS_CRUST;
        }
        if (from == Blocks.STONE || from == Blocks.GRAVEL) {
            return ModBlocks.CHAOS_STONE;
        }
        return null;
    }

    private static Block block(String id) {
        if (id.equals("air") || id.equals("minecraft:air")) {
            return Blocks.AIR;
        }
        String[] p = id.split(":");
        if (p.length < 2) {
            return null;
        }
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(p[0], p[1]));
        return b == null || (b == Blocks.AIR && !id.endsWith("air")) ? null : b;
    }

    /** Осквернить блок; true — если что-то изменилось. */
    public static boolean corrupt(World world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        IBlockState to = rules().get(s.getBlock());
        if (to == null || world.getTileEntity(pos) != null) {
            return false;
        }
        world.setBlockState(pos, to, 2);
        return true;
    }

    /** Сменить биом столбца на осквернённый. true — если сменился. */
    public static boolean corruptBiome(World world, BlockPos pos) {
        if (!ChaosConfig.anchor.changeBiomes) {
            return false;
        }
        Biome old = world.getBiome(pos);
        Biome target = targetBiome(old);
        if (target == null || target == old) {
            return false;
        }
        Chunk chunk = world.getChunk(pos);
        byte[] arr = chunk.getBiomeArray();
        arr[(pos.getZ() & 15) << 4 | (pos.getX() & 15)] = (byte) (Biome.getIdForBiome(target) & 255);
        chunk.markDirty();
        return true;
    }

    private static Biome targetBiome(Biome old) {
        if (old.getRegistryName() != null && "defiledlands".equals(old.getRegistryName().getNamespace())) {
            return old;
        }
        String id = "plains_defiled";
        if (BiomeDictionary.hasType(old, BiomeDictionary.Type.SANDY)) {
            id = "desert_defiled";
        } else if (BiomeDictionary.hasType(old, BiomeDictionary.Type.SNOWY)) {
            id = "ice_plains_defiled";
        } else if (BiomeDictionary.hasType(old, BiomeDictionary.Type.SWAMP)) {
            id = "swamp_defiled";
        } else if (BiomeDictionary.hasType(old, BiomeDictionary.Type.MOUNTAIN) || BiomeDictionary.hasType(old, BiomeDictionary.Type.HILLS)) {
            id = "hills_defiled";
        } else if (BiomeDictionary.hasType(old, BiomeDictionary.Type.FOREST)) {
            id = "forest_tenebra";
        }
        return ForgeRegistries.BIOMES.getValue(new ResourceLocation("defiledlands", id));
    }

    /** Сбросить кэш правил (после смены конфига). */
    public static void reset() {
        rules = null;
    }

    private Corruption() {
    }
}
