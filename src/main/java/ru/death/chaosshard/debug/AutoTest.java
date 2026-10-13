package ru.death.chaosshard.debug;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.advancements.Advancement;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.garrot.GarrotRaids;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.servant.BeastData;
import ru.death.chaosshard.servant.Beasts;
import ru.death.chaosshard.servant.Resync;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.trial.TrialBlocks;
import ru.death.chaosshard.trial.TrialCondition;
import ru.death.chaosshard.trial.TrialData;
import ru.death.chaosshard.trial.TrialDimension;
import ru.death.chaosshard.trial.TrialLayout;
import ru.death.chaosshard.trial.TrialManager;
import ru.death.chaosshard.trial.TrialRoom;
import ru.death.chaosshard.trial.TrialTheme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * /oh autotest — сценарии, которые сами разыгрывают системы мода на сервере, без игрока:
 * свита, звери, Якорь и Скверна, набег, рудокоп, обломок со всеми комнатами и законами, деревня у входа.
 * Пишет chaosshard-autotest.txt. С -Dchaosshard.autotest=true запускается сам и гасит сервер по окончании (для CI).
 */
public final class AutoTest {

    public static final String FILE = "chaosshard-autotest.txt";
    public static AutoTest running;

    private final MinecraftServer server;
    private final boolean shutdown;
    private final List<Object[]> steps = new ArrayList<>();   // {имя, Runnable | BooleanSupplier, таймаут}
    private int idx, waited, sleep;
    private final StringBuilder out = new StringBuilder();
    private int pass, fail;
    private String section = "";
    private final List<String> failures = new ArrayList<>();

    // общее состояние сценариев
    private WorldServer ow, archive;
    private FakePlayer me, meA;
    private BlockPos base, anchorPos, chest;
    private final Map<Servants.Kind, EntityLiving> servants = new EnumMap<>(Servants.Kind.class);
    private EntityLiving miner;
    private TrialData.Trial trial, trial2;
    private int roomTicks;
    private long roomBegan;

    private AutoTest(MinecraftServer server, boolean shutdown) {
        this.server = server;
        this.shutdown = shutdown;
    }

    public static boolean start(MinecraftServer server, boolean shutdown) {
        if (running != null) {
            return false;
        }
        AutoTest a = new AutoTest(server, shutdown);
        a.plan();
        running = a;
        MinecraftForge.EVENT_BUS.register(a);
        a.line("Автотест «Осколка Хаоса» " + new java.util.Date());
        return true;
    }

    // ------------------------------------------------------------------ движок

    private void step(String name, Runnable r) {
        steps.add(new Object[]{name, r, 0});
    }

    /** Шаг, который ждёт условия (не дольше timeout тиков). */
    private void until(String name, BooleanSupplier cond, int timeout) {
        steps.add(new Object[]{name, cond, timeout});
    }

    private void sleep(int ticks) {
        steps.add(new Object[]{"…", null, ticks});
    }

    private void section(String s) {
        steps.add(new Object[]{"§" + s, null, 0});
    }

    private void line(String s) {
        out.append(s).append('\n');
        ChaosShard.LOG.info("[autotest] {}", s);
    }

    private void check(boolean ok, String what) {
        if (ok) {
            pass++;
            line("  [OK]   " + what);
        } else {
            fail++;
            failures.add(section + ": " + what);
            line("  [FAIL] " + what);
        }
    }

    /** [ожидаемо загрузятся, пропущено из-за отсутствующих модов] — по json-файлам в jar. */
    private static int[] expectedAdvancements() {
        int ok = 0, skip = 0;
        try {
            java.io.File f = Loader.instance().getIndexedModList().get("chaosshard").getSource();
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(f)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (!n.startsWith("assets/chaosshard/advancements/m/") || !n.endsWith(".json")) continue;
                    String txt;
                    try (java.io.InputStream in = z.getInputStream(e)) {
                        txt = new String(org.apache.commons.io.IOUtils.toByteArray(in), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"item\"\\s*:\\s*\"([a-z0-9_\\-.]+):").matcher(txt);
                    boolean missing = false;
                    while (m.find()) {
                        String mod = m.group(1);
                        if (!mod.equals("minecraft") && !Loader.isModLoaded(mod)) missing = true;
                    }
                    if (missing) skip++; else ok++;
                }
            }
        } catch (Exception e) {
            ChaosShard.LOG.warn("[autotest] не прочитал вехи из jar", e);
        }
        return new int[]{ok, skip};
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || running != this) {
            return;
        }
        // Подставные игроки держат чанки вокруг себя, как настоящие:
        // без этого измерение без игроков выгружает их каждый тик.
        for (EntityPlayerMP f : TestHooks.FAKES.values()) {
            ((WorldServer) f.world).getPlayerChunkMap().updateMovingPlayer(f);
        }
        if (sleep > 0) {
            sleep--;
            return;
        }
        if (idx >= steps.size()) {
            finish();
            return;
        }
        Object[] s = steps.get(idx);
        String name = (String) s[0];
        Object act = s[1];
        int limit = (Integer) s[2];
        try {
            if (name.startsWith("§")) {
                section = name.substring(1);
                line("");
                line("--- " + section + " ---");
                idx++;
            } else if (act == null) {
                sleep = limit;
                idx++;
            } else if (act instanceof Runnable) {
                ((Runnable) act).run();
                idx++;
            } else {
                if (((BooleanSupplier) act).getAsBoolean()) {
                    check(true, name + " (за " + waited + " тиков)");
                    waited = 0;
                    idx++;
                } else if (++waited > limit) {
                    check(false, name + " — не дождался за " + limit + " тиков");
                    waited = 0;
                    idx++;
                }
            }
        } catch (Throwable t) {
            check(false, name + " — исключение: " + t);
            ChaosShard.LOG.error("[autotest] {}", name, t);
            waited = 0;
            idx++;
        }
    }

    private void finish() {
        line("");
        line("Итого: пройдено " + pass + ", провалено " + fail);
        for (String f : failures) {
            line("  ✘ " + f);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(FILE)), StandardCharsets.UTF_8)) {
            w.write(out.toString());
        } catch (Exception e) {
            ChaosShard.LOG.error("[autotest] не записал отчёт", e);
        }
        ChaosShard.LOG.info("AUTOTEST DONE pass={} fail={}", pass, fail);
        for (EntityPlayerMP f : TestHooks.FAKES.values()) {
            WorldServer w = (WorldServer) f.world;
            w.getPlayerChunkMap().removePlayer(f);
            w.playerEntities.remove(f);
        }
        TestHooks.FAKES.clear();
        MinecraftForge.EVENT_BUS.unregister(this);
        running = null;
        if (shutdown) {
            server.initiateShutdown();
        }
    }

    // ------------------------------------------------------------------ подставной игрок

    private FakePlayer fake(WorldServer w, String name, UUID id) {
        FakePlayer p = new FakePlayer(w, new GameProfile(id, name));
        NetworkManager nm = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        new EmbeddedChannel(nm);
        new NetHandlerPlayServer(server, nm, p);
        TestHooks.FAKES.put(id, p);
        p.setPosition(w.getSpawnPoint().getX() + 0.5, 150, w.getSpawnPoint().getZ() + 0.5);
        p.managedPosX = p.posX;
        p.managedPosZ = p.posZ;
        w.playerEntities.add(p);
        w.getPlayerChunkMap().addPlayer(p);
        return p;
    }

    private void kill(EntityLivingBase e) {
        e.attackEntityFrom(DamageSource.causePlayerDamage(me), 100000F);
        if (e.isEntityAlive()) {
            e.setHealth(0);
            e.onDeath(DamageSource.causePlayerDamage(me));
            e.setDead();
        }
    }

    private List<EntityLivingBase> tagged(WorldServer w, AxisAlignedBB box, String tag) {
        return w.getEntitiesWithinAABB(EntityLivingBase.class, box, e -> e.isEntityAlive() && (e.getTags().contains(tag) || e.getEntityData().hasKey(tag)));
    }

    // ------------------------------------------------------------------ сценарии

    private final java.util.Map<String, ru.death.chaosshard.structure.OrderStructureData.Entry> structs = new java.util.HashMap<>();
    private ru.death.chaosshard.structure.OrderStructureData.Entry planned;

    private void plan() {
        section("Подготовка");
        step("площадка и подставной игрок", () -> {
            ow = server.getWorld(0);
            BlockPos sp = ow.getSpawnPoint();
            base = new BlockPos(sp.getX(), 150, sp.getZ());
            for (int x = -34; x <= 34; x++) {
                for (int z = -34; z <= 34; z++) {
                    for (int y = -4; y <= 0; y++) {
                        ow.setBlockState(base.add(x, y, z), y == 0 ? Blocks.GRASS.getDefaultState() : y == -1 ? Blocks.DIRT.getDefaultState()
                                : Blocks.STONE.getDefaultState(), 2);
                    }
                    for (int y = 1; y <= 8; y++) {
                        ow.setBlockToAir(base.add(x, y, z));
                    }
                }
            }
            me = fake(ow, "ОсколокТест", UUID.fromString("c4a05fa0-0000-4000-8000-000000000001"));
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            ru.death.chaosshard.thread.ThreadManager.setPoints(me, 3000);
            check(me.connection != null, "подставной игрок создан");
        });

        section("Реестр");
        step("существа, вехи", () -> {
            for (String id : new String[]{"servant_husk", "servant_bone", "servant_executioner", "servant_reaper", "servant_lumberjack",
                    "servant_miner", "servant_hare", "servant_cat", "servant_tiger"}) {
                check(ForgeRegistries.ENTITIES.containsKey(new ResourceLocation("chaosshard", id)), "сущность " + id);
            }
            int all = 0, noDisplay = 0;
            for (Advancement a : server.getAdvancementManager().getAdvancements()) {
                if (a.getId().getNamespace().equals("chaosshard") && a.getId().getPath().startsWith("m/")) {
                    all++;
                    if (a.getDisplay() == null) noDisplay++;
                }
            }
            int[] exp = expectedAdvancements();
            check(exp[0] > 0 && all == exp[0], "вех загружено: " + all + " из " + exp[0]
                    + (exp[1] > 0 ? " (ещё " + exp[1] + " со значком из модов, которых нет на тестовом сервере)" : ""));
            check(noDisplay == 0, "вехи без значка и названия: " + noDisplay);
        });

        section("Свита и звери");
        step("призыв всех видов", () -> {
            int i = 0;
            for (Servants.Kind k : Servants.Kind.values()) {
                EntityLiving m = Servants.summon(me, k, base.add(-8 + i * 2, 1, -6));
                servants.put(k, m);
                check(m != null, "призван: " + k);
                i++;
            }
        });
        sleep(40);
        step("живы, свои, на учёте", () -> {
            for (Map.Entry<Servants.Kind, EntityLiving> e : servants.entrySet()) {
                EntityLiving m = e.getValue();
                if (m == null) continue;
                check(m.isEntityAlive() && m.world.loadedEntityList.contains(m), e.getKey() + " жив и загружен");
                check(Servants.kindOf(m) == e.getKey(), e.getKey() + " распознан как свой вид");
                check(Resync.entry(m) != null, e.getKey() + " на учёте у трекера");
                ResourceLocation key = net.minecraft.entity.EntityList.getKey(m);
                check(key != null && key.getNamespace().equals("chaosshard"), e.getKey() + " — своё существо (" + key + ")");
            }
            int common = Servants.count(me);
            check(common == Servants.Kind.COMMON.length, "лимит считает только обычных слуг: " + common);
        });
        step("отойти — свита идёт следом", () -> me.setPosition(base.getX() + 20.5, base.getY() + 1, base.getZ() + 0.5));
        until("Хаск догнал хозяина", () -> servants.get(Servants.Kind.HUSK) != null && servants.get(Servants.Kind.HUSK).getDistance(me) < 8, 400);
        step("опыт и уровень зайца", () -> {
            Beasts.debug(me, BeastData.Beast.HARE, "xp", 400);
            BeastData.Entry e = Beasts.entry(ow, me.getUniqueID(), BeastData.Beast.HARE);
            check(e.known, "заяц знаком");
            check(e.level() >= 3, "заяц 3-го уровня после 400 опыта: " + e.level());
        });
        step("отпустить зайца и позвать из Гримуара", () -> {
            EntityLiving hare = servants.get(Servants.Kind.HARE);
            BeastData.Entry e = Beasts.entry(ow, me.getUniqueID(), BeastData.Beast.HARE);
            hare.setDead();
            e.entity = null;
            Beasts.recall(me, BeastData.Beast.HARE);
        });
        sleep(5);
        step("заяц вернулся с уровнем", () -> {
            List<ru.death.chaosshard.servant.EntityChaosBeast> l = ow.getEntitiesWithinAABB(ru.death.chaosshard.servant.EntityChaosBeast.class,
                    me.getEntityBoundingBox().grow(8), b -> b.isEntityAlive() && b.beast() == BeastData.Beast.HARE);
            check(!l.isEmpty(), "заяц рядом после зова");
            check(Beasts.entry(ow, me.getUniqueID(), BeastData.Beast.HARE).level() >= 3, "уровень сохранился");
        });

        section("Якорь и Скверна");
        step("поставить Якорь 3-го уровня и дать Скверне расползтись", () -> {
            anchorPos = base.add(10, 1, 10);
            ow.setBlockState(anchorPos, ModBlocks.CHAOS_ANCHOR.getDefaultState(), 3);
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            check(a != null, "Якорь поставлен");
            a.setOwner(me);
            a.setCorrupted(ChaosConfig.anchor.levelThresholds[2]);
            int done = a.forceSpread(800);
            check(done > 30, "Скверна расползлась: " + done + " блоков");
            check(a.level() >= 3, "уровень Якоря: " + a.level());
        });
        sleep(60);
        step("существа на осквернённой земле на месте и на учёте", () -> {
            int alive = 0, tracked = 0;
            for (EntityLiving m : servants.values()) {
                if (m != null && m.isEntityAlive()) {
                    alive++;
                    if (Resync.entry(m) != null) tracked++;
                }
            }
            check(alive >= servants.size() - 1 && tracked == alive, "живых " + alive + ", на учёте " + tracked);
        });
        step("гарнизон не занимает лимит", () -> {
            EntityLiving bone = servants.get(Servants.Kind.BONE);
            int before = Servants.count(me);
            bone.getEntityData().setLong(Servants.TAG_ANCHOR, anchorPos.toLong());
            check(Servants.count(me) == before - 1, "в гарнизоне — не в лимите (" + before + " → " + Servants.count(me) + ")");
        });

        section("Набег Гаррота");
        step("набег 5-го уровня", () -> {
            GarrotRaids.spawn(ow, anchorPos, me, 5, 0.0);
        });
        sleep(20);
        final int[] corrupted = new int[1];
        step("отряд на месте, капитан пал", () -> {
            AxisAlignedBB box = new AxisAlignedBB(anchorPos).grow(80);
            List<EntityLivingBase> army = tagged(ow, box, GarrotRaids.TAG_ARMY);
            check(army.size() >= 5, "в отряде " + army.size());
            int tracked = 0;
            for (EntityLivingBase e : army) if (Resync.entry(e) != null) tracked++;
            check(tracked == army.size(), "весь отряд на учёте у трекера");
            List<EntityLivingBase> cap = tagged(ow, box, GarrotRaids.TAG_CAPTAIN);
            check(!cap.isEmpty(), "капитан есть");
            corrupted[0] = ((TileAnchor) ow.getTileEntity(anchorPos)).getCorrupted();
            for (EntityLivingBase c : cap) kill(c);
        });
        sleep(20);
        step("отряд ушёл, Якорь окреп", () -> {
            check(tagged(ow, new AxisAlignedBB(anchorPos).grow(80), GarrotRaids.TAG_ARMY).isEmpty(), "отряд ушёл");
            check(((TileAnchor) ow.getTileEntity(anchorPos)).getCorrupted() > corrupted[0], "Якорь окреп");
        });

        section("Рудокоп");
        step("рудокоп у сундука на настоящей земле", () -> {
            BlockPos ground = ow.getTopSolidOrLiquidBlock(ow.getSpawnPoint().add(-40, 0, -40));
            chest = ground;
            ow.setBlockState(chest, Blocks.CHEST.getDefaultState(), 3);
            miner = Servants.summon(me, Servants.Kind.MINER, chest.add(1, 0, 0));
            miner.getEntityData().setLong(Servants.TAG_CHEST, chest.toLong());
            miner.getEntityData().setInteger(Servants.TAG_DIR, 0);
            miner.getEntityData().removeTag(Servants.TAG_GUARD);
            ((net.minecraft.entity.EntityCreature) miner).setHomePosAndDistance(chest, 32);
            me.setPosition(chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5);
        });
        sleep(2400);
        step("ствол 3×3 открытый, ступени вдоль стен", () -> {
            EnumFacing d = EnumFacing.SOUTH;
            BlockPos c = chest.offset(d, 3);
            int stairs = 0, solid = 0, depth = 0;
            for (int y = chest.getY() - 2; y > chest.getY() - 14; y--) {
                boolean any = false;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        IBlockState s = ow.getBlockState(new BlockPos(c.getX() + dx, y, c.getZ() + dz));
                        if (s.getBlock() == Blocks.STONE_STAIRS) stairs++;
                        else if (!s.getBlock().isAir(s, ow, c) && s.getBlock() != Blocks.TORCH) solid++;
                        if (s.getBlock().isAir(s, ow, c)) any = true;
                    }
                }
                if (any) depth++;
            }
            IBlockState center = ow.getBlockState(new BlockPos(c.getX(), chest.getY() - 4, c.getZ()));
            check(depth >= 8, "ствол прокопан на " + depth + " уровней");
            check(stairs >= 6, "ступеней: " + stairs);
            check(center.getBlock().isAir(center, ow, c), "в центре колодец, а не столб");
            check(solid <= stairs / 2 + 4, "лишнего камня в стволе: " + solid);
            line("  (сундук " + chest + ", центр ствола " + c + ")");
        });

        section("Обломок: все комнаты и законы");
        step("построить обломок со всеми комнатами", () -> {
            net.minecraftforge.common.DimensionManager.keepDimensionLoaded(TrialDimension.id(), true);
            archive = server.getWorld(TrialDimension.id());
            meA = fake(archive, "ОсколокТестА", UUID.fromString("c4a05fa0-0000-4000-8000-000000000002"));
            TrialData data = TrialData.get(archive);
            trial = new TrialData.Trial();
            trial.id = data.nextId++;
            trial.owner = meA.getUniqueID();
            trial.tier = 2;
            trial.theme = TrialTheme.RUINS.ordinal();
            trial.conditions = (1 << TrialCondition.ECHO.ordinal()) | (1 << TrialCondition.MEMORY.ordinal())
                    | (1 << TrialCondition.PRICE.ordinal()) | (1 << TrialCondition.DEBT.ordinal()) | (1 << TrialCondition.CLOCK.ordinal());
            TrialRoom[] rooms = {TrialRoom.ENTRY, TrialRoom.ARENA, TrialRoom.RUNES, TrialRoom.SEQUENCE, TrialRoom.HOLD, TrialRoom.TRAPS,
                    TrialRoom.PARKOUR, TrialRoom.OFFERING, TrialRoom.GUARD, TrialRoom.TREASURE};
            trial.rooms = new int[rooms.length];
            for (int i = 0; i < rooms.length; i++) trial.rooms[i] = rooms[i].ordinal();
            trial.returnDim = 0;
            trial.returnPos = base;
            data.trials.put(trial.id, trial);
            TrialLayout.build(archive, trial);
            int seals = 0;
            int[][] b = trial.bounds();
            for (int i = 1; i < b.length - 1; i++) {
                if (archive.getBlockState(trial.origin().add(0, 1, b[i][1] + 1)).getBlock() == ModBlocks.TRIAL_SEAL) seals++;
            }
            check(seals == b.length - 2, "барьеры между комнатами: " + seals);
            BlockPos ex = trial.origin().add(0, 0, b[b.length - 1][0] + 3);
            check(archive.getBlockState(ex).getBlock() == ModBlocks.TRIAL_EXIT
                    && archive.getBlockState(ex.up()).getBlock() == ModBlocks.TRIAL_EXIT, "Разрыв в два блока");
            meA.setPosition(trial.origin().getX() + 0.5, trial.origin().getY(), trial.origin().getZ() + 2.5);
            roomBegan = archive.getTotalWorldTime();
        });
        for (int r = 0; r < 10; r++) {
            final int room = r;
            until("комната " + room + " пройдена", () -> drive(room), room == 4 ? 900 : 700);
        }
        step("законы обломка", () -> {
            check(trial.fast > 0, "Мир часов: комнат в срок " + trial.fast);
            meA.setHealth(10);
            meA.heal(4);
            check(meA.getHealth() <= 10.01F, "Мир цены: здоровье само не растёт");
        });

        section("Обломок: Охотник и тишина");
        step("малый обломок", () -> {
            TrialData data = TrialData.get(archive);
            trial2 = new TrialData.Trial();
            trial2.id = data.nextId++;
            trial2.owner = meA.getUniqueID();
            trial2.tier = 1;
            trial2.theme = TrialTheme.FORGE.ordinal();
            trial2.conditions = (1 << TrialCondition.HUNTER.ordinal()) | (1 << TrialCondition.SILENCE.ordinal());
            trial2.rooms = new int[]{TrialRoom.ENTRY.ordinal(), TrialRoom.ARENA.ordinal(), TrialRoom.GUARD.ordinal(), TrialRoom.TREASURE.ordinal()};
            data.trials.put(trial2.id, trial2);
            TrialLayout.build(archive, trial2);
            meA.setPosition(trial2.origin().getX() + 0.5, trial2.origin().getY(), trial2.origin().getZ() + 2.5);
        });
        until("Охотник пришёл и идёт за мной", () -> {
            TrialManager.tick(meA, archive.getTotalWorldTime() % 10 == 0);
            if (trial2.hunter == null) return false;
            Entity h = archive.getEntityFromUuid(trial2.hunter);
            return h != null && h.isEntityAlive();
        }, 500);
        step("в арену; тишина прячет дальних тварей", () -> {
            int[][] b = trial2.bounds();
            meA.setPosition(trial2.origin().getX() + 0.5, trial2.origin().getY(), trial2.origin().getZ() + b[1][0] + 1.5);
        });
        until("дальние твари невидимы", () -> {
            TrialManager.tick(meA, archive.getTotalWorldTime() % 10 == 0);
            List<EntityLivingBase> mobs = archive.getEntitiesWithinAABB(EntityLivingBase.class, meA.getEntityBoundingBox().grow(40),
                    e -> e.isEntityAlive() && e.getEntityData().getInteger(TrialManager.TAG) == trial2.id && !e.getEntityData().getBoolean(TrialManager.TAG_HUNTER)
                            && e.getDistanceSq(meA) > 49);
            return !mobs.isEmpty() && mobs.stream().allMatch(e -> e.isPotionActive(net.minecraft.init.MobEffects.INVISIBILITY));
        }, 200);

        section("Облики обломков");
        step("каждый облик строится", () -> {
            TrialData data = TrialData.get(archive);
            for (TrialTheme th : TrialTheme.values()) {
                TrialData.Trial t = new TrialData.Trial();
                t.id = data.nextId++;
                t.tier = 3;
                t.theme = th.ordinal();
                t.rooms = new int[TrialRoom.values().length];
                for (int i = 0; i < t.rooms.length; i++) t.rooms[i] = i;
                data.trials.put(t.id, t);
                TrialLayout.build(archive, t);
                check(archive.getBlockState(t.origin().add(1, -1, 1)).getBlock() != Blocks.AIR, "облик " + th + " построен");
            }
        });

        section("Деревня у входа в Лучезарию");
        step("деревня встаёт у точки", () -> {
            BlockPos at = ow.getSpawnPoint().add(400, 0, 0);
            BlockPos v = ru.death.chaosshard.village.EntranceVillage.build(ow, at, new java.util.Random(7));
            check(v != null, "деревня поставлена: " + v);
            if (v != null) {
                int doors = 0;
                for (BlockPos p : BlockPos.getAllInBoxMutable(v.add(-48, -20, -48), v.add(48, 20, 48))) {
                    if (ow.getBlockState(p).getBlock() == Blocks.OAK_DOOR || ow.getBlockState(p).getBlock() == Blocks.SPRUCE_DOOR
                            || ow.getBlockState(p).getBlock() == Blocks.ACACIA_DOOR) doors++;
                }
                check(doors >= 4, "дверей в деревне: " + doors / 2);
            }
        });
        sleep(20);
        step("жители", () -> {
            BlockPos at = ow.getSpawnPoint().add(400, 0, 0);
            int vill = ow.getEntitiesWithinAABB(EntityVillager.class, new AxisAlignedBB(at).grow(160, 120, 160)).size();
            check(vill > 0, "жителей рядом: " + vill);
        });

        section("Постройки Ордена в Лучезарии");
        step("все пять встают, блоки разобраны, сундуки с добычей", () -> {
            ru.death.chaosshard.structure.BlueprintPlacer.FAILED.clear();
            structs.clear();
            String[] names = ru.death.chaosshard.structure.bp.Structures.ALL;
            for (int i = 0; i < names.length; i++) {
                int x = ow.getSpawnPoint().getX() - 300 + i * 70, z = ow.getSpawnPoint().getZ() + 300;
                BlockPos g = ru.death.chaosshard.structure.OrderStructures.ground(ow, x, z);
                long seed = 1000 + i;
                ru.death.chaosshard.structure.OrderStructureData.Entry e = ru.death.chaosshard.structure.OrderStructures.placeNow(ow, names[i], g, i % 4, seed);
                check(e != null && e.state == ru.death.chaosshard.structure.OrderStructureData.PLACED, names[i] + " встала у " + g);
                if (e == null) continue;
                structs.put(names[i], e);
                ru.death.chaosshard.structure.bp.Blueprint bp = ru.death.chaosshard.structure.bp.Structures.build(names[i], new java.util.Random(seed));
                int bad = 0;
                for (java.util.Map.Entry<Long, String> be : bp.blocks.entrySet()) {
                    long k = be.getKey();
                    BlockPos p = ru.death.chaosshard.structure.BlueprintPlacer.at(e.origin(), ru.death.chaosshard.structure.bp.Blueprint.kx(k),
                            ru.death.chaosshard.structure.bp.Blueprint.ky(k), ru.death.chaosshard.structure.bp.Blueprint.kz(k), e.rot);
                    if (ow.getBlockState(p).getBlock() != ru.death.chaosshard.structure.BlueprintPlacer.parse(be.getValue()).getBlock()) bad++;
                }
                check(bad <= bp.blocks.size() / 100, names[i] + ": блоков не на месте " + bad + " из " + bp.blocks.size());
                int chests = 0, loot = 0;
                for (ru.death.chaosshard.structure.bp.Blueprint.Mark c : bp.chests) {
                    net.minecraft.tileentity.TileEntity te = ow.getTileEntity(ru.death.chaosshard.structure.BlueprintPlacer.at(e.origin(), c.x, c.y, c.z, e.rot));
                    if (te instanceof net.minecraft.tileentity.TileEntityChest) {
                        chests++;
                        if (((net.minecraft.tileentity.TileEntityChest) te).getLootTable() != null) loot++;
                    }
                }
                check(chests == bp.chests.size() && loot == chests, names[i] + ": сундуков " + chests + "/" + bp.chests.size() + ", с добычей " + loot);
                if (ow.getLootTableManager() != null) {
                    for (ru.death.chaosshard.structure.bp.Blueprint.Mark c : bp.chests) {
                        net.minecraft.world.storage.loot.LootTable lt = ow.getLootTableManager().getLootTableFromLocation(
                                new ResourceLocation("chaosshard", "structures/" + c.kind));
                        check(lt != net.minecraft.world.storage.loot.LootTable.EMPTY_LOOT_TABLE, "таблица добычи structures/" + c.kind + " загружена");
                    }
                }
            }
            check(ru.death.chaosshard.structure.BlueprintPlacer.FAILED.isEmpty(), "все строки блоков разобраны " + ru.death.chaosshard.structure.BlueprintPlacer.FAILED);
        });
        sleep(10);
        step("стража на местах", () -> {
            for (String n : new String[]{"dome_tower", "sanctum"}) {
                ru.death.chaosshard.structure.OrderStructureData.Entry e = structs.get(n);
                if (e == null) continue;
                List<ru.death.chaosshard.structure.EntityOrderKnight> ks = ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                        new AxisAlignedBB(e.origin()).grow(20, 40, 20));
                long keepers = ks.stream().filter(k -> k.variant() == ru.death.chaosshard.structure.EntityOrderKnight.KEEPER).count();
                long caps = ks.stream().filter(k -> k.variant() == ru.death.chaosshard.structure.EntityOrderKnight.CAPTAIN).count();
                if (n.equals("dome_tower")) {
                    check(ks.size() == 5 && caps == 1, "башня: стражей " + ks.size() + ", капитанов " + caps);
                } else {
                    check(ks.size() == 5 && keepers == 1, "святилище: стражей " + ks.size() + ", Хранителей " + keepers);
                    check(ks.stream().allMatch(k -> k.getMaxHealth() >= 30), "у стражи настоящее здоровье");
                }
            }
        });
        step("тайник святилища заперт, светочи горят", () -> {
            ru.death.chaosshard.structure.OrderStructureData.Entry e = structs.get("sanctum");
            if (e == null) { check(false, "святилища нет"); return; }
            check(e.beams.size() == 2, "светочей " + e.beams.size());
            boolean lit = e.beams.stream().allMatch(p -> ow.getTileEntity(p) instanceof ru.death.chaosshard.structure.TileOrderBeacon
                    && ((ru.death.chaosshard.structure.TileOrderBeacon) ow.getTileEntity(p)).lit);
            check(lit, "светочи горят");
            BlockPos chest = ru.death.chaosshard.structure.BlueprintPlacer.at(e.origin(), 0, 3, 0, e.rot);
            check(ow.getBlockState(chest).getBlock() == Blocks.CHEST, "тайник на помосте");
            PlayerInteractEvent.RightClickBlock ev = new PlayerInteractEvent.RightClickBlock(me, EnumHand.MAIN_HAND, chest, EnumFacing.UP, new Vec3d(0.5, 1, 0.5));
            MinecraftForge.EVENT_BUS.post(ev);
            check(ev.isCanceled(), "пока Хранитель жив — тайник не открыть");
        });
        step("Хранитель пал — светочи гаснут, тайник открыт", () -> {
            ru.death.chaosshard.structure.OrderStructureData.Entry e = structs.get("sanctum");
            if (e == null) return;
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(e.origin()).grow(20, 40, 20))) {
                if (k.variant() == ru.death.chaosshard.structure.EntityOrderKnight.KEEPER) kill(k);
            }
            check(e.keeperDead, "Хранитель отмечен павшим");
            boolean off = e.beams.stream().allMatch(p -> ow.getTileEntity(p) instanceof ru.death.chaosshard.structure.TileOrderBeacon
                    && !((ru.death.chaosshard.structure.TileOrderBeacon) ow.getTileEntity(p)).lit);
            check(off, "светочи погасли");
            BlockPos chest = ru.death.chaosshard.structure.BlueprintPlacer.at(e.origin(), 0, 3, 0, e.rot);
            PlayerInteractEvent.RightClickBlock ev = new PlayerInteractEvent.RightClickBlock(me, EnumHand.MAIN_HAND, chest, EnumFacing.UP, new Vec3d(0.5, 1, 0.5));
            MinecraftForge.EVENT_BUS.post(ev);
            check(!ev.isCanceled(), "после Хранителя тайник открывается");
        });
        step("отложенная постройка: план → встаёт, когда чанки готовы", () -> {
            int x = ow.getSpawnPoint().getX() + 600, z = ow.getSpawnPoint().getZ() - 600;
            for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++) {
                for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
                    ow.getChunkProvider().provideChunk(cx, cz);
                }
            }
            planned = ru.death.chaosshard.structure.OrderStructures.plan(ru.death.chaosshard.structure.OrderStructureData.get(ow), "pavilion", x, z, 1, 77L);
        });
        until("запланированная постройка решена (встала или отклонена по рельефу)",
                () -> planned != null && planned.state != ru.death.chaosshard.structure.OrderStructureData.PLANNED, 200);
        step("итог отложенной", () -> line("    отложенная pavilion: " + (planned.state == 1 ? "встала на " + planned.y : "отклонена (рельеф/вода)")));

        section("Самотест");
        step("самотест", () -> {
            ru.death.chaosshard.command.SelfTest st = ru.death.chaosshard.command.SelfTest.run(server, me);
            line("  самотест: " + st.percent() + "%, ошибок " + st.failures() + " (подробно — chaosshard-selftest.txt)");
            try {
                st.write();
            } catch (Exception ignored) {
                // не страшно
            }
        });
    }

    /** Провести подставного игрока через комнату обломка. true — комната пройдена. */
    private boolean drive(int room) {
        TrialData.Trial t = trial;
        long now = archive.getTotalWorldTime();
        if (t.room > room) {
            int[][] b = t.bounds();
            boolean open = room >= b.length - 1
                    || archive.getBlockState(t.origin().add(0, 1, b[room][1] + 1)).getBlock() != ModBlocks.TRIAL_SEAL;
            line("    " + t.roomType(room) + ": пройдена за " + (now - roomBegan) + " тиков, барьер " + (open ? "погас" : "НЕ погас"));
            roomBegan = now;
            roomTicks = 0;
            return open;
        }
        roomTicks++;
        int[] b = t.bounds()[room];
        BlockPos o = t.origin();
        TrialRoom type = t.roomType(room);
        double x = o.getX() + 0.5, y = o.getY(), z = o.getZ() + b[0] + 1.5;
        switch (type) {
            case HOLD:
                z = o.getZ() + (b[0] + b[1]) / 2.0 + 0.5;
                break;
            case TRAPS:
                z = roomTicks < 20 ? z : o.getZ() + b[1] - 0.5;
                break;
            case PARKOUR:
                z = roomTicks < 20 ? z : o.getZ() + b[1] - 0.5;
                break;
            case TREASURE:
                z = o.getZ() + b[0] + 1.5;
                break;
            default:
                break;
        }
        meA.setPosition(x, y, z);
        meA.onGround = true;
        TrialManager.tick(meA, now % 10 == 0);
        if (t.roomState != 1) {
            return false;
        }
        switch (type) {
            case ARENA: case GUARD: {
                List<EntityLivingBase> mobs = archive.getEntitiesWithinAABB(EntityLivingBase.class,
                        new AxisAlignedBB(o.add(-12, -10, b[0] - 6), o.add(12, 10, b[1] + 6)),
                        e -> e.isEntityAlive() && e.getEntityData().getInteger(TrialManager.TAG) == t.id && !e.getEntityData().getBoolean(TrialManager.TAG_HUNTER));
                if (type == TrialRoom.ARENA && roomTicks == 15) {
                    // Мир долга: открыть сундук-засаду
                    BlockPos c = o.add(t.roomType(room).half - 1, 0, b[0] + 1);
                    int before = mobs.size();
                    MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(meA, EnumHand.MAIN_HAND, c, EnumFacing.UP, new Vec3d(0.5, 1, 0.5)));
                    int after = archive.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(o.add(-12, -10, b[0] - 6), o.add(12, 10, b[1] + 6)),
                            e -> e.isEntityAlive() && e.getEntityData().getInteger(TrialManager.TAG) == t.id).size();
                    check(after > before, "Мир долга: сундук-засада (" + before + " → " + after + ")");
                    // Мир памяти: пройденная плитка меняет вид
                    BlockPos tile = new BlockPos(x, y - 1, z);
                    meA.setPosition(x, y, z);
                    TrialManager.tick(meA, false);
                    check(archive.getBlockState(tile) == t.theme().trodden(), "Мир памяти: плитка под ногами отмечена");
                    return false;
                }
                if (type == TrialRoom.ARENA && roomTicks == 20 && !mobs.isEmpty()) {
                    // Мир эха: убить одного и отойти от места смерти
                    kill(mobs.get(0));
                    return false;
                }
                if (type == TrialRoom.ARENA && roomTicks == 100) {
                    boolean echo = !archive.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(o.add(-12, -10, b[0] - 6), o.add(12, 10, b[1] + 6)),
                            e -> e.isEntityAlive() && e.getEntityData().getBoolean(TrialManager.TAG_RISEN)).isEmpty();
                    check(echo, "Мир эха: павший встал тенью");
                }
                if (roomTicks > 105 || type == TrialRoom.GUARD) {
                    for (EntityLivingBase e : mobs) kill(e);
                }
                return false;
            }
            case RUNES:
                if (roomTicks == 10) {
                    for (BlockPos p : TrialLayout.runePositions(t, room)) TrialManager.onRune(archive, p, meA);
                }
                return false;
            case SEQUENCE:
                if (t.showStep >= t.seq.length * 2 && t.seqPos == 0) {
                    BlockPos[] p = TrialLayout.sequencePositions(t, room);
                    for (int i : t.seq.clone()) TrialManager.onRune(archive, p[i], meA);
                }
                return false;
            case OFFERING:
                if (roomTicks == 10) {
                    meA.setHeldItem(EnumHand.MAIN_HAND, TrialManager.offeringSample(t, room));
                    TrialManager.onRune(archive, o.add(0, 0, (b[0] + b[1]) / 2), meA);
                }
                return false;
            default:
                return false;
        }
    }
}
