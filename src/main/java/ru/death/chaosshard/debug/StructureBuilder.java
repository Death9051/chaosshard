package ru.death.chaosshard.debug;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.breach.BreachRitual;
import ru.death.chaosshard.heart.HeartTiers;
import ru.death.chaosshard.heart.ItemHeartModule;
import ru.death.chaosshard.heart.TileHeart;
import ru.death.chaosshard.init.ModBlocks;

/**
 * Строит готовые структуры мода для тестов.
 * Точка установки — уровень предметов; пол кладётся на блок ниже.
 */
public final class StructureBuilder {

    public enum Kind {
        /** Алтарь Прорыва со столбами — готов к ритуалу. */
        BREACH,
        /** Сердце острова: макс. уровень, все модули, с наростами. */
        HEART,
        /** Сердце острова со Жнецом — автоматизация. */
        HEART_AUTO,
        /** Якорь Хаоса с уже захваченной округой. */
        ANCHOR,
        /** Малый остров: Сердце, Жнец и Алтарь Прорыва на парящем диске. */
        ISLAND
    }

    public static String build(World world, BlockPos pos, Kind kind, EntityPlayerMP player) {
        switch (kind) {
            case BREACH:
                clear(world, pos, 3, 8);
                floor(world, pos.down(), 3, ModBlocks.CHAOS_CRUST.getDefaultState());
                breach(world, pos);
                return null;
            case HEART:
            case HEART_AUTO:
                clear(world, pos, 4, 4);
                floor(world, pos.down(), 4, ModBlocks.CHAOS_CRUST.getDefaultState());
                heart(world, pos, kind == Kind.HEART_AUTO);
                return null;
            case ANCHOR:
                if (AntechamberDimension.is(world)) {
                    return "chaosshard.anchor.not_here";
                }
                return anchor(world, pos, player);
            case ISLAND:
                island(world, pos);
                return null;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ части

    private static void breach(World world, BlockPos altar) {
        world.setBlockState(altar, ModBlocks.BREACH_ALTAR.getDefaultState());
        for (BlockPos p : BreachRitual.pillars(altar)) {
            world.setBlockState(p, ModBlocks.CHAOS_STONE.getDefaultState());
            world.setBlockState(p.up(), ModBlocks.CHAOS_CRUST.getDefaultState());
        }
        // Над алтарём должно быть небо
        for (int y = altar.getY() + 1; y < world.getHeight(); y++) {
            BlockPos p = new BlockPos(altar.getX(), y, altar.getZ());
            if (!world.isAirBlock(p)) {
                world.setBlockToAir(p);
            }
        }
    }

    private static void heart(World world, BlockPos pos, boolean harvester) {
        world.setBlockState(pos.down(), ModBlocks.CHAOS_STONE.getDefaultState());
        world.setBlockState(pos, ModBlocks.ISLAND_HEART.getDefaultState());
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileHeart)) {
            ChaosShard.LOG.warn("Тестовая структура: у Сердца нет TileEntity в {}", pos);
            return;
        }
        TileHeart heart = (TileHeart) te;
        heart.setTier(HeartTiers.maxTier());
        ItemHeartModule.Type[] types = ItemHeartModule.Type.values();
        for (int i = 0; i < Math.min(types.length, TileHeart.MODULE_SLOTS); i++) {
            Item module = Item.getByNameOrId("chaosshard:heart_module_" + types[i].name().toLowerCase());
            if (module != null) {
                heart.modules.setStackInSlot(i, new ItemStack(module, 4));
            }
        }
        if (harvester) {
            world.setBlockState(pos.up(), ModBlocks.HEART_HARVESTER.getDefaultState());
        } else {
            for (int i = 0; i < 12; i++) {
                heart.grow(true);
            }
        }
        heart.markDirty();
    }

    private static String anchor(World world, BlockPos pos, EntityPlayerMP player) {
        world.setBlockState(pos, ModBlocks.CHAOS_ANCHOR.getDefaultState());
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileAnchor)) {
            return null;
        }
        TileAnchor anchor = (TileAnchor) te;
        if (player != null) {
            anchor.setOwner(player);
        }
        AnchorData data = AnchorData.get(world);
        if (!data.anchors.contains(pos)) {
            data.anchors.add(pos);
            data.markDirty();
        }
        anchor.forceSpread(1500);
        return null;
    }

    private static void island(World world, BlockPos c) {
        int r = 10;
        IBlockState stone = ModBlocks.CHAOS_STONE.getDefaultState();
        IBlockState crust = ModBlocks.CHAOS_CRUST.getDefaultState();
        clear(world, c, r, 8);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.3) {
                    continue;
                }
                // Конус вниз: чем ближе к центру, тем глубже
                int depth = 1 + (int) Math.round((r - d) * 0.8);
                for (int dy = 1; dy <= depth; dy++) {
                    world.setBlockState(c.add(dx, -dy, dz), dy == 1 ? crust : stone, 2);
                }
            }
        }
        heart(world, c.add(0, 0, 3), true);
        breach(world, c.add(0, 0, -5));
    }

    // ------------------------------------------------------------------ утилиты

    /** Очистить квадрат радиуса r на высоту h от pos. */
    private static void clear(World world, BlockPos pos, int r, int h) {
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-r, 0, -r), pos.add(r, h, r))) {
            if (!world.isAirBlock(p)) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
            }
        }
    }

    private static void floor(World world, BlockPos center, int r, IBlockState state) {
        for (BlockPos p : BlockPos.getAllInBox(center.add(-r, 0, -r), center.add(r, 0, r))) {
            world.setBlockState(p, state, 2);
        }
    }

    private StructureBuilder() {
    }
}
