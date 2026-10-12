package ru.death.chaosshard.debug;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Подставные игроки автотеста: для систем мода они «в сети», хотя на сервере никого нет. */
public final class TestHooks {

    public static final Map<UUID, EntityPlayerMP> FAKES = new HashMap<>();

    public static EntityPlayerMP player(MinecraftServer server, UUID id) {
        if (id == null) {
            return null;
        }
        EntityPlayerMP f = FAKES.get(id);
        if (f != null) {
            return f;
        }
        return server == null ? null : server.getPlayerList().getPlayerByUUID(id);
    }

    private TestHooks() {
    }
}
