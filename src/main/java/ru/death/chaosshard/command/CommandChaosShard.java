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
 *   anchor [list|spread <n>]    — Якоря Хаоса: список / прогнать Скверну у ближайшего
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
            case "anchor": {
                // /oh anchor [list|spread <n>] — Якоря Хаоса
                String sub = args.length >= 2 ? args[1] : "list";
                World w = sender.getEntityWorld();
                ru.death.chaosshard.anchor.AnchorData data = ru.death.chaosshard.anchor.AnchorData.get(w);
                if ("warn".equals(sub)) {
                    // набег с предупреждением: через минуту с небольшим
                    BlockPos near = data.nearest(sender.getPosition(), null);
                    if (near == null || !(w.getTileEntity(near) instanceof ru.death.chaosshard.anchor.TileAnchor)) {
                        throw new CommandException("chaosshard.command.anchor.none");
                    }
                    ru.death.chaosshard.anchor.TileAnchor a = (ru.death.chaosshard.anchor.TileAnchor) w.getTileEntity(near);
                    for (ru.death.chaosshard.anchor.TileAnchor t : ru.death.chaosshard.anchor.AnchorClusters.cluster(w, a)) {
                        t.setRaidTimer(ru.death.chaosshard.anchor.TileAnchor.WARN + 40);
                    }
                } else if ("raid".equals(sub)) {
                    BlockPos near = data.nearest(sender.getPosition(), null);
                    if (near == null || !(w.getTileEntity(near) instanceof ru.death.chaosshard.anchor.TileAnchor)) {
                        throw new CommandException("chaosshard.command.anchor.none");
                    }
                    ((ru.death.chaosshard.anchor.TileAnchor) w.getTileEntity(near)).forceRaid(getCommandSenderAsPlayer(sender));
                } else if ("level".equals(sub)) {
                    int lvl = args.length >= 3 ? parseInt(args[2], 1, ru.death.chaosshard.anchor.TileAnchor.maxLevel())
                            : ru.death.chaosshard.anchor.TileAnchor.maxLevel();
                    BlockPos near = data.nearest(sender.getPosition(), null);
                    if (near == null || !(w.getTileEntity(near) instanceof ru.death.chaosshard.anchor.TileAnchor)) {
                        throw new CommandException("chaosshard.command.anchor.none");
                    }
                    ru.death.chaosshard.anchor.TileAnchor a = (ru.death.chaosshard.anchor.TileAnchor) w.getTileEntity(near);
                    a.setCorrupted(ru.death.chaosshard.ChaosConfig.anchor.levelThresholds[lvl - 1]);
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Якорь " + near + ": уровень " + a.level()
                            + ", осквернено " + a.getCorrupted() + ", потолок радиуса " + a.radiusCap()));
                } else if ("spread".equals(sub)) {
                    int n = args.length >= 3 ? parseInt(args[2], 1, 10000) : 100;
                    BlockPos near = data.nearest(sender.getPosition(), null);
                    if (near == null || !(w.getTileEntity(near) instanceof ru.death.chaosshard.anchor.TileAnchor)) {
                        throw new CommandException("chaosshard.command.anchor.none");
                    }
                    ru.death.chaosshard.anchor.TileAnchor a = (ru.death.chaosshard.anchor.TileAnchor) w.getTileEntity(near);
                    int done = a.forceSpread(n);
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Якорь " + near + ": осквернено ещё " + done
                            + ", всего " + a.getCorrupted() + ", радиус " + a.radius()));
                } else {
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Якорей в этом мире: " + data.anchors.size()));
                    for (BlockPos p : data.anchors) {
                        net.minecraft.tileentity.TileEntity te = w.getTileEntity(p);
                        String st = te instanceof ru.death.chaosshard.anchor.TileAnchor
                                ? ((ru.death.chaosshard.anchor.TileAnchor) te).getCorrupted() + " осквернено"
                                + (((ru.death.chaosshard.anchor.TileAnchor) te).isCaptured() ? ", захвачен" : "") : "чанк не загружен";
                        sender.sendMessage(new net.minecraft.util.text.TextComponentString("  " + p.getX() + " " + p.getY() + " " + p.getZ() + " — " + st));
                    }
                }
                break;
            }
            case "autotest": {
                // /oh autotest — сценарии без игрока, отчёт в chaosshard-autotest.txt
                boolean ok = ru.death.chaosshard.debug.AutoTest.start(server, false);
                sender.sendMessage(new net.minecraft.util.text.TextComponentString(ok ? "Автотест запущен — отчёт в " + ru.death.chaosshard.debug.AutoTest.FILE : "Автотест уже идёт"));
                break;
            }
            case "servant": {
                // /oh servant <husk|bone|executioner|reaper|lumberjack|miner> — призвать без ритуала
                // /oh servant up <УЛУЧШЕНИЕ> — дать улучшение слугам этого типа рядом (8 блоков)
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                if (args.length >= 3 && "up".equals(args[1])) {
                    ru.death.chaosshard.servant.ServantUpgrade u;
                    try {
                        u = ru.death.chaosshard.servant.ServantUpgrade.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new WrongUsageException("/oh servant up <" + java.util.Arrays.toString(ru.death.chaosshard.servant.ServantUpgrade.values()) + ">");
                    }
                    ru.death.chaosshard.servant.ServantUpgrade.perform(player, u, player.getPosition());
                } else if (args.length >= 2) {
                    ru.death.chaosshard.servant.Servants.Kind k;
                    try {
                        k = ru.death.chaosshard.servant.Servants.Kind.valueOf(args[1].toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new WrongUsageException("/oh servant <husk|bone|executioner|reaper|lumberjack|miner> | up <улучшение>");
                    }
                    ru.death.chaosshard.servant.Servants.summon(player, k, player.getPosition().offset(player.getHorizontalFacing(), 2));
                } else {
                    throw new WrongUsageException("/oh servant <husk|bone|executioner|reaper|lumberjack|miner> | up <улучшение>");
                }
                break;
            }
            case "beast": {
                // /oh beast <hare|cat|tiger> [info|xp <n>|asc|return|reset] — уникальные звери
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                ru.death.chaosshard.servant.BeastData.Beast b = args.length >= 2 ? ru.death.chaosshard.servant.BeastData.Beast.byKey(args[1]) : null;
                if (b == null) {
                    throw new WrongUsageException("/oh beast <hare|cat|tiger> [info|xp <n>|asc|return|reset]");
                }
                String what = args.length >= 3 ? args[2] : "info";
                int n = args.length >= 4 ? parseInt(args[3]) : 0;
                sender.sendMessage(new net.minecraft.util.text.TextComponentString(ru.death.chaosshard.servant.Beasts.debug(player, b, what, n)));
                break;
            }
            case "trial": {
                // /oh trial [start <ступень> [облик] [законы…]|leave] — Испытания
                // облик: ruins|ice|forge|overgrown; законы: echo hunter price silence debt memory clock
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                String sub = args.length >= 2 ? args[1] : "start";
                if ("leave".equals(sub)) {
                    ru.death.chaosshard.trial.TrialManager.finish(player, player.world, player.getPosition());
                } else {
                    int tier = args.length >= 3 ? parseInt(args[2], 1, 3) : 1;
                    net.minecraft.nbt.NBTTagCompound tag = ru.death.chaosshard.trial.ItemTrialKey.roll((tier - 1) * 2, player.world.rand);
                    int theme = tag.getInteger("theme");
                    int cond = tag.getInteger("cond");
                    if (args.length >= 4) {
                        for (ru.death.chaosshard.trial.TrialTheme th : ru.death.chaosshard.trial.TrialTheme.values()) {
                            if (th.name().equalsIgnoreCase(args[3])) theme = th.ordinal();
                        }
                    }
                    if (args.length >= 5) {
                        cond = 0;
                        for (int a = 4; a < args.length; a++) {
                            for (ru.death.chaosshard.trial.TrialCondition c : ru.death.chaosshard.trial.TrialCondition.values()) {
                                if (c.name().equalsIgnoreCase(args[a])) cond |= 1 << c.ordinal();
                            }
                        }
                    }
                    ru.death.chaosshard.trial.TrialManager.enter(player, tier, cond, theme);
                }
                break;
            }
            case "structure": {
                // /oh structure <имя> [поворот] — поставить постройку Ордена перед собой
                // /oh structure list — постройки рядом (500 блоков)
                EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) pl.world;
                if (args.length < 2 || "list".equals(args[1])) {
                    int n = 0;
                    for (ru.death.chaosshard.structure.OrderStructureData.Entry e : ru.death.chaosshard.structure.OrderStructureData.get(ws).entries) {
                        double d = Math.sqrt((e.x - pl.posX) * (e.x - pl.posX) + (e.z - pl.posZ) * (e.z - pl.posZ));
                        if (d < 500) {
                            String st = e.state == 0 ? "ждёт" : e.state == 1 ? "стоит" : "не встала";
                            sender.sendMessage(new net.minecraft.util.text.TextComponentString(e.name + " — " + e.x + " " + (e.state == 1 ? e.y : "~") + " " + e.z
                                    + " (" + (int) d + " бл., " + st + (e.name.equals("sanctum") ? (e.keeperDead ? ", Хранитель пал" : ", Хранитель жив") : "") + ")"));
                            n++;
                        }
                    }
                    if (n == 0) {
                        sender.sendMessage(new net.minecraft.util.text.TextComponentString("Построек Ордена рядом нет (500 блоков)"));
                    }
                    break;
                }
                String name = args[1];
                int rot = args.length >= 3 ? parseInt(args[2], 0, 3) : 0;
                ru.death.chaosshard.structure.bp.Blueprint bp;
                try {
                    bp = ru.death.chaosshard.structure.bp.Structures.build(name, new java.util.Random(ws.rand.nextLong()));
                } catch (IllegalArgumentException ex) {
                    throw new CommandException("Нет такой постройки: " + name + ". Есть: " + String.join(", ", ru.death.chaosshard.structure.bp.Structures.ALL));
                }
                int off = ru.death.chaosshard.structure.OrderStructures.reach(bp) + 4;
                net.minecraft.util.EnumFacing f = pl.getHorizontalFacing();
                BlockPos c = ru.death.chaosshard.structure.OrderStructures.ground(ws, (int) Math.floor(pl.posX) + f.getXOffset() * off,
                        (int) Math.floor(pl.posZ) + f.getZOffset() * off);
                ru.death.chaosshard.structure.OrderStructureData.Entry e = ru.death.chaosshard.structure.OrderStructures.placeNow(ws, name, c, rot, ws.rand.nextLong());
                sender.sendMessage(new net.minecraft.util.text.TextComponentString(e == null ? "Не встала" : name + " встала: " + c.getX() + " " + c.getY() + " " + c.getZ()));
                break;
            }
            case "garrot": {
                // /oh garrot — состояние; /oh garrot duel — Гаррот приходит сейчас; /oh garrot reset — снова непокорён
                net.minecraft.world.WorldServer ws = server.getWorld(ru.death.chaosshard.breach.Breach.luchezariyaDim());
                ru.death.chaosshard.garrot.GarrotData gd = ru.death.chaosshard.garrot.GarrotData.get(ws);
                if (args.length >= 2 && "duel".equals(args[1])) {
                    EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                    if (ru.death.chaosshard.garrot.GarrotDuel.of(pl) != null) {
                        throw new CommandException("Дуэль уже идёт");
                    }
                    ru.death.chaosshard.garrot.GarrotDuel.start(pl);
                    break;
                }
                if (args.length >= 2 && "reset".equals(args[1])) {
                    ru.death.chaosshard.garrot.GarrotDuel.clear(ws);
                    gd.submitted = false;
                    gd.lastDuel = -1000000;
                    gd.markDirty();
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Гаррот снова непокорён"));
                    break;
                }
                long left = ru.death.chaosshard.ChaosConfig.order.duelCooldown - (ws.getTotalWorldTime() - gd.lastDuel);
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("Гаррот: " + (gd.submitted ? "склонился" : "непокорён")
                        + ", дуэлей " + gd.duels + ", Опор пало " + ru.death.chaosshard.hero.HeroData.get(ws).fallenCount() + "/" + ru.death.chaosshard.ChaosConfig.order.duelAfterPillars
                        + (left > 0 && !gd.submitted ? ", вернётся через " + left / 20 + " с" : "")));
                break;
            }
            case "act5": {
                // /oh act5 start — все Опоры пали и Гаррот склонился (сразу к Акту 5); /oh act5 raid — набег Ордена на ближний Якорь
                net.minecraft.world.WorldServer ws = server.getWorld(ru.death.chaosshard.breach.Breach.luchezariyaDim());
                if (args.length >= 2 && "start".equals(args[1])) {
                    ru.death.chaosshard.hero.HeroData hd = ru.death.chaosshard.hero.HeroData.get(ws);
                    for (ru.death.chaosshard.hero.HeroData.Pillar p : hd.pillars) {
                        p.state = ru.death.chaosshard.hero.HeroData.FALLEN;
                    }
                    hd.markDirty();
                    ru.death.chaosshard.garrot.GarrotData gd = ru.death.chaosshard.garrot.GarrotData.get(ws);
                    gd.submitted = true;
                    gd.markDirty();
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Акт 5: все Опоры пали, Гаррот склонился — Орден наступает"));
                    break;
                }
                if (args.length >= 2 && "raid".equals(args[1])) {
                    EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                    BlockPos near = ru.death.chaosshard.anchor.AnchorData.get(pl.world).nearest(pl.getPosition(), null);
                    if (near == null || !(pl.world.getTileEntity(near) instanceof ru.death.chaosshard.anchor.TileAnchor)) {
                        throw new CommandException("Якоря рядом нет");
                    }
                    int lvl = ((ru.death.chaosshard.anchor.TileAnchor) pl.world.getTileEntity(near)).level();
                    ru.death.chaosshard.act5.OrderRaids.spawn((net.minecraft.world.WorldServer) pl.world, near, pl, Math.max(1, lvl), pl.world.rand.nextDouble() * Math.PI * 2);
                    break;
                }
                if (args.length >= 2 && "capital".equals(args[1])) {
                    // поставить столицу в 40 блоках перед игроком
                    EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                    net.minecraft.util.EnumFacing f = pl.getHorizontalFacing();
                    BlockPos c = ru.death.chaosshard.structure.OrderStructures.ground(pl.world, (int) pl.posX + f.getXOffset() * 40, (int) pl.posZ + f.getZOffset() * 40);
                    ru.death.chaosshard.structure.OrderStructureData.Entry e = ru.death.chaosshard.structure.OrderStructures.placeNow(
                            (net.minecraft.world.WorldServer) pl.world, "capital", c, 0, pl.world.rand.nextLong());
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString(e == null ? "Столица не встала" : "Столица встала: " + c.getX() + " " + c.getY() + " " + c.getZ()));
                    break;
                }
                if (args.length >= 2 && "open".equals(args[1])) {
                    ru.death.chaosshard.act5.Crown.open(ws);
                    break;
                }
                if (args.length >= 2 && "rift".equals(args[1])) {
                    EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                    ru.death.chaosshard.antechamber.ChaosTeleporter.toPos(pl, ru.death.chaosshard.act5.RiftDimension.id(), ru.death.chaosshard.act5.RiftDimension.arrival());
                    break;
                }
                if (args.length >= 2 && "reset".equals(args[1])) {
                    ru.death.chaosshard.act5.CapitalData cd = ru.death.chaosshard.act5.CapitalData.get(ws);
                    cd.state = ru.death.chaosshard.act5.CapitalData.NONE;
                    cd.introShown = false;
                    cd.kingSpawned = false;
                    cd.riftOpen = false;
                    cd.markDirty();
                    net.minecraft.world.WorldServer rw = server.getWorld(ru.death.chaosshard.act5.RiftDimension.id());
                    if (rw != null) {
                        ru.death.chaosshard.act5.Rift.forget(rw);
                    }
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Акт 5 сброшен: столица забыта, Разлом ждёт снова"));
                    break;
                }
                ru.death.chaosshard.act5.CapitalData cd = ru.death.chaosshard.act5.CapitalData.get(ws);
                String[] cs = {"нет", "указана", "стоит", "дворец открыт", "Король пал"};
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("Наступление Ордена: " + (ru.death.chaosshard.act5.OrderRaids.active(ws) ? "идёт" : "ещё нет")
                        + "; столица: " + cs[cd.state] + (cd.state > 0 ? " у " + cd.x + " " + cd.z : "") + "; маяков горит " + ru.death.chaosshard.act5.Crown.lit(ws)));
                break;
            }
            case "pillar": {
                // /oh pillar — состояние Опор; /oh pillar fall <герой> — считать павшей; /oh pillar reset — забыть все
                net.minecraft.world.WorldServer ws = server.getWorld(ru.death.chaosshard.breach.Breach.luchezariyaDim());
                ru.death.chaosshard.hero.HeroData hd = ru.death.chaosshard.hero.HeroData.get(ws);
                if (args.length >= 2 && "reset".equals(args[1])) {
                    for (ru.death.chaosshard.hero.HeroData.Pillar p : hd.pillars) {
                        p.state = ru.death.chaosshard.hero.HeroData.NONE;
                        p.entry = -1;
                        p.fallRing = -1;
                    }
                    hd.markDirty();
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Опоры забыты"));
                    break;
                }
                if (args.length >= 3 && "intro".equals(args[1])) {
                    // /oh pillar intro <герой> — показать сцену появления ещё раз (стоять у Опоры)
                    EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                    ru.death.chaosshard.hero.Lightbearer h = ru.death.chaosshard.hero.Lightbearer.valueOf(args[2].toUpperCase());
                    ru.death.chaosshard.hero.EntityHero hero = ru.death.chaosshard.hero.Pillars.heroOf(ws, h, hd.of(h));
                    if (hero == null || pl.world != ws) {
                        throw new CommandException("Светоч не найден рядом с его Опорой");
                    }
                    ru.death.chaosshard.hero.Pillars.intro(pl, hero);
                    break;
                }
                if (args.length >= 3 && "fall".equals(args[1])) {
                    ru.death.chaosshard.hero.Lightbearer h = ru.death.chaosshard.hero.Lightbearer.valueOf(args[2].toUpperCase());
                    hd.of(h).state = ru.death.chaosshard.hero.HeroData.FALLEN;
                    hd.markDirty();
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Опора " + h.id() + " считается павшей"));
                    break;
                }
                String[] st = {"нет", "указана", "стоит", "пала"};
                for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) {
                    ru.death.chaosshard.hero.HeroData.Pillar p = hd.of(h);
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString(h.id() + ": " + st[Math.max(0, Math.min(3, p.state))]
                            + (p.state != 0 ? " — " + p.x + " " + p.y + " " + p.z : "") + (h.ready() ? "" : " (герой ещё не готов)")));
                }
                break;
            }
            case "village": {
                if (args.length >= 2 && "entrance".equals(args[1])) {
                    // /oh village entrance — поставить деревню рядом со мной (как у входа в Лучезарию)
                    EntityPlayerMP pl = getCommandSenderAsPlayer(sender);
                    BlockPos v = ru.death.chaosshard.village.EntranceVillage.build((net.minecraft.world.WorldServer) pl.world, pl.getPosition(), pl.world.rand);
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString(v == null ? "Деревня не встала" : "Деревня: " + v.getX() + " " + v.getZ()));
                    break;
                }
                // /oh village [info|xp <n>|level <n>] — ближайший Знак покровителя
                World w = sender.getEntityWorld();
                ru.death.chaosshard.village.VillageData vd = ru.death.chaosshard.village.VillageData.get(w);
                BlockPos me = sender.getPosition();
                BlockPos best = null;
                for (BlockPos p : vd.sigils) {
                    if (best == null || p.distanceSq(me) < best.distanceSq(me)) {
                        best = p;
                    }
                }
                if (best == null || !(w.getTileEntity(best) instanceof ru.death.chaosshard.village.TilePatronSigil)) {
                    throw new CommandException("chaosshard.command.village.none");
                }
                ru.death.chaosshard.village.TilePatronSigil sg = (ru.death.chaosshard.village.TilePatronSigil) w.getTileEntity(best);
                String sub = args.length >= 2 ? args[1] : "info";
                if ("xp".equals(sub) && args.length >= 3) {
                    sg.addXp(parseInt(args[2], 1, 100000));
                } else if ("level".equals(sub)) {
                    int lvl = args.length >= 3 ? parseInt(args[2], 1, ru.death.chaosshard.village.TilePatronSigil.maxLevel())
                            : ru.death.chaosshard.village.TilePatronSigil.maxLevel();
                    sg.setXp(ru.death.chaosshard.ChaosConfig.order.sigilLevels[lvl - 1]);
                }
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("Знак " + best.getX() + " " + best.getY() + " " + best.getZ()
                        + ": уровень " + sg.level() + ", опыт " + sg.getXp() + (sg.isEvolved() ? ", Обитель Хаоса" : "")));
                break;
            }
            case "surge": {
                // /oh surge — Всплеск Хаоса на инструмент в руке прямо сейчас (снять: ещё раз)
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                if (!net.minecraftforge.fml.common.Loader.isModLoaded("tconstruct")) {
                    throw new CommandException("Tinkers' Construct не установлен");
                }
                ru.death.chaosshard.compat.tinkers.SurgeCommand.run(player, sender);
                break;
            }
            case "shrine": {
                // /oh shrine [list|place] — святыни Ордена
                String sub = args.length >= 2 ? args[1] : "list";
                World w = sender.getEntityWorld();
                if ("place".equals(sub)) {
                    BlockPos at = sender.getPosition();
                    ru.death.chaosshard.order.ShrineGen.build(w, at, w.rand);
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Святыня поставлена: " + at.getX() + " " + at.getY() + " " + at.getZ()));
                } else {
                    java.util.Set<BlockPos> all = ru.death.chaosshard.order.ShrineData.get(w).shrines;
                    sender.sendMessage(new net.minecraft.util.text.TextComponentString("Святынь в этом мире: " + all.size()
                            + ", осквернено: " + ru.death.chaosshard.order.ShrineData.get(w).desecrated));
                    BlockPos me = sender.getPosition();
                    all.stream().sorted(java.util.Comparator.comparingDouble(p -> p.distanceSq(me))).limit(8).forEach(p ->
                            sender.sendMessage(new net.minecraft.util.text.TextComponentString("  " + p.getX() + " " + p.getY() + " " + p.getZ()
                                    + " — " + (int) Math.sqrt(p.distanceSq(me)) + " бл.")));
                }
                break;
            }
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
            case "servants": {
                // где мои слуги, знает ли о них сервер, отправлены ли они мне и есть ли они у меня в игре
                int n = 0;
                for (net.minecraft.world.WorldServer w : server.worlds) {
                    for (net.minecraft.entity.Entity e : new java.util.ArrayList<>(w.loadedEntityList)) {
                        if (!ru.death.chaosshard.servant.Servants.isServantOf(e, player.getUniqueID())) {
                            continue;
                        }
                        n++;
                        net.minecraft.entity.EntityTrackerEntry en = ru.death.chaosshard.servant.Resync.entry(e);
                        boolean same = w == player.world;
                        String line = "§d" + e.getName() + "§7 мир " + w.provider.getDimension() + " " + e.getPosition().getX() + " " + e.getPosition().getY()
                                + " " + e.getPosition().getZ() + (e.isEntityAlive() ? "" : " §cмёртв§7")
                                + " | учёт: " + (en == null ? "§cнет§7" : "да")
                                + (same && en != null ? " | мне отправлен: " + (en.trackingPlayers.contains(player) ? "да" : "§cнет§7") : "")
                                + (same ? " | чанк виден: " + (w.getPlayerChunkMap().isPlayerWatchingChunk(player, e.chunkCoordX, e.chunkCoordZ) ? "да" : "§cнет§7") : "")
                                + (e.addedToChunk ? "" : " | §cвне чанка§7")
                                + (same ? " | в игре: " + ChaosShard.proxy.clientEntity(e.getEntityId()) : "");
                        sender.sendMessage(new net.minecraft.util.text.TextComponentString(line));
                        ChaosShard.LOG.info("[servants] {}", line.replaceAll("§.", ""));
                    }
                }
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("§7Слуг загружено: " + n + " (подробно — в latest.log)"));
                break;
            }
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
                        "chaosshard:breach_altar", "chaosshard:ritual_spire 4", "chaosshard:ritual_bowl", "chaosshard:chaos_chalk", "chaosshard:trial_key 2", "chaosshard:star_pump", "chaosshard:chaos_anchor 2", "chaosshard:patron_sigil", "chaosshard:anchor_seal_fury 2", "chaosshard:anchor_seal_reach 2", "chaosshard:anchor_seal_harvest", "chaosshard:anchor_seal_well 2", "chaosshard:anchor_seal_blood 2", "chaosshard:chaos_essence 16", "minecraft:dye 32 15", "chaosshard:breach_book", "chaosshard:world_shard 8", "chaosshard:veil_shard 8",
                        "astralsorcery:itemjournal", "astralsorcery:itemwand", "astralsorcery:itemhandtelescope",
                        "astralsorcery:blockaltar 1 0", "astralsorcery:blockwell", "astralsorcery:itemrockcrystalsimple 4",
                        "astralsorcery:itemcraftingcomponent 32 0", "astralsorcery:blockmarble 64 0",
                        // тестовые блоки-структуры
                        "chaosshard:seed_breach", "chaosshard:seed_heart", "chaosshard:seed_heart_auto",
                        "chaosshard:seed_anchor 2", "chaosshard:seed_island", "chaosshard:seed_shrine 2",
                        // Акт 3 и прочее для проверок
                        "chaosshard:chaos_essence 16", "chaosshard:patron_sigil 2", "chaosshard:heart_module_rich 4",
                        "chaosshard:heart_module_pulse 4", "chaosshard:chaos_anchor", "chaosshard:chaos_catch 4"
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
            return getListOfStringsMatchingLastWord(args, "nit", "tp", "sky", "grimoire", "debug", "breach", "heart", "quests", "selftest", "anchor", "shrine", "surge", "village", "trial", "beast", "servant", "autotest", "structure", "pillar", "garrot", "act5");
        }
        if (args.length == 2 && "act5".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "raid", "capital", "open", "rift", "reset");
        }
        if (args.length == 2 && "garrot".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "duel", "reset");
        }
        if (args.length == 2 && "pillar".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "fall", "intro", "reset");
        }
        if (args.length == 3 && "pillar".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "kael", "irena", "velmor", "lian", "elis");
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
        if (args.length == 2 && "anchor".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "list", "spread", "level", "raid", "warn");
        }
        if (args.length == 2 && "servant".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "husk", "bone", "executioner", "reaper", "lumberjack", "miner", "up");
        }
        if (args.length == 3 && "servant".equals(args[0]) && "up".equals(args[1])) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (ru.death.chaosshard.servant.ServantUpgrade u : ru.death.chaosshard.servant.ServantUpgrade.values()) {
                names.add(u.name().toLowerCase(java.util.Locale.ROOT));
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 2 && "structure".equals(args[0])) {
            java.util.List<String> names = new java.util.ArrayList<>(java.util.Arrays.asList(ru.death.chaosshard.structure.bp.Structures.ALL));
            names.add("list");
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 2 && "beast".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "hare", "cat", "tiger");
        }
        if (args.length == 3 && "beast".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "info", "xp", "asc", "return", "reset");
        }
        if (args.length == 2 && "trial".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "leave");
        }
        if (args.length == 2 && "village".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "info", "xp", "level", "entrance");
        }
        if (args.length == 2 && "shrine".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "list", "place");
        }
        if (args.length == 2 && "debug".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "report", "reset", "kit", "servants");
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
