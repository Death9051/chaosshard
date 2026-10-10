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
 *   grimoire [игрок]            — выдать Гримуар
 *   breach [on|off] [игрок]     — отметить Прорыв пройденным / сбросить
 *   heart [place|grow [n]|tier N] — Сердце Острова
 *   selftest                    — самопроверка сборки → chaosshard-selftest.txt
 *   quests complete|reset|edit [игрок] — выполнить все квесты / сбросить / режим редактора BQ
 *   debug [report|reset|kit]    — отчёт в chaosshard-debug.txt / сброс «первого входа» / набор для теста
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
            case "debug":
                debug(server, sender, args);
                break;
            case "selftest": {
                SelfTest test = SelfTest.run(server, getCommandSenderAsPlayer(sender));
                try {
                    java.io.File file = test.write();
                    sender.sendMessage(new TextComponentTranslation("chaosshard.command.selftest.done", test.percent(), test.failures(), file.getPath()));
                } catch (Exception e) {
                    throw new CommandException("chaosshard.command.debug.failed", e.toString());
                }
                break;
            }
            case "quests": {
                // /oh quests <complete|reset|edit> [игрок] — через команды Better Questing
                if (!net.minecraftforge.fml.common.Loader.isModLoaded("betterquesting")) {
                    throw new CommandException("chaosshard.grimoire.no_bq");
                }
                String sub = args.length >= 2 ? args[1] : "";
                EntityPlayerMP player = args.length >= 3 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
                net.minecraft.command.ICommandManager cm = server.getCommandManager();
                if ("complete".equals(sub)) {
                    int count = questCount();
                    for (int id = 0; id < count; id++) {
                        cm.executeCommand(server, "bq_admin complete " + id + " " + player.getName());
                    }
                    sender.sendMessage(new net.minecraft.util.text.TextComponentTranslation("chaosshard.command.quests.completed", count, player.getName()));
                } else if ("reset".equals(sub)) {
                    cm.executeCommand(server, "bq_admin reset all " + player.getName());
                    sender.sendMessage(new net.minecraft.util.text.TextComponentTranslation("chaosshard.command.quests.reset", player.getName()));
                } else if ("edit".equals(sub)) {
                    cm.executeCommand(server, "bq_admin edit");
                    sender.sendMessage(new net.minecraft.util.text.TextComponentTranslation("chaosshard.command.quests.edit"));
                } else {
                    throw new WrongUsageException("chaosshard.command.quests.usage");
                }
                break;
            }
            case "heart": {
                // /oh heart [place|grow|tier <N>] — Сердце Острова
                net.minecraft.world.World ante = server.getWorld(ru.death.chaosshard.antechamber.AntechamberDimension.id());
                BlockPos hp = ru.death.chaosshard.heart.HeartPlacer.heartPos();
                String sub = args.length >= 2 ? args[1] : "info";
                if ("place".equals(sub)) {
                    ru.death.chaosshard.heart.HeartPlacer.place(ante);
                }
                net.minecraft.tileentity.TileEntity te = ante.getTileEntity(hp);
                if (!(te instanceof ru.death.chaosshard.heart.TileHeart)) {
                    throw new CommandException("chaosshard.command.heart.missing");
                }
                ru.death.chaosshard.heart.TileHeart heart = (ru.death.chaosshard.heart.TileHeart) te;
                if ("grow".equals(sub)) {
                    int n = args.length >= 3 ? parseInt(args[2], 1, 64) : 1;
                    for (int i = 0; i < n; i++) {
                        heart.grow(true);
                    }
                } else if ("tier".equals(sub) && args.length >= 3) {
                    heart.setTier(parseInt(args[2], 1, ru.death.chaosshard.heart.HeartTiers.maxTier()));
                }
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("Сердце Острова " + hp + ": ступень "
                        + heart.getTier() + ", наростов " + heart.countGrowths()
                        + "/" + heart.maxGrowths()));
                break;
            }
            case "breach": {
                // /oh breach [on|off] [игрок] — отметить Прорыв пройденным или сбросить
                boolean on = args.length < 2 || !"off".equals(args[1]);
                EntityPlayerMP player = args.length >= 3 ? getPlayer(server, sender, args[2]) : getCommandSenderAsPlayer(sender);
                ru.death.chaosshard.breach.Breach.setBreached(player, on);
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("Прорыв для " + player.getName() + ": " + (on ? "пройден" : "сброшен")));
                break;
            }
            case "grimoire": {
                EntityPlayerMP player = args.length >= 2 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
                ru.death.chaosshard.antechamber.AntechamberEvents.giveGrimoire(player);
                break;
            }
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

    private void debug(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String mode = args.length >= 2 ? args[1] : "report";
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        switch (mode) {
            case "report": {
                DebugReport report = DebugReport.build(server, player);
                try {
                    java.io.File file = report.write();
                    sender.sendMessage(new TextComponentTranslation("chaosshard.command.debug.written", file.getPath(), report.problems()));
                } catch (Exception e) {
                    throw new CommandException("chaosshard.command.debug.failed", e.toString());
                }
                break;
            }
            case "reset": {
                // Как будто игрок впервые зашёл: флаги, Нить и этапы сбрасываются
                net.minecraft.nbt.NBTTagCompound persisted = player.getEntityData()
                        .getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
                persisted.removeTag("chaosshard_arrived");
                persisted.removeTag("chaosshard_grimoire_given");
                persisted.removeTag("chaosshard_breached");
                persisted.removeTag("chaosshard_luch_return");
                ru.death.chaosshard.thread.IThread thread = ru.death.chaosshard.thread.ThreadCapability.get(player);
                if (thread != null) {
                    for (int l = ThreadManager.maxLevel(); l >= 0; l--) {
                        server.getCommandManager().executeCommand(server, ru.death.chaosshard.ChaosConfig.thread.stageRemoveCommand
                                .replace("{player}", player.getName())
                                .replace("{stage}", ru.death.chaosshard.ChaosConfig.thread.stagePrefix + l));
                    }
                    thread.setPoints(0);
                    thread.setAnnouncedLevel(-1);
                    ThreadManager.sync(player);
                }
                sender.sendMessage(new TextComponentTranslation("chaosshard.command.debug.reset"));
                break;
            }
            case "kit": {
                String[] kit = {
                        "chaosshard:grimoire", "chaosshard:chaos_stone 64", "chaosshard:chaos_crust 64",
                        "chaosshard:breach_altar", "chaosshard:breach_book", "chaosshard:memory_fragment 8", "chaosshard:veil_shard 8",
                        "astralsorcery:itemjournal", "astralsorcery:itemwand", "astralsorcery:itemhandtelescope",
                        "astralsorcery:blockaltar 1 0", "astralsorcery:blockwell", "astralsorcery:itemrockcrystalsimple 4",
                        "astralsorcery:itemcraftingcomponent 32 0", "astralsorcery:blockmarble 64 0"
                };
                for (String item : kit) {
                    server.getCommandManager().executeCommand(server, "give " + player.getName() + " " + item);
                }
                break;
            }
            default:
                throw new WrongUsageException("chaosshard.command.debug.usage");
        }
    }

    /** Число квестов в базе Better Questing (через отражение, чтобы не зависеть от BQ при компиляции). */
    private static int questCount() throws CommandException {
        try {
            Object db = Class.forName("betterquesting.questing.QuestDatabase").getField("INSTANCE").get(null);
            return (Integer) db.getClass().getMethod("size").invoke(db);
        } catch (Exception e) {
            throw new CommandException("chaosshard.command.debug.failed", e.toString());
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
            return getListOfStringsMatchingLastWord(args, "nit", "tp", "sky", "grimoire", "debug", "breach", "heart", "quests", "selftest");
        }
        if (args.length == 2 && "nit".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "get", "set", "add", "resync");
        }
        if (args.length == 2 && "quests".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "complete", "reset", "edit");
        }
        if (args.length == 3 && "quests".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && "heart".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "info", "place", "grow", "tier");
        }
        if (args.length == 2 && "debug".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "report", "reset", "kit");
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
