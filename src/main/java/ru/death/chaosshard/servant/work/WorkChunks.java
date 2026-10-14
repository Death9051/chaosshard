package ru.death.chaosshard.servant.work;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosShard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Станции добытчиков держат чанки загруженными: сундук с округой (3×3 чанка) и чанк, где сейчас каждый
 * закреплённый рабочий. Так добытчики работают, даже когда я далеко или в другом мире.
 * Пропали все рабочие станции (погибли, откреплены, шахта выработана) — чанки отпускаются.
 * После перезапуска сервера Forge возвращает билеты, и станции снова загружаются сами.
 */
public class WorkChunks implements ForgeChunkManager.LoadingCallback {

    private static final long TTL = 600;

    private static final class Station {
        final int dim;
        final BlockPos chest;
        Ticket ticket;
        final Map<UUID, Object[]> workers = new HashMap<>();

        Station(int dim, BlockPos chest) {
            this.dim = dim;
            this.chest = chest;
        }
    }

    private static final Map<String, Station> STATIONS = new HashMap<>();

    private static String key(int dim, BlockPos chest) {
        return dim + ":" + chest.toLong();
    }

    /** Рабочий на месте: держать его чанк (или чанк, куда он сейчас шагнёт). */
    public static void touch(Entity m, BlockPos at) {
        BlockPos chest = Work.chest(m);
        if (chest == null || m.world.isRemote) {
            return;
        }
        int dim = m.world.provider.getDimension();
        Station s = STATIONS.computeIfAbsent(key(dim, chest), k -> new Station(dim, chest));
        if (s.ticket == null) {
            s.ticket = ForgeChunkManager.requestTicket(ChaosShard.INSTANCE, m.world, ForgeChunkManager.Type.NORMAL);
            if (s.ticket == null) {
                return;   // билеты кончились (лимит Forge)
            }
            s.ticket.getModData().setLong("chest", chest.toLong());
        }
        ChunkPos cp = new ChunkPos(at);
        Object[] prev = s.workers.get(m.getUniqueID());
        s.workers.put(m.getUniqueID(), new Object[]{cp, m.world.getTotalWorldTime()});
        if (prev == null || !cp.equals(prev[0]) || s.ticket.getChunkList().isEmpty()) {
            sync(s, m.world);
        }
    }

    private static void sync(Station s, World w) {
        if (s.ticket == null) {
            return;
        }
        Set<ChunkPos> want = new HashSet<>();
        ChunkPos c = new ChunkPos(s.chest);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                want.add(new ChunkPos(c.x + dx, c.z + dz));
            }
        }
        for (Object[] o : s.workers.values()) {
            want.add((ChunkPos) o[0]);
        }
        int max = s.ticket.getMaxChunkListDepth();
        for (ChunkPos p : new HashSet<>(s.ticket.getChunkList())) {
            if (!want.contains(p)) {
                ForgeChunkManager.unforceChunk(s.ticket, p);
            }
        }
        for (ChunkPos p : want) {
            if (!s.ticket.getChunkList().contains(p) && s.ticket.getChunkList().size() < max) {
                ForgeChunkManager.forceChunk(s.ticket, p);
            }
        }
        NBTTagList l = new NBTTagList();
        for (ChunkPos p : s.ticket.getChunkList()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("x", p.x);
            t.setInteger("z", p.z);
            l.appendTag(t);
        }
        s.ticket.getModData().setTag("chunks", l);
    }

    /** Раз в 10 секунд: убрать пропавших рабочих, отпустить пустые станции. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STATIONS.isEmpty()) {
            return;
        }
        WorldServer over = DimensionManager.getWorld(0);
        if (over == null || over.getTotalWorldTime() % 200 != 0) {
            return;
        }
        long now = over.getTotalWorldTime();
        STATIONS.values().removeIf(s -> {
            WorldServer w = DimensionManager.getWorld(s.dim);
            int before = s.workers.size();
            s.workers.values().removeIf(o -> now - (Long) o[1] > TTL);
            boolean chestGone = w != null && w.isBlockLoaded(s.chest) && Work.handler(w, s.chest) == null;
            if (s.workers.isEmpty() || chestGone || w == null) {
                if (s.ticket != null) {
                    ForgeChunkManager.releaseTicket(s.ticket);
                }
                return true;
            }
            if (s.workers.size() != before) {
                sync(s, w);
            }
            return false;
        });
    }

    /** Билеты после перезапуска: снова загрузить сохранённые чанки, рабочие дальше отметятся сами. */
    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        long now = world.getTotalWorldTime();
        for (Ticket t : tickets) {
            NBTTagCompound d = t.getModData();
            if (!d.hasKey("chest")) {
                ForgeChunkManager.releaseTicket(t);
                continue;
            }
            BlockPos chest = BlockPos.fromLong(d.getLong("chest"));
            int dim = world.provider.getDimension();
            Station s = new Station(dim, chest);
            s.ticket = t;
            NBTTagList l = d.getTagList("chunks", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                ChunkPos p = new ChunkPos(l.getCompoundTagAt(i).getInteger("x"), l.getCompoundTagAt(i).getInteger("z"));
                ForgeChunkManager.forceChunk(t, p);
                // временная отметка: настоящие рабочие отметятся, как только чанк оживёт
                s.workers.put(UUID.randomUUID(), new Object[]{p, now});
            }
            STATIONS.put(key(dim, chest), s);
        }
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        STATIONS.clear();
    }
}
