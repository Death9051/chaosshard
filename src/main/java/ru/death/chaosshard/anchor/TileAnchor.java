package ru.death.chaosshard.anchor;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Якорь Хаоса. Пускает Скверну кругом, который растёт по мере осквернения.
 * Набрав порог, край считается захваченным: владелец получает Нить разом и потом понемногу.
 */
public class TileAnchor extends TileEntity implements ITickable {

    private UUID owner;
    private String ownerName = "";
    private int corrupted;
    private boolean captured;
    private int timer;
    private int passiveTimer;
    /** Захват уже засчитан владельцу (для квестов). */
    private boolean counted;
    /** Тики до следующего набега Мрачной Армии (−1 — ещё не назначен). */
    private int raidTimer = -1;

    /** Раз в секунду: отсчёт до набега. Набег приходит, только если владелец рядом. */
    private void tickArmy() {
        if (!ru.death.chaosshard.ChaosConfig.order.raids) {
            return;
        }
        if (raidTimer < 0) {
            raidTimer = ru.death.chaosshard.ChaosConfig.order.firstRaidDelay;
            markDirty();
        }
        raidTimer = Math.max(0, raidTimer - 20);
        if (raidTimer > 0) {
            return;
        }
        EntityPlayerMP o = ownerOnline();
        if (o == null || o.world != world || o.getDistanceSq(pos) > 96 * 96) {
            return;
        }
        ru.death.chaosshard.garrot.GarrotRaids.spawn((WorldServer) world, pos, o);
        raidTimer = ru.death.chaosshard.ChaosConfig.order.raidInterval / 2
                + world.rand.nextInt(Math.max(1, ru.death.chaosshard.ChaosConfig.order.raidInterval));
        markDirty();
    }

    /** Для отладки: набег прямо сейчас. */
    public void forceRaid(EntityPlayerMP player) {
        ru.death.chaosshard.garrot.GarrotRaids.spawn((WorldServer) world, pos, player);
    }
    private final Set<Long> dirtyChunks = new HashSet<>();

    public void setOwner(EntityPlayerMP player) {
        owner = player.getUniqueID();
        ownerName = player.getName();
        markDirty();
    }

    public int getCorrupted() {
        return corrupted;
    }

    public boolean isCaptured() {
        return captured;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Множитель восстановления энергии Хаоса для игрока в этой точке: на земле любого Якоря — energyRegenMultiplier,
     * и ещё +1 за каждую печать «Родник» на этом Якоре. Вне Якорей — 1.
     */
    public static double energyMultiplier(net.minecraft.world.World world, BlockPos at) {
        double best = 1.0;
        for (BlockPos p : AnchorData.get(world).anchors) {
            if (!world.isBlockLoaded(p)) {
                continue;
            }
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileAnchor)) {
                continue;
            }
            TileAnchor a = (TileAnchor) te;
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            int r = a.radius();
            if (dx * dx + dz * dz <= (double) r * r) {
                best = Math.max(best, ChaosConfig.anchor.energyRegenMultiplier + a.seals(ItemAnchorSeal.Type.WELL));
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ уровни

    /** Уровень (1..5) по числу осквернённых блоков. */
    public int level() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        int lvl = 1;
        for (int i = 1; i < t.length; i++) {
            if (corrupted >= t[i]) {
                lvl = i + 1;
            }
        }
        return lvl;
    }

    public static int maxLevel() {
        return ChaosConfig.anchor.levelThresholds.length;
    }

    /** Сколько осквернить до следующего уровня (−1 — уровень последний). */
    public int nextThreshold() {
        int[] t = ChaosConfig.anchor.levelThresholds;
        int l = level();
        return l < t.length ? t[l] : -1;
    }

    public int radiusCap() {
        int[] r = ChaosConfig.anchor.levelRadius;
        int base = r.length == 0 ? 32 : r[Math.min(level(), r.length) - 1];
        return base + 6 * seals(ItemAnchorSeal.Type.REACH);
    }

    public int radius() {
        return Math.min(radiusCap(), ChaosConfig.anchor.startRadius + corrupted / ChaosConfig.anchor.blocksPerRadius
                + 6 * seals(ItemAnchorSeal.Type.REACH));
    }

    // ------------------------------------------------------------------ печати

    private final java.util.List<ItemAnchorSeal.Type> seals = new java.util.ArrayList<>();

    public java.util.List<ItemAnchorSeal.Type> getSeals() {
        return seals;
    }

    public int seals(ItemAnchorSeal.Type type) {
        int n = 0;
        for (ItemAnchorSeal.Type t : seals) {
            if (t == type) {
                n++;
            }
        }
        return n;
    }

    /** Навесить печать; false — нет места (печатей не больше уровня). */
    public boolean addSeal(ItemAnchorSeal.Type type) {
        if (seals.size() >= level()) {
            return false;
        }
        seals.add(type);
        changed();
        return true;
    }

    public ItemAnchorSeal.Type removeSeal() {
        if (seals.isEmpty()) {
            return null;
        }
        ItemAnchorSeal.Type t = seals.remove(seals.size() - 1);
        changed();
        return t;
    }

    private int shownLevel = 0;

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    /** Колонна: основание + (уровень − 1) сегментов, верхний — остриё. Препятствия (кроме мягких блоков) останавливают рост. */
    public void growColumn() {
        int lvl = level();
        net.minecraft.block.state.IBlockState base = world.getBlockState(pos);
        if (base.getBlock() instanceof BlockAnchor) {
            boolean grown = lvl >= 2;
            if (base.getValue(BlockAnchor.GROWN) != grown) {
                world.setBlockState(pos, base.withProperty(BlockAnchor.GROWN, grown), 2);
            }
        }
        int height = lvl - 1;
        for (int i = 1; i <= height; i++) {
            BlockPos p = pos.up(i);
            boolean top = i == height;
            net.minecraft.block.state.IBlockState want = ru.death.chaosshard.init.ModBlocks.ANCHOR_PART.getDefaultState()
                    .withProperty(BlockAnchorPart.TOP, top);
            net.minecraft.block.state.IBlockState have = world.getBlockState(p);
            if (have == want) {
                continue;
            }
            boolean ours = have.getBlock() instanceof BlockAnchorPart;
            boolean soft = world.isAirBlock(p) || have.getBlock().isReplaceable(world, p) || have.getMaterial() == net.minecraft.block.material.Material.LEAVES
                    || have.getMaterial() == net.minecraft.block.material.Material.PLANTS || have.getMaterial() == net.minecraft.block.material.Material.VINE;
            if (!ours && !soft) {
                break;
            }
            world.setBlockState(p, want, 3);
            if (!ours) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        20, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    private void onLevelUp(int lvl) {
        int[] rw = ChaosConfig.anchor.levelReward;
        int reward = lvl - 1 < rw.length ? rw[lvl - 1] : 0;
        EntityPlayerMP p = ownerOnline();
        if (lvl == 2) {
            checkCapture();
        } else if (p != null) {
            if (reward > 0) {
                ThreadManager.addPoints(p, reward);
            }
            p.sendMessage(Breach.grimoire("chaosshard.anchor.level_up", pos.getX(), pos.getZ(), lvl, radiusCap(), reward));
            if (lvl >= maxLevel()) {
                ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.ANCHOR_MAX);
            }
        }
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.5F, 0.6F + 0.1F * lvl);
        growColumn();
        BlockPos tip = pos.up(Math.max(0, lvl - 1));
        ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendToAllAround(
                new ru.death.chaosshard.network.PacketHeartBolt(tip.getX() + 0.5, tip.getY() + 0.9, tip.getZ() + 0.5,
                        tip.getX() + 0.5, tip.getY() + 8, tip.getZ() + 0.5, 0xB070FF),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 96));
        changed();
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (++timer >= ChaosConfig.anchor.spreadInterval) {
            timer = 0;
            int done = 0;
            int attempts = (int) Math.round((ChaosConfig.anchor.attemptsPerSpread + (level() - 1) * ChaosConfig.anchor.attemptsPerLevel)
                    * (1 + 0.5 * seals(ItemAnchorSeal.Type.FURY)));
            for (int i = 0; i < attempts; i++) {
                if (spreadOnce(world.rand)) {
                    done++;
                }
            }
            if (done > 0) {
                addCorrupted(done);
            }
        }
        if (timer % 200 == 100) {
            growColumn();
        }
        if (timer % 20 == 5 && captured) {
            tickArmy();
        }
        if (timer == 11 && world.getTotalWorldTime() % 200 < 40) {
            EntityPlayerMP o = ownerOnline();
            ru.death.chaosshard.village.VillageFate.checkAnchor(world, pos, radius(), o);
        }
        if (captured && !counted && timer % 20 == 0) {
            EntityPlayerMP owner = ownerOnline();
            if (owner != null) {
                ru.death.chaosshard.quest.Milestones.onCapture(owner);
                counted = true;
                markDirty();
            }
        }
        if (timer % 200 == 0 && !dirtyChunks.isEmpty()) {
            resendChunks();
        }
        if (captured && ChaosConfig.anchor.passiveThread > 0
                && (passiveTimer += 1 + seals(ItemAnchorSeal.Type.HARVEST)) >= ChaosConfig.anchor.passiveInterval) {
            passiveTimer = 0;
            EntityPlayerMP p = ownerOnline();
            if (p != null) {
                ThreadManager.addPoints(p, ChaosConfig.anchor.passiveThread);
            }
        }
    }

    /** Для отладки: n попыток подряд. */
    public int forceSpread(int n) {
        int done = 0;
        for (int i = 0; i < n; i++) {
            if (spreadOnce(world.rand)) {
                done++;
            }
        }
        addCorrupted(done);
        resendChunks();
        return done;
    }

    private void addCorrupted(int n) {
        int before = level();
        corrupted += n;
        markDirty();
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l);
        }
        if (after != shownLevel) {
            shownLevel = after;
            changed();
        }
    }

    /** Для отладки: сразу выставить число осквернённых блоков (уровни засчитываются). */
    public void setCorrupted(int n) {
        int before = level();
        corrupted = Math.max(0, n);
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l);
        }
        growColumn();
        changed();
    }

    /** Одна попытка: поверхность случайного столбца в круге либо случайный блок в объёме (деревья, камень). */
    public boolean spreadOnce(Random rand) {
        int r = radius();
        double ang = rand.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(rand.nextDouble()) * r;
        int x = pos.getX() + (int) Math.round(Math.cos(ang) * dist);
        int z = pos.getZ() + (int) Math.round(Math.sin(ang) * dist);
        if (!world.isBlockLoaded(new BlockPos(x, 0, z))) {
            return false;
        }
        // Святая земля и деревни под покровительством Скверне не по зубам
        if (ru.death.chaosshard.order.Shrines.protects(world, x, z)) {
            return false;
        }
        BlockPos sigil = ru.death.chaosshard.village.VillageFate.protector(world, x, z);
        if (sigil != null) {
            ru.death.chaosshard.village.VillageFate.repelled(world, sigil);
            return false;
        }
        boolean changed = false;
        if (rand.nextBoolean()) {
            BlockPos top = world.getHeight(new BlockPos(x, 0, z)).down();
            for (int d = 0; d < 3; d++) {
                changed |= Corruption.corrupt(world, top.down(d));
            }
            // растения сверху
            changed |= Corruption.corrupt(world, top.up());
            if (changed && Corruption.corruptBiome(world, top)) {
                dirtyChunks.add(ChunkPos.asLong(x >> 4, z >> 4));
            }
            if (changed) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, x + 0.5, top.getY() + 1.2, z + 0.5,
                        4, 0.3, 0.2, 0.3, 0.01);
            }
        } else {
            int y = pos.getY() - 6 + rand.nextInt(18);
            changed = Corruption.corrupt(world, new BlockPos(x, y, z));
        }
        return changed;
    }

    private void checkCapture() {
        if (captured || level() < 2) {
            return;
        }
        captured = true;
        markDirty();
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.6F, 0.7F);
        EntityPlayerMP p = ownerOnline();
        if (p != null) {
            int reward = ChaosConfig.anchor.levelReward.length > 1 ? ChaosConfig.anchor.levelReward[1] : 0;
            ThreadManager.addPoints(p, reward);
            p.sendMessage(Breach.grimoire("chaosshard.anchor.captured", pos.getX(), pos.getZ(), reward));
        }
    }

    private EntityPlayerMP ownerOnline() {
        if (owner == null || world.getMinecraftServer() == null) {
            return null;
        }
        return world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
    }

    /** Отправить игрокам чанки со сменённым биомом, чтобы цвет травы и неба обновился. */
    private void resendChunks() {
        WorldServer ws = (WorldServer) world;
        for (long key : dirtyChunks) {
            int cx = (int) (key & 0xFFFFFFFFL), cz = (int) (key >>> 32);
            PlayerChunkMapEntry entry = ws.getPlayerChunkMap().getEntry(cx, cz);
            Chunk chunk = world.getChunk(cx, cz);
            if (entry != null && entry.isSentToPlayers()) {
                entry.sendPacket(new SPacketChunkData(chunk, 65535));
            }
        }
        dirtyChunks.clear();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (owner != null) {
            tag.setUniqueId("owner", owner);
        }
        tag.setString("ownerName", ownerName);
        tag.setInteger("corrupted", corrupted);
        tag.setBoolean("captured", captured);
        tag.setInteger("passive", passiveTimer);
        tag.setBoolean("counted", counted);
        tag.setInteger("raidTimer", raidTimer);
        writeSeals(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
        ownerName = tag.getString("ownerName");
        corrupted = tag.getInteger("corrupted");
        captured = tag.getBoolean("captured");
        passiveTimer = tag.getInteger("passive");
        counted = tag.getBoolean("counted");
        raidTimer = tag.hasKey("raidTimer") ? tag.getInteger("raidTimer") : -1;
        readSeals(tag);
    }

    private void writeSeals(NBTTagCompound tag) {
        int[] arr = new int[seals.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = seals.get(i).ordinal();
        }
        tag.setIntArray("seals", arr);
    }

    private void readSeals(NBTTagCompound tag) {
        seals.clear();
        for (int o : tag.getIntArray("seals")) {
            if (o >= 0 && o < ItemAnchorSeal.Type.values().length) {
                seals.add(ItemAnchorSeal.Type.values()[o]);
            }
        }
    }

    // ------------------------------------------------------------------ клиент: уровень и печати для отрисовки

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("corrupted", corrupted);
        writeSeals(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        corrupted = tag.getInteger("corrupted");
        readSeals(tag);
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos.add(-2, 0, -2), pos.add(3, 7, 3));
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
