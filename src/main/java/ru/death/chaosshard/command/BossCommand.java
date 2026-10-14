package ru.death.chaosshard.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.act5.CapitalData;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.hero.HeroData;
import ru.death.chaosshard.hero.Lightbearer;
import ru.death.chaosshard.hero.Pillars;

/**
 * /oh boss [kael|irena|velmor|lian|elis|garrot|king|archon] — проверка боссов: привести к боссу (и поставить его, если нужно).
 * Без аргумента — где кто и в каком состоянии.
 */
final class BossCommand {

    static final String[] NAMES = {"kael", "irena", "velmor", "lian", "elis", "garrot", "king", "archon"};

    static void run(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        WorldServer lu = server.getWorld(Breach.luchezariyaDim());
        if (args.length < 2) {
            HeroData hd = HeroData.get(lu);
            for (Lightbearer h : Lightbearer.values()) {
                HeroData.Pillar pl = hd.of(h);
                String st = pl.state == HeroData.NONE ? "нет" : pl.state == HeroData.PLANNED ? "указана" : pl.state == HeroData.STANDING ? "стоит" : "пала";
                sender.sendMessage(new TextComponentString(h.id() + ": Опора " + st + (pl.state == HeroData.NONE ? "" : " у " + pl.x + " " + pl.z)));
            }
            CapitalData cd = CapitalData.get(lu);
            sender.sendMessage(new TextComponentString("king: столица " + (cd.state == CapitalData.NONE ? "нет" : "у " + cd.x + " " + cd.z
                    + (cd.state >= CapitalData.OPEN ? ", открыта" : ", закрыта"))));
            sender.sendMessage(new TextComponentString("Привести к боссу: /oh boss <" + String.join("|", NAMES) + ">"));
            return;
        }
        EntityPlayerMP p = net.minecraft.command.CommandBase.getCommandSenderAsPlayer(sender);
        String who = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (who) {
            case "garrot":
                server.getCommandManager().executeCommand(sender, "oh garrot duel");
                return;
            case "archon":
                server.getCommandManager().executeCommand(sender, "oh act5 rift");
                return;
            case "king": {
                CapitalData cd = CapitalData.get(lu);
                if (p.dimension != lu.provider.getDimension()) {
                    ChaosTeleporter.toWorldSpawn(p, lu.provider.getDimension());
                }
                if (cd.state == CapitalData.NONE) {
                    server.getCommandManager().executeCommand(sender, "oh act5 capital");
                    cd = CapitalData.get(lu);
                }
                if (cd.state == CapitalData.NONE) {
                    throw new CommandException("Столица не встала — отойдите на ровное место и повторите");
                }
                ru.death.chaosshard.act5.Crown.open(lu);
                tp(p, lu, cd.center().getX(), cd.center().getZ() - 24);
                return;
            }
            default:
        }
        Lightbearer h;
        try {
            h = Lightbearer.valueOf(who.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new CommandException("Нет такого босса: " + who + ". Есть: " + String.join(", ", NAMES));
        }
        if (p.dimension != lu.provider.getDimension()) {
            ChaosTeleporter.toWorldSpawn(p, lu.provider.getDimension());
        }
        HeroData.Pillar pl = HeroData.get(lu).of(h);
        if (pl.state == HeroData.FALLEN) {
            throw new CommandException("Опора " + h.id() + " уже пала. Сбросить все: /oh pillar reset");
        }
        if (pl.state == HeroData.NONE && !Pillars.omen(p, lu, p.getPosition(), h, true)) {
            throw new CommandException("Знамение не нашло места для Опоры");
        }
        // встать в 24 блоках от Опоры: постройка достроится, когда загрузятся чанки, а сцена появления начнётся при подходе
        tp(p, lu, pl.x, pl.z - 24);
        sender.sendMessage(new TextComponentString("Опора " + h.id() + " у " + pl.x + " " + pl.z + " — подойдите ближе, Светоч появится"));
    }

    private static void tp(EntityPlayerMP p, WorldServer w, int x, int z) {
        w.getChunk(x >> 4, z >> 4);
        BlockPos top = w.getHeight(new BlockPos(x, 0, z));
        ChaosTeleporter.toPos(p, w.provider.getDimension(), top.up());
    }

    private BossCommand() {
    }
}
