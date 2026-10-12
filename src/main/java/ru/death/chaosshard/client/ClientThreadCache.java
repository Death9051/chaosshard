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

    private static int penalty;

    static void setPenalty(int p) {
        penalty = p;
        if (Minecraft.getMinecraft().world != null) {
            changedAt = Minecraft.getMinecraft().world.getTotalWorldTime();
        }
    }

    /** Насколько истончена Нить (святая земля). */
    public static int penalty() {
        return penalty;
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

    // ------------------------------------------------------------------ энергия Хаоса

    private static int energy = -1, maxEnergy = 40;
    private static long energyChangedAt = -1_000_000L;

    static void setEnergy(int e, int max) {
        if (energy != -1 && e < energy && Minecraft.getMinecraft().world != null) {
            energyChangedAt = Minecraft.getMinecraft().world.getTotalWorldTime();
        }
        energy = e;
        maxEnergy = Math.max(1, max);
    }

    public static int energy() {
        return Math.max(energy, 0);
    }

    public static int maxEnergy() {
        return maxEnergy;
    }

    public static boolean energyKnown() {
        return energy >= 0;
    }

    /** Когда энергию последний раз тратили. */
    public static long energyChangedAt() {
        return energyChangedAt;
    }

    private ClientThreadCache() {
    }
}
