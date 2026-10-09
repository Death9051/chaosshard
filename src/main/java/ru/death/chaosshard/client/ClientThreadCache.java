package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;

/**
 * Последнее известное клиенту значение Нити.
 */
public final class ClientThreadCache {

    private static int points = -1;
    /** Время (в тиках мира клиента), когда Нить последний раз менялась. */
    private static long changedAt = -1_000_000L;

    static void update(int newPoints) {
        if (points != -1 && newPoints != points && Minecraft.getMinecraft().world != null) {
            changedAt = Minecraft.getMinecraft().world.getTotalWorldTime();
        }
        points = newPoints;
    }

    public static int points() {
        return Math.max(points, 0);
    }

    public static boolean known() {
        return points >= 0;
    }

    public static long changedAt() {
        return changedAt;
    }

    private ClientThreadCache() {
    }
}
