package ru.death.chaosshard.cutscene;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketCutscene;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Катсцены на сервере: пока идёт сцена, игрок стоит на месте, его не бьют и не замечают,
 * а сценарий (Script) по тикам делает своё — призывает, двигает, гремит.
 * Клиент может пропустить сцену — тогда сценарий доигрывает всё сразу.
 */
public class CutsceneManager {

    /** Что делает сцена в мире. */
    public interface Script {
        /** Тик сцены (с 0). */
        void tick(EntityPlayerMP p, int t);

        /** Сцена кончилась: skipped — пропущена, aborted — игрок вышел или умер. */
        void end(EntityPlayerMP p, boolean skipped, boolean aborted);
    }

    static final class Run {
        final Cutscene scene;
        final Script script;
        final Vec3d hold;
        final int dim;
        int t;

        Run(Cutscene scene, Script script, Vec3d hold, int dim) {
            this.scene = scene;
            this.script = script;
            this.hold = hold;
            this.dim = dim;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static boolean playing(EntityPlayer p) {
        return RUNS.containsKey(p.getUniqueID());
    }

    /** Тик сцены у игрока, -1 — сцены нет. */
    public static int tickOf(EntityPlayer p) {
        Run r = RUNS.get(p.getUniqueID());
        return r == null ? -1 : r.t;
    }

    public static void play(EntityPlayerMP p, Cutscene scene, Script script) {
        Run old = RUNS.remove(p.getUniqueID());
        if (old != null) {
            old.script.end(p, false, true);
        }
        p.motionX = p.motionY = p.motionZ = 0;
        RUNS.put(p.getUniqueID(), new Run(scene, script, p.getPositionVector(), p.dimension));
        ChaosNetwork.CHANNEL.sendTo(PacketCutscene.start(scene), p);
    }

    /** Клиент попросил пропустить. */
    public static void skip(EntityPlayerMP p) {
        Run r = RUNS.remove(p.getUniqueID());
        if (r != null) {
            ChaosNetwork.CHANNEL.sendTo(PacketCutscene.stop(), p);
            r.script.end(p, true, false);
        }
    }

    /** Оборвать сцену (смерть, выход, смена мира). */
    public static void abort(EntityPlayerMP p) {
        Run r = RUNS.remove(p.getUniqueID());
        if (r != null) {
            ChaosNetwork.CHANNEL.sendTo(PacketCutscene.stop(), p);
            r.script.end(p, false, true);
        }
    }

    /** Один тик сцены игрока (вынесено для автотеста). */
    public static void tick(EntityPlayerMP p) {
        Run r = RUNS.get(p.getUniqueID());
        if (r == null) {
            return;
        }
        if (p.isDead || p.dimension != r.dim) {
            abort(p);
            return;
        }
        // стоять на месте
        if (p.getDistanceSq(r.hold.x, r.hold.y, r.hold.z) > 0.04) {
            p.setPositionAndUpdate(r.hold.x, r.hold.y, r.hold.z);
            p.setPosition(r.hold.x, r.hold.y, r.hold.z);
        }
        p.motionX = p.motionZ = 0;
        p.fallDistance = 0;
        r.script.tick(p, r.t);
        r.t++;
        if (r.t > r.scene.length) {
            RUNS.remove(p.getUniqueID());
            ChaosNetwork.CHANNEL.sendTo(PacketCutscene.stop(), p);
            r.script.end(p, false, false);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty() || !(event.player instanceof EntityPlayerMP)
                || event.player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        tick((EntityPlayerMP) event.player);
    }

    /** В сцене игрока не бьют. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!RUNS.isEmpty() && event.getEntityLiving() instanceof EntityPlayer && playing((EntityPlayer) event.getEntityLiving())
                && !event.getSource().canHarmInCreative()) {
            event.setCanceled(true);
        }
    }

    /** …и не замечают. */
    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent event) {
        if (!RUNS.isEmpty() && event.getTarget() instanceof EntityPlayer && playing((EntityPlayer) event.getTarget())
                && event.getEntityLiving() instanceof net.minecraft.entity.EntityLiving) {
            ((net.minecraft.entity.EntityLiving) event.getEntityLiving()).setAttackTarget(null);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            Run r = RUNS.remove(event.player.getUniqueID());
            if (r != null) {
                r.script.end((EntityPlayerMP) event.player, false, true);
            }
        }
    }

    /** Для отладки: оборвать все сцены. */
    public static void clear() {
        Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
