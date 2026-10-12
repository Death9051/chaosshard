package ru.death.chaosshard.servant.work;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.servant.ServantGear;
import ru.death.chaosshard.servant.ServantUpgrade;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.servant.work.MineData.Job;
import ru.death.chaosshard.servant.work.MineData.Mine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Рудокоп. Шахта у сундука:
 *  — винтовая каменная лестница вокруг столба (ствол 3×3 рядом с сундуком, в ту сторону, куда я смотрел при закреплении)
 *    до коренной породы;
 *  — на уровнях, где руды больше всего (≈50: медь, олово, железо, уголь; ≈28: олово, свинец, серебро, золото, осмий;
 *    ≈11: алмазы, редстоун, золото, лазурит, никель, серебро), — штрек 128 блоков, от него через каждые 3 блока
 *    ответвления по 32 в обе стороны.
 * Несколько Рудокопов у одного сундука делят шахту: лестница, штреки и ответвления — отдельные участки.
 * Руду выбирает жилами, жидкости заделывает булыжником, факелы — из сундука. Пустую породу оставляет в отвале.
 */
public class WorkMiner extends EntityAIBase {

    public static final String TAG_MINE = "chaosshard_mine";
    public static final int[] LEVELS = {50, 28, 11};
    public static final int MAIN_LEN = 128, BRANCH_LEN = 32, BRANCH_EVERY = 3, VEIN_MAX = 48;

    private final EntityCreature m;
    private final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
    private int veinCount;
    private int t;

    public WorkMiner(EntityCreature m) {
        this.m = m;
        setMutexBits(3);
    }

    private Mine mine() {
        return MineData.get(m.world).mine(Work.chest(m), Work.dir(m).getHorizontalIndex());
    }

    @Override
    public boolean shouldExecute() {
        if (Work.chest(m) == null || m.getAttackTarget() != null || !Work.alive(m)) {
            return false;
        }
        Mine mine = mine();
        if (mine.done()) {
            announce(mine);
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    private EnumFacing dir(Mine mine) {
        EnumFacing[] h = {EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST};
        return h[Math.floorMod(mine.dir, 4)];
    }

    @Override
    public void updateTask() {
        t++;
        int step = Math.max(3, 10 - ServantGear.tier(m));
        if (ServantUpgrade.has(m, ServantUpgrade.MINE_PICK)) {
            step = Math.max(2, step / 2);
        }
        if (t % step != 0) {
            return;
        }
        m.getNavigator().clearPath();
        World w = m.world;
        // сначала — жила
        while (!vein.isEmpty()) {
            BlockPos p = vein.poll();
            if (isOre(w, p, w.getBlockState(p))) {
                dig(w, p);
                if (veinCount < VEIN_MAX) {
                    for (EnumFacing f : EnumFacing.values()) {
                        BlockPos n = p.offset(f);
                        if (isOre(w, n, w.getBlockState(n)) && !vein.contains(n)) {
                            vein.add(n);
                            veinCount++;
                        }
                    }
                }
                return;
            }
        }
        Mine mine = mine();
        long now = w.getTotalWorldTime();
        Job job = mine.claim(now, m.getUniqueID());
        if (job == null) {
            // всё разобрано другими — ждать у ствола
            if (t % 100 == 0) {
                BlockPos c = Work.chest(m);
                m.getNavigator().tryMoveToXYZ(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 0.9);
            }
            return;
        }
        MineData.get(w).markDirty();
        List<BlockPos> col = column(mine, job);
        // стоять у забоя: далеко — шагнуть по Нити на предыдущий (уже чистый) шаг
        BlockPos stand = stand(mine, job);
        if (stand != null && m.getDistanceSq(stand) > 25 && w.isAirBlock(stand) && w.isAirBlock(stand.up())) {
            WorkChunks.touch(m, stand);
            m.setPositionAndUpdate(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            ru.death.chaosshard.servant.Resync.later(m, 3);
        }
        boolean blocked = false;
        for (BlockPos p : col) {
            IBlockState st = w.getBlockState(p);
            if (st.getMaterial().isLiquid()) {
                w.setBlockState(p, Blocks.COBBLESTONE.getDefaultState(), 3);
                return;
            }
            if (st.getBlock().isAir(st, w, p) || st.getBlock() instanceof BlockTorch) {
                continue;
            }
            if (st.getMaterial().isReplaceable()) {
                w.setBlockToAir(p);
                continue;
            }
            if (!diggable(w, p, st)) {
                blocked = true;
                break;
            }
            m.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
            m.getLookHelper().setLookPosition(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 30, 30);
            dig(w, p);
            return;
        }
        BlockPos feet = col.get(0);
        if (!blocked) {
            WorkChunks.touch(m, feet);
            m.setPositionAndUpdate(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            scan(w, col);
        }
        advance(mine, job, feet, blocked);
    }

    // ------------------------------------------------------------------ план

    /** Ствол 3×3: центр (колодец) — в 3 блоках от сундука. */
    private BlockPos center(Mine mine) {
        return Work.chest(m).offset(dir(mine), 3);
    }

    /** 8 клеток-ступеней вокруг колодца по кругу; соседние — через сторону. a — вдоль dir, b — вправо. */
    private static final int[][] RING = {{-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}};

    private BlockPos ring(Mine mine, int k) {
        int[] r = RING[Math.floorMod(k, 8)];
        EnumFacing d = dir(mine);
        return center(mine).offset(d, r[0]).offset(d.rotateY(), r[1]).down(k);
    }

    private BlockPos origin(Mine mine, Job job) {
        BlockPos c = center(mine).offset(dir(mine), 2);
        return new BlockPos(c.getX(), job.ty, c.getZ());
    }

    private EnumFacing side(Mine mine, Job job) {
        return job.side == 0 ? dir(mine).rotateYCCW() : dir(mine).rotateY();
    }

    private BlockPos feet(Mine mine, Job job) {
        switch (job.type) {
            case MineData.SPIRAL:
                return ring(mine, mine.spiralK);
            case MineData.MAIN:
                return origin(mine, job).offset(dir(mine), job.j);
            default:
                return origin(mine, job).offset(dir(mine), job.j).offset(side(mine, job), job.k);
        }
    }

    private BlockPos stand(Mine mine, Job job) {
        switch (job.type) {
            case MineData.SPIRAL:
                return mine.spiralK > 0 ? ring(mine, mine.spiralK - 1) : null;
            case MineData.MAIN:
                return job.j > 0 ? origin(mine, job).offset(dir(mine), job.j - 1) : null;
            default:
                return origin(mine, job).offset(dir(mine), job.j).offset(side(mine, job), job.k - 1);
        }
    }

    private List<BlockPos> column(Mine mine, Job job) {
        BlockPos f = feet(mine, job);
        List<BlockPos> out = new ArrayList<>();
        out.add(f);
        out.add(f.up());
        out.add(f.up(2));
        if (job.type == MineData.SPIRAL) {
            // ствол целиком 3×3: над ступенью — пусто до ступени следующего витка (она на 8 выше, её не трогаем),
            // середина вынимается вровень со ступенями — колодец не обгоняет лестницу
            for (int up = 3; up <= 6; up++) {
                out.add(f.up(up));
            }
            BlockPos c = center(mine).down(mine.spiralK);
            out.add(c);
            out.add(c.up());
            out.add(c.up(2));
            return out;
        }
        if (!ServantUpgrade.has(m, ServantUpgrade.MINE_WIDE)) {
            out.remove(2);
            return out;
        }
        EnumFacing along = job.type == MineData.MAIN ? dir(mine) : side(mine, job);
        BlockPos f2 = f.offset(along.rotateY());
        out.add(f2);
        out.add(f2.up());
        out.add(f2.up(2));
        return out;
    }

    private void advance(Mine mine, Job job, BlockPos feet, boolean blocked) {
        World w = m.world;
        MineData data = MineData.get(w);
        switch (job.type) {
            case MineData.SPIRAL: {
                BlockPos floor = feet.down();
                IBlockState fs = w.getBlockState(floor);
                boolean bedrock = blocked || floor.getY() <= 0 || fs.getBlock() == Blocks.BEDROCK || fs.getBlockHardness(w, floor) < 0;
                if (!blocked && !bedrock) {
                    stair(mine, mine.spiralK, floor);
                    if (mine.spiralK % 8 == 4) {
                        wallTorch(mine, feet);
                    }
                    // уровень руды: штрек уходит от клетки на стороне dir
                    int[] r = RING[Math.floorMod(mine.spiralK, 8)];
                    while (mine.level < LEVELS.length && feet.getY() < LEVELS[mine.level] - 4) {
                        mine.level++;   // уже ниже этого уровня (сундук стоит глубоко)
                    }
                    if (mine.level < LEVELS.length && r[0] == 1 && r[1] == 0 && feet.getY() <= LEVELS[mine.level] + 3) {
                        Job main = new Job();
                        main.type = MineData.MAIN;
                        main.ty = feet.getY();
                        main.j = 0;
                        mine.jobs.add(main);
                        mine.level++;
                    }
                }
                if (bedrock) {
                    mine.spiralDone = true;
                    mine.spiral.claim = null;
                } else {
                    mine.spiralK++;
                }
                break;
            }
            case MineData.MAIN: {
                if (!blocked && job.j > 0 && job.j % 8 == 0) {
                    torch(w, feet);
                }
                if (!blocked && job.j > 0 && job.j % BRANCH_EVERY == 0) {
                    for (int s = 0; s < 2; s++) {
                        Job b = new Job();
                        b.type = MineData.BRANCH;
                        b.ty = job.ty;
                        b.j = job.j;
                        b.side = s;
                        b.k = 1;
                        mine.jobs.add(b);
                    }
                }
                if (blocked || job.j >= MAIN_LEN - 1) {
                    mine.jobs.remove(job);
                } else {
                    job.j++;
                }
                break;
            }
            default: {
                if (!blocked && job.k % 8 == 0) {
                    torch(w, feet);
                }
                if (blocked || job.k >= BRANCH_LEN) {
                    mine.jobs.remove(job);
                } else {
                    job.k++;
                }
                break;
            }
        }
        data.markDirty();
    }

    private void announce(Mine mine) {
        if (mine.announced) {
            return;
        }
        mine.announced = true;
        MineData.get(m.world).markDirty();
        EntityPlayer o = Servants.owner(m);
        if (o != null) {
            o.sendMessage(Breach.grimoire("chaosshard.work.mine_done"));
        }
    }

    // ------------------------------------------------------------------ лестница

    /** Каменная ступень: высокой стороной к предыдущему (верхнему) шагу. */
    private void stair(Mine mine, int k, BlockPos floor) {
        World w = m.world;
        IBlockState fs = w.getBlockState(floor);
        if (!fs.getBlock().isAir(fs, w, floor) && !diggable(w, floor, fs)) {
            return;
        }
        EnumFacing up;
        if (k == 0) {
            up = dir(mine).getOpposite();
        } else {
            BlockPos a = ring(mine, k), b = ring(mine, k - 1);
            up = EnumFacing.getFacingFromVector(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
        }
        if (fs.getBlock() != Blocks.STONE_STAIRS && !fs.getBlock().isAir(fs, w, floor)) {
            dig(w, floor);
        }
        w.setBlockState(floor, Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, up)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM), 3);
    }

    /** Факел на внешней стене ствола, раз в виток. */
    private void wallTorch(Mine mine, BlockPos feet) {
        World w = m.world;
        BlockPos at = feet.up();
        BlockPos c = center(mine);
        int dx = at.getX() - c.getX(), dz = at.getZ() - c.getZ();
        if (Math.abs(dx) + Math.abs(dz) != 1 || !w.isAirBlock(at)) {
            return;
        }
        EnumFacing out = EnumFacing.getFacingFromVector(dx, 0, dz);
        BlockPos wall = at.offset(out);
        if (!w.getBlockState(wall).isSideSolid(w, wall, out.getOpposite())) {
            return;
        }
        if (!ServantUpgrade.has(m, ServantUpgrade.MINE_TORCH)
                && Work.take(m, s -> s.getItem() == Item.getItemFromBlock(Blocks.TORCH)).isEmpty()) {
            return;
        }
        w.setBlockState(at, Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, out.getOpposite()), 3);
    }

    // ------------------------------------------------------------------ копание

    private static boolean diggable(World w, BlockPos p, IBlockState s) {
        float h = s.getBlockHardness(w, p);
        return h >= 0 && h < 50 && !s.getBlock().hasTileEntity(s) && s.getBlock() != Blocks.BEDROCK;
    }

    static boolean isOre(World w, BlockPos p, IBlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.LIT_REDSTONE_ORE || b == Blocks.REDSTONE_ORE) {
            return true;
        }
        Item item = Item.getItemFromBlock(b);
        if (item == net.minecraft.init.Items.AIR) {
            return false;
        }
        ItemStack st = new ItemStack(item, 1, b.damageDropped(s));
        if (st.isEmpty()) {
            return false;
        }
        for (int id : OreDictionary.getOreIDs(st)) {
            if (OreDictionary.getOreName(id).startsWith("ore")) {
                return true;
            }
        }
        return false;
    }

    private static boolean junk(ItemStack s) {
        Item i = s.getItem();
        return i == Item.getItemFromBlock(Blocks.COBBLESTONE) || i == Item.getItemFromBlock(Blocks.DIRT)
                || i == Item.getItemFromBlock(Blocks.GRAVEL) || i == Item.getItemFromBlock(Blocks.SAND)
                || i == Item.getItemFromBlock(Blocks.NETHERRACK) || i == Item.getItemFromBlock(Blocks.STONE)
                || i == Item.getItemFromBlock(Blocks.SANDSTONE) || i == Item.getItemFromBlock(Blocks.STONE_STAIRS);
    }

    private void dig(World w, BlockPos p) {
        IBlockState s = w.getBlockState(p);
        if (!diggable(w, p, s)) {
            return;
        }
        boolean ore = isOre(w, p, s);
        NonNullList<ItemStack> drops = NonNullList.create();
        s.getBlock().getDrops(drops, w, p, s, 0);
        w.playEvent(2001, p, Block.getStateId(s));
        w.setBlockToAir(p);
        boolean smelt = ore && ServantUpgrade.has(m, ServantUpgrade.MINE_SMELT);
        for (ItemStack d : drops) {
            if (d.isEmpty() || junk(d)) {
                continue;
            }
            if (smelt) {
                ItemStack r = FurnaceRecipes.instance().getSmeltingResult(d);
                if (!r.isEmpty()) {
                    d = new ItemStack(r.getItem(), r.getCount() * d.getCount(), r.getMetadata());
                }
            }
            Work.deposit(m, d);
        }
        // заделать жидкости вокруг
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = p.offset(f);
            if (w.getBlockState(n).getMaterial().isLiquid()) {
                w.setBlockState(n, Blocks.COBBLESTONE.getDefaultState(), 3);
            }
        }
    }

    /** Осмотреть стены забоя: руда — в очередь жилы. */
    private void scan(World w, List<BlockPos> col) {
        int r = ServantUpgrade.has(m, ServantUpgrade.MINE_SENSE) ? 2 : 1;
        veinCount = 0;
        for (BlockPos c : col) {
            for (BlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -r, -r), c.add(r, r, r))) {
                if (isOre(w, p, w.getBlockState(p))) {
                    BlockPos q = p.toImmutable();
                    if (!vein.contains(q)) {
                        vein.add(q);
                    }
                }
            }
        }
    }

    private void torch(World w, BlockPos feet) {
        if (!w.isAirBlock(feet) || !w.getBlockState(feet.down()).isSideSolid(w, feet.down(), EnumFacing.UP)) {
            return;
        }
        if (!ServantUpgrade.has(m, ServantUpgrade.MINE_TORCH)
                && Work.take(m, s -> s.getItem() == Item.getItemFromBlock(Blocks.TORCH)).isEmpty()) {
            return;
        }
        w.setBlockState(feet, Blocks.TORCH.getDefaultState(), 3);
    }
}
