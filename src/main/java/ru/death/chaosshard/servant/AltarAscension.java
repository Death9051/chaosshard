package ru.death.chaosshard.servant;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.breach.BlockRitualSpire;
import ru.death.chaosshard.breach.BreachRitual;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketHeartBolt;
import ru.death.chaosshard.servant.BeastData.Beast;
import ru.death.chaosshard.servant.BeastData.Entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вознесение — высокий ритуал у Алтаря Прорыва. Алтарь с четырьмя целыми шпилями, зверь рядом (до 8 блоков),
 * подношения в инвентаре. ПКМ Гримуаром по Алтарю: зверь поднимается над Алтарём, шпили бьют в него дугами,
 * через 8 секунд он вознесён (5-й уровень).
 */
public class AltarAscension {

    public static final int DURATION = 160;
    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    private static final class Running {
        final BlockPos altar;
        final int dim;
        final EntityChaosBeast beast;
        final List<ItemStack> paid;
        int ticks;

        Running(BlockPos altar, int dim, EntityChaosBeast beast, List<ItemStack> paid) {
            this.altar = altar;
            this.dim = dim;
            this.beast = beast;
            this.paid = paid;
        }
    }

    /** Подношения Вознесения. */
    public static List<ItemStack> offerings(Beast b) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(ModItems.CHAOS_ESSENCE, 8));
        switch (b) {
            case HARE:
                out.add(new ItemStack(Items.GOLDEN_APPLE, 2));
                out.add(new ItemStack(Items.NETHER_STAR, 1));
                break;
            case CAT:
                out.add(new ItemStack(ModItems.VEIL_SHARD, 4));
                out.add(new ItemStack(Items.FISH, 4, 1));
                break;
            default:
                out.add(new ItemStack(ModItems.DRAGON_SCALE, 2));
                out.add(new ItemStack(Items.BLAZE_ROD, 4));
                break;
        }
        return out;
    }

    public static int minThread(Beast b) {
        return b == Beast.TIGER ? 4 : 3;
    }

    /** ПКМ Гримуаром по Алтарю. */
    public static void tryStart(EntityPlayerMP p, BlockPos altar) {
        if (RUNNING.containsKey(p.getUniqueID()) || BreachRitual.isRunning(p)) {
            return;
        }
        for (BlockPos s : BreachRitual.pillars(altar)) {
            if (!BlockRitualSpire.isWhole(p.world, s)) {
                p.sendMessage(Breach.grimoire("chaosshard.altar.no_spires"));
                return;
            }
        }
        List<EntityChaosBeast> near = p.world.getEntitiesWithinAABB(EntityChaosBeast.class, new AxisAlignedBB(altar).grow(8),
                x -> x.isEntityAlive() && Servants.isServantOf(x, p.getUniqueID()));
        if (near.isEmpty()) {
            p.sendMessage(Breach.grimoire("chaosshard.beast.ascend.near"));
            return;
        }
        String why = null;
        for (EntityChaosBeast beast : near) {
            Beast b = beast.beast();
            Entry e = Beasts.entry(p.world, p.getUniqueID(), b);
            if (e.ascended) {
                why = why == null ? "chaosshard.beast.ascend.already" : why;
                continue;
            }
            if (e.xp < BeastData.XP[4]) {
                why = "chaosshard.beast.ascend.xp";
                continue;
            }
            if (ru.death.chaosshard.thread.ThreadBonus.level(p) < minThread(b) && !p.isCreative()) {
                p.sendMessage(Breach.grimoire("chaosshard.ritual.thin", minThread(b)));
                return;
            }
            List<ItemStack> need = offerings(b);
            if (!p.isCreative() && !hasAll(p, need)) {
                p.sendMessage(Breach.grimoire("chaosshard.altar.need",
                        Beasts.title(b, e), list(need)));
                return;
            }
            if (!p.isCreative()) {
                for (ItemStack s : need) {
                    take(p, s);
                }
            }
            begin(p, altar, beast, p.isCreative() ? new ArrayList<>() : need);
            return;
        }
        p.sendMessage(Breach.grimoire(why == null ? "chaosshard.beast.ascend.xp" : why));
    }

    private static void begin(EntityPlayerMP p, BlockPos altar, EntityChaosBeast beast, List<ItemStack> paid) {
        RUNNING.put(p.getUniqueID(), new Running(altar, p.dimension, beast, paid));
        beast.setNoAI(true);
        beast.setNoGravity(true);
        beast.setPositionAndUpdate(altar.getX() + 0.5, altar.getY() + 1.1, altar.getZ() + 0.5);
        p.sendMessage(Breach.grimoire("chaosshard.altar.ascend.start",
                Beasts.title(beast.beast(), Beasts.entry(p.world, p.getUniqueID(), beast.beast()))));
        p.world.playSound(null, altar, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.6F, 0.9F);
    }

    private static void release(EntityChaosBeast beast) {
        beast.setNoAI(false);
        beast.setNoGravity(false);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNNING.isEmpty() || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        Running r = RUNNING.get(p.getUniqueID());
        if (r == null) {
            return;
        }
        WorldServer ws = (WorldServer) p.world;
        if (p.dimension != r.dim || p.isDead || p.getDistanceSq(r.altar) > 16 * 16 || !r.beast.isEntityAlive()
                || ws.getBlockState(r.altar).getBlock() != ModBlocks.BREACH_ALTAR) {
            RUNNING.remove(p.getUniqueID());
            if (r.beast.isEntityAlive()) {
                release(r.beast);
            }
            for (ItemStack s : r.paid) {
                ItemHandlerHelper.giveItemToPlayer(p, s.copy());
            }
            p.sendMessage(Breach.grimoire("chaosshard.altar.ascend.broken"));
            return;
        }
        r.ticks++;
        double cx = r.altar.getX() + 0.5, cz = r.altar.getZ() + 0.5;
        double progress = r.ticks / (double) DURATION;
        double y = r.altar.getY() + 1.1 + progress * 1.6;
        r.beast.setPositionAndUpdate(cx, y, cz);
        r.beast.rotationYaw = r.beast.renderYawOffset = r.beast.rotationYawHead = (float) (r.ticks * 6 % 360);
        double angle = r.ticks * 0.3;
        ws.spawnParticle(EnumParticleTypes.PORTAL, cx + Math.cos(angle) * 1.2, y + 0.4, cz + Math.sin(angle) * 1.2, 6, 0.05, 0.05, 0.05, 0.3);
        ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, cx, y + 0.5, cz, 3, 0.3, 0.4, 0.3, 0.0);
        if (r.ticks % 12 == 0) {
            for (BlockPos s : BreachRitual.pillars(r.altar)) {
                ChaosNetwork.CHANNEL.sendToAllAround(new PacketHeartBolt(s.getX() + 0.5, s.getY() + 1.95, s.getZ() + 0.5,
                                cx, y + r.beast.height / 2, cz, r.ticks > DURATION * 2 / 3 ? 0xFFD27A : 0xB070FF),
                        new NetworkRegistry.TargetPoint(p.dimension, cx, y, cz, 64));
            }
            ws.playSound(null, r.altar, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.BLOCKS, 0.15F, 1.8F);
        }
        if (r.ticks == DURATION / 2) {
            p.sendMessage(Breach.grimoire("chaosshard.altar.ascend.mid"));
        }
        if (r.ticks >= DURATION) {
            RUNNING.remove(p.getUniqueID());
            release(r.beast);
            r.beast.setPositionAndUpdate(cx, r.altar.getY() + 1.1, cz);
            Beasts.ascend(p, r.beast.beast());
        }
    }

    // ------------------------------------------------------------------ инвентарь

    private static boolean same(ItemStack a, ItemStack want) {
        return a.getItem() == want.getItem() && (want.getItem() != Items.FISH || a.getMetadata() == want.getMetadata())
                && (want.getItem() != Items.GOLDEN_APPLE || a.getMetadata() == 0);
    }

    private static boolean hasAll(EntityPlayerMP p, List<ItemStack> need) {
        for (ItemStack w : need) {
            int n = 0;
            for (ItemStack s : p.inventory.mainInventory) {
                if (same(s, w)) {
                    n += s.getCount();
                }
            }
            if (n < w.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static void take(EntityPlayerMP p, ItemStack w) {
        int n = w.getCount();
        for (ItemStack s : p.inventory.mainInventory) {
            if (n > 0 && same(s, w)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }

    private static net.minecraft.util.text.ITextComponent list(List<ItemStack> need) {
        net.minecraft.util.text.ITextComponent out = new net.minecraft.util.text.TextComponentString("");
        for (int i = 0; i < need.size(); i++) {
            ItemStack s = need.get(i);
            out.appendText((i > 0 ? ", " : "") + s.getCount() + "× ");
            out.appendSibling(new TextComponentTranslation(s.getTranslationKey() + ".name"));
        }
        return out;
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        RUNNING.clear();
    }
}
