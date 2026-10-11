package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.ItemStackHandler;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.init.ModBlocks;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Сердце Острова. Раз в несколько секунд вокруг него нарастает руда — кольцом
 * на расстоянии 2–3 блоков, на уровне Сердца и на блок выше, только в пустом месте:
 * само Сердце всегда открыто, а земля и постройки рядом не ломаются.
 * Модули (вставные модификаторы) меняют скорость, лимит, редкость и количество наростов.
 * Игрок может выключить ненужные руды — тогда растут только выбранные.
 */
public class TileHeart extends TileEntity implements ITickable {

    public static final int RADIUS = 3;
    public static final int MODULE_SLOTS = 4;

    private int tier = 1;
    private int timer;
    private final Set<String> disabled = new HashSet<>();

    public final ItemStackHandler modules = new ItemStackHandler(MODULE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot < tier && stack.getItem() instanceof ItemHeartModule;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 4;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
            sync();
        }
    };

    // ------------------------------------------------------------------ состояние

    public int getTier() {
        return tier;
    }

    public int getTimer() {
        return timer;
    }

    public void setTier(int tier) {
        this.tier = Math.max(1, Math.min(tier, HeartTiers.maxTier()));
        markDirty();
        sync();
    }

    /** Счётчики модулей на клиенте (для вида Сердца). */
    private final int[] clientModules = new int[ItemHeartModule.Type.values().length];

    /** Шанс, что вместо руды вырастет Жила Завесы: 10% за каждый модуль «Богатство». */
    public float richChance() {
        return ChaosConfig.heart.veinChancePerModule * count(ItemHeartModule.Type.RICH);
    }

    /** Какой нарост вырастет: руда из пула или (с «Богатством») Жила Завесы. */
    private IBlockState pickGrowth(List<HeartTiers.Growth> pool, Random rand) {
        if (rand.nextFloat() < richChance()) {
            return ModBlocks.VEIL_VEIN.getDefaultState();
        }
        return HeartTiers.pick(pool, rand);
    }

    /** Все блоки, которые считаются наростами. */
    private static Set<IBlockState> growthStates() {
        Set<IBlockState> states = new HashSet<>();
        for (HeartTiers.Growth g : HeartTiers.pool(HeartTiers.maxTier())) {
            states.add(g.state);
        }
        states.add(ModBlocks.VEIL_VEIN.getDefaultState());
        return states;
    }

    // ------------------------------------------------------------------ рождение нароста из разлома

    /** Нарост, который ещё «проступает» из разлома. */
    private static final class Pending {
        final BlockPos at;
        final IBlockState state;
        int ticks;

        Pending(BlockPos at, IBlockState state, int ticks) {
            this.at = at;
            this.state = state;
            this.ticks = ticks;
        }
    }

    private final List<Pending> pending = new ArrayList<>();

    private void tickPending() {
        if (pending.isEmpty()) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        java.util.Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            double x = p.at.getX() + 0.5, y = p.at.getY() + 0.5, z = p.at.getZ() + 0.5;
            if (--p.ticks > 0) {
                // Разлом: частицы тянутся в точку, где появится нарост
                if (p.ticks % 3 == 0) {
                    ws.spawnParticle(EnumParticleTypes.PORTAL, x, y, z, 14, 0.15, 0.15, 0.15, 1.2);
                    ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, 2, 0.1, 0.1, 0.1, 0.0);
                }
                if (p.ticks == RIFT_TICKS - 1) {
                    world.playSound(null, p.at, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.25F, 1.8F);
                }
                continue;
            }
            it.remove();
            if (!canReplace(p.at)) {
                continue;
            }
            world.setBlockState(p.at, p.state, 3);
            boolean rare = p.state.getBlock() == ModBlocks.VEIL_VEIN || HeartTiers.weightOf(p.state) <= ChaosConfig.heart.lightningMaxWeight;
            if (rare && ChaosConfig.heart.lightning) {
                // Редкий нарост — тонкая дуга из самого Сердца (как у виспов), без грома
                boltTo(p.at, p.state.getBlock() == ModBlocks.VEIL_VEIN ? 0xC070FF : 0x9A60FF);
            }
            world.playSound(null, p.at, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.BLOCKS, 0.5F, 0.6F);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, 20, 0.4, 0.4, 0.4, 0.05);
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x, y, z, 12, 0.3, 0.3, 0.3, 0.3);
        }
    }

    private static final int RIFT_TICKS = 40;

    private void boltTo(BlockPos target, int color) {
        double sx = pos.getX() + 0.5, sy = pos.getY() + 0.55, sz = pos.getZ() + 0.5;
        ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendToAllAround(
                new ru.death.chaosshard.network.PacketHeartBolt(sx, sy, sz,
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, color),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(world.provider.getDimension(), sx, sy, sz, 64));
        world.playSound(null, target, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.BLOCKS, 0.15F, 1.9F);
    }

    public int count(ItemHeartModule.Type type) {
        if (world != null && world.isRemote) {
            return clientModules[type.ordinal()];
        }
        int n = 0;
        for (int i = 0; i < Math.min(tier, MODULE_SLOTS); i++) {
            ItemStack s = modules.getStackInSlot(i);
            if (s.getItem() instanceof ItemHeartModule && ((ItemHeartModule) s.getItem()).type == type) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int countServer(ItemHeartModule.Type type) {
        int n = 0;
        for (int i = 0; i < Math.min(tier, MODULE_SLOTS); i++) {
            ItemStack s = modules.getStackInSlot(i);
            if (s.getItem() instanceof ItemHeartModule && ((ItemHeartModule) s.getItem()).type == type) {
                n += s.getCount();
            }
        }
        return n;
    }

    public int interval() {
        int[] iv = ChaosConfig.heart.growthInterval;
        int base = iv.length == 0 ? 600 : iv[Math.min(tier, iv.length) - 1];
        return Math.max(20, (int) (base / (1.0 + 0.35 * count(ItemHeartModule.Type.PULSE))));
    }

    public int maxGrowths() {
        int[] mg = ChaosConfig.heart.maxGrowths;
        int base = mg.length == 0 ? 8 : mg[Math.min(tier, mg.length) - 1];
        return base + 6 * count(ItemHeartModule.Type.CAPACITY);
    }

    // ------------------------------------------------------------------ выбор руды

    public static String key(IBlockState state) {
        return state.getBlock().getRegistryName() + ":" + state.getBlock().getMetaFromState(state);
    }

    public boolean isEnabled(IBlockState state) {
        return !disabled.contains(key(state));
    }

    public void toggle(String key) {
        if (!disabled.remove(key)) {
            disabled.add(key);
        }
        markDirty();
        sync();
    }

    /** Пул с учётом выключенных руд. */
    public List<HeartTiers.Growth> activePool() {
        List<HeartTiers.Growth> all = HeartTiers.pool(tier);
        List<HeartTiers.Growth> out = new ArrayList<>();
        for (HeartTiers.Growth g : all) {
            if (isEnabled(g.state)) {
                out.add(g);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ рост

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        tickPending();
        if (++timer < interval()) {
            return;
        }
        timer = 0;
        if (grow(false) && world.rand.nextFloat() < 0.25F * count(ItemHeartModule.Type.FERTILE)) {
            grow(false);
        }
    }

    /** Жнец над Сердцем, если стоит. */
    public TileHarvester harvester() {
        TileEntity te = world.getTileEntity(pos.up());
        return te instanceof TileHarvester ? (TileHarvester) te : null;
    }

    /** Блок руды как предмет (так же, как он выпадает при добыче). */
    private static ItemStack asItem(IBlockState state) {
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(state.getBlock());
        return item == net.minecraft.init.Items.AIR ? ItemStack.EMPTY
                : new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
    }

    /** Жнец срезает один уже выросший нарост вокруг Сердца. */
    private void harvestExisting(TileHarvester h) {
        Set<IBlockState> states = growthStates();
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-RADIUS, 0, -RADIUS), pos.add(RADIUS, 1, RADIUS))) {
            IBlockState s = world.getBlockState(p);
            if (states.contains(s)) {
                ItemStack item = harvestItem(s);
                if (!item.isEmpty() && h.hasRoomFor(item)) {
                    h.accept(item);
                    world.setBlockToAir(p);
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                            10, 0.3, 0.3, 0.3, 0.3);
                }
                return;
            }
        }
    }

    /** Вырастить один нарост. force — игнорировать лимит. */
    public boolean grow(boolean force) {
        List<HeartTiers.Growth> pool = activePool();
        if (pool.isEmpty()) {
            return false;
        }
        TileHarvester h = harvester();
        if (h != null) {
            // С Жнецом нарост сразу уходит в хранилище; места нет — растёт как обычно
            harvestExisting(h);
            IBlockState ore = pickGrowth(pool, world.rand);
            ItemStack item = ore == null ? ItemStack.EMPTY : harvestItem(ore);
            if (!item.isEmpty() && h.hasRoomFor(item)) {
                h.accept(item);
                world.playSound(null, pos, SoundEvents.BLOCK_CHORUS_FLOWER_GROW, SoundCategory.BLOCKS, 0.4F, 1.2F);
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5, pos.getY() + 1.2,
                        pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.02);
                return true;
            }
        }
        if (!force && countGrowths() >= maxGrowths()) {
            return false;
        }
        List<BlockPos> spots = new ArrayList<>();
        // Только на уровне Сердца и на блок выше: землю под собой Сердце не трогает
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-RADIUS, 0, -RADIUS), pos.add(RADIUS, 1, RADIUS))) {
            int ring = Math.max(Math.abs(p.getX() - pos.getX()), Math.abs(p.getZ() - pos.getZ()));
            if (ring >= 2 && canReplace(p) && !isPending(p)) {
                spots.add(p);
            }
        }
        if (spots.isEmpty()) {
            return false;
        }
        Random rand = world.rand;
        BlockPos target = spots.get(rand.nextInt(spots.size()));
        IBlockState ore = pickGrowth(pool, rand);
        if (ore == null) {
            return false;
        }
        // Нарост не появляется сразу: сначала над местом открывается разлом
        pending.add(new Pending(target.toImmutable(), ore, RIFT_TICKS));
        markDirty();
        return true;
    }

    private boolean isPending(BlockPos p) {
        for (Pending x : pending) {
            if (x.at.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** Что получает Жнец за нарост: блок руды или Осколки Завесы за жилу. */
    private ItemStack harvestItem(IBlockState state) {
        if (state.getBlock() == ModBlocks.VEIL_VEIN) {
            return new ItemStack(ru.death.chaosshard.init.ModItems.VEIL_SHARD, 1 + world.rand.nextInt(2));
        }
        return asItem(state);
    }

    private boolean canReplace(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        Block b = s.getBlock();
        if (world.getTileEntity(p) != null) {
            return false;
        }
        // Наросты занимают только пустое место (траву и прочее заменяемое), блоки не ломают
        return world.isAirBlock(p) || (b.isReplaceable(world, p) && !s.getMaterial().isLiquid());
    }

    /** Наросты вокруг Сердца (любая руда любой ступени). */
    public int countGrowths() {
        Set<IBlockState> states = growthStates();
        int n = pending.size();
        for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-RADIUS, -RADIUS, -RADIUS), pos.add(RADIUS, RADIUS, RADIUS))) {
            if (states.contains(world.getBlockState(p))) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ сохранение и синхронизация

    private void sync() {
        if (world != null && !world.isRemote) {
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeShared(tag);
        tag.setInteger("timer", timer);
        tag.setTag("modules", modules.serializeNBT());
        NBTTagList pend = new NBTTagList();
        for (Pending p : pending) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", p.at.toLong());
            c.setInteger("state", Block.getStateId(p.state));
            c.setInteger("ticks", p.ticks);
            pend.appendTag(c);
        }
        tag.setTag("pending", pend);
        return tag;
    }

    private void writeShared(NBTTagCompound tag) {
        tag.setInteger("tier", tier);
        int[] counts = new int[ItemHeartModule.Type.values().length];
        if (world == null || !world.isRemote) {
            for (ItemHeartModule.Type t : ItemHeartModule.Type.values()) {
                counts[t.ordinal()] = countServer(t);
            }
        }
        tag.setIntArray("mods", counts);
        NBTTagList list = new NBTTagList();
        for (String k : disabled) {
            list.appendTag(new NBTTagString(k));
        }
        tag.setTag("disabled", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readShared(tag);
        timer = tag.getInteger("timer");
        if (tag.hasKey("modules")) {
            modules.deserializeNBT(tag.getCompoundTag("modules"));
        }
        pending.clear();
        NBTTagList pend = tag.getTagList("pending", 10);
        for (int i = 0; i < pend.tagCount(); i++) {
            NBTTagCompound c = pend.getCompoundTagAt(i);
            pending.add(new Pending(BlockPos.fromLong(c.getLong("pos")), Block.getStateById(c.getInteger("state")), c.getInteger("ticks")));
        }
    }

    private void readShared(NBTTagCompound tag) {
        tier = Math.max(1, tag.getInteger("tier"));
        int[] counts = tag.getIntArray("mods");
        for (int i = 0; i < clientModules.length && i < counts.length; i++) {
            clientModules[i] = counts[i];
        }
        disabled.clear();
        NBTTagList list = tag.getTagList("disabled", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            disabled.add(list.getStringTagAt(i));
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.writeToNBT(new NBTTagCompound());
        writeShared(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readShared(tag);
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readShared(pkt.getNbtCompound());
        if (world != null) {
            world.markBlockRangeForRenderUpdate(pos, pos);
            world.checkLight(pos);
        }
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos.add(-2, -1, -2), pos.add(3, 4, 3));
    }
}
