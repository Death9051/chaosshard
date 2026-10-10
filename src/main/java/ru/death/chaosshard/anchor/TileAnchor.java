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

    public int radius() {
        return Math.min(ChaosConfig.anchor.maxRadius, ChaosConfig.anchor.startRadius + corrupted / ChaosConfig.anchor.blocksPerRadius);
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (++timer >= ChaosConfig.anchor.spreadInterval) {
            timer = 0;
            int done = 0;
            for (int i = 0; i < ChaosConfig.anchor.attemptsPerSpread; i++) {
                if (spreadOnce(world.rand)) {
                    done++;
                }
            }
            if (done > 0) {
                corrupted += done;
                markDirty();
                checkCapture();
            }
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
        if (captured && ChaosConfig.anchor.passiveThread > 0 && ++passiveTimer >= ChaosConfig.anchor.passiveInterval) {
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
        corrupted += done;
        markDirty();
        checkCapture();
        resendChunks();
        return done;
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
        if (captured || corrupted < ChaosConfig.anchor.captureThreshold) {
            return;
        }
        captured = true;
        markDirty();
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.6F, 0.7F);
        EntityPlayerMP p = ownerOnline();
        if (p != null) {
            ThreadManager.addPoints(p, ChaosConfig.anchor.captureReward);
            p.sendMessage(Breach.grimoire("chaosshard.anchor.captured", pos.getX(), pos.getZ(), ChaosConfig.anchor.captureReward));
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
    }
}
