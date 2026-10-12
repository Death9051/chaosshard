package ru.death.chaosshard.servant.work;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.servant.ServantGear;
import ru.death.chaosshard.servant.ServantUpgrade;
import ru.death.chaosshard.servant.Servants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Рудокоп: роет настоящую шахту от сундука. Лестница вниз (в сторону, куда я смотрел, когда закреплял) до высоты 12,
 * затем три штрека по 32 блока (вперёд, вправо, влево), через каждые 4 блока — боковые ходы по 12 в обе стороны.
 * Руду в стенах выбирает целыми жилами. Жидкости заделывает булыжником, каждые 8 шагов ставит факел (из сундука).
 * Пустую породу (булыжник, землю, гравий и т.п.) оставляет в отвале — в сундук идёт только ценное.
 */
public class WorkMiner extends EntityAIBase {

    public static final String TAG_MINE = "chaosshard_mine";
    public static final int TARGET_Y = 12, MAIN_LEN = 32, BRANCH_LEN = 12, VEIN_MAX = 48;

    private final EntityCreature m;
    private final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
    private int veinCount;
    private int t;

    public WorkMiner(EntityCreature m) {
        this.m = m;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return Work.chest(m) != null && m.getAttackTarget() == null && Work.alive(m) && !state().getBoolean("done");
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    private NBTTagCompound state() {
        NBTTagCompound d = m.getEntityData();
        if (!d.hasKey(TAG_MINE)) {
            d.setTag(TAG_MINE, new NBTTagCompound());
        }
        return d.getCompoundTag(TAG_MINE);
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
        NBTTagCompound s = state();
        List<BlockPos> col = column(s);
        if (col == null) {
            finish();
            return;
        }
        boolean blocked = false;
        for (BlockPos p : col) {
            IBlockState st = w.getBlockState(p);
            if (st.getMaterial().isLiquid()) {
                w.setBlockState(p, Blocks.COBBLESTONE.getDefaultState(), 3);
                return;
            }
            if (st.getBlock().isAir(st, w, p) || st.getBlock() == Blocks.TORCH || st.getMaterial().isReplaceable() && st.getMaterial() != net.minecraft.block.material.Material.WATER) {
                if (!st.getBlock().isAir(st, w, p) && st.getBlock() != Blocks.TORCH) {
                    w.setBlockToAir(p);
                }
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
            m.setPositionAndUpdate(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            scan(w, col);
            int n = s.getInteger("n") + 1;
            s.setInteger("n", n);
            if (n % 8 == 0) {
                torch(w, feet);
            }
        }
        advance(s, blocked);
    }

    // ------------------------------------------------------------------ план шахты

    private EnumFacing[] mains() {
        EnumFacing d = Work.dir(m);
        return new EnumFacing[]{d, d.rotateY(), d.rotateYCCW()};
    }

    /** Блоки текущего забоя: первый — где встанут ноги. null — шахта выработана. */
    private List<BlockPos> column(NBTTagCompound s) {
        BlockPos c = Work.chest(m);
        EnumFacing dir = Work.dir(m);
        int phase = s.getInteger("phase");
        List<BlockPos> out = new ArrayList<>();
        boolean wide = ServantUpgrade.has(m, ServantUpgrade.MINE_WIDE);
        if (phase == 0) {
            int i = s.getInteger("i");
            BlockPos feet = c.offset(dir, 2 + i).down(i);
            out.add(feet);
            out.add(feet.up());
            out.add(feet.up(2));
            return out;
        }
        if (!s.hasKey("by")) {
            return null;
        }
        BlockPos bottom = new BlockPos(s.getInteger("bx"), s.getInteger("by"), s.getInteger("bz"));
        int dn = s.getInteger("dn");
        if (dn >= 3) {
            return null;
        }
        EnumFacing d = mains()[dn];
        int j = Math.max(1, s.getInteger("j"));
        BlockPos feet;
        EnumFacing along;
        if (phase == 1) {
            feet = bottom.offset(d, j);
            along = d;
        } else {
            EnumFacing side = s.getInteger("side") == 0 ? d.rotateYCCW() : d.rotateY();
            feet = bottom.offset(d, j).offset(side, s.getInteger("k"));
            along = side;
        }
        out.add(feet);
        out.add(feet.up());
        if (wide) {
            out.add(feet.up(2));
            BlockPos f2 = feet.offset(along.rotateY());
            out.add(f2);
            out.add(f2.up());
            out.add(f2.up(2));
        }
        return out;
    }

    private void advance(NBTTagCompound s, boolean blocked) {
        int phase = s.getInteger("phase");
        if (phase == 0) {
            int i = s.getInteger("i");
            BlockPos feet = Work.chest(m).offset(Work.dir(m), 2 + i).down(i);
            if (blocked || feet.getY() <= TARGET_Y) {
                BlockPos bottom = blocked && i > 0 ? Work.chest(m).offset(Work.dir(m), 1 + i).down(i - 1) : feet;
                s.setInteger("bx", bottom.getX());
                s.setInteger("by", bottom.getY());
                s.setInteger("bz", bottom.getZ());
                s.setInteger("phase", 1);
                s.setInteger("dn", 0);
                s.setInteger("j", 1);
            } else {
                s.setInteger("i", i + 1);
            }
            return;
        }
        int j = Math.max(1, s.getInteger("j"));
        if (phase == 1) {
            if (blocked || j >= MAIN_LEN) {
                nextMain(s);
            } else if (j % 4 == 0) {
                s.setInteger("phase", 2);
                s.setInteger("side", 0);
                s.setInteger("k", 1);
            } else {
                s.setInteger("j", j + 1);
            }
            return;
        }
        int k = s.getInteger("k");
        if (blocked || k >= BRANCH_LEN) {
            if (s.getInteger("side") == 0) {
                s.setInteger("side", 1);
                s.setInteger("k", 1);
                teleportBack(s);
            } else {
                s.setInteger("phase", 1);
                s.setInteger("j", j + 1);
                teleportBack(s);
            }
        } else {
            s.setInteger("k", k + 1);
        }
    }

    private void nextMain(NBTTagCompound s) {
        s.setInteger("dn", s.getInteger("dn") + 1);
        s.setInteger("j", 1);
        s.setInteger("phase", 1);
        teleportBack(s);
    }

    /** Вернуться к развилке по штреку (просто встать на неё). */
    private void teleportBack(NBTTagCompound s) {
        if (!s.hasKey("by") || s.getInteger("dn") >= 3) {
            return;
        }
        BlockPos bottom = new BlockPos(s.getInteger("bx"), s.getInteger("by"), s.getInteger("bz"));
        BlockPos at = bottom.offset(mains()[s.getInteger("dn")], Math.max(0, s.getInteger("j") - 1));
        if (m.world.isAirBlock(at) && m.world.isAirBlock(at.up())) {
            m.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
    }

    private void finish() {
        NBTTagCompound s = state();
        if (s.getBoolean("done")) {
            return;
        }
        s.setBoolean("done", true);
        EntityPlayer o = Servants.owner(m);
        if (o != null) {
            o.sendMessage(Breach.grimoire("chaosshard.work.mine_done"));
        }
        BlockPos c = Work.chest(m);
        m.setPositionAndUpdate(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ копание

    private static boolean diggable(World w, BlockPos p, IBlockState s) {
        return s.getBlockHardness(w, p) >= 0 && s.getBlockHardness(w, p) < 50 && !s.getBlock().hasTileEntity(s)
                && s.getBlock() != Blocks.BEDROCK;
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
                || i == Item.getItemFromBlock(Blocks.SANDSTONE);
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
        // гравий/песок сверху осыплется — в следующий шаг будет выкопан
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
