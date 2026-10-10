package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.tileentity.TileEntity;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.init.ModBlocks;

import java.util.List;
import java.util.Random;

/**
 * Сердце Острова. Раз в несколько секунд на нём нарастает руда:
 * нарост «ползёт» от сердца по уже выросшим наростам и встаёт в пустоту или в хаосит рядом.
 */
public class TileHeart extends TileEntity implements ITickable {

    public static final int RADIUS = 3;

    private int tier = 1;
    private int timer;

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.max(1, Math.min(tier, HeartTiers.maxTier()));
        markDirty();
        if (world != null) {
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    public int interval() {
        int[] iv = ChaosConfig.heart.growthInterval;
        return iv.length == 0 ? 600 : iv[Math.min(tier, iv.length) - 1];
    }

    public int maxGrowths() {
        int[] mg = ChaosConfig.heart.maxGrowths;
        return mg.length == 0 ? 8 : mg[Math.min(tier, mg.length) - 1];
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (++timer < interval()) {
            return;
        }
        timer = 0;
        grow(false);
    }

    /** Попробовать вырастить один нарост. force — игнорировать лимит. */
    public boolean grow(boolean force) {
        List<HeartTiers.Growth> pool = HeartTiers.pool(tier);
        if (pool.isEmpty()) {
            return false;
        }
        if (!force && countGrowths(pool) >= maxGrowths()) {
            return false;
        }
        Random rand = world.rand;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos p = pos;
            int steps = 1 + rand.nextInt(RADIUS);
            BlockPos target = null;
            for (int i = 0; i < steps; i++) {
                BlockPos next = p.offset(EnumFacing.random(rand));
                if (!within(next)) {
                    break;
                }
                if (isGrowth(world.getBlockState(next), pool) || next.equals(pos)) {
                    p = next;
                    continue;
                }
                target = next;
                break;
            }
            if (target == null || !canReplace(target)) {
                continue;
            }
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
        return false;
    }

    private boolean within(BlockPos p) {
        return Math.abs(p.getX() - pos.getX()) <= RADIUS && Math.abs(p.getY() - pos.getY()) <= RADIUS
                && Math.abs(p.getZ() - pos.getZ()) <= RADIUS;
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

    private static boolean isGrowth(IBlockState s, List<HeartTiers.Growth> pool) {
        for (HeartTiers.Growth g : pool) {
            if (g.state == s) {
                return true;
            }
        }
        return false;
    }

    public int countGrowths(List<HeartTiers.Growth> pool) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-RADIUS, -RADIUS, -RADIUS), pos.add(RADIUS, RADIUS, RADIUS))) {
            if (isGrowth(world.getBlockState(p), pool)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("tier", tier);
        tag.setInteger("timer", timer);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tier = Math.max(1, tag.getInteger("tier"));
        timer = tag.getInteger("timer");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
