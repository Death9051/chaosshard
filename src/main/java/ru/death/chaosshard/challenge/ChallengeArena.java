package ru.death.chaosshard.challenge;

import net.minecraft.block.BlockQuartz;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/**
 * Арена Вызова в архиве: круглый двор радиуса 16 над пустотой, стены по краю,
 * невидимый купол из барьера (не уйти ни жемчугом, ни столбом). Облик — по стороне.
 */
public final class ChallengeArena {

    public static void build(WorldServer w, BlockPos c, ChallengeFaction f) {
        int R = ChallengeManager.ARENA_R;
        IBlockState floorA, floorB, wall, wallTop, light, under;
        switch (f) {
            case ORDER:
                floorA = Blocks.QUARTZ_BLOCK.getDefaultState();
                floorB = Blocks.QUARTZ_BLOCK.getDefaultState().withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.CHISELED);
                wall = Blocks.QUARTZ_BLOCK.getDefaultState().withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.LINES_Y);
                wallTop = Blocks.GOLD_BLOCK.getDefaultState();
                light = Blocks.SEA_LANTERN.getDefaultState();
                under = Blocks.STONEBRICK.getDefaultState();
                break;
            case GARROT:
                floorA = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
                floorB = Blocks.NETHER_BRICK.getDefaultState();
                wall = Blocks.NETHER_BRICK.getDefaultState();
                wallTop = Blocks.WOOL.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR, EnumDyeColor.RED);
                light = Blocks.GLOWSTONE.getDefaultState();
                under = Blocks.OBSIDIAN.getDefaultState();
                break;
            case CYBER:
                // свалка тел: бетон, железо, решётки
                floorA = Blocks.CONCRETE.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR, EnumDyeColor.GRAY);
                floorB = Blocks.IRON_BLOCK.getDefaultState();
                wall = Blocks.CONCRETE.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR, EnumDyeColor.SILVER);
                wallTop = Blocks.IRON_BARS.getDefaultState();
                light = Blocks.SEA_LANTERN.getDefaultState();
                under = Blocks.STONE.getDefaultState();
                break;
            default:
                floorA = Blocks.GRASS.getDefaultState();
                floorB = Blocks.MOSSY_COBBLESTONE.getDefaultState();
                wall = Blocks.MOSSY_COBBLESTONE.getDefaultState();
                wallTop = Blocks.LEAVES.getDefaultState().withProperty(net.minecraft.block.BlockLeaves.DECAYABLE, false)
                        .withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, false);
                light = Blocks.LIT_PUMPKIN.getDefaultState();
                under = Blocks.DIRT.getDefaultState();
                break;
        }
        IBlockState barrier = Blocks.BARRIER.getDefaultState();
        int H = 14;
        for (int x = -R - 2; x <= R + 2; x++) {
            for (int z = -R - 2; z <= R + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 1.5) {
                    continue;
                }
                BlockPos p = c.add(x, 0, z);
                // основание и пол
                w.setBlockState(p.down(2), under, 2);
                boolean ring = Math.abs(d - 6) < 0.6 || Math.abs(d - 11) < 0.6;
                boolean ray = (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) && d > 2;
                w.setBlockState(p.down(), ring || ray ? floorB : floorA, 2);
                for (int y = 0; y <= H; y++) {
                    w.setBlockToAir(p.up(y));
                }
                if (d > R + 0.5) {
                    // стена и невидимый купол над ней
                    for (int y = 0; y < 4; y++) {
                        w.setBlockState(p.up(y), y == 3 ? wallTop : wall, 2);
                    }
                    for (int y = 4; y <= H; y++) {
                        w.setBlockState(p.up(y), barrier, 2);
                    }
                } else {
                    w.setBlockState(p.up(H), barrier, 2);
                }
            }
        }
        // свет: восемь светильников в полу и по стене
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            w.setBlockState(c.add(Math.cos(a) * (R - 1), -1, Math.sin(a) * (R - 1)), light, 2);
            w.setBlockState(c.add(Math.cos(a) * (R + 1), 2, Math.sin(a) * (R + 1)), light, 2);
        }
        w.setBlockState(c.down(), light, 2);
    }

    private ChallengeArena() {
    }
}
