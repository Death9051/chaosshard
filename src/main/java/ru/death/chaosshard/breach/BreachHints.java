package ru.death.chaosshard.breach;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.init.ModBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Подсветка постройки Прорыва частицами (видит только этот игрок):
 *  - зелёные искры — блок на месте;
 *  - контур нужного цвета — здесь должен стоять блок (фиолетовый — хаосит, розовый — корка);
 *  - красный контур и «запрет» — стоит не тот блок;
 *  - красный столб над алтарём — что-то закрывает небо.
 * Включается при ПКМ по алтарю без книги и при неудачной попытке начать ритуал.
 */
public class BreachHints {

    private static final int DURATION = 300;
    private static final Map<UUID, Hint> HINTS = new HashMap<>();

    private static final class Hint {
        final BlockPos altar;
        final int dim;
        int left = DURATION;

        Hint(BlockPos altar, int dim) {
            this.altar = altar;
            this.dim = dim;
        }
    }

    /** Ожидаемые блоки постройки. */
    public static final class Need {
        public final BlockPos pos;
        public final Block block;

        Need(BlockPos pos, Block block) {
            this.pos = pos;
            this.block = block;
        }
    }

    public static List<Need> needs(BlockPos altar) {
        List<Need> out = new ArrayList<>();
        for (BlockPos p : BreachRitual.pillars(altar)) {
            out.add(new Need(p, ModBlocks.RITUAL_SPIRE));
        }
        return out;
    }

    /** Первый блок, закрывающий небо над алтарём, или null. */
    public static BlockPos skyBlocker(World world, BlockPos altar) {
        for (BlockPos p = altar.up(); p.getY() < world.getHeight(); p = p.up()) {
            if (!world.isAirBlock(p) && world.getBlockState(p).getMaterial().blocksMovement()) {
                return p;
            }
        }
        return null;
    }

    public static void show(EntityPlayerMP player, BlockPos altar) {
        HINTS.put(player.getUniqueID(), new Hint(altar, player.dimension));
        int ok = 0;
        List<Need> needs = needs(altar);
        for (Need n : needs) {
            if (BlockRitualSpire.isWhole(player.world, n.pos)) {
                ok++;
            }
        }
        TextComponentTranslation msg = new TextComponentTranslation("chaosshard.breach.hint", ok, needs.size());
        msg.getStyle().setColor(ok == needs.size() ? TextFormatting.GREEN : TextFormatting.LIGHT_PURPLE);
        player.sendStatusMessage(msg, true);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || HINTS.isEmpty()
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Hint h = HINTS.get(player.getUniqueID());
        if (h == null) {
            return;
        }
        if (--h.left <= 0 || player.dimension != h.dim || player.getDistanceSq(h.altar) > 48 * 48) {
            HINTS.remove(player.getUniqueID());
            return;
        }
        if (h.left % 5 != 0) {
            return;
        }
        WorldServer world = (WorldServer) player.world;
        for (Need n : needs(h.altar)) {
            if (BlockRitualSpire.isWhole(world, n.pos)) {
                dust(world, player, n.pos.getX() + 0.5, n.pos.getY() + 2.05, n.pos.getZ() + 0.5, 0.3F, 1.0F, 0.3F);
            } else if (world.isAirBlock(n.pos) && world.isAirBlock(n.pos.up())) {
                // место под шпиль — контур в два блока
                outline(world, player, n.pos, 0.6F, 0.2F, 1.0F);
                outline(world, player, n.pos.up(), 0.6F, 0.2F, 1.0F);
            } else {
                outline(world, player, n.pos, 1.0F, 0.1F, 0.1F);
                world.spawnParticle(player, EnumParticleTypes.BARRIER, true,
                        n.pos.getX() + 0.5, n.pos.getY() + 0.5, n.pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
        BlockPos blocker = skyBlocker(world, h.altar);
        if (blocker != null) {
            for (int y = h.altar.getY() + 1; y <= blocker.getY(); y++) {
                dust(world, player, h.altar.getX() + 0.5, y + 0.5, h.altar.getZ() + 0.5, 1.0F, 0.1F, 0.1F);
            }
            outline(world, player, blocker, 1.0F, 0.1F, 0.1F);
        }
    }

    /** Контур блока цветной пылью. */
    private static void outline(WorldServer world, EntityPlayerMP player, BlockPos p, float r, float g, float b) {
        double x0 = p.getX(), y0 = p.getY(), z0 = p.getZ();
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0;
            for (int a = 0; a <= 1; a++) {
                for (int c = 0; c <= 1; c++) {
                    dust(world, player, x0 + t, y0 + a, z0 + c, r, g, b);
                    dust(world, player, x0 + a, y0 + t, z0 + c, r, g, b);
                    dust(world, player, x0 + a, y0 + c, z0 + t, r, g, b);
                }
            }
        }
    }

    /** Цветная пыль: при count = 0 смещения задают цвет. */
    private static void dust(WorldServer world, EntityPlayerMP player, double x, double y, double z, float r, float g, float b) {
        world.spawnParticle(player, EnumParticleTypes.REDSTONE, true, x, y, z, 0, r == 0 ? 0.001 : r, g, b, 1.0);
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        HINTS.clear();
    }
}
