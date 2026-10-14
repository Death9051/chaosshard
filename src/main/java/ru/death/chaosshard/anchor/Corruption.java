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

    /** Очистить осквернённый блок (обратное правило). true — если что-то изменилось. */
    public static boolean cleanse(World world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        if (world.getTileEntity(pos) != null) {
            return false;
        }
        for (Map.Entry<Block, IBlockState> r : rules().entrySet()) {
            if (r.getValue().getBlock() == s.getBlock() && s.getBlock() != Blocks.AIR && r.getKey() != Blocks.AIR) {
                world.setBlockState(pos, r.getKey().getDefaultState(), 2);
                return true;
            }
        }
        if (s.getBlock() == ModBlocks.CHAOS_CRUST) {
            world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 2);
            return true;
        }
        if (s.getBlock() == ModBlocks.CHAOS_STONE) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
            return true;
        }
        return false;
    }

    /** Блок уже осквернён (результат одного из правил). */
    public static boolean isCorrupted(IBlockState state) {
        Block b = state.getBlock();
        if (b == ModBlocks.CHAOS_CRUST || b == ModBlocks.CHAOS_STONE) {
            return true;
        }
        for (IBlockState to : rules().values()) {
            if (to.getBlock() == b && b != Blocks.AIR) {
                return true;
            }
        }
        return false;
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

    /** Отправить игрокам, которые видят эти чанки, их новые биомы (сам чанк не пересылается — существа в нём остаются видны). */
    public static void resendChunks(net.minecraft.world.WorldServer ws, java.util.Set<Long> keys) {
        for (long key : keys) {
            int cx = (int) (key & 0xFFFFFFFFL), cz = (int) (key >>> 32);
            net.minecraft.server.management.PlayerChunkMapEntry entry = ws.getPlayerChunkMap().getEntry(cx, cz);
            if (entry != null && entry.isSentToPlayers()) {
                entry.sendPacket(ru.death.chaosshard.network.ChaosNetwork.CHANNEL.getPacketFrom(
                        new ru.death.chaosshard.network.PacketBiomes(cx, cz, ws.getChunk(cx, cz).getBiomeArray())));
            }
        }
    }

    /** Сбросить кэш правил (после смены конфига). */
    private static java.util.List<Block> flora;

    /** Растения Скверной флоры, которые есть в сборке. */
    public static java.util.List<Block> floraBlocks() {
        if (flora == null) {
            flora = new java.util.ArrayList<>();
            for (String id : ChaosConfig.anchor.flora) {
                ResourceLocation rl = new ResourceLocation(id.trim());
                if (ForgeRegistries.BLOCKS.containsKey(rl)) {
                    Block b = ForgeRegistries.BLOCKS.getValue(rl);
                    if (b != null && b != Blocks.AIR) {
                        flora.add(b);
                    }
                }
            }
        }
        return flora;
    }

    /** Скверная флора: изредка посадить растение на освободившееся место над осквернённой землёй. */
    public static boolean flora(World world, BlockPos at, java.util.Random rand) {
        if (rand.nextDouble() >= ChaosConfig.anchor.floraChance) {
            return false;
        }
        return plant(world, at, rand);
    }

    /** Посадить случайное растение флоры, если место пустое и растение там приживётся. */
    public static boolean plant(World world, BlockPos at, java.util.Random rand) {
        java.util.List<Block> list = floraBlocks();
        if (list.isEmpty() || !world.isAirBlock(at) || world.canSeeSky(at) == false) {
            return false;
        }
        Block b = list.get(rand.nextInt(list.size()));
        if (!b.canPlaceBlockAt(world, at)) {
            return false;
        }
        world.setBlockState(at, b.getDefaultState(), 2);
        return true;
    }

    public static void reset() {
        flora = null;
        rules = null;
    }

    private Corruption() {
    }
}
