package ru.death.chaosshard.structure;

import com.google.common.base.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.structure.bp.Blueprint;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Ставит {@link Blueprint} в мир: разбор строк блоков, поворот, опоры, сундуки с добычей. */
public final class BlueprintPlacer {

    private static final Map<String, IBlockState> CACHE = new HashMap<>();
    /** Строки блоков, которые не удалось разобрать (для автотеста). */
    public static final Set<String> FAILED = new LinkedHashSet<>();

    public static IBlockState parse(String id) {
        IBlockState cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        IBlockState st = doParse(id);
        CACHE.put(id, st);
        return st;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState doParse(String id) {
        String name = id;
        String props = null;
        int br = id.indexOf('[');
        if (br > 0) {
            name = id.substring(0, br);
            props = id.substring(br + 1, id.length() - 1);
        }
        Block block = Block.REGISTRY.getObject(new ResourceLocation(name));
        if (block == Blocks.AIR && !name.equals("minecraft:air")) {
            fail(id, "нет блока");
            return Blocks.STONE.getDefaultState();
        }
        IBlockState st = block.getDefaultState();
        if (props != null) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                IProperty prop = block.getBlockState().getProperty(p[0]);
                if (prop == null) {
                    fail(id, "нет свойства " + p[0]);
                    continue;
                }
                Optional v = prop.parseValue(p[1]);
                if (!v.isPresent()) {
                    fail(id, "нет значения " + p[1]);
                    continue;
                }
                st = st.withProperty(prop, (Comparable) v.get());
            }
        }
        return st;
    }

    private static void fail(String id, String why) {
        if (FAILED.add(id)) {
            ChaosShard.LOG.warn("Чертёж: не разобрал «{}» — {}", id, why);
        }
    }

    public static Rotation rotation(int rot) {
        return Rotation.values()[rot & 3];
    }

    /** Относительная точка → мировая с поворотом на rot×90° по часовой. */
    public static BlockPos at(BlockPos origin, int x, int y, int z, int rot) {
        int rx = x, rz = z;
        for (int i = 0; i < (rot & 3); i++) {
            int t = rx;
            rx = -rz;
            rz = t;
        }
        return origin.add(rx, y, rz);
    }

    /**
     * Поставить чертёж. origin — центр на уровне земли (первый воздушный слой).
     * Сначала расчищает объём (деревья, трава), потом блоки, опоры до грунта, сундуки.
     */
    public static void place(World world, Blueprint b, BlockPos origin, int rot, Random rand) {
        Rotation rotation = rotation(rot);
        int[] bb = b.bounds();
        // расчистка: всё, что выше земли в пятне постройки, — долой (листва, брёвна, трава)
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (long k : b.blocks.keySet()) {
            columns.add(Blueprint.key(Blueprint.kx(k), 0, Blueprint.kz(k)));
        }
        for (long k : columns) {
            int x = Blueprint.kx(k), z = Blueprint.kz(k);
            for (int y = 0; y <= bb[4] + 2; y++) {
                BlockPos p = at(origin, x, y, z, rot);
                IBlockState s = world.getBlockState(p);
                if (!s.getBlock().isAir(s, world, p) && !b.blocks.containsKey(Blueprint.key(x, y, z))) {
                    Material m = s.getMaterial();
                    if (m == Material.LEAVES || m == Material.WOOD || m == Material.PLANTS || m == Material.VINE
                            || m == Material.SNOW || m.isReplaceable() || y <= 2) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
        }
        // два прохода: сначала воздух (чтобы тропа не легла под грунт и не стала землёй),
        // потом блоки снизу вверх (цветы и факелы — уже на готовую опору)
        java.util.List<Map.Entry<Long, String>> solid = new java.util.ArrayList<>();
        for (Map.Entry<Long, String> e : b.blocks.entrySet()) {
            if (Blueprint.AIR.equals(e.getValue())) {
                long k = e.getKey();
                world.setBlockState(at(origin, Blueprint.kx(k), Blueprint.ky(k), Blueprint.kz(k), rot), Blocks.AIR.getDefaultState(), 2);
            } else {
                solid.add(e);
            }
        }
        solid.sort(java.util.Comparator.comparingInt(e -> Blueprint.ky(e.getKey())));
        for (Map.Entry<Long, String> e : solid) {
            long k = e.getKey();
            BlockPos p = at(origin, Blueprint.kx(k), Blueprint.ky(k), Blueprint.kz(k), rot);
            IBlockState st = parse(e.getValue()).withRotation(rotation);
            world.setBlockState(p, st, 2);
        }
        // опоры: от нижнего слоя вниз до твёрдого грунта (не глубже 24)
        for (Map.Entry<Long, String> e : b.footings.entrySet()) {
            long k = e.getKey();
            IBlockState st = parse(e.getValue());
            BlockPos p = at(origin, Blueprint.kx(k), Blueprint.ky(k) - 1, Blueprint.kz(k), rot);
            for (int i = 0; i < 24 && p.getY() > 1; i++, p = p.down()) {
                IBlockState s = world.getBlockState(p);
                if (s.isOpaqueCube() && s.getMaterial() != Material.LEAVES && s.getMaterial() != Material.WOOD) {
                    break;
                }
                world.setBlockState(p, st, 2);
            }
        }
        for (Blueprint.Mark c : b.chests) {
            BlockPos p = at(origin, c.x, c.y, c.z, rot);
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileEntityLockableLoot) {
                ((TileEntityLockableLoot) te).setLootTable(new ResourceLocation(ru.death.chaosshard.Tags.MOD_ID, "structures/" + c.kind), rand.nextLong());
            }
        }
    }

    private BlueprintPlacer() {
    }
}
