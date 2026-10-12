package ru.death.chaosshard.order;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketThinned;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Святыни Ордена: на святой земле Нить истончается, а Скверна не ползёт.
 * Святыню можно осквернить — Эссенцией Хаоса по Святому камню.
 */
public class Shrines {

    /** Текущее истончение Нити у игроков (сервер). */
    private static final Map<UUID, Integer> PENALTY = new HashMap<>();

    public static int penalty(UUID player) {
        Integer p = PENALTY.get(player);
        return p == null ? 0 : p;
    }

    public static BlockPos nearest(World world, BlockPos pos, int radius) {
        BlockPos best = null;
        double bd = (double) radius * radius;
        for (BlockPos s : ShrineData.get(world).shrines) {
            double dx = s.getX() - pos.getX(), dz = s.getZ() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    /** Святая земля: Скверна здесь не ползёт. */
    public static boolean protects(World world, int x, int z) {
        return nearest(world, new BlockPos(x, 0, z), ChaosConfig.order.shrineRadius) != null;
    }

    public static void register(World world, BlockPos pos) {
        ShrineData d = ShrineData.get(world);
        if (d.shrines.add(pos.toImmutable())) {
            d.markDirty();
        }
    }

    public static void unregister(World world, BlockPos pos) {
        ShrineData d = ShrineData.get(world);
        if (d.shrines.remove(pos)) {
            d.markDirty();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 20 != 3) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        boolean holy = nearest(player.world, player.getPosition(), ChaosConfig.order.shrineRadius) != null;
        int now = holy ? ChaosConfig.order.shrinePenalty : 0;
        // Глушитель Порядка (имплант Хаоса) гасит часть света святыни
        if (now > 0 && ru.death.chaosshard.compat.body.Body.hasWarden(player)) {
            now = Math.max(0, now - 1);
        }
        if (holy) {
            Milestones.grant(player, Milestones.SHRINE_NEAR);
        }
        int before = penalty(player.getUniqueID());
        if (now == before) {
            return;
        }
        PENALTY.put(player.getUniqueID(), now);
        ChaosNetwork.CHANNEL.sendTo(new PacketThinned(now), player);
        if (now > 0) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.shrine.enter"), true);
            Milestones.grant(player, Milestones.SHRINE_NEAR);
        } else {
            player.sendStatusMessage(Breach.grimoire("chaosshard.shrine.leave"), true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENALTY.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        PENALTY.remove(event.player.getUniqueID());
    }

    /** Осквернить святыню. true — получилось. */
    public static boolean desecrate(World world, BlockPos stone, EntityPlayerMP player, ItemStack essence) {
        if (ThreadManager.getLevel(player) < ChaosConfig.order.desecrateThread && !player.isCreative()) {
            player.sendMessage(Breach.grimoire("chaosshard.shrine.thin", ChaosConfig.order.desecrateThread));
            return false;
        }
        if (!player.isCreative()) {
            essence.shrink(1);
        }
        unregister(world, stone);
        IBlockState dead = ModBlocks.DESECRATED_STONE.getDefaultState();
        world.setBlockState(stone, dead, 3);
        // Золото под камнем тускнеет
        if (world.getBlockState(stone.down()).getBlock() == Blocks.GOLD_BLOCK) {
            world.setBlockState(stone.down(), ModBlocks.CHAOS_STONE.getDefaultState(), 3);
        }
        WorldServer ws = (WorldServer) world;
        ws.addWeatherEffect(new EntityLightningBolt(world, stone.getX() + 0.5, stone.getY() + 1, stone.getZ() + 0.5, true));
        ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, stone.getX() + 0.5, stone.getY() + 1, stone.getZ() + 0.5, 60, 1.5, 1.0, 1.5, 0.05);
        world.playSound(null, stone, net.minecraft.init.SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.BLOCKS, 0.5F, 1.4F);
        net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(world,
                stone.getX() + 0.5, stone.getY() + 1.2, stone.getZ() + 0.5, new ItemStack(ModItems.MEMORY_FRAGMENT));
        world.spawnEntity(drop);
        ThreadManager.addPoints(player, ChaosConfig.order.desecrateReward);
        ShrineData d = ShrineData.get(world);
        d.desecrated++;
        d.markDirty();
        player.sendMessage(Breach.grimoire("chaosshard.shrine.desecrated", ChaosConfig.order.desecrateReward));
        Milestones.grant(player, Milestones.SHRINE_DESECRATED);
        return true;
    }
}
