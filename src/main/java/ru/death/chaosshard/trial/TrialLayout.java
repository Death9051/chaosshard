package ru.death.chaosshard.trial;

import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;

import java.util.List;
import java.util.Random;

/**
 * Постройка обломка: цепочка комнат вдоль оси z, между ними коридоры с барьерами.
 * Первый коридор открыт, остальные барьеры гаснут, когда комната перед ними пройдена.
 */
public final class TrialLayout {

    public static final int HEIGHT = 6;
    public static final int PIT = 7;

    public static void build(World world, TrialData.Trial t) {
        BlockPos o = t.origin();
        Random r = new Random(t.id * 31L + 7);
        TrialTheme th = t.theme();
        int[][] b = t.bounds();
        for (int i = 0; i < t.rooms.length; i++) {
            TrialRoom type = t.roomType(i);
            int h = type.half, z0 = b[i][0], z1 = b[i][1];
            boolean pit = type.pit();
            int y0 = pit ? -PIT - 1 : -1;
            box(world, o, -h - 1, y0, z0 - 1, h + 1, HEIGHT, z1 + 1, th.wall(), th.wall2(), r);
            fill(world, o, -h, 0, z0, h, HEIGHT - 1, z1, Blocks.AIR.getDefaultState());
            for (int x = -h; x <= h; x++) {
                for (int z = z0; z <= z1; z++) {
                    world.setBlockState(o.add(x, -1, z), th.floor(r.nextInt(4) == 0), 2);
                }
            }
            for (int x = -h + 2; x <= h - 2; x += 4) {
                for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                    world.setBlockState(o.add(x, HEIGHT, z), th.light(), 2);
                }
            }
            if (h <= 1) {
                for (int z = z0 + 2; z <= z1; z += 4) {
                    world.setBlockState(o.add(0, HEIGHT, z), th.light(), 2);
                }
            }
            switch (type) {
                case ARENA: case GUARD:
                    for (int[] c : new int[][]{{-4, 4}, {4, 4}, {-4, -4}, {4, -4}}) {
                        int zc = (z0 + z1) / 2 + c[1];
                        fill(world, o, c[0], 0, zc, c[0], HEIGHT - 1, zc, th.wall2());
                    }
                    break;
                case RUNES:
                    for (BlockPos p : runePositions(t, i)) {
                        world.setBlockState(p, ModBlocks.TRIAL_RUNE.getDefaultState(), 2);
                    }
                    break;
                case SEQUENCE:
                case MIRROR:
                    for (BlockPos p : sequencePositions(t, i)) {
                        world.setBlockState(p, ModBlocks.TRIAL_RUNE.getDefaultState(), 2);
                    }
                    break;
                case LIGHTS:
                    for (BlockPos p : lightsPositions(t, i)) {
                        world.setBlockState(p, ModBlocks.TRIAL_RUNE.getDefaultState(), 2);
                    }
                    break;
                case COLLAPSE: {
                    // яма во всю ширину, над ней — тонкий настил из корки: шагнул — через секунду провалится
                    fill(world, o, -h, -PIT, z0 + 2, h, -1, z1 - 2, Blocks.AIR.getDefaultState());
                    fill(world, o, -h, -PIT - 1, z0 + 2, h, -PIT - 1, z1 - 2, th.pit());
                    fill(world, o, -h, -1, z0 + 2, h, -1, z1 - 2, ModBlocks.CHAOS_CRUST.getDefaultState());
                    break;
                }
                case HOLD: {
                    int zc = (z0 + z1) / 2;
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            if (x * x + z * z <= 6) {
                                world.setBlockState(o.add(x, -1, zc + z), ModBlocks.CHAOS_CRUST.getDefaultState(), 2);
                            }
                        }
                    }
                    break;
                }
                case PARKOUR: {
                    // яма: пол уходит вниз, дно — шершавая «корка»; путь из столбиков с пропусками
                    fill(world, o, -h, -PIT, z0 + 2, h, -1, z1 - 2, Blocks.AIR.getDefaultState());
                    fill(world, o, -h, -PIT - 1, z0 + 2, h, -PIT - 1, z1 - 2, th.pit());
                    int x = 0;
                    for (int z = z0 + 3; z <= z1 - 3; ) {
                        fill(world, o, x, -PIT, z, x, -1, z, th.wall2());
                        z += 2 + (r.nextInt(3) == 0 ? 1 : 0);
                        x = Math.max(-h + 1, Math.min(h - 1, x + r.nextInt(3) - 1));
                    }
                    break;
                }
                case OFFERING: {
                    int zc = (z0 + z1) / 2;
                    world.setBlockState(o.add(0, 0, zc), ModBlocks.TRIAL_RUNE.getDefaultState(), 2);
                    BlockPos sign = o.add(0, 1, z1);
                    world.setBlockState(sign, Blocks.WALL_SIGN.getDefaultState().withProperty(BlockWallSign.FACING, EnumFacing.NORTH), 2);
                    TileEntity te = world.getTileEntity(sign);
                    if (te instanceof TileEntitySign) {
                        String[] lines = TrialManager.riddleLines(t, i);
                        for (int k = 0; k < 4; k++) {
                            ((TileEntitySign) te).signText[k] = new TextComponentString(k < lines.length ? lines[k] : "");
                        }
                    }
                    break;
                }
                case TREASURE: {
                    BlockPos chest = o.add(0, 0, z1 - 1);
                    world.setBlockState(chest, Blocks.CHEST.getDefaultState(), 2);
                    fillChest(world, chest, TrialLoot.roll(t, r), r);
                    if (TrialCondition.has(t.conditions, TrialCondition.DEBT)) {
                        fillChest(world, chest, TrialLoot.roll(t, r), r);
                    }
                    world.setBlockState(o.add(0, 0, z0 + 3), ModBlocks.TRIAL_EXIT.getDefaultState(), 2);
                    world.setBlockState(o.add(0, 1, z0 + 3), ModBlocks.TRIAL_EXIT.getDefaultState().withProperty(TrialBlocks.Exit.TOP, true), 2);
                    break;
                }
                default:
                    break;
            }
            // Мир долга: в комнатах-испытаниях стоит сундук-засада
            if (TrialCondition.has(t.conditions, TrialCondition.DEBT) && h >= 3 && type != TrialRoom.ENTRY
                    && type != TrialRoom.TREASURE && !type.pit()) {
                BlockPos c = o.add(h - 1, 0, z0 + 1);
                world.setBlockState(c, Blocks.CHEST.getDefaultState(), 2);
                fillChest(world, c, java.util.Arrays.asList(new ItemStack(ModItems.VEIL_SHARD, 1 + r.nextInt(3)),
                        new ItemStack(ModItems.CHAOS_ESSENCE, 1 + r.nextInt(2 + t.tier))), r);
            }
        }
        // коридоры и барьеры
        for (int i = 0; i < t.rooms.length - 1; i++) {
            int za = b[i][1] + 1, zb = b[i + 1][0] - 1;
            box(world, o, -2, -1, za, 2, 3, zb, th.wall(), th.wall2(), r);
            fill(world, o, -1, 0, za, 1, 2, zb, Blocks.AIR.getDefaultState());
            fill(world, o, -1, -1, za, 1, -1, zb, th.floor(false));
            if (i >= 1) {
                fill(world, o, -1, 0, za, 1, 2, za, ModBlocks.TRIAL_SEAL.getDefaultState());
            }
        }
    }

    private static void fillChest(World world, BlockPos chest, List<ItemStack> loot, Random r) {
        TileEntity te = world.getTileEntity(chest);
        if (!(te instanceof IInventory)) {
            return;
        }
        IInventory inv = (IInventory) te;
        List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) {
                slots.add(i);
            }
        }
        java.util.Collections.shuffle(slots, r);
        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            inv.setInventorySlotContents(slots.get(i), loot.get(i));
        }
    }

    /** Добавить в сокровищницу награду (Мир часов). */
    public static void addToTreasure(World world, TrialData.Trial t, List<ItemStack> loot) {
        int[][] b = t.bounds();
        int last = t.rooms.length - 1;
        fillChest(world, t.origin().add(0, 0, b[last][1] - 1), loot, world.rand);
    }

    public static BlockPos[] runePositions(TrialData.Trial t, int room) {
        BlockPos o = t.origin();
        int[] b = t.bounds()[room];
        int h = t.roomType(room).half - 2;
        return new BlockPos[]{o.add(-h, 0, b[0] + 2), o.add(h, 0, b[0] + 2), o.add(-h, 0, b[1] - 2), o.add(h, 0, b[1] - 2)};
    }

    /** Пять рун у дальней стены: свет и тень. */
    public static BlockPos[] lightsPositions(TrialData.Trial t, int room) {
        BlockPos o = t.origin();
        int z = t.bounds()[room][1];
        return new BlockPos[]{o.add(-4, 0, z), o.add(-2, 0, z), o.add(0, 0, z), o.add(2, 0, z), o.add(4, 0, z)};
    }

    public static BlockPos[] sequencePositions(TrialData.Trial t, int room) {
        BlockPos o = t.origin();
        int z = t.bounds()[room][1];
        return new BlockPos[]{o.add(-3, 0, z), o.add(-1, 0, z), o.add(1, 0, z), o.add(3, 0, z)};
    }

    /** Погасить барьер после комнаты i. */
    public static void openDoor(World world, TrialData.Trial t, int i) {
        if (i >= t.rooms.length - 1) {
            return;
        }
        BlockPos o = t.origin();
        int za = t.bounds()[i][1] + 1;
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
