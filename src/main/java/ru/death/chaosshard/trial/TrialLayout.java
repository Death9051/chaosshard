package ru.death.chaosshard.trial;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;

import java.util.List;
import java.util.Random;

/**
 * Обломок: пять комнат по оси +z от начала Испытания.
 *  A вход → B арена (волна тварей) → C зал рун (4 руны за 20 с) → D страж обломка → E сокровищница с выходом.
 * Между комнатами — проходы, запертые Печатями; печать снимается, когда комната пройдена.
 */
public final class TrialLayout {

    /** Комнаты: полуширина по x, начало и конец по z (внутренность). */
    public static final int[][] ROOMS = {{4, 0, 8}, {7, 13, 27}, {5, 31, 41}, {7, 45, 59}, {4, 63, 71}};
    public static final int HEIGHT = 6;

    public static void build(World world, TrialData.Trial t) {
        BlockPos o = t.origin();
        Random r = new Random(t.id * 31L + 7);
        boolean dark = TrialCondition.has(t.conditions, TrialCondition.DARK);
        IBlockState wall = ModBlocks.DESECRATED_STONE.getDefaultState();
        IBlockState wall2 = ModBlocks.CHAOS_STONE.getDefaultState();
        IBlockState floor = Blocks.STONEBRICK.getStateFromMeta(r.nextBoolean() ? 1 : 2);
        IBlockState light = Blocks.SEA_LANTERN.getDefaultState();
        // комнаты
        for (int i = 0; i < ROOMS.length; i++) {
            int h = ROOMS[i][0], z0 = ROOMS[i][1], z1 = ROOMS[i][2];
            box(world, o, -h - 1, -1, z0 - 1, h + 1, HEIGHT, z1 + 1, wall, wall2, r);
            fill(world, o, -h, 0, z0, h, HEIGHT - 1, z1, Blocks.AIR.getDefaultState());
            fill(world, o, -h, -1, z0, h, -1, z1, floor);
            if (!dark) {
                for (int x = -h + 2; x <= h - 2; x += 4) {
                    for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                        world.setBlockState(o.add(x, HEIGHT, z), light, 2);
                    }
                }
            }
            // колонны в аренах
            if (i == 1 || i == 3) {
                for (int[] c : new int[][]{{-4, 4}, {4, 4}, {-4, -4}, {4, -4}}) {
                    int zc = (z0 + z1) / 2 + c[1];
                    fill(world, o, c[0], 0, zc, c[0], HEIGHT - 1, zc, wall2);
                }
            }
        }
        // проходы и печати
        for (int i = 0; i < ROOMS.length - 1; i++) {
            int za = ROOMS[i][2] + 1, zb = ROOMS[i + 1][1] - 1;
            box(world, o, -2, -1, za, 2, 3, zb, wall, wall2, r);
            fill(world, o, -1, 0, za, 1, 2, zb, Blocks.AIR.getDefaultState());
            fill(world, o, -1, -1, za, 1, -1, zb, floor);
            if (i >= 1) {
                fill(world, o, -1, 0, za, 1, 2, za, ModBlocks.TRIAL_SEAL.getDefaultState());
            }
        }
        // руны
        int[] c = ROOMS[2];
        for (BlockPos p : runePositions(t)) {
            world.setBlockState(p, ModBlocks.TRIAL_RUNE.getDefaultState(), 2);
        }
        // сокровищница
        int[] e = ROOMS[4];
        BlockPos chest = o.add(0, 0, e[2] - 1);
        world.setBlockState(chest, Blocks.CHEST.getDefaultState(), 2);
        TileEntity te = world.getTileEntity(chest);
        if (te instanceof IInventory) {
            List<ItemStack> loot = TrialLoot.roll(t, r);
            List<Integer> slots = new java.util.ArrayList<>();
            for (int i = 0; i < ((IInventory) te).getSizeInventory(); i++) {
                slots.add(i);
            }
            java.util.Collections.shuffle(slots, r);
            for (int i = 0; i < loot.size() && i < slots.size(); i++) {
                ((IInventory) te).setInventorySlotContents(slots.get(i), loot.get(i));
            }
        }
        world.setBlockState(o.add(0, 0, e[1] + 3), ModBlocks.TRIAL_EXIT.getDefaultState(), 2);
        world.setBlockState(o.add(0, 1, e[1] + 3), ModBlocks.TRIAL_EXIT.getDefaultState()
                .withProperty(ru.death.chaosshard.trial.TrialBlocks.Exit.TOP, true), 2);
    }

    public static BlockPos[] runePositions(TrialData.Trial t) {
        BlockPos o = t.origin();
        int[] c = ROOMS[2];
        return new BlockPos[]{o.add(-3, 0, c[1] + 2), o.add(3, 0, c[1] + 2), o.add(-3, 0, c[2] - 2), o.add(3, 0, c[2] - 2)};
    }

    /** Блоки печати между комнатами i и i+1. */
    public static void openDoor(World world, TrialData.Trial t, int i) {
        BlockPos o = t.origin();
        int za = ROOMS[i][2] + 1;
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                BlockPos p = o.add(x, y, za);
                if (world.getBlockState(p).getBlock() == ModBlocks.TRIAL_SEAL) {
                    world.setBlockToAir(p);
                }
            }
        }
    }

    private static void box(World w, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, IBlockState a, IBlockState b, Random r) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    w.setBlockState(o.add(x, y, z), r.nextInt(5) == 0 ? b : a, 2);
                }
            }
        }
    }

    private static void fill(World w, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, IBlockState s) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    w.setBlockState(o.add(x, y, z), s, 2);
                }
            }
        }
    }

    private TrialLayout() {
    }
}
