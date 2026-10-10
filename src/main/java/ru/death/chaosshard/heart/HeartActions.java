package ru.death.chaosshard.heart;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.List;

/** Действия игрока над Сердцем (выполняются на сервере). */
public final class HeartActions {

    public static boolean canUpgrade(EntityPlayerMP player, TileHeart heart) {
        int tier = heart.getTier();
        if (tier >= HeartTiers.maxTier()) {
            return false;
        }
        if (player.isCreative()) {
            return true;
        }
        int target = tier + 1;
        if (ThreadManager.getLevel(player) < HeartTiers.threadFor(target)) {
            return false;
        }
        for (HeartTiers.Cost c : HeartTiers.costFor(target)) {
            if (HeartTiers.count(player, c) < c.count) {
                return false;
            }
        }
        return true;
    }

    public static void upgrade(EntityPlayerMP player, TileHeart heart) {
        int tier = heart.getTier();
        if (tier >= HeartTiers.maxTier()) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.heart.max"), true);
            return;
        }
        int target = tier + 1;
        if (!player.isCreative()) {
            if (ThreadManager.getLevel(player) < HeartTiers.threadFor(target)) {
                player.sendStatusMessage(Breach.grimoire("chaosshard.heart.thin", HeartTiers.threadFor(target)), true);
                return;
            }
            List<HeartTiers.Cost> costs = HeartTiers.costFor(target);
            for (HeartTiers.Cost c : costs) {
                if (HeartTiers.count(player, c) < c.count) {
                    player.sendStatusMessage(Breach.grimoire("chaosshard.heart.poor"), true);
                    return;
                }
            }
            for (HeartTiers.Cost c : costs) {
                HeartTiers.take(player, c);
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            player.openContainer.detectAndSendChanges();
        }
        heart.setTier(target);
        World world = heart.getWorld();
        world.playSound(null, heart.getPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 0.6F);
        world.playSound(null, heart.getPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.3F, 1.6F);
        player.sendMessage(Breach.grimoire("chaosshard.heart.upgraded." + Math.min(target, 4), target));
        heart.grow(true);
    }

    private HeartActions() {
    }
}
