package ru.death.chaosshard.antechamber;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;
import ru.death.chaosshard.init.ModBlocks;

/**
 * Переносит сущность в заданную точку другого мира без порталов.
 * В Преддверии под ногами при необходимости достраивается площадка.
 */
public class ChaosTeleporter implements ITeleporter {

    private final BlockPos target;

    public ChaosTeleporter(BlockPos target) {
        this.target = target;
    }

    @Override
    public void placeEntity(World world, Entity entity, float yaw) {
        BlockPos pos = this.target;
        if (AntechamberDimension.is(world)) {
            ensurePlatform(world, pos);
        } else if (pos.getY() <= 0) {
            // Цель «на поверхности»: находим верхний твёрдый блок
            pos = world.getTopSolidOrLiquidBlock(new BlockPos(pos.getX(), 0, pos.getZ()));
        }
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
        entity.fallDistance = 0;
        // При смене измерения Forge сам отправит игроку новую позицию после placeEntity
        entity.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, entity.rotationPitch);
    }

    /** Площадка 3×3 из хаосита и два блока воздуха над ней. */
    private static void ensurePlatform(World world, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = feet.add(dx, -1, dz);
                if (world.isAirBlock(floor)) {
                    world.setBlockState(floor, ModBlocks.CHAOS_STONE.getDefaultState());
                }
            }
        }
        clearIfSolid(world, feet);
        clearIfSolid(world, feet.up());
    }

    /** Освободить место для игрока, но не трогать сундуки и прочие блоки с содержимым. */
    private static void clearIfSolid(World world, BlockPos pos) {
        if (!world.isAirBlock(pos) && world.getTileEntity(pos) == null) {
            world.setBlockToAir(pos);
        }
    }

    /** Перенести игрока в Преддверие (или просто переместить, если он уже там). */
    public static void toAntechamber(EntityPlayerMP player) {
        BlockPos spawn = AntechamberDimension.spawnPoint();
        if (AntechamberDimension.is(player.world)) {
            ensurePlatform(player.world, spawn);
            player.connection.setPlayerLocation(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        } else {
            player.changeDimension(AntechamberDimension.id(), new ChaosTeleporter(spawn));
        }
    }

    /** Перенести игрока в точку (pos.y <= 0 — на поверхность). */
    public static void toPos(EntityPlayerMP player, int dimension, BlockPos pos) {
        if (player.dimension == dimension) {
            BlockPos p = pos.getY() > 0 ? pos : player.world.getTopSolidOrLiquidBlock(new BlockPos(pos.getX(), 0, pos.getZ()));
            player.connection.setPlayerLocation(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        } else {
            player.changeDimension(dimension, new ChaosTeleporter(pos));
        }
    }

    /** Перенести игрока в указанное измерение к точке появления этого мира. */
    public static void toWorldSpawn(EntityPlayerMP player, int dimension) {
        if (AntechamberDimension.is(dimension)) {
            toAntechamber(player);
            return;
        }
        World target = player.getServer().getWorld(dimension);
        BlockPos spawn = target.getSpawnPoint();
        BlockPos ground = new BlockPos(spawn.getX(), 0, spawn.getZ());
        if (player.dimension == dimension) {
            BlockPos top = target.getTopSolidOrLiquidBlock(ground);
            player.connection.setPlayerLocation(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        } else {
            player.changeDimension(dimension, new ChaosTeleporter(ground));
        }
    }
}
