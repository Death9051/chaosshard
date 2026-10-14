package ru.death.chaosshard.structure;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.structure.bp.Blueprint;
import ru.death.chaosshard.structure.bp.Structures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Постройки Ордена в Лучезарии. При генерации чанка постройка только планируется,
 * а ставится, когда вся её площадь загружена — так не будит соседние чанки.
 */
public class OrderStructures implements IWorldGenerator {

    /** Радиус, в котором постройка и её стража считаются «одним местом». */
    public static final int RADIUS = 18;

    // ------------------------------------------------------------------ планирование

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator gen, IChunkProvider provider) {
        if (!ChaosConfig.order.structures || world.provider.getDimension() != Breach.luchezariyaDim()
                || random.nextInt(ChaosConfig.order.structureRarity) != 0) {
            return;
        }
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        BlockPos spawn = world.getSpawnPoint();
        if (Math.abs(x - spawn.getX()) < 96 && Math.abs(z - spawn.getZ()) < 96) {
            return;
        }
        OrderStructureData data = OrderStructureData.get(world);
        int sp = ChaosConfig.order.structureSpacing;
        for (OrderStructureData.Entry e : data.entries) {
            long dx = e.x - x, dz = e.z - z;
            if (dx * dx + dz * dz < (long) sp * sp) {
                return;
            }
        }
        plan(data, pickName(random), x, z, random.nextInt(4), random.nextLong());
    }

    public static OrderStructureData.Entry plan(OrderStructureData data, String name, int x, int z, int rot, long seed) {
        OrderStructureData.Entry e = new OrderStructureData.Entry();
        e.id = data.nextId++;
        e.name = name;
        e.x = x;
        e.z = z;
        e.rot = rot;
        e.seed = seed;
        e.state = OrderStructureData.PLANNED;
        data.entries.add(e);
        data.markDirty();
        return e;
    }

    static String pickName(Random r) {
        int[] w = ChaosConfig.order.structureWeights;
        int total = 0;
        for (int i = 0; i < Structures.ALL.length; i++) {
            total += i < w.length ? Math.max(0, w[i]) : 1;
        }
        int roll = r.nextInt(Math.max(1, total));
        for (int i = 0; i < Structures.ALL.length; i++) {
            roll -= i < w.length ? Math.max(0, w[i]) : 1;
            if (roll < 0) {
                return Structures.ALL[i];
            }
        }
        return Structures.ALL[0];
    }

    /** Насколько неровной может быть земля под постройкой. */
    static int tolerance(String name) {
        switch (name) {
            case "crystal_spires": return 7;
            case "sanctum": return 5;
            case "dome_tower": return 4;
            default: return 3;
        }
    }

    // ------------------------------------------------------------------ установка

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || event.world.getTotalWorldTime() % 20 != 7) {
            return;
        }
        World world = event.world;
        if (world.provider.getDimension() != Breach.luchezariyaDim()) {
            return;
        }
        OrderStructureData data = OrderStructureData.get(world);
        for (OrderStructureData.Entry e : data.entries) {
            if (e.state != OrderStructureData.PLANNED) {
                continue;
            }
            Blueprint b = Structures.build(e.name, new Random(e.seed));
            int r = reach(b) + 2;
            if (!populated(world, e.x - r, e.z - r, e.x + r, e.z + r)) {
                continue;
            }
            tryPlace((WorldServer) world, data, e, b);
            return; // не больше одной постройки за раз
        }
    }

    /** Все чанки области загружены и уже заселены (деревья, руды) — иначе позже прорастут сквозь постройку. */
    static boolean populated(World world, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                net.minecraft.world.chunk.Chunk c = world.getChunkProvider().getLoadedChunk(cx, cz);
                if (c == null || !c.isTerrainPopulated()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int reach(Blueprint b) {
        int[] bb = b.bounds();
        return Math.max(Math.max(Math.abs(bb[0]), Math.abs(bb[3])), Math.max(Math.abs(bb[2]), Math.abs(bb[5])));
    }

    /** Постройки Акта 4: встают всегда, место уже выбрало Знамение. */
    public static boolean forced(String name) {
        return name.startsWith("pillar_") || name.equals("monastery") || name.equals("velmor_ward") || name.equals("capital");
    }

    /** Найти землю, проверить ровность и воду, поставить. */
    static boolean tryPlace(WorldServer world, OrderStructureData data, OrderStructureData.Entry e, Blueprint b) {
        int r = Math.min(reach(b), 12);
        List<Integer> hs = new ArrayList<>();
        int water = 0, samples = 0;
        for (int dx = -r; dx <= r; dx += Math.max(1, r / 3)) {
            for (int dz = -r; dz <= r; dz += Math.max(1, r / 3)) {
                BlockPos top = ground(world, e.x + dx, e.z + dz);
                samples++;
                if (world.getBlockState(top.down()).getMaterial().isLiquid()) {
                    water++;
                }
                hs.add(top.getY());
            }
        }
        Biome biome = world.getBiome(new BlockPos(e.x, 64, e.z));
        boolean wet = BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN);
        int[] a = hs.stream().mapToInt(Integer::intValue).sorted().toArray();
        int median = a[a.length / 2];
        int lo = a[a.length / 6], hi = a[a.length - 1 - a.length / 6];
        boolean spires = e.name.equals("crystal_spires");
        // Опора Завесы и монастыри Ирены встают всегда: место выбрало Знамение
        boolean pillar = forced(e.name);
        if (!pillar && (wet || hi - lo > tolerance(e.name) || water * 100 / Math.max(1, samples) > (spires ? 70 : 15))) {
            e.state = OrderStructureData.FAILED;
            data.markDirty();
            ChaosShard.LOG.info("Постройка {} у {} {} не встала: неровно {} / вода {}%", e.name, e.x, e.z, hi - lo, water * 100 / Math.max(1, samples));
            return false;
        }
        // шпили стоят в воде: основание — на поверхности воды
        e.y = pillar ? Math.max(median, world.getSeaLevel() + 1) : median;
        place(world, data, e, b);
        return true;
    }

    /** Первый воздушный слой над грунтом (без листвы, брёвен и травы); над водой — над водой. */
    public static BlockPos ground(World world, int x, int z) {
        world.getChunk(x >> 4, z >> 4); // в незагруженном чанке высота читается как 0
        BlockPos p = world.getHeight(new BlockPos(x, 0, z));
        while (p.getY() > 1) {
            IBlockState s = world.getBlockState(p.down());
            Material m = s.getMaterial();
            if (m == Material.AIR || m == Material.LEAVES || m == Material.WOOD || m == Material.PLANTS || m == Material.VINE
                    || m == Material.SNOW || m.isReplaceable() && !m.isLiquid()) {
                p = p.down();
                continue;
            }
            break;
        }
        return p;
    }

    /** Поставить постройку e (y уже известен), стражу и светочи. */
    public static void place(WorldServer world, OrderStructureData data, OrderStructureData.Entry e, Blueprint b) {
        Random rand = new Random(e.seed ^ 0x5DEECE66DL);
        BlockPos o = e.origin();
        BlueprintPlacer.place(world, b, o, e.rot, rand);
        e.beams.clear();
        for (Blueprint.Mark m : b.marks) {
            BlockPos p = BlueprintPlacer.at(o, m.x, m.y, m.z, e.rot);
            if ("beam".equals(m.kind)) {
                world.setBlockState(p, ModBlocks.ORDER_BEACON.getDefaultState(), 3);
                e.beams.add(p);
            } else if ("keeper".equals(m.kind)) {
                spawn(world, p, EntityOrderKnight.KEEPER, o, e.id);
            }
        }
        for (Blueprint.Mark m : b.marks) {
            if ("hero".equals(m.kind)) {
                ru.death.chaosshard.hero.Pillars.placed(world, e, BlueprintPlacer.at(o, m.x, m.y, m.z, e.rot));
            } else if ("shrine".equals(m.kind)) {
                ru.death.chaosshard.hero.Pillars.shrinePlaced(world, e, BlueprintPlacer.at(o, m.x, m.y, m.z, e.rot));
            } else if ("seal".equals(m.kind)) {
                ru.death.chaosshard.hero.Pillars.sealPlaced(world, e, BlueprintPlacer.at(o, m.x, m.y, m.z, e.rot));
            }
        }
        if ("capital".equals(e.name)) {
            ru.death.chaosshard.act5.Crown.placed(world, e, b);
        }
        for (Blueprint.Mark m : b.guards) {
            BlockPos p = BlueprintPlacer.at(o, m.x, m.y, m.z, e.rot);
            spawn(world, p, "order_captain".equals(m.kind) ? EntityOrderKnight.CAPTAIN : EntityOrderKnight.GUARD, o, e.id);
        }
        e.state = OrderStructureData.PLACED;
        data.markDirty();
        ChaosShard.LOG.info("Постройка Ордена {} встала: {} {} {}", e.name, e.x, e.y, e.z);
    }

    static EntityOrderKnight spawn(WorldServer world, BlockPos p, int variant, BlockPos home, long id) {
        EntityOrderKnight k = new EntityOrderKnight(world);
        k.setup(variant);
        k.structureId = id;
        k.setLocationAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, world.rand.nextFloat() * 360, 0);
        k.setHomePosAndDistance(p, variant == EntityOrderKnight.KEEPER ? 10 : 8);
        world.spawnEntity(k);
        return k;
    }

    // ------------------------------------------------------------------ события

    /** Хранитель пал: лучи гаснут, тайник открыт. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityOrderKnight) || event.getEntityLiving().world.isRemote) {
            return;
        }
        EntityOrderKnight k = (EntityOrderKnight) event.getEntityLiving();
        if (k.variant() != EntityOrderKnight.KEEPER) {
            return;
        }
        World world = k.world;
        OrderStructureData data = OrderStructureData.get(world);
        OrderStructureData.Entry e = k.structureId >= 0 ? data.byId(k.structureId) : data.near(k.getPosition(), 40);
        if (e != null && !e.keeperDead) {
            e.keeperDead = true;
            data.markDirty();
            for (BlockPos p : e.beams) {
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileOrderBeacon) {
                    ((TileOrderBeacon) te).setLit(false);
                }
            }
            world.playSound(null, k.getPosition(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 3.0F, 0.5F);
            for (EntityPlayer pl : world.getEntitiesWithinAABB(EntityPlayer.class, k.getEntityBoundingBox().grow(48))) {
                pl.sendMessage(new TextComponentTranslation("chaosshard.order.keeper_dead").setStyle(
                        new net.minecraft.util.text.Style().setColor(TextFormatting.GOLD)));
            }
        }
        if (event.getSource().getTrueSource() instanceof EntityPlayerMP) {
            ru.death.chaosshard.thread.ThreadManager.addPoints((EntityPlayerMP) event.getSource().getTrueSource(), ChaosConfig.order.keeperReward);
        }
    }

    /** Тайник святилища не открыть, пока жив Хранитель. */
    @SubscribeEvent
    public void onOpen(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || world.getBlockState(event.getPos()).getBlock() != Blocks.CHEST
                || world.provider.getDimension() != Breach.luchezariyaDim()) {
            return;
        }
        OrderStructureData.Entry e = OrderStructureData.get(world).near(event.getPos(), RADIUS);
        if (e != null && "sanctum".equals(e.name) && e.state == OrderStructureData.PLACED && !e.keeperDead
                && !event.getEntityPlayer().isCreative()) {
            final long id = e.id;
            if (world.getEntitiesWithinAABB(EntityOrderKnight.class, new net.minecraft.util.math.AxisAlignedBB(event.getPos()).grow(64, 32, 64),
                    k -> k.isEntityAlive() && k.variant() == EntityOrderKnight.KEEPER && (k.structureId == id || k.structureId < 0)).isEmpty()) {
                // Хранителя нет (пропал или не появился) — тайник не должен остаться запертым навсегда
                e.keeperDead = true;
                OrderStructureData.get(world).markDirty();
                for (BlockPos p : e.beams) {
                    TileEntity te = world.getTileEntity(p);
                    if (te instanceof TileOrderBeacon) {
                        ((TileOrderBeacon) te).setLit(false);
                    }
                }
                ChaosShard.LOG.warn("Хранителя святилища {} нет — тайник открыт", e.id);
                return;
            }
            event.setCanceled(true);
            event.getEntityPlayer().sendStatusMessage(new TextComponentTranslation("chaosshard.order.keeper_alive"), true);
        }
    }

    // ------------------------------------------------------------------ для команды и автотеста

    /** Поставить постройку прямо сейчас у точки (для /oh structure и автотеста). */
    public static OrderStructureData.Entry placeNow(WorldServer world, String name, BlockPos at, int rot, long seed) {
        if (!Arrays.asList(Structures.ALL).contains(name) && !forced(name)) {
            return null;
        }
        OrderStructureData data = OrderStructureData.get(world);
        OrderStructureData.Entry e = plan(data, name, at.getX(), at.getZ(), rot, seed);
        e.y = at.getY();
        place(world, data, e, Structures.build(name, new Random(seed)));
        return e;
    }
}
