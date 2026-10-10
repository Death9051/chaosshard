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
 * на расстоянии 2–3 блоков, так что само Сердце всегда открыто.
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
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
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

    public int count(ItemHeartModule.Type type) {
        int n = 0;
        for (int i = 0; i < Math.min(tier, MODULE_SLOTS); i++) {
            ItemStack s = modules.getStackInSlot(i);
            if (s.getItem() instanceof ItemHeartModule && ((ItemHeartModule) s.getItem()).type == type) {
                n++;
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

    /** Пул с учётом выключенных руд и модулей «Богатство». */
    public List<HeartTiers.Growth> activePool() {
        List<HeartTiers.Growth> all = HeartTiers.pool(tier);
        List<HeartTiers.Growth> out = new ArrayList<>();
        int maxWeight = 1;
        for (HeartTiers.Growth g : all) {
            maxWeight = Math.max(maxWeight, g.weight);
        }
        int rich = count(ItemHeartModule.Type.RICH);
        for (HeartTiers.Growth g : all) {
            if (isEnabled(g.state)) {
                // Богатство подтягивает редкие руды к частым
                out.add(new HeartTiers.Growth(g.state, g.weight + rich * Math.max(1, maxWeight / 3)));
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
        if (++timer < interval()) {
            return;
        }
        timer = 0;
        if (grow(false) && world.rand.nextFloat() < 0.25F * count(ItemHeartModule.Type.FERTILE)) {
            grow(false);
        }
    }

    /** Вырастить один нарост. force — игнорировать лимит. */
    public boolean grow(boolean force) {
        List<HeartTiers.Growth> pool = activePool();
        if (pool.isEmpty()) {
            return false;
        }
        if (!force && countGrowths() >= maxGrowths()) {
            return false;
        }
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-RADIUS, -1, -RADIUS), pos.add(RADIUS, 1, RADIUS))) {
            int ring = Math.max(Math.abs(p.getX() - pos.getX()), Math.abs(p.getZ() - pos.getZ()));
            if (ring >= 2 && canReplace(p)) {
                spots.add(p);
            }
        }
        if (spots.isEmpty()) {
            return false;
        }
        Random rand = world.rand;
        BlockPos target = spots.get(rand.nextInt(spots.size()));
        IBlockState ore = HeartTiers.pick(pool, rand);
        if (ore == null) {
            return false;
        }
        world.setBlockState(target, ore, 3);
        world.playSound(null, target, SoundEvents.BLOCK_CHORUS_FLOWER_GROW, SoundCategory.BLOCKS, 0.6F, 0.7F);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.02);
        return true;
    }

    private boolean canReplace(BlockPos p) {
        IBlockState s = world.getBlockState(p);
        Block b = s.getBlock();
        if (world.getTileEntity(p) != null) {
            return false;
        }
        return world.isAirBlock(p) || b == ModBlocks.CHAOS_STONE || b == ModBlocks.CHAOS_CRUST
                || b.isReplaceable(world, p);
    }

    /** Наросты вокруг Сердца (любая руда любой ступени). */
    public int countGrowths() {
        List<HeartTiers.Growth> pool = HeartTiers.pool(HeartTiers.maxTier());
        Set<IBlockState> states = new HashSet<>();
        for (HeartTiers.Growth g : pool) {
            states.add(g.state);
        }
        int n = 0;
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
        return tag;
    }

    private void writeShared(NBTTagCompound tag) {
        tag.setInteger("tier", tier);
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
    }

    private void readShared(NBTTagCompound tag) {
        tier = Math.max(1, tag.getInteger("tier"));
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
    }
}
