package ru.death.chaosshard.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.stars.SkyRules;
import ru.death.chaosshard.thread.ThreadManager;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * /chaosshard (сокращённо /oh):
 *   nit get [игрок]
 *   nit set <игрок> <очки>
 *   nit add <игрок> <очки>      — отрицательное число убавляет
 *   nit resync [игрок]          — заново выдать этапы Game Stages
 *   tp antechamber [игрок]      — в Преддверие
 *   tp world <id> [игрок]       — к точке появления измерения
 *   sky                         — какие правила неба действуют здесь (отладка)
 */
public class CommandChaosShard extends CommandBase {

    @Override
    public String getName() {
        return "chaosshard";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("oh");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "chaosshard.command.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        switch (args[0]) {
            case "nit":
                thread(server, sender, args);
                break;
            case "tp":
                teleport(server, sender, args);
                break;
            case "sky":
                sky(sender);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private void thread(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("chaosshard.command.nit.usage");
        }
        String action = args[1];
        switch (action) {
            case "get": {
                EntityPlayerMP player = args.length >= 3 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
                int points = ThreadManager.getPoints(player);
                int level = ThreadManager.levelFor(points);
                sender.sendMessage(new TextComponentTranslation("chaosshard.command.nit.get", player.getName(), level, points));
                break;
            }
            case "set":
            case "add": {
                if (args.length < 4) {
                    throw new WrongUsageException("chaosshard.command.nit.usage");
                }
                EntityPlayerMP player = getPlayer(server, sender, args[2]);
                int value = parseInt(args[3]);
                if ("set".equals(action)) {
                    ThreadManager.setPoints(player, value);
                } else {
                    ThreadManager.addPoints(player, value);
                }
                int points = ThreadManager.getPoints(player);
                notifyCommandListener(sender, this, "chaosshard.command.nit.changed", player.getName(), ThreadManager.levelFor(points), points);
                break;
            }
            case "resync": {
                EntityPlayerMP player = args.length >= 3 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
                ThreadManager.reapplyStages(player);
                ThreadManager.sync(player);
                notifyCommandListener(sender, this, "chaosshard.command.nit.resynced", player.getName());
                break;
            }
            default:
                throw new WrongUsageException("chaosshard.command.nit.usage");
        }
    }

    private void teleport(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("chaosshard.command.tp.usage");
        }
        if ("antechamber".equals(args[1])) {
            EntityPlayerMP player = args.length >= 3 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
            ChaosTeleporter.toAntechamber(player);
        } else if ("world".equals(args[1]) && args.length >= 3) {
            int dim = parseInt(args[2]);
            EntityPlayerMP player = args.length >= 4 ? getPlayer(server, sender, args[3]) : getCommandSenderAsPlayer(sender);
            if (!net.minecraftforge.common.DimensionManager.isDimensionRegistered(dim)) {
                throw new CommandException("chaosshard.command.tp.nodim", dim);
            }
            ChaosTeleporter.toWorldSpawn(player, dim);
        } else {
            throw new WrongUsageException("chaosshard.command.tp.usage");
        }
    }

    private void sky(ICommandSender sender) {
        World world = sender.getEntityWorld();
        SkyRules.Kind kind = SkyRules.classify(world);
        sender.sendMessage(new TextComponentTranslation("chaosshard.command.sky", world.provider.getDimension(),
                world.provider.getClass().getName(), new TextComponentTranslation("chaosshard.sky.kind." + kind.name().toLowerCase())));
        if (ChaosShard.astralLoaded) {
            sender.sendMessage(new TextComponentTranslation("chaosshard.command.sky.active",
                    ru.death.chaosshard.stars.AstralCompat.describeActive(world)));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "nit", "tp", "sky");
        }
        if (args.length == 2 && "nit".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "get", "set", "add", "resync");
        }
        if (args.length == 2 && "tp".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "antechamber", "world");
        }
        if (args.length == 3 && "nit".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3 && "tp".equals(args[0]) && "antechamber".equals(args[1])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 2 && args.length > 0 && "nit".equals(args[0]);
    }
}
