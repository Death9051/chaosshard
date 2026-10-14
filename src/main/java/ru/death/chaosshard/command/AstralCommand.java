package ru.death.chaosshard.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import ru.death.chaosshard.stars.ChaosLink;
import ru.death.chaosshard.stars.Perks;

/**
 * /oh astral [info|ready [созвездие]|link|exp <n>] — проверка Astral Sorcery и связки с Хаосом.
 * Класс трогает Astral — вызывать только при установленном моде.
 */
final class AstralCommand {

    static final String[] MAJOR = {"aevitas", "armara", "discidia", "evorsio", "vicio"};

    static void run(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP p = CommandBase.getCommandSenderAsPlayer(sender);
        String sub = args.length >= 2 ? args[1] : "info";
        switch (sub) {
            case "ready": {
                // всё открыто, созвездия известны, привязка к главному (по умолчанию дискидия)
                String c = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "discidia";
                String n = p.getName();
                server.getCommandManager().executeCommand(server, "astralsorcery progress " + n + " all");
                server.getCommandManager().executeCommand(server, "astralsorcery constellation " + n + " all");
                server.getCommandManager().executeCommand(server, "astralsorcery attune " + n + " astralsorcery.constellation." + c);
                sender.sendMessage(new TextComponentString("Astral: всё открыто, привязка — " + c + ". Дальше: /oh astral exp <n>, /oh astral link"));
                break;
            }
            case "link": {
                int n = ChaosLink.link(p);
                sender.sendMessage(new TextComponentString(n < 0 ? "Связь с Хаосом не вышла: нужна привязка к главному созвездию"
                        : "Связь с Хаосом: открыто корней " + n));
                break;
            }
            case "exp": {
                if (args.length < 3) {
                    throw new CommandException("/oh astral exp <n>");
                }
                server.getCommandManager().executeCommand(server, "astralsorcery exp " + p.getName() + " " + CommandBase.parseInt(args[2], 0));
                break;
            }
            default: {
                String att = ru.death.chaosshard.compat.astral.AstralOverflow.attuned(p);
                sender.sendMessage(new TextComponentString("Привязка: " + (att == null ? "нет" : att)
                        + ", Связь с Хаосом: " + (ChaosLink.linked(p) ? "есть" : "нет")
                        + ", опыт перков: " + (int) ru.death.chaosshard.compat.astral.AstralOverflow.exp(p)));
                sender.sendMessage(new TextComponentString("Перки Хаоса: искра " + yes(Perks.spark(p)) + ", ночь " + yes(Perks.night(p))
                        + ", тень " + yes(Perks.shadow(p)) + ", голод " + yes(Perks.hunger(p)) + ", колодец " + yes(Perks.well(p))));
                for (String line : ru.death.chaosshard.stars.AstralCompat.debugSky(p).split("\n")) {
                    sender.sendMessage(new TextComponentString(line));
                }
                sender.sendMessage(new TextComponentString("Подготовить: /oh astral ready [" + String.join("|", MAJOR) + "]"));
            }
        }
    }

    private static String yes(boolean b) {
        return b ? "да" : "нет";
    }

    private AstralCommand() {
    }
}
