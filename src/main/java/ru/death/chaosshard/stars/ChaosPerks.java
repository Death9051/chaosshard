package ru.death.chaosshard.stars;

import hellfirepvp.astralsorcery.common.constellation.perk.AbstractPerk;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.PerkTree;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.PerkTreePoint;
import hellfirepvp.astralsorcery.common.constellation.perk.tree.nodes.KeyPerk;
import hellfirepvp.astralsorcery.common.data.research.PlayerProgress;
import hellfirepvp.astralsorcery.common.data.research.ResearchManager;
import hellfirepvp.astralsorcery.common.event.APIRegistryEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.thread.ThreadBonus;

import java.awt.Point;

/**
 * Ветка перков Хаоса в древе Astral Sorcery. Растёт от самого нижнего узла древа вниз:
 *
 *        (низ древа)
 *            |
 *        Искра Хаоса        Нить 1: +10 к запасу энергии
 *         /       \\
 *  Жилы ночи    Тень Нити   Нить 2: восстановление энергии +25% / святая земля истончает на уровень меньше
 *         \\       /
 *       Голод Скверны       Нить 3: убийство возвращает 5 энергии
 *            |
 *     Глубокий колодец      Нить 4: +30 к запасу, перезарядка заклинаний на четверть короче
 *
 * Каждый перк можно взять только с нужным уровнем Нити.
 */
/** Подписывается вручную из AstralCompat — только когда Astral Sorcery загружен (иначе сервер падает на чужих классах). */
public final class ChaosPerks {

    public static ChaosPerk SPARK, NIGHT, SHADOW, HUNGER, WELL;

    public static class ChaosPerk extends KeyPerk {
        final int thread;

        ChaosPerk(String name, int x, int y, int thread) {
            super(name, x, y);
            this.thread = thread;
        }

        @Override
        public boolean mayUnlockPerk(PlayerProgress progress, EntityPlayer player) {
            return super.mayUnlockPerk(progress, player) && ThreadBonus.level(player) >= thread;
        }

        @Override
        public void onUnlockPerkServer(EntityPlayer player, PlayerProgress progress, NBTTagCompound data) {
            super.onUnlockPerkServer(player, progress, data);
            if (player instanceof EntityPlayerMP) {
                ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) player, ru.death.chaosshard.quest.Milestones.CHAOS_PERK);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRegister(APIRegistryEvent.PerkRegister event) {
        // Самый нижний узел древа — к нему прицепляем ветку
        AbstractPerk bottom = null;
        for (PerkTreePoint<?> p : PerkTree.PERK_TREE.getPerkPoints()) {
            AbstractPerk perk = p.getPerk();
            if (perk == null) {
                continue;
            }
            Point o = perk.getOffset();
            if (bottom == null || o.y > bottom.getOffset().y
                    || (o.y == bottom.getOffset().y && Math.abs(o.x) < Math.abs(bottom.getOffset().x))) {
                bottom = perk;
            }
        }
        if (bottom == null) {
            return;
        }
        int bx = bottom.getOffset().x, by = bottom.getOffset().y;
        SPARK = new ChaosPerk("chaos_spark", bx, by + 3, 1);
        NIGHT = new ChaosPerk("chaos_night_veins", bx - 3, by + 6, 2);
        SHADOW = new ChaosPerk("chaos_thread_shadow", bx + 3, by + 6, 2);
        HUNGER = new ChaosPerk("chaos_blight_hunger", bx, by + 9, 3);
        WELL = new ChaosPerk("chaos_deep_well", bx, by + 12, 4);
        event.registerPerk(SPARK).connect(bottom);
        event.registerPerk(NIGHT).connect(SPARK);
        event.registerPerk(SHADOW).connect(SPARK);
        event.registerPerk(HUNGER).connect(NIGHT).connect(SHADOW);
        event.registerPerk(WELL).connect(HUNGER);
    }

    /** Есть ли у игрока действующий перк (только сервер). */
    public static boolean has(EntityPlayer player, AbstractPerk perk) {
        if (perk == null || player == null || player.world == null || player.world.isRemote) {
            return false;
        }
        PlayerProgress progress = ResearchManager.getProgress(player, Side.SERVER);
        return progress != null && progress.isValid() && progress.hasPerkEffect(perk);
    }

    private ChaosPerks() {
    }
}
