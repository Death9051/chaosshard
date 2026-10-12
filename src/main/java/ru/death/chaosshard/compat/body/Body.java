package ru.death.chaosshard.compat.body;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

/**
 * Безопасная точка входа в тело Осколка: всё, что связано с Robotic Parts и Metamorph,
 * вызывается только когда соответствующий мод загружен.
 */
public final class Body {

    public static boolean cyber() {
        return Loader.isModLoaded("cyberware");
    }

    public static void registerItems() {
        if (cyber()) {
            BodyCompat.registerItems();
        }
    }

    public static void init() {
        if (cyber()) {
            BodyCompat.init();
        }
        if (Loader.isModLoaded("metamorph")) {
            MorphCompat.init();
        }
    }

    /** Раз в секунду из тика энергии. */
    public static void tick(EntityPlayerMP player) {
        if (cyber()) {
            BodyCompat.updateTolerance(player);
            BodyCompat.tickImplants(player);
        }
    }

    public static boolean hasCore(EntityPlayer p) {
        return cyber() && BodyCompat.has(p, ItemChaosImplant.CORE);
    }

    public static boolean hasReceiver(EntityPlayer p) {
        return cyber() && BodyCompat.has(p, ItemChaosImplant.RECEIVER);
    }

    /** Глушитель Порядка: святая земля истончает Нить на уровень меньше. */
    public static boolean hasWarden(EntityPlayer p) {
        return cyber() && BodyCompat.has(p, ItemChaosImplant.WARDEN);
    }

    public static boolean hasVeins(EntityPlayer p) {
        return cyber() && BodyCompat.has(p, ItemChaosImplant.VEINS);
    }

    private Body() {
    }
}
