package ru.death.chaosshard.lair;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.thread.ThreadBonus;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Сердце Логова. ПКМ — сведения и точка возрождения. Shift+ПКМ — поднять логово на следующий уровень
 * (подношение берётся из инвентаря).
 *  2 — Башня: в Лучезарии, на захваченной земле, Нить 3, 8 Искажённого металла, 4 Осколка мира, 8 обсидиана.
 *  3 — Цитадель: на земле Якоря 5-го уровня, Нить 4, звезда Незера, 4 дыхания дракона, 16 Искажённого металла.
 */
public class BlockLair extends Block {

    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 1, 3);
    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.0625, 0, 0.0625, 0.9375, 1, 0.9375);

    public BlockLair() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(5.0F);
        setResistance(2000.0F);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(blockState.getBaseState().withProperty(LEVEL, 1));
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LEVEL);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LEVEL, Math.max(1, Math.min(3, meta + 1)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LEVEL) - 1;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    /** Уровень логова, который помнит предмет (снятое Сердце не теряет выращенного). */
    public static final String TAG_LEVEL = "LairLevel";

    public static ItemStack withLevel(Block b, int level) {
        ItemStack s = new ItemStack(b);
        if (level > 1) {
            net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
            t.setInteger(TAG_LEVEL, level);
            s.setTagCompound(t);
        }
        return s;
    }

    public static int levelOf(ItemStack s) {
        return s.hasTagCompound() ? Math.max(1, Math.min(3, s.getTagCompound().getInteger(TAG_LEVEL))) : 1;
    }

    public static final String TAG_ENERGY = "LairEnergy";
    /** Запас батареи снимаемого Сердца: harvestBlock видит блок-сущность, getDrops — уже нет. */
    private static final ThreadLocal<Integer> DROP_ENERGY = new ThreadLocal<>();

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack tool) {
        DROP_ENERGY.set(te instanceof TileLair ? ((TileLair) te).energy() : 0);
        try {
            super.harvestBlock(world, player, pos, state, te, tool);
        } finally {
            DROP_ENERGY.remove();
        }
    }

    public static int energyOf(ItemStack s) {
        return s.hasTagCompound() ? s.getTagCompound().getInteger(TAG_ENERGY) : 0;
    }

    /** Сердце падает со своим уровнем и запасом батареи: переставить логово можно без потерь. */
    @Override
    public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ItemStack s = withLevel(this, state.getValue(LEVEL));
        Integer e = DROP_ENERGY.get();
        if (e != null && e > 0) {
            if (!s.hasTagCompound()) {
                s.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
            }
            s.getTagCompound().setInteger(TAG_ENERGY, e);
        }
        drops.add(s);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return withLevel(this, state.getValue(LEVEL));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, java.util.List<String> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        int lv = levelOf(stack);
        if (lv > 1) {
            tooltip.add(net.minecraft.util.text.TextFormatting.LIGHT_PURPLE
                    + net.minecraft.util.text.translation.I18n.translateToLocal("chaosshard.lair.item_level_" + lv));
        }
        if (energyOf(stack) > 0) {
            tooltip.add(net.minecraft.util.text.TextFormatting.AQUA
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted("chaosshard.lair.item_energy", energyOf(stack)));
        }
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState();
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 5 + 3 * state.getValue(LEVEL);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileLair();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(placer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) placer;
        LairData data = LairData.get(world);
        BlockPos old = data.lairOf(p.getUniqueID(), world.provider.getDimension());
        if (old != null && !old.equals(pos) && world.getBlockState(old).getBlock() == this) {
            p.sendMessage(Breach.grimoire("chaosshard.lair.one", old.getX(), old.getY(), old.getZ()));
            world.setBlockToAir(pos);
            if (!p.isCreative()) {
                ItemStack back = stack.copy();
                back.setCount(1);
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, back);
            }
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        int lv = levelOf(stack);
        if (te instanceof TileLair) {
            ((TileLair) te).setOwner(p);
            if (lv > 1) {
                ((TileLair) te).setLevel(lv);
            }
            ((TileLair) te).setEnergy(energyOf(stack));
        }
        data.set(p.getUniqueID(), world.provider.getDimension(), pos.toImmutable());
        if (lv >= 3) {
            data.setCitadel(p.getUniqueID(), world.provider.getDimension(), pos.toImmutable());
        }
        p.sendMessage(Breach.grimoire("chaosshard.lair.placed"));
        ru.death.chaosshard.quest.Milestones.grant(p, "lair_1");
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileLair && ((TileLair) te).getOwner() != null) {
                LairData d = LairData.get(world);
                if (pos.equals(d.lairOf(((TileLair) te).getOwner(), world.provider.getDimension()))) {
                    d.set(((TileLair) te).getOwner(), world.provider.getDimension(), null);
                }
                // снятая Цитадель больше не держит тварей
                Object[] c = d.citadelOf(((TileLair) te).getOwner());
                if (c != null && ((Integer) c[0]) == world.provider.getDimension() && pos.equals(c[1])) {
                    d.setCitadel(((TileLair) te).getOwner(), world.provider.getDimension(), null);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileLair)) {
            return true;
        }
        TileLair lair = (TileLair) te;
        EntityPlayerMP p = (EntityPlayerMP) player;
        if (lair.getOwner() != null && !lair.getOwner().equals(p.getUniqueID())) {
            p.sendMessage(Breach.grimoire("chaosshard.lair.foreign", lair.getOwnerName()));
            return true;
        }
        // окно логова: точка возрождения и рост
        p.openGui(ru.death.chaosshard.ChaosShard.INSTANCE, ru.death.chaosshard.heart.HeartGuiHandler.LAIR, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    /** Возрождаться у этого логова. */
    public static void setSpawn(World world, BlockPos pos, EntityPlayerMP p) {
        int dim = world.provider.getDimension();
        p.setSpawnChunk(pos.up(), true, dim);
        p.setSpawnDimension(dim);
        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_PORTAL_TRIGGER, net.minecraft.util.SoundCategory.BLOCKS, 0.3F, 1.6F);
    }

    /** Возрождаюсь ли я здесь. */
    public static boolean spawnHere(World world, BlockPos pos, EntityPlayer p) {
        BlockPos b = p.getBedLocation(world.provider.getDimension());
        return b != null && b.equals(pos.up()) && p.isSpawnForced(world.provider.getDimension()) && p.getSpawnDimension() == world.provider.getDimension();
    }

    /** Что стоит рост до следующего уровня. */
    public static Object[][] cost(int next) {
        return next == 2
                ? new Object[][]{{ModItems.DISTORTED_STARMETAL, 4}, {ModItems.WORLD_SHARD, 2}, {Item.getItemFromBlock(net.minecraft.init.Blocks.OBSIDIAN), 4}}
                : new Object[][]{{ModItems.DISTORTED_STARMETAL, 8}, {ModItems.WORLD_SHARD, 4}};
    }

    /** Нить, нужная для роста до next. */
    public static int needNit(int next) {
        return next == 2 ? 3 : 4;
    }

    /** Подходит ли земля под логово уровня next. */
    public static boolean landOk(World world, BlockPos pos, int next) {
        TileAnchor a = anchorAt(world, pos);
        if (AntechamberDimension.is(world) || a == null || !a.isCaptured()) {
            return false;
        }
        return next < 3 || a.level() >= TileAnchor.maxLevel();
    }

    /**
     * Окно логова: что даёт сейчас и что нужно для роста (считается на сервере, окно только рисует).
     */
    public static net.minecraft.nbt.NBTTagCompound view(World world, BlockPos pos, TileLair lair, EntityPlayerMP p) {
        net.minecraft.nbt.NBTTagCompound v = new net.minecraft.nbt.NBTTagCompound();
        v.setInteger("level", lair.getLevel());
        v.setInteger("radius", lair.radius());
        v.setInteger("power", lair.power());
        v.setInteger("base", TileLair.POWER[lair.getLevel() - 1]);
        v.setInteger("energy", lair.energy());
        v.setInteger("capacity", lair.capacity());
        v.setInteger("output", lair.output());
        v.setInteger("inflow", lair.inflow());
        v.setInteger("forges", lair.forges());
        v.setInteger("generators", lair.generators());
        v.setBoolean("spawn", spawnHere(world, pos, p));
        int next = lair.getLevel() + 1;
        if (next <= 3) {
            v.setInteger("next", next);
            TileAnchor a = anchorAt(world, pos);
            boolean captured = !AntechamberDimension.is(world) && a != null && a.isCaptured();
            v.setBoolean("land", captured);
            v.setBoolean("pillar", next < 3 || captured && a.level() >= TileAnchor.maxLevel());
            v.setInteger("pillarLevel", TileAnchor.maxLevel());
            v.setInteger("nit", ThreadBonus.level(p));
            v.setInteger("nitNeed", needNit(next));
            net.minecraft.nbt.NBTTagList items = new net.minecraft.nbt.NBTTagList();
            for (Object[] c : cost(next)) {
                net.minecraft.nbt.NBTTagCompound t = new ItemStack((Item) c[0]).writeToNBT(new net.minecraft.nbt.NBTTagCompound());
                t.setInteger("need", (Integer) c[1]);
                t.setInteger("have", count(p, (Item) c[0]));
                items.appendTag(t);
            }
            v.setTag("cost", items);
            v.setBoolean("creative", p.isCreative());
        }
        return v;
    }

    /** Вырастить логово (из окна). */
    public static void tryUpgrade(World world, BlockPos pos, TileLair lair, EntityPlayerMP p) {
        int next = lair.getLevel() + 1;
        if (next > 3) {
            p.sendMessage(Breach.grimoire("chaosshard.lair.max"));
            return;
        }
        int nit = ThreadBonus.level(p);
        if (!landOk(world, pos, 2)) {
            p.sendMessage(Breach.grimoire("chaosshard.lair.need_land"));
            return;
        }
        if (!landOk(world, pos, next)) {
            p.sendMessage(Breach.grimoire("chaosshard.lair.need_pillar"));
            return;
        }
        int needNit = needNit(next);
        if (nit < needNit && !p.isCreative()) {
            p.sendMessage(Breach.grimoire("chaosshard.lair.thin", needNit));
            return;
        }
        Object[][] cost = cost(next);
        if (!p.isCreative()) {
            for (Object[] c : cost) {
                if (count(p, (Item) c[0]) < (Integer) c[1]) {
                    p.sendMessage(Breach.grimoire("chaosshard.lair.cost_" + next));
                    return;
                }
            }
            for (Object[] c : cost) {
                take(p, (Item) c[0], (Integer) c[1]);
            }
        }
        lair.setLevel(next);
        if (next == 3) {
            LairData.get(world).setCitadel(p.getUniqueID(), world.provider.getDimension(), pos);
        }
        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_END_PORTAL_SPAWN, net.minecraft.util.SoundCategory.BLOCKS, 0.8F, 0.6F);
        ((net.minecraft.world.WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, pos.getX() + 0.5, pos.getY() + 1,
                pos.getZ() + 0.5, 120, 1.5, 1.5, 1.5, 0.02);
        p.sendMessage(Breach.grimoire("chaosshard.lair.up_" + next));
        ru.death.chaosshard.quest.Milestones.grant(p, "lair_" + next);
    }

    private static TileAnchor anchorAt(World world, BlockPos pos) {
        TileAnchor best = null;
        for (BlockPos ap : AnchorData.get(world).anchors) {
            if (!world.isBlockLoaded(ap)) {
                continue;
            }
            TileEntity te = world.getTileEntity(ap);
            if (te instanceof TileAnchor) {
                TileAnchor a = (TileAnchor) te;
                double dx = ap.getX() - pos.getX(), dz = ap.getZ() - pos.getZ();
                if (dx * dx + dz * dz <= (double) a.radius() * a.radius() && (best == null || a.level() > best.level())) {
                    best = a;
                }
            }
        }
        return best;
    }

    private static int count(EntityPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s.getItem() == item) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void take(EntityPlayer p, Item item, int n) {
        for (ItemStack s : p.inventory.mainInventory) {
            if (n > 0 && s.getItem() == item) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        int lvl = state.getValue(LEVEL);
        for (int i = 0; i < lvl; i++) {
            world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5 + (rand.nextDouble() - 0.5), pos.getY() + 0.5 + rand.nextDouble() * lvl,
                    pos.getZ() + 0.5 + (rand.nextDouble() - 0.5), 0, 0.05, 0);
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
}
