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
    private final int[] bloodXp = {-1};
    private final int[] mainXp = {-1};
    private final int[] forgeE = {0};
    private final int[] xpBefore = {0};

    private TileAnchor specT(BlockPos p) {
        TileAnchor t = p == null ? null : (TileAnchor) ow.getTileEntity(p);
        if (t == null) check(false, "Якоря специализаций нет");
        return t;
    }

    /** Сменить путь тестового Якоря (уровень — заданный). */
    private TileAnchor respec(BlockPos p, ru.death.chaosshard.anchor.AnchorSpec s, int lvl) {
        TileAnchor t = specT(p);
        if (t == null) return null;
        t.resetSpec();
        t.setCorrupted(ChaosConfig.anchor.levelThresholds[lvl - 1]);
        boolean ok = t.chooseSpec(s);
        check(ok && t.getSpec() == s, "путь: " + s + ", уровень " + t.level());
        return ok ? t : null;
    }
    private BlockPos base, anchorPos, chest, genPos, capPos;
    private final Map<Servants.Kind, EntityLiving> servants = new EnumMap<>(Servants.Kind.class);
    private EntityLiving miner;
    private TrialData.Trial trial, trial2;
    private int roomTicks;
    /** Тики с начала испытания комнаты (roomState == 1). */
    private int liveTicks;
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
            // ближние 5×5 чанков — сразу, а не в очередь: сущности тикают, только если вокруг них ±32 блока загружено
            int cx = net.minecraft.util.math.MathHelper.floor(f.posX) >> 4, cz = net.minecraft.util.math.MathHelper.floor(f.posZ) >> 4;
            net.minecraft.world.gen.ChunkProviderServer cp = ((WorldServer) f.world).getChunkProvider();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (!cp.chunkExists(cx + dx, cz + dz)) {
                        cp.provideChunk(cx + dx, cz + dz);
                    }
                }
            }
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
    private BlockPos bowlPos, spherePos, pullChest, altarPos, moonPos, shrinePos;
    private UUID lordId;
    private ru.death.chaosshard.structure.OrderStructureData.Entry monastery;
    private ru.death.chaosshard.hero.EntityHero irena;
    private float irenaHp;
    private ru.death.chaosshard.hero.EntityHero velmor;
    private BlockPos sealHere;
    private ru.death.chaosshard.hero.EntityHero lian;
    private UUID lostHero;
    private int challengeNit, challengeCorrupt;

    /** Подобрать с земли рядом сферу нужного ритуала (true — была). */
    private boolean takeSphere(BlockPos at, ru.death.chaosshard.ritual.Ritual r) {
        boolean found = false;
        for (net.minecraft.entity.item.EntityItem ei : ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(at).grow(3),
                x -> x.isEntityAlive() && x.getItem().getItem() == ru.death.chaosshard.init.ModItems.RITUAL_SPHERE)) {
            if (ei.getItem().getMetadata() == r.sphereIndex()) found = true;
            ei.setDead();
        }
        boolean offeringsLeft = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(at).grow(3),
                net.minecraft.entity.Entity::isEntityAlive).isEmpty();
        return found && !offeringsLeft;
    }

    /** Разбить сферу по блоку at (сфера в руке подставного игрока); true — разбилась. */
    private boolean useSphere(ru.death.chaosshard.ritual.Ritual r, BlockPos at) {
        me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.ritual.ItemRitualSphere.of(r, 0));
        return me.getHeldItemMainhand().getItem().onItemUse(me, ow, at, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F)
                == net.minecraft.util.EnumActionResult.SUCCESS && me.getHeldItemMainhand().isEmpty();
    }
    private net.minecraft.entity.monster.EntityZombie sightZombie;

    /** Бросить предметы на Алтарь (как игрок, клавишей Q). */
    private void drop(BlockPos altar, net.minecraft.item.ItemStack... stacks) {
        for (net.minecraft.item.ItemStack st : stacks) {
            net.minecraft.entity.item.EntityItem ei = new net.minecraft.entity.item.EntityItem(ow, altar.getX() + 0.5, altar.getY() + 1.1, altar.getZ() + 0.5, st);
            ei.motionX = ei.motionY = ei.motionZ = 0;
            ei.setPickupDelay(1000);
            ow.spawnEntity(ei);
        }
    }

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
            // как при установке игроком: Якорь попадает в список Якорей мира
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            ad.anchors.add(anchorPos);
            ad.markDirty();
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

        section("Сеть Якорей");
        final BlockPos[] farA = new BlockPos[1];
        step("дальний Якорь — в той же сети", () -> {
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            if (a == null) { check(false, "Якоря нет"); return; }
            BlockPos far = ow.getHeight(anchorPos.add(320, 0, 0));
            ow.setBlockState(far.down(), net.minecraft.init.Blocks.STONE.getDefaultState(), 3);
            ow.setBlockState(far, ModBlocks.CHAOS_ANCHOR.getDefaultState(), 3);
            TileAnchor b = (TileAnchor) ow.getTileEntity(far);
            check(b != null, "дальний Якорь поставлен");
            if (b == null) return;
            b.setOwner(me);
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            ad.anchors.add(far);
            ad.markDirty();
            b.syncRec();
            a.syncRec();
            farA[0] = far;
            List<TileAnchor> c = ru.death.chaosshard.anchor.AnchorClusters.cluster(ow, a);
            check(c.contains(b), "в сети и далёкий Якорь (320 блоков): " + c.size());
            int size = ru.death.chaosshard.anchor.AnchorNet.size(ow, me.getUniqueID());
            check(size >= 2, "сеть по сводкам: " + size);
            double e = TileAnchor.energyMultiplier(ow, anchorPos);
            check(Math.abs(e - (ru.death.chaosshard.anchor.AnchorNet.landMultiplier(size) + a.seals(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.WELL))) < 1e-6,
                    "энергия на земле ×" + e + " при " + size + " Якорях");
            check(ru.death.chaosshard.anchor.AnchorNet.landMultiplier(20) == ChaosConfig.anchor.networkEnergyMax, "предел ×5");
            check(Math.abs(ru.death.chaosshard.anchor.AnchorNet.outsideMultiplier(7) - 3.0) < 1e-6, "вне земли — половина: ×3 при ×5");
        });
        step("перелив: излишек 5-го уровня уходит растущим", () -> {
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            TileAnchor b = farA[0] == null ? null : (TileAnchor) ow.getTileEntity(farA[0]);
            if (a == null || b == null) { check(false, "нет Якорей"); return; }
            mainXp[0] = a.getCorrupted();
            a.setCorrupted(TileAnchor.maxXp());
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            int before = ru.death.chaosshard.anchor.AnchorNet.reserve(ow, me.getUniqueID());
            for (BlockPos p : ru.death.chaosshard.anchor.AnchorNet.members(ow, me.getUniqueID())) before += ad.rec(p).inbox;
            a.addXp(4000, false);
            int after = ru.death.chaosshard.anchor.AnchorNet.reserve(ow, me.getUniqueID());
            for (BlockPos p : ru.death.chaosshard.anchor.AnchorNet.members(ow, me.getUniqueID())) after += ad.rec(p).inbox;
            check(a.getCorrupted() == TileAnchor.maxXp(), "у вершины опыт не растёт выше " + TileAnchor.maxXp());
            check(after - before == 2000, "в сеть ушла половина излишка: " + (after - before));
            check(ad.rec(farA[0]).inbox > 0, "дальнему досталось: " + ad.rec(farA[0]).inbox);
        });
        sleep(45);
        step("дальний Якорь вырос переливом, но край не взят", () -> {
            TileAnchor b = farA[0] == null ? null : (TileAnchor) ow.getTileEntity(farA[0]);
            if (b == null) { check(false, "нет Якоря"); return; }
            check(b.level() >= 3, "уровень после перелива: " + b.level() + " (опыт " + b.getCorrupted() + ")");
            check(!b.isCaptured() && b.getLand() < 300, "край не захвачен: осквернено " + b.getLand());
        });
        step("кровь в Якорь: бак принимает только кровь", () -> {
            TileAnchor b = farA[0] == null ? null : (TileAnchor) ow.getTileEntity(farA[0]);
            if (b == null) { check(false, "нет Якоря"); return; }
            net.minecraftforge.fluids.Fluid blood = null;
            for (String n : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().keySet()) {
                if (n.contains("blood")) { blood = net.minecraftforge.fluids.FluidRegistry.getFluid(n); break; }
            }
            net.minecraftforge.fluids.capability.IFluidHandler h = b.getCapability(
                    net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.DOWN);
            check(h != null, "у Якоря есть бак для труб");
            if (h == null) return;
            check(h.fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000), true) == 0, "воду не берёт");
            if (blood == null) { line("  [--]   крови в сборке нет — переработка не проверена"); return; }
            check(h.fill(new net.minecraftforge.fluids.FluidStack(blood, 2000), true) == 2000, "кровь (" + blood.getName() + ") принята");
            check(h.drain(500, true) == null, "из бака не выкачать");
            bloodXp[0] = b.getCorrupted();
        });
        sleep(80);
        step("кровь переплавилась в опыт", () -> {
            TileAnchor b = farA[0] == null ? null : (TileAnchor) ow.getTileEntity(farA[0]);
            if (b == null || bloodXp[0] < 0) { line("  [--]   пропущено"); return; }
            check(b.blood.getFluidAmount() < 2000, "кровь убывает: " + b.blood.getFluidAmount());
            check(b.getCorrupted() > bloodXp[0], "опыт вырос: " + bloodXp[0] + " → " + b.getCorrupted());
        });
        step("окно Якоря: печать с руки в место, сведения о сети", () -> {
            TileAnchor b = farA[0] == null ? null : (TileAnchor) ow.getTileEntity(farA[0]);
            if (b == null) { check(false, "нет Якоря"); return; }
            me.inventory.clear();
            me.inventory.setInventorySlotContents(0, new net.minecraft.item.ItemStack(
                    ru.death.chaosshard.init.ModItems.SEALS.get(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY), 2));
            ru.death.chaosshard.anchor.ContainerAnchor ca = new ru.death.chaosshard.anchor.ContainerAnchor(me, b);
            int idx = -1;
            for (int i = 0; i < ca.inventorySlots.size(); i++) {
                net.minecraft.inventory.Slot sl = ca.inventorySlots.get(i);
                if (sl.inventory == me.inventory && sl.getSlotIndex() == 0) idx = i;
            }
            int was = b.seals(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY);
            ca.transferStackInSlot(me, idx);
            check(b.seals(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY) > was, "печать встала: Ярость " + b.seals(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY));
            check(b.getSeals().size() <= b.sealSlots(), "печатей не больше открытых мест");
            net.minecraft.nbt.NBTTagCompound v = ru.death.chaosshard.anchor.ContainerAnchor.view(b, me);
            check(v.getTagList("list", 10).tagCount() >= 2 && v.getBoolean("net"), "вкладка «Сеть»: Якорей " + v.getTagList("list", 10).tagCount());
            me.inventory.clear();
        });
        step("за пределами Лучезарии Якорь не встаёт", () -> {
            WorldServer nether = server.getWorld(-1);
            if (nether == null) { line("  [--]   Незера нет"); return; }
            BlockPos np = new BlockPos(0, 100, 0);
            nether.setBlockState(np.down(), net.minecraft.init.Blocks.NETHERRACK.getDefaultState(), 3);
            nether.setBlockState(np, ModBlocks.CHAOS_ANCHOR.getDefaultState(), 3);
            ModBlocks.CHAOS_ANCHOR.onBlockPlacedBy(nether, np, nether.getBlockState(np), me,
                    new net.minecraft.item.ItemStack(ModBlocks.CHAOS_ANCHOR));
            check(nether.isAirBlock(np), "в Незере Якорь снят");
            me.inventory.clear();
        });
        step("Скверная флора", () -> {
            if (ru.death.chaosshard.anchor.Corruption.floraBlocks().isEmpty()) { line("  [--]   растений флоры нет в сборке"); return; }
            int planted = 0;
            java.util.Random r = new java.util.Random(7);
            for (int i = 0; i < 200 && planted == 0; i++) {
                BlockPos at = ow.getHeight(anchorPos.add(r.nextInt(31) - 15, 0, r.nextInt(31) - 15));
                if (ru.death.chaosshard.anchor.Corruption.isCorrupted(ow.getBlockState(at.down()))
                        && ru.death.chaosshard.anchor.Corruption.plant(ow, at, r)) planted++;
            }
            check(planted > 0, "на осквернённой земле выросло растение");
        });
        step("дальний Якорь убран — сводка ушла", () -> {
            if (farA[0] == null) return;
            ow.setBlockToAir(farA[0]);
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            if (a != null && mainXp[0] >= 0) a.setCorrupted(mainXp[0]);
            ru.death.chaosshard.anchor.AnchorData.get(ow).reserve.remove(me.getUniqueID());
            check(ru.death.chaosshard.anchor.AnchorData.get(ow).rec(farA[0]) == null, "сводки нет");
        });

        section("Специализации Якоря");
        final BlockPos[] specA = new BlockPos[1];
        final net.minecraft.entity.EntityLiving[] specMob = new net.minecraft.entity.EntityLiving[1];
        step("путь не выбрать до 3-го уровня, на 3-м — можно", () -> {
            BlockPos at = ow.getHeight(anchorPos.add(-64, 0, 40));   // в спавн-чанках: там твари живут и без игрока
            ow.setBlockState(at.down(), net.minecraft.init.Blocks.STONE.getDefaultState(), 3);
            ow.setBlockState(at, ModBlocks.CHAOS_ANCHOR.getDefaultState(), 3);
            TileAnchor t = (TileAnchor) ow.getTileEntity(at);
            if (t == null) { check(false, "Якорь не встал"); return; }
            t.setOwner(me);
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            ad.anchors.add(at);
            ad.markDirty();
            t.syncRec();
            specA[0] = at;
            check(!t.chooseSpec(ru.death.chaosshard.anchor.AnchorSpec.BASTION), "на 1-м уровне путь закрыт");
            t.setCorrupted(ChaosConfig.anchor.levelThresholds[2]);
            check(t.chooseSpec(ru.death.chaosshard.anchor.AnchorSpec.BASTION), "на 3-м уровне выбран Бастион");
            check(ad.rec(at).spec == ru.death.chaosshard.anchor.AnchorSpec.BASTION.ordinal(), "сеть знает о пути");
            check(!t.chooseSpec(ru.death.chaosshard.anchor.AnchorSpec.ROOTS), "второй путь без Перекройки не взять");
            check(Math.abs(ru.death.chaosshard.anchor.AnchorNet.stack(3) - 1.75) < 1e-9, "одинаковые складываются по убывающей: 1 + ½ + ¼");
        });
        sleep(25);
        step("Бастион: гарнизон сети +2", () -> {
            TileAnchor t = specT(specA[0]);
            if (t == null) return;
            List<TileAnchor> c = ru.death.chaosshard.anchor.AnchorClusters.cluster(ow, t);
            int lim = ru.death.chaosshard.anchor.AnchorClusters.garrisonLimit(c);
            int lv = ru.death.chaosshard.anchor.AnchorNet.levelSum(ow, me.getUniqueID());
            int base = 0;
            for (BlockPos p : ru.death.chaosshard.anchor.AnchorNet.members(ow, me.getUniqueID()))
                base += Math.max(1, ru.death.chaosshard.anchor.AnchorData.get(ow).rec(p).level);
            check(lv == base + 2, "предел гарнизона " + lv + " = уровни " + base + " + 2");
            check(t.siegeLimit() > 1200 + 900 * (t.level() - 1), "осада держится дольше: " + t.siegeLimit());
        });
        step("Перекройка: путь сброшен, −25% опыта", () -> {
            TileAnchor t = specT(specA[0]);
            if (t == null) return;
            int xp = t.getCorrupted();
            check(t.resetSpec() && t.getSpec() == null, "путь сброшен");
            check(t.getCorrupted() == (int) (xp * 0.75), "опыт " + xp + " → " + t.getCorrupted());
        });
        step("Жила: добыча в хранилище, рудная жила", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.VEIN, 5);
            if (t == null) return;
            int put = ru.death.chaosshard.anchor.AnchorSpecs.veinLoot(t);
            int items = 0;
            for (int i = 0; i < 9; i++) items += t.storage.getStackInSlot(i).getCount();
            check(put > 0 && items > 0, "в хранилище " + items + " предметов");
            BlockPos v = ru.death.chaosshard.anchor.AnchorSpecs.vein(t);
            check(v != null && !ow.isAirBlock(v), "жила вышла: " + v);
        });
        step("Горн: энергия и кабель", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.FORGE, 3);
            if (t == null) return;
            check(t.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, EnumFacing.UP), "у Горна есть энергия для кабелей");
            check(ru.death.chaosshard.anchor.AnchorSpecs.forgeRate(t) > 0, "выработка " + ru.death.chaosshard.anchor.AnchorSpecs.forgeRate(t) + " RF/т");
            forgeE[0] = t.forgeEnergy;
        });
        sleep(20);
        step("Горн копит энергию", () -> {
            TileAnchor t = specT(specA[0]);
            if (t == null) return;
            check(t.forgeEnergy > forgeE[0], "запас " + forgeE[0] + " → " + t.forgeEnergy);
            net.minecraftforge.energy.IEnergyStorage es = t.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, EnumFacing.UP);
            check(es != null && es.extractEnergy(100, false) == 100, "кабель забирает энергию");
        });
        step("Жатва: моб на поле, добыча и опыт — в Якорь", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.HARVEST, 3);
            if (t == null) return;
            net.minecraft.entity.EntityLiving m = null;
            for (int i = 0; i < 20 && m == null; i++) m = ru.death.chaosshard.anchor.AnchorSpecs.spawnField(t, ow);
            check(m != null, "на поле пришёл моб: " + (m == null ? "—" : m.getName()));
            if (m == null) return;
            check(m.getEntityData().getLong(ru.death.chaosshard.anchor.AnchorSpecs.TAG_FIELD) == specA[0].toLong(), "моб с поля");
            int xp = t.fieldXp;
            m.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(me), 1000F);
            check(!m.isEntityAlive(), "моб убит");
            specMob[0] = m;
            xpBefore[0] = xp;
        });
        sleep(25);
        step("Жатва: опыт копится в колонне", () -> {
            TileAnchor t = specT(specA[0]);
            if (t == null || specMob[0] == null) return;
            check(t.fieldXp > xpBefore[0], "опыт поля " + xpBefore[0] + " → " + t.fieldXp);
            int had = me.experienceTotal;
            ru.death.chaosshard.anchor.AnchorSpecs.takeXp(me, t);
            check(t.fieldXp == 0 && me.experienceTotal > had, "опыт забран");
        });
        step("Гнездо: твари у колонны вне лимита свиты", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.NEST, 3);
            if (t == null) return;
            int before = Servants.count(me);
            me.setPosition(specA[0].getX() + 3.5, specA[0].getY(), specA[0].getZ() + 0.5);
            ru.death.chaosshard.anchor.AnchorSpecs.nest(t);
            List<net.minecraft.entity.EntityLiving> g = ru.death.chaosshard.anchor.AnchorSpecs.nestOf(t, false);
            check(!g.isEmpty(), "тварь Гнезда пришла");
            check(!ru.death.chaosshard.anchor.AnchorSpecs.nestOf(t, true).isEmpty(), "сопровождающая пришла");
            check(Servants.count(me) == before, "в лимит свиты не идут");
            t.resetSpec();
            check(ru.death.chaosshard.anchor.AnchorSpecs.nestOf(t, false).isEmpty(), "Гнездо распущено — твари ушли");
        });
        step("Пульс: заклинания дешевле, энергии больше на своей земле", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.PULSE, 3);
            if (t == null) return;
            me.setPosition(specA[0].getX() + 3.5, specA[0].getY(), specA[0].getZ() + 0.5);
            check(ru.death.chaosshard.anchor.AnchorSpecs.PULSE.cost(me, ru.death.chaosshard.spell.Spell.values()[0]) < 1.0, "цена ниже");
            check(ru.death.chaosshard.anchor.AnchorSpecs.PULSE.maxEnergy(me) == 20, "максимум +20");
            check(TileAnchor.energyMultiplier(ow, specA[0].add(3, 0, 0)) > ru.death.chaosshard.anchor.AnchorNet.landMultiplier(
                    ru.death.chaosshard.anchor.AnchorNet.size(ow, me.getUniqueID())), "энергия на своей земле ×1,5");
        });
        step("Тропа: перенос к Якорю сети", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.PATH, 3);
            if (t == null) return;
            ru.death.chaosshard.spell.ChaosEnergy.set(me, 100);
            boolean ok = ru.death.chaosshard.anchor.AnchorSpecs.travel(me, t, anchorPos);
            check(ok && me.getDistanceSq(anchorPos) < 64, "перенесён к Якорю: " + (int) Math.sqrt(me.getDistanceSq(anchorPos)) + " блоков");
            check(ru.death.chaosshard.spell.ChaosEnergy.get(me) <= 70.5F, "потрачено 30 энергии");
        });
        step("Цитадель: тварь на земле — под молнией; спавн закрыт на 5-м", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.CITADEL, 5);
            if (t == null) return;
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            BlockPos zp = ow.getHeight(specA[0].add(5, 0, 0));
            z.setLocationAndAngles(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5, 0, 0);
            z.enablePersistence();
            ow.spawnEntity(z);
            specMob[0] = z;
            net.minecraft.entity.monster.EntityZombie probe = new net.minecraft.entity.monster.EntityZombie(ow);
            probe.setPosition(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5);
            net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn ev = new net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn(
                    probe, ow, (float) probe.posX, (float) probe.posY, (float) probe.posZ, false);
            MinecraftForge.EVENT_BUS.post(ev);
            check(ev.getResult() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY, "спавн тварей запрещён");
        });
        sleep(70);
        step("Цитадель ударила", () -> {
            net.minecraft.entity.EntityLiving z = specMob[0];
            check(z != null && (!z.isEntityAlive() || z.getHealth() < z.getMaxHealth()), "зомби ранен: " + (z == null ? -1 : z.getHealth()));
            if (z != null) z.setDead();
        });
        step("Дозор: вошедший враг подсвечен, карта сети", () -> {
            TileAnchor t = respec(specA[0], ru.death.chaosshard.anchor.AnchorSpec.WATCH, 5);
            if (t == null) return;
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            BlockPos zp = ow.getHeight(specA[0].add(6, 0, 2));
            z.setLocationAndAngles(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5, 0, 0);
            z.enablePersistence();
            ow.spawnEntity(z);
            specMob[0] = z;
            net.minecraft.nbt.NBTTagCompound v = ru.death.chaosshard.anchor.ContainerAnchor.view(t, me);
            check(v.getBoolean("watch") && v.hasKey("dots"), "карта сети в окне");
            net.minecraft.nbt.NBTTagCompound sc = ru.death.chaosshard.anchor.AnchorView.scry(ow, anchorPos, me.getUniqueID());
            check(sc.getByteArray("c").length == 96 * 96, "взгляд: вид сверху 96×96");
        });
        sleep(45);
        step("Дозор подсветил", () -> {
            net.minecraft.entity.EntityLiving z = specMob[0];
            check(z != null && z.isPotionActive(net.minecraft.init.MobEffects.GLOWING), "зомби светится");
            if (z != null) z.setDead();
        });
        step("Якорь специализаций убран", () -> {
            if (specA[0] != null) ow.setBlockToAir(specA[0]);
            check(ru.death.chaosshard.anchor.AnchorData.get(ow).rec(specA[0]) == null, "сводки нет");
        });

        section("Альфа, знаки, заимствование");
        final BlockPos[] alA = new BlockPos[1];
        final java.util.List<BlockPos> fakes = new java.util.ArrayList<>();
        final net.minecraft.entity.EntityLiving[] sMob = new net.minecraft.entity.EntityLiving[2];
        step("Альфа: нужна сеть из 10, площадка 3×3", () -> {
            BlockPos at = ow.getHeight(anchorPos.add(-40, 0, -60));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                ow.setBlockState(at.add(dx, -1, dz), net.minecraft.init.Blocks.STONE.getDefaultState(), 3);
                for (int dy = 0; dy < 14; dy++) ow.setBlockToAir(at.add(dx, dy, dz));
            }
            ow.setBlockState(at, ModBlocks.CHAOS_ANCHOR.getDefaultState(), 3);
            TileAnchor t = (TileAnchor) ow.getTileEntity(at);
            if (t == null) { check(false, "Якорь не встал"); return; }
            t.setOwner(me);
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            ad.anchors.add(at);
            t.setCorrupted(TileAnchor.maxXp());
            t.syncRec();
            alA[0] = at;
            check("chaosshard.anchor.alpha.count".equals(t.evolve()), "без 10 Якорей Альфа не встаёт");
            int need = 10 - ru.death.chaosshard.anchor.AnchorNet.size(ow, me.getUniqueID());
            for (int i = 0; i < need; i++) {
                BlockPos f = new BlockPos(100000 + i * 200, 64, 100000);
                ad.anchors.add(f);
                ru.death.chaosshard.anchor.AnchorData.Rec r = ad.recOrNew(f);
                r.owner = me.getUniqueID();
                r.level = 3;
                fakes.add(f);
            }
            ad.markDirty();
            String err = t.evolve();
            check(err == null && t.isAlpha(), "Альфа встала: " + err);
            check(ow.getBlockState(at.add(1, 0, 1)).getBlock() == ModBlocks.ANCHOR_RIM, "основание 3×3");
            check(t.alphaLevel() == 1 && t.getSpec() == null, "1-й уровень Альфы, пути нет");
        });
        step("Альфа: свой опыт на четверть, кровь и перелив — полностью", () -> {
            TileAnchor t = (TileAnchor) ow.getTileEntity(alA[0]);
            if (t == null) { check(false, "нет Альфы"); return; }
            t.addXp(400, false);
            check(t.getAlphaXp() == 100, "свой опыт 400 → " + t.getAlphaXp());
            t.addXp(1000, false, true);
            check(t.getAlphaXp() == 1100 && t.alphaLevel() == 2, "кровь 1000 → " + t.getAlphaXp() + ", ур. " + t.alphaLevel());
            check(t.level() == 5 && t.radiusCap() >= 60, "уровень Якоря и радиус остались");
            t.refreshNet();
            check(t.sealSlots() == 6, "мест печатей: " + t.sealSlots());
            check(t.columnHeight() >= 5, "колонна выше: " + t.columnHeight());
            TileAnchor main = (TileAnchor) ow.getTileEntity(anchorPos);
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            int before = ad.rec(alA[0]).inbox;
            int xp = main.getCorrupted();
            main.setCorrupted(TileAnchor.maxXp());
            main.addXp(1200, false);
            main.setCorrupted(xp);
            check(ad.rec(alA[0]).inbox - before > 1200 * 0.5 / 12, "Альфе — двойная доля перелива: +" + (ad.rec(alA[0]).inbox - before));
        });
        step("заимствование: место по числу Альф, предел по донорам", () -> {
            TileAnchor main = (TileAnchor) ow.getTileEntity(anchorPos);
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            ad.rec(fakes.get(0)).spec = ru.death.chaosshard.anchor.AnchorSpec.CITADEL.ordinal();
            main.refreshNet();
            check(main.borrowSlots() == 1, "мест заимствования: " + main.borrowSlots());
            check(main.setBorrow(0, ru.death.chaosshard.anchor.AnchorFx.CITADEL_STRIKE), "взята молния Цитадели");
            check(main.hasFx(ru.death.chaosshard.anchor.AnchorFx.CITADEL_STRIKE), "эффект работает");
            check(!main.setBorrow(0, ru.death.chaosshard.anchor.AnchorFx.VEIN_LOOT), "без донора Жилы не взять");
            ad.rec(fakes.get(0)).spec = -1;
            main.refreshNet();
            check(!main.hasFx(ru.death.chaosshard.anchor.AnchorFx.CITADEL_STRIKE), "донор пропал — место пусто");
        });
        step("печати: силовые по убывающей, знак — один на Якорь", () -> {
            TileAnchor t = (TileAnchor) ow.getTileEntity(alA[0]);
            if (t == null) return;
            t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY);
            t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY);
            check(Math.abs(t.power(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.FURY) - 1.5) < 1e-6, "две Ярости = 1,5");
            check(t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.THORNS), "Шипы встали");
            check(!t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.THORNS), "вторые Шипы не встают");
            t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.RAIN);
            t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.WHISPER);
            t.syncRec();
            TileAnchor main = (TileAnchor) ow.getTileEntity(anchorPos);
            main.refreshNet();
            double p = main.power(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.THORNS);
            check(p > 0 && p < 1, "Шипы Альфы на чужом Якоре сети: " + p);
        });
        step("знаки бьют: Шипы, Кровавый дождь, Шёпот", () -> {
            TileAnchor t = (TileAnchor) ow.getTileEntity(alA[0]);
            if (t == null) return;
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            BlockPos zp = ow.getHeight(alA[0].add(6, 0, 0));
            z.setLocationAndAngles(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5, 0, 0);
            z.enablePersistence();
            ow.spawnEntity(z);
            net.minecraft.entity.monster.EntitySkeleton sk = new net.minecraft.entity.monster.EntitySkeleton(ow);
            sk.setLocationAndAngles(zp.getX() + 2.5, zp.getY(), zp.getZ() + 0.5, 0, 0);
            sk.enablePersistence();
            ow.spawnEntity(sk);
            sMob[0] = z;
            sMob[1] = sk;
            ru.death.chaosshard.anchor.AnchorSigns.Mods m = ru.death.chaosshard.anchor.AnchorSigns.Mods.of(t);
            int hit = ru.death.chaosshard.anchor.AnchorSigns.thorns(t, m, 1, false, 0);
            check(hit >= 1 && z.getHealth() < z.getMaxHealth() && z.isPotionActive(net.minecraft.init.MobEffects.SLOWNESS), "Шипы: урон и замедление");
            check(z.isPotionActive(net.minecraft.init.MobEffects.WITHER), "Шипы + Кровавый дождь: кровотечение");
            sk.hurtResistantTime = 0;
            float hp = sk.getHealth();
            net.minecraft.entity.EntityLiving serv = Servants.spawn(me, Servants.Kind.HUSK, ow.getHeight(alA[0].add(3, 0, 2)));
            if (serv != null) serv.setHealth(5);
            int blood = t.blood.getFluidAmount();
            ru.death.chaosshard.anchor.AnchorSigns.rain(t, m, 1, false);
            check(sk.getHealth() < hp, "дождь ранит чужих");
            check(serv != null && serv.getHealth() > 5, "дождь лечит своих: " + (serv == null ? -1 : serv.getHealth()));
            if (serv != null) serv.setDead();
            check(!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("evilcraftblood") || t.blood.getFluidAmount() > blood, "в баке прибыло крови");
            ru.death.chaosshard.anchor.AnchorSigns.madden(z, m);
            check(ru.death.chaosshard.anchor.AnchorSigns.mad(z) && z.getAttackTarget() == sk, "Шёпот: зомби обезумел и бросился на скелета");
            me.setHealth(me.getMaxHealth());
        });
        step("Искажение: свои не разбиваются, снаряд летит назад", () -> {
            TileAnchor t = (TileAnchor) ow.getTileEntity(alA[0]);
            if (t == null) return;
            t.addSeal(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.DISTORT);
            me.setPosition(alA[0].getX() + 3.5, alA[0].getY(), alA[0].getZ() + 0.5);
            net.minecraftforge.event.entity.living.LivingFallEvent fe = new net.minecraftforge.event.entity.living.LivingFallEvent(me, 10, 1);
            MinecraftForge.EVENT_BUS.post(fe);
            check(fe.getDamageMultiplier() == 0, "урона от падения нет");
            net.minecraft.entity.projectile.EntityTippedArrow ar = new net.minecraft.entity.projectile.EntityTippedArrow(ow, sMob[1]);
            ar.setPosition(alA[0].getX() + 4.5, alA[0].getY() + 2, alA[0].getZ() + 0.5);
            ar.motionX = 1.0;
            ar.motionY = 0;
            ar.motionZ = 0;
            ow.spawnEntity(ar);
            ru.death.chaosshard.anchor.AnchorSigns.projectiles(t, ru.death.chaosshard.anchor.AnchorSigns.Mods.of(t), 1, true, false);
            check(ar.motionX < 0, "Рикошет: стрела летит обратно (" + ar.motionX + ")");
            ar.setDead();
        });
        step("Альфа и подставные Якоря убраны", () -> {
            for (net.minecraft.entity.EntityLiving e : sMob) if (e != null) e.setDead();
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(ow);
            for (BlockPos f : fakes) { ad.anchors.remove(f); ad.recs.remove(f.toLong()); }
            ad.markDirty();
            if (alA[0] != null) ow.setBlockToAir(alA[0]);
            check(ow.isAirBlock(alA[0].add(1, 0, 1)), "основание снято");
            TileAnchor main = (TileAnchor) ow.getTileEntity(anchorPos);
            if (main != null) { main.setBorrow(0, null); main.refreshNet(); }
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

        section("Обломок: Свет и тень, Зеркало, Обвал, Страж с повадками");
        step("обломок с новыми комнатами", () -> {
            TrialData data = TrialData.get(archive);
            TrialData.Trial t3 = new TrialData.Trial();
            t3.id = data.nextId++;
            t3.owner = meA.getUniqueID();
            t3.tier = 2;
            t3.theme = TrialTheme.RUINS.ordinal();
            t3.conditions = (1 << TrialCondition.ECHO.ordinal()) | (1 << TrialCondition.PRICE.ordinal()) | (1 << TrialCondition.CLOCK.ordinal());
            TrialRoom[] rooms = {TrialRoom.ENTRY, TrialRoom.LIGHTS, TrialRoom.MIRROR, TrialRoom.COLLAPSE, TrialRoom.GUARD, TrialRoom.TREASURE};
            t3.rooms = new int[rooms.length];
            for (int i = 0; i < rooms.length; i++) t3.rooms[i] = rooms[i].ordinal();
            t3.returnDim = 0;
            t3.returnPos = base;
            data.trials.put(t3.id, t3);
            TrialLayout.build(archive, t3);
            trial = t3;
            meA.setPosition(t3.origin().getX() + 0.5, t3.origin().getY(), t3.origin().getZ() + 2.5);
            roomBegan = archive.getTotalWorldTime();
            roomTicks = 0;
            liveTicks = 0;
        });
        for (int r = 0; r < 6; r++) {
            final int room = r;
            until("новая комната " + room + " пройдена", () -> drive(room), 700);
        }

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

        section("Сферы ритуалов");
        step("Рост в Чаше даёт сферу", () -> {
            bowlPos = base.add(-14, 1, -14);
            ow.setBlockState(bowlPos, ModBlocks.RITUAL_BOWL.getDefaultState(), 3);
            for (int[] rr : ru.death.chaosshard.ritual.TileRitualBowl.RING) {
                ow.setBlockState(bowlPos.add(rr[0], 0, rr[1]), ModBlocks.CHALK.getDefaultState(), 3);
            }
            ru.death.chaosshard.ritual.TileRitualBowl bowl = (ru.death.chaosshard.ritual.TileRitualBowl) ow.getTileEntity(bowlPos);
            check(bowl != null && bowl.chalkCount() == 8, "Чаша и круг мела");
            if (bowl == null) return;
            bowl.insert(me, new net.minecraft.item.ItemStack(net.minecraft.init.Items.DYE, 8, 15), true);
            bowl.insert(me, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 1), true);
            bowl.tryStart(me);
            List<net.minecraft.entity.item.EntityItem> items = ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,
                    new AxisAlignedBB(bowlPos).grow(3), ei -> ei.getItem().getItem() == ru.death.chaosshard.init.ModItems.RITUAL_SPHERE);
            check(items.size() == 1 && items.get(0).getItem().getMetadata() == ru.death.chaosshard.ritual.Ritual.GROWTH.sphereIndex(),
                    "над Чашей появилась Сфера роста");
            check(bowl.getActive() == null, "Чаша не держит ритуал у себя");
            check(bowl.getItems().stream().allMatch(net.minecraft.item.ItemStack::isEmpty), "подношения потрачены");
            items.forEach(net.minecraft.entity.Entity::setDead);
        });
        step("Сфера роста ставится в поле", () -> {
            BlockPos field = base.add(-14, 0, 2);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    ow.setBlockState(field.add(dx, 0, dz), Blocks.FARMLAND.getDefaultState().withProperty(net.minecraft.block.BlockFarmland.MOISTURE, 7), 2);
                    ow.setBlockState(field.add(dx, 1, dz), Blocks.WHEAT.getDefaultState(), 2);
                }
            }
            me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.ritual.ItemRitualSphere.of(ru.death.chaosshard.ritual.Ritual.GROWTH, 0));
            net.minecraft.util.EnumActionResult res = me.getHeldItemMainhand().getItem().onItemUse(me, ow, field, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
            spherePos = field.up();
            check(res == net.minecraft.util.EnumActionResult.SUCCESS && ow.getBlockState(spherePos).getBlock() == ModBlocks.RITUAL_SPHERE, "сфера встала над полем");
            check(me.getHeldItemMainhand().isEmpty(), "сфера ушла из рук");
        });
        sleep(600);
        step("пшеница вокруг сферы подросла", () -> {
            BlockPos field = base.add(-14, 0, 2);
            int grown = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    IBlockState st = ow.getBlockState(field.add(dx, 1, dz));
                    if (st.getBlock() == Blocks.WHEAT && st.getValue(net.minecraft.block.BlockCrops.AGE) > 0) grown++;
                }
            }
            check(grown > 0, "подросло ростков: " + grown + " из 48");
        });
        step("сферу можно забрать с остатком времени", () -> {
            me.setSneaking(true);
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            ModBlocks.RITUAL_SPHERE.onBlockActivated(ow, spherePos, ow.getBlockState(spherePos), me, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
            me.setSneaking(false);
            check(ow.isAirBlock(spherePos), "сфера снята");
            net.minecraft.item.ItemStack got = net.minecraft.item.ItemStack.EMPTY;
            for (int i = 0; i < me.inventory.getSizeInventory(); i++) {
                if (me.inventory.getStackInSlot(i).getItem() == ru.death.chaosshard.init.ModItems.RITUAL_SPHERE) got = me.inventory.getStackInSlot(i);
            }
            int left = got.isEmpty() ? -1 : ru.death.chaosshard.ritual.ItemRitualSphere.ticks(got);
            check(left > 0 && left < ru.death.chaosshard.ritual.Ritual.GROWTH.duration, "в руки вернулась сфера с остатком " + left + " тиков");
            me.inventory.clear();
        });
        step("Сфера оберега гонит тварей", () -> {
            BlockPos at = base.add(14, 0, -14);
            me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.ritual.ItemRitualSphere.of(ru.death.chaosshard.ritual.Ritual.WARD, 0));
            me.getHeldItemMainhand().getItem().onItemUse(me, ow, at, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
            check(ow.getBlockState(at.up()).getBlock() == ModBlocks.RITUAL_SPHERE, "сфера оберега встала");
        });
        sleep(30);
        step("в 32 блоках от сферы спавн запрещён", () -> {
            BlockPos at = base.add(14, 1, -14);
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setPosition(at.getX() + 10, at.getY(), at.getZ());
            net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn ev = new net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn(
                    z, ow, (float) z.posX, (float) z.posY, (float) z.posZ, null);
            MinecraftForge.EVENT_BUS.post(ev);
            check(ev.getResult() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY, "зомби в 10 блоках не появится");
            net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn far = new net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn(
                    z, ow, (float) z.posX + 60, (float) z.posY, (float) z.posZ, null);
            MinecraftForge.EVENT_BUS.post(far);
            check(far.getResult() != net.minecraftforge.fml.common.eventhandler.Event.Result.DENY, "в 70 блоках — может");
            ow.setBlockToAir(at.up());
        });
        step("Сфера порыва срабатывает только у Якоря", () -> {
            me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.ritual.ItemRitualSphere.of(ru.death.chaosshard.ritual.Ritual.CORRUPTION, 0));
            net.minecraft.util.EnumActionResult far = me.getHeldItemMainhand().getItem().onItemUse(me, ow, base.add(0, 0, 0).add(-30, 0, -30).add(-200, 0, 0),
                    EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
            check(far == net.minecraft.util.EnumActionResult.FAIL && !me.getHeldItemMainhand().isEmpty(), "вдали от Якоря — не тратится");
            if (anchorPos != null && ow.getTileEntity(anchorPos) instanceof TileAnchor) {
                net.minecraft.util.EnumActionResult near = me.getHeldItemMainhand().getItem().onItemUse(me, ow, anchorPos.add(4, -1, 0),
                        EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
                check(near == net.minecraft.util.EnumActionResult.SUCCESS && me.getHeldItemMainhand().isEmpty(), "у Якоря — Скверна рванулась, сфера потрачена");
            }
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        });

        section("Новые ритуалы: Чаша");
        step("Сумерки: Чаша даёт сферу, сфера меняет день на закат и ночь на рассвет", () -> {
            ru.death.chaosshard.ritual.TileRitualBowl bowl = (ru.death.chaosshard.ritual.TileRitualBowl) ow.getTileEntity(bowlPos);
            if (bowl == null) { check(false, "Чаши нет"); return; }
            bowl.insert(me, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 1), true);
            bowl.insert(me, new net.minecraft.item.ItemStack(net.minecraft.init.Items.COAL, 4, 1), true);
            bowl.tryStart(me);
            check(takeSphere(bowlPos, ru.death.chaosshard.ritual.Ritual.DUSK), "над Чашей — Сфера сумерек (древесный уголь годится)");
            long day0 = ow.getWorldTime() - ow.getWorldTime() % 24000;
            ow.setWorldTime(day0 + 1000);
            check(useSphere(ru.death.chaosshard.ritual.Ritual.DUSK, base.add(0, 0, -20)), "сфера разбита");
            check(ow.getWorldTime() % 24000 == 13000, "день стал закатом (" + ow.getWorldTime() % 24000 + ")");
            useSphere(ru.death.chaosshard.ritual.Ritual.DUSK, base.add(0, 0, -20));
            check(ow.getWorldTime() % 24000 == 0, "ночь стала рассветом (" + ow.getWorldTime() % 24000 + ")");
        });
        step("Приплод: без зверей не тратится, у зверей — готовы", () -> {
            BlockPos pen = base.add(20, 0, 20);
            check(!useSphere(ru.death.chaosshard.ritual.Ritual.BREED, pen.add(0, 0, -60)) && !me.getHeldItemMainhand().isEmpty(),
                    "в пустом месте сфера цела");
            List<net.minecraft.entity.passive.EntityCow> cows = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                net.minecraft.entity.passive.EntityCow c = new net.minecraft.entity.passive.EntityCow(ow);
                c.setPosition(pen.getX() + 2 + i, pen.getY() + 1, pen.getZ() + 2);
                ow.spawnEntity(c);
                cows.add(c);
            }
            check(useSphere(ru.death.chaosshard.ritual.Ritual.BREED, pen), "сфера разбита у загона");
            check(cows.stream().allMatch(net.minecraft.entity.passive.EntityAnimal::isInLove), "все три коровы готовы к приплоду");
        });
        step("Сфера тяги: вещи плывут в сундук под ней", () -> {
            pullChest = base.add(-20, 1, 14);
            ow.setBlockState(pullChest, Blocks.CHEST.getDefaultState(), 3);
            me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.ritual.ItemRitualSphere.of(ru.death.chaosshard.ritual.Ritual.PULL, 0));
            me.getHeldItemMainhand().getItem().onItemUse(me, ow, pullChest, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
            check(ow.getBlockState(pullChest.up()).getBlock() == ModBlocks.RITUAL_SPHERE, "сфера тяги встала на сундук");
            net.minecraft.entity.item.EntityItem ei = new net.minecraft.entity.item.EntityItem(ow, pullChest.getX() + 7.5, pullChest.getY() + 0.5,
                    pullChest.getZ() + 0.5, new net.minecraft.item.ItemStack(Blocks.DIRT, 5));
            ei.setPickupDelay(1000);
            ow.spawnEntity(ei);
        });
        sleep(200);
        step("вещь доехала до сундука", () -> {
            net.minecraft.tileentity.TileEntity te = ow.getTileEntity(pullChest);
            int dirt = 0;
            if (te instanceof net.minecraft.tileentity.TileEntityChest) {
                for (int i = 0; i < ((net.minecraft.tileentity.TileEntityChest) te).getSizeInventory(); i++) {
                    net.minecraft.item.ItemStack st = ((net.minecraft.tileentity.TileEntityChest) te).getStackInSlot(i);
                    if (st.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.DIRT)) dirt += st.getCount();
                }
            }
            check(dirt == 5, "в сундуке земли: " + dirt + " из 5");
            ow.setBlockToAir(pullChest.up());
        });

        section("Старшие ритуалы: Алтарь Прорыва");
        step("Алтарь и четыре шпиля: Взор Нити складывается в сферу", () -> {
            altarPos = base.add(-24, 1, -24);
            ow.setBlockState(altarPos, ModBlocks.BREACH_ALTAR.getDefaultState(), 3);
            for (BlockPos sp : ru.death.chaosshard.breach.BreachRitual.pillars(altarPos)) {
                ow.setBlockState(sp, ModBlocks.RITUAL_SPIRE.getDefaultState().withProperty(ru.death.chaosshard.breach.BlockRitualSpire.HALF,
                        ru.death.chaosshard.breach.BlockRitualSpire.Half.LOWER), 3);
                ow.setBlockState(sp.up(), ModBlocks.RITUAL_SPIRE.getDefaultState().withProperty(ru.death.chaosshard.breach.BlockRitualSpire.HALF,
                        ru.death.chaosshard.breach.BlockRitualSpire.Half.UPPER), 3);
            }
            me.setPosition(altarPos.getX() + 3.5, altarPos.getY(), altarPos.getZ() + 0.5);
            drop(altarPos, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 4),
                    new net.minecraft.item.ItemStack(net.minecraft.init.Items.ENDER_EYE, 2), new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.VEIL_SHARD, 2));
            ru.death.chaosshard.ritual.AltarRituals.tryStart(me, altarPos);
            check(ru.death.chaosshard.ritual.AltarRituals.isRunning(me), "старший ритуал пошёл");
            for (int i = 0; i < ru.death.chaosshard.ritual.AltarRituals.DURATION + 2; i++) ru.death.chaosshard.ritual.AltarRituals.tick(me);
            check(!ru.death.chaosshard.ritual.AltarRituals.isRunning(me), "ритуал завершился");
            check(takeSphere(altarPos, ru.death.chaosshard.ritual.Ritual.SIGHT), "над Алтарём — Сфера Взора Нити, подношения ушли");
        });
        step("Взор Нити: где разбили — там светятся твари", () -> {
            BlockPos at = base.add(10, 0, -10);
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setPosition(at.getX() + 10, at.getY() + 1, at.getZ());
            z.enablePersistence();
            ow.spawnEntity(z);
            sightZombie = z;
            check(useSphere(ru.death.chaosshard.ritual.Ritual.SIGHT, at), "сфера разбита");
            check(z.isPotionActive(net.minecraft.init.MobEffects.GLOWING), "зомби в 10 блоках светится");
        });
        step("чужие подношения — не ритуал, ничего не тратится", () -> {
            drop(altarPos, new net.minecraft.item.ItemStack(Blocks.DIRT, 3));
            ru.death.chaosshard.ritual.AltarRituals.tryStart(me, altarPos);
            check(!ru.death.chaosshard.ritual.AltarRituals.isRunning(me), "по земле ритуал не начался");
            check(!ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(altarPos).grow(2), net.minecraft.entity.Entity::isEntityAlive).isEmpty(),
                    "земля осталась лежать");
            ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(altarPos).grow(2)).forEach(net.minecraft.entity.Entity::setDead);
        });
        step("Зов из Нити: без зверей в Нити сфера цела; со зверем — возвращает", () -> {
            check(!useSphere(ru.death.chaosshard.ritual.Ritual.RECALL, base) && !me.getHeldItemMainhand().isEmpty(), "в Нити никого — сфера цела");
            BeastData.Entry e = Beasts.entry(ow, me.getUniqueID(), BeastData.Beast.HARE);
            e.returnAt = ow.getTotalWorldTime() + 20000;
            check(useSphere(ru.death.chaosshard.ritual.Ritual.RECALL, base), "заяц в Нити — сфера разбита");
            check(e.returnAt > 0 && e.returnAt <= ow.getTotalWorldTime(), "заяц отпущен из Нити сейчас");
        });
        step("Кровавая луна: сфера встаёт и держит луну", () -> {
            moonPos = base.add(-24, 0, 4);
            me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.ritual.ItemRitualSphere.of(ru.death.chaosshard.ritual.Ritual.BLOOD_MOON, 0));
            me.getHeldItemMainhand().getItem().onItemUse(me, ow, moonPos, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
            check(ow.getBlockState(moonPos.up()).getBlock() == ModBlocks.RITUAL_SPHERE, "сфера луны встала");
        });
        sleep(30);
        step("под луной: твари идут, опыт вдвое", () -> {
            check(ru.death.chaosshard.ritual.AltarRituals.moonAt(ow, moonPos.add(30, 0, 0)), "в 30 блоках — луна");
            check(!ru.death.chaosshard.ritual.AltarRituals.moonAt(ow, moonPos.add(100, 0, 0)), "в 100 блоках — нет");
            me.setPosition(moonPos.getX() + 0.5, moonPos.getY() + 1, moonPos.getZ() + 0.5);
            for (int i = 0; i < 4; i++) ru.death.chaosshard.ritual.AltarRituals.spawnMoonMob(ow, me);
            check(!tagged(ow, new AxisAlignedBB(me.getPosition()).grow(20), "chaosshard_moon").isEmpty(), "твари луны появились рядом");
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setPosition(moonPos.getX() + 5, moonPos.getY() + 1, moonPos.getZ());
            net.minecraftforge.event.entity.living.LivingExperienceDropEvent xe = new net.minecraftforge.event.entity.living.LivingExperienceDropEvent(z, me, 5);
            MinecraftForge.EVENT_BUS.post(xe);
            check(xe.getDroppedExperience() == 10, "опыт за тварь удвоен: " + xe.getDroppedExperience());
            ow.setBlockToAir(moonPos.up());
        });
        step("Великий порыв: у Якоря — оскверняет, вдали — сфера цела", () -> {
            check(!useSphere(ru.death.chaosshard.ritual.Ritual.GREAT_SURGE, base.add(-600, 0, 0)) && !me.getHeldItemMainhand().isEmpty(), "вдали от Якорей — цела");
            if (anchorPos == null || !(ow.getTileEntity(anchorPos) instanceof TileAnchor)) { check(false, "Якоря нет"); return; }
            int before = ((TileAnchor) ow.getTileEntity(anchorPos)).getCorrupted();
            check(useSphere(ru.death.chaosshard.ritual.Ritual.GREAT_SURGE, anchorPos.add(5, -1, 0)), "у Якоря — разбита");
            int after = ((TileAnchor) ow.getTileEntity(anchorPos)).getCorrupted();
            check(after > before, "Скверна выросла: " + before + " → " + after);
        });

        section("Вызов");
        step("состав Вызова разбирается по подношениям", () -> {
            List<net.minecraft.item.ItemStack> ok = new ArrayList<>();
            ok.add(new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 4));
            ok.add(new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.VEIL_SHARD, 2));
            ok.add(new net.minecraft.item.ItemStack(net.minecraft.init.Items.GOLD_INGOT, 4));
            ok.add(new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.WORLD_SHARD, 3));
            ok.add(new net.minecraft.item.ItemStack(net.minecraft.init.Items.BLAZE_POWDER, 1));
            ok.add(new net.minecraft.item.ItemStack(net.minecraft.init.Items.BONE, 1));
            ru.death.chaosshard.challenge.ChallengeSpec sp = ru.death.chaosshard.challenge.ChallengeSpec.parse(ok);
            check(sp != null && sp.faction == ru.death.chaosshard.challenge.ChallengeFaction.ORDER && sp.tier == 3
                    && sp.mods == (ru.death.chaosshard.challenge.ChallengeMod.FURY.bit() | ru.death.chaosshard.challenge.ChallengeMod.HORDE.bit()),
                    "Орден, 3-я ступень, Ярость и Орда");
            check(sp != null && Math.abs(sp.rewardMul() - 1.5) < 1e-6, "награда +50%");
            ok.add(new net.minecraft.item.ItemStack(net.minecraft.init.Items.ROTTEN_FLESH, 8));
            check(ru.death.chaosshard.challenge.ChallengeSpec.parse(ok) == null, "две стороны сразу — не Вызов");
        });
        step("Вызов на Алтаре складывается в Сферу Вызова", () -> {
            drop(altarPos, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 4),
                    new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.VEIL_SHARD, 2),
                    new net.minecraft.item.ItemStack(net.minecraft.init.Items.ROTTEN_FLESH, 8),
                    new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.WORLD_SHARD, 1));
            me.setPosition(altarPos.getX() + 3.5, altarPos.getY(), altarPos.getZ() + 0.5);
            ru.death.chaosshard.ritual.AltarRituals.tryStart(me, altarPos);
            check(ru.death.chaosshard.ritual.AltarRituals.isRunning(me), "ритуал Вызова пошёл");
            for (int i = 0; i < ru.death.chaosshard.ritual.AltarRituals.DURATION + 2; i++) ru.death.chaosshard.ritual.AltarRituals.tick(me);
            List<net.minecraft.entity.item.EntityItem> got = ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(altarPos).grow(3),
                    x -> x.isEntityAlive() && x.getItem().getItem() == ru.death.chaosshard.init.ModItems.CHALLENGE_SPHERE);
            ru.death.chaosshard.challenge.ChallengeSpec sp = got.isEmpty() ? null : ru.death.chaosshard.challenge.ItemChallengeSphere.spec(got.get(0).getItem());
            check(sp != null && sp.faction == ru.death.chaosshard.challenge.ChallengeFaction.WILD && sp.tier == 1, "Сфера Вызова: твари мира, 1-я ступень");
            got.forEach(net.minecraft.entity.Entity::setDead);
        });
        step("арена в архиве строится", () -> {
            net.minecraft.world.WorldServer arc = server.getWorld(ru.death.chaosshard.trial.TrialDimension.id());
            BlockPos c = new BlockPos(ru.death.chaosshard.challenge.ChallengeManager.ARENA_X - 99 * 256, ru.death.chaosshard.challenge.ChallengeManager.ARENA_Y, 0);
            for (ru.death.chaosshard.challenge.ChallengeFaction f : ru.death.chaosshard.challenge.ChallengeFaction.values()) {
                ru.death.chaosshard.challenge.ChallengeArena.build(arc, c, f);
                check(!arc.isAirBlock(c.down()) && !arc.isAirBlock(c.add(10, -1, 0)), "арена " + f + ": пол есть");
                check(arc.getBlockState(c.add(0, 14, 0)).getBlock() == Blocks.BARRIER && arc.isAirBlock(c.add(5, 1, 5)), "арена " + f + ": купол и простор");
                check(!arc.isAirBlock(c.add(17, 1, 0)), "арена " + f + ": стена");
            }
        });
        step("смерть на арене — проигрыш, а не смерть", () -> {
            ru.death.chaosshard.challenge.ChallengeSpec sp = new ru.death.chaosshard.challenge.ChallengeSpec();
            sp.faction = ru.death.chaosshard.challenge.ChallengeFaction.WILD;
            ru.death.chaosshard.challenge.ChallengeManager.begin(me, sp, me.getPosition(), true, 0);
            net.minecraftforge.event.entity.living.LivingDeathEvent ev = new net.minecraftforge.event.entity.living.LivingDeathEvent(me, DamageSource.GENERIC);
            MinecraftForge.EVENT_BUS.post(ev);
            check(ev.isCanceled(), "смерть отменена (вещи остаются)");
            check(ru.death.chaosshard.challenge.ChallengeManager.of(me) == null, "Вызов проигран");
        });
        step("Цена крови: здоровье само не растёт", () -> {
            ru.death.chaosshard.challenge.ChallengeSpec sp = new ru.death.chaosshard.challenge.ChallengeSpec();
            sp.mods = ru.death.chaosshard.challenge.ChallengeMod.BLOOD_PRICE.bit();
            ru.death.chaosshard.challenge.ChallengeManager.begin(me, sp, me.getPosition(), true, 0);
            net.minecraftforge.event.entity.living.LivingHealEvent h = new net.minecraftforge.event.entity.living.LivingHealEvent(me, 2F);
            MinecraftForge.EVENT_BUS.post(h);
            check(h.isCanceled(), "лечение отменено");
            ru.death.chaosshard.challenge.ChallengeManager.abort(me);
        });
        step("Вызов у Якоря: тварей 1-й ступени", () -> {
            if (anchorPos == null) { check(false, "Якоря нет"); return; }
            me.setPosition(anchorPos.getX() + 2.5, anchorPos.getY(), anchorPos.getZ() + 0.5);
            ru.death.chaosshard.challenge.ChallengeSpec sp = new ru.death.chaosshard.challenge.ChallengeSpec();
            sp.faction = ru.death.chaosshard.challenge.ChallengeFaction.WILD;
            sp.tier = 1;
            me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.challenge.ItemChallengeSphere.of(sp));
            me.getHeldItemMainhand().getItem().onItemUse(me, ow, anchorPos.add(2, -1, 0), EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
            ru.death.chaosshard.challenge.ChallengeManager.Challenge c = ru.death.chaosshard.challenge.ChallengeManager.of(me);
            check(c != null && !c.arena && c.center.equals(anchorPos), "Вызов начат у Якоря");
            check(me.getHeldItemMainhand().isEmpty(), "сфера разбита");
            if (c != null) c.timer = 2;
            challengeNit = ru.death.chaosshard.thread.ThreadManager.getPoints(me);
            overSnap = ru.death.chaosshard.thread.ThreadManager.overflowed;
            challengeCorrupt = ((TileAnchor) ow.getTileEntity(anchorPos)).getCorrupted();
        });
        for (int wv = 1; wv <= 3; wv++) {
            final int waveNo = wv;
            sleep(5);
            step("волна " + wv + ": пришли и пали", () -> {
                ru.death.chaosshard.challenge.ChallengeManager.Challenge c = ru.death.chaosshard.challenge.ChallengeManager.of(me);
                if (c == null) { check(false, "Вызова нет"); return; }
                check(c.fighting && c.mobs.size() >= 4, "врагов в волне " + waveNo + ": " + c.mobs.size());
                for (UUID id : new ArrayList<>(c.mobs)) {
                    Entity e = ow.getEntityFromUuid(id);
                    if (e instanceof EntityLivingBase) kill((EntityLivingBase) e);
                }
            });
            until("волна " + wv + " засчитана", () -> {
                ru.death.chaosshard.challenge.ChallengeManager.Challenge c = ru.death.chaosshard.challenge.ChallengeManager.of(me);
                if (c == null) return waveNo == 3;
                if (!c.fighting && c.wave == waveNo) { c.timer = 2; return true; }
                return false;
            }, 60);
        }
        step("Вызов выдержан: награда, Нить, Якорь окреп", () -> {
            check(ru.death.chaosshard.challenge.ChallengeManager.of(me) == null, "Вызов завершён");
            check(nitGrew(challengeNit), "Нить выросла (или перелилась): " + challengeNit + " → " + ru.death.chaosshard.thread.ThreadManager.getPoints(me));
            check(((TileAnchor) ow.getTileEntity(anchorPos)).getCorrupted() > challengeCorrupt, "Якорь окреп");
            int items = 0;
            for (int i = 0; i < me.inventory.getSizeInventory(); i++) if (!me.inventory.getStackInSlot(i).isEmpty()) items++;
            check(items > 0, "дары в сумке: стопок " + items);
            me.inventory.clear();
        });
        step("бесконечный: после волны можно уйти с наградой", () -> {
            ru.death.chaosshard.challenge.ChallengeSpec sp = new ru.death.chaosshard.challenge.ChallengeSpec();
            sp.faction = ru.death.chaosshard.challenge.ChallengeFaction.WILD;
            sp.tier = 0;
            ru.death.chaosshard.challenge.ChallengeManager.Challenge c = ru.death.chaosshard.challenge.ChallengeManager.begin(me, sp, anchorPos, false, 0);
            c.timer = 2;
        });
        sleep(5);
        step("бесконечный: Shift+ПКМ Гримуаром во время волны — она последняя", () -> {
            ru.death.chaosshard.challenge.ChallengeManager.Challenge c = ru.death.chaosshard.challenge.ChallengeManager.of(me);
            if (c == null) { check(false, "Вызова нет"); return; }
            check(c.fighting, "волна идёт");
            me.setSneaking(true);
            me.setHeldItem(EnumHand.MAIN_HAND, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.GRIMOIRE));
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(me, EnumHand.MAIN_HAND));
            me.setSneaking(false);
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            check(c.lastWave && ru.death.chaosshard.challenge.ChallengeManager.of(me) == c && c.fighting, "волна продолжается, отмечена последней");
            for (UUID id : new ArrayList<>(c.mobs)) {
                Entity e = ow.getEntityFromUuid(id);
                if (e instanceof EntityLivingBase) kill((EntityLivingBase) e);
            }
        });
        until("последняя волна отбита — Вызов закрыт с наградой", () -> ru.death.chaosshard.challenge.ChallengeManager.of(me) == null, 60);
        step("награда за бесконечный в сумке", () -> {
            int items = 0;
            for (int i = 0; i < me.inventory.getSizeInventory(); i++) if (!me.inventory.getStackInSlot(i).isEmpty()) items++;
            check(items > 0, "стопок в сумке: " + items);
            me.inventory.clear();
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
                    if (ow.getBlockState(p).getBlock() != ru.death.chaosshard.structure.BlueprintPlacer.parse(be.getValue()).getBlock()
                            && ow.getBlockState(p).getBlock() != ModBlocks.ORDER_BEACON) {
                        if (bad < 6) line("      " + names[i] + " (" + ru.death.chaosshard.structure.bp.Blueprint.kx(k) + "," + ru.death.chaosshard.structure.bp.Blueprint.ky(k) + ","
                                + ru.death.chaosshard.structure.bp.Blueprint.kz(k) + "): ждали " + be.getValue() + ", стоит " + ow.getBlockState(p).getBlock().getRegistryName());
                        bad++;
                    }
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

        section("Акт 4: Знамение и Опора Каэля");
        step("Знамения идут строго по порядку", () -> {
            ru.death.chaosshard.hero.HeroData hd = ru.death.chaosshard.hero.HeroData.get(ow);
            for (ru.death.chaosshard.hero.HeroData.Pillar p : hd.pillars) {
                p.state = ru.death.chaosshard.hero.HeroData.NONE;
                p.entry = -1;
                p.fallRing = -1;
            }
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            check(!useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_IRENA, base), "Знамение Ирены до падения Каэля не срабатывает");
            check(hd.of(ru.death.chaosshard.hero.Lightbearer.IRENA).state == ru.death.chaosshard.hero.HeroData.NONE, "Опора Ирены не указана");
        });
        step("Знамение Каэля указывает место в нетронутых землях", () -> {
            me.rotationYaw = 0;   // на юг
            boolean ok = useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_KAEL, base);
            check(ok, "сфера Знамения разбилась");
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.KAEL);
            check(p.state == ru.death.chaosshard.hero.HeroData.PLANNED, "Опора Каэля указана: " + p.x + " " + p.z);
            double d = Math.sqrt((p.x - base.getX()) * (double) (p.x - base.getX()) + (p.z - base.getZ()) * (double) (p.z - base.getZ()));
            check(d >= ChaosConfig.order.omenDistance[0] - 2, "дальше " + ChaosConfig.order.omenDistance[0] + " блоков: " + (int) d);
            check(p.z > base.getZ(), "впереди по взгляду (юг): z " + p.z + " > " + base.getZ());
            check(!ow.getChunkProvider().isChunkGeneratedAt(p.x >> 4, p.z >> 4), "чанк Опоры ещё не создан");
            ru.death.chaosshard.structure.OrderStructureData.Entry e = ru.death.chaosshard.structure.OrderStructureData.get(ow).byId(p.entry);
            check(e != null && "pillar_0".equals(e.name), "постройка запланирована: " + (e == null ? "нет" : e.name));
            check(!useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_KAEL, base), "второе Знамение Каэля не тратится");
            planned = e;
            me.setPosition(p.x + 0.5, 200, p.z + 0.5);
        });
        until("Опора встаёт, когда я подхожу", () -> planned != null && planned.state == ru.death.chaosshard.structure.OrderStructureData.PLACED, 1200);
        sleep(10);
        step("Опора на месте: колонна, луч, Каэль и стража", () -> {
            if (planned == null || planned.state != ru.death.chaosshard.structure.OrderStructureData.PLACED) { check(false, "Опоры нет"); return; }
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.KAEL);
            check(p.state == ru.death.chaosshard.hero.HeroData.STANDING, "Опора стоит");
            BlockPos o = planned.origin();
            check(!ow.isAirBlock(o.up(ru.death.chaosshard.structure.bp.VeilPillar.FLOOR + ru.death.chaosshard.structure.bp.VeilPillar.COLUMN_H)), "колонна во весь рост");
            check(planned.beams.size() == 1, "луч в небо: " + planned.beams.size());
            List<ru.death.chaosshard.hero.EntityHero> hs = ow.getEntitiesWithinAABB(ru.death.chaosshard.hero.EntityHero.class, new AxisAlignedBB(o).grow(24, 40, 24));
            check(hs.size() == 1 && hs.get(0).hero() == ru.death.chaosshard.hero.Lightbearer.KAEL, "Каэль на Опоре: " + hs.size());
            check(!hs.isEmpty() && hs.get(0).getMaxHealth() >= 500, "у Каэля здоровье босса");
            int guards = ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class, new AxisAlignedBB(o).grow(24, 40, 24)).size();
            check(guards == 4, "стража Опоры: " + guards);
            check(ru.death.chaosshard.structure.BlueprintPlacer.FAILED.isEmpty(), "все блоки Опоры разобраны " + ru.death.chaosshard.structure.BlueprintPlacer.FAILED);
            // стражу убрать — мешает проверкам боя
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(o).grow(24, 40, 24))) {
                k.setDead();
            }
            // святыня рядом — должна погаснуть с Опорой
            shrinePos = o.add(40, 0, 0);
            BlockPos sg = ru.death.chaosshard.structure.OrderStructures.ground(ow, shrinePos.getX(), shrinePos.getZ());
            shrinePos = sg;
            ow.setBlockState(sg, Blocks.GOLD_BLOCK.getDefaultState(), 3);
            ow.setBlockState(sg.up(), ModBlocks.SHRINE_STONE.getDefaultState(), 3);
            shrinePos = sg.up();
            ru.death.chaosshard.order.Shrines.register(ow, shrinePos);
        });
        step("сцена появления: Каэль замирает, камера кончает в глазах", () -> {
            List<ru.death.chaosshard.hero.EntityHero> hs = ow.getEntitiesWithinAABB(ru.death.chaosshard.hero.EntityHero.class, new AxisAlignedBB(planned.origin()).grow(24, 40, 24));
            if (hs.isEmpty()) { check(false, "Каэля нет"); return; }
            ru.death.chaosshard.hero.EntityHero k = hs.get(0);
            me.setPosition(k.posX, k.posY, k.posZ + 10);
            ru.death.chaosshard.hero.Pillars.intro(me, k);
            check(ru.death.chaosshard.cutscene.CutsceneManager.playing(me) && k.scripted, "сцена идёт, Каэль по сценарию");
            float h0 = k.getHealth();
            k.hurtResistantTime = 0;
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            check(k.getHealth() == h0, "в сцене Каэль неуязвим");
            ru.death.chaosshard.cutscene.Cutscene sc = ru.death.chaosshard.hero.HeroScenes.intro(ow, k, me);
            check(sc.at(sc.length)[0].distanceTo(new Vec3d(me.posX, me.posY + me.getEyeHeight(), me.posZ)) < 0.2, "камера кончает в глазах");
            check(sc.lines.size() == 2, "реплик: " + sc.lines.size());
            ru.death.chaosshard.cutscene.CutsceneManager.skip(me);
            check(!k.scripted, "после сцены Каэль снова в бою");
        });
        step("щит спереди, спина открыта", () -> {
            List<ru.death.chaosshard.hero.EntityHero> hs = ow.getEntitiesWithinAABB(ru.death.chaosshard.hero.EntityHero.class, new AxisAlignedBB(planned.origin()).grow(24, 40, 24));
            if (hs.isEmpty()) { check(false, "Каэля нет"); return; }
            ru.death.chaosshard.hero.EntityHero k = hs.get(0);
            k.setAttackTarget(null);
            k.rotationYaw = k.renderYawOffset = k.rotationYawHead = 0;   // смотрит на юг (+z)
            me.setPosition(k.posX, k.posY, k.posZ + 3);
            float h0 = k.getHealth();
            k.hurtResistantTime = 0;
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            float front = h0 - k.getHealth();
            k.setHealth(h0);
            k.hurtResistantTime = 0;
            me.setPosition(k.posX, k.posY, k.posZ - 3);
            k.rotationYaw = k.renderYawOffset = k.rotationYawHead = 0;
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            float back = h0 - k.getHealth();
            k.setHealth(h0);
            k.hurtResistantTime = 0;
            check(back > 0 && front < back / 4, "урон спереди " + front + ", в спину " + back);
        });
        step("вспышка слепит того, кто смотрит, и не трогает отвернувшегося", () -> {
            List<ru.death.chaosshard.hero.EntityHero> hs = ow.getEntitiesWithinAABB(ru.death.chaosshard.hero.EntityHero.class, new AxisAlignedBB(planned.origin()).grow(24, 40, 24));
            if (hs.isEmpty()) { check(false, "Каэля нет"); return; }
            ru.death.chaosshard.hero.EntityHero k = hs.get(0);
            BlockPos stand = planned.origin().up(ru.death.chaosshard.structure.bp.VeilPillar.FLOOR + 1);
            k.setPositionAndUpdate(stand.getX() + 0.5, stand.getY(), stand.getZ() + 6.5);
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setNoAI(true);
            z.setChild(false);
            z.setLocationAndAngles(k.posX, k.posY, k.posZ + 4, 180, 0);
            z.rotationYawHead = 180;   // лицом на север, к Каэлю
            ow.spawnEntity(z);
            k.flash(ow);
            check(z.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS), "смотрел — ослеп (виден: " + k.canEntityBeSeen(z)
                    + ", взгляд " + z.getLook(1F) + ")");
            z.clearActivePotions();
            z.rotationYaw = z.rotationYawHead = 0;     // отвернулся
            k.flash(ow);
            check(!z.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS), "отвернулся — не ослеп");
            z.setDead();
            me.setHealth(me.getMaxHealth());
        });
        step("Каэль пал — Опора рушится, святыня гаснет, трофей у ног", () -> {
            List<ru.death.chaosshard.hero.EntityHero> hs = ow.getEntitiesWithinAABB(ru.death.chaosshard.hero.EntityHero.class, new AxisAlignedBB(planned.origin()).grow(24, 40, 24));
            if (hs.isEmpty()) { check(false, "Каэля нет"); return; }
            int nit = ru.death.chaosshard.thread.ThreadManager.getPoints(me);
            overSnap = ru.death.chaosshard.thread.ThreadManager.overflowed;
            kill(hs.get(0));
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.KAEL);
            check(p.state == ru.death.chaosshard.hero.HeroData.FALLEN, "Опора пала");
            BlockPos o = planned.origin();
            check(!ow.isAirBlock(o.up(ru.death.chaosshard.structure.bp.VeilPillar.FLOOR + ru.death.chaosshard.structure.bp.VeilPillar.COLUMN_H)),
                    "колонна ещё стоит — рухнет под камерой");
            boolean blade = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(o).grow(24, 40, 24),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.KAEL_BLADE).isEmpty();
            check(blade, "Клинок Каэля лежит у Опоры");
            check(nitGrew(nit), "Нить окрепла (или перелилась): " + nit + " → " + ru.death.chaosshard.thread.ThreadManager.getPoints(me));
            Advancement a = server.getAdvancementManager().getAdvancement(new ResourceLocation("chaosshard", "m/pillar_kael"));
            check(a != null, "веха «Опора Каэля пала» загружена");
            // подставному игроку Forge вехи не засчитывает — только для сведения
            line("    веха у подставного игрока: " + (a != null && me.getAdvancements().getProgress(a).isDone() ? "засчитана" : "не засчитана (FakePlayer)"));
        });
        until("колонна рухнула", () -> ow.isAirBlock(planned.origin().up(ru.death.chaosshard.structure.bp.VeilPillar.FLOOR
                + ru.death.chaosshard.structure.bp.VeilPillar.COLUMN_H)), ru.death.chaosshard.hero.Pillars.COLLAPSE_DELAY + 40);
        step("после обрушения: луч погас, святыня погасла", () -> {
            boolean off = planned.beams.stream().allMatch(b -> !(ow.getTileEntity(b) instanceof ru.death.chaosshard.structure.TileOrderBeacon)
                    || !((ru.death.chaosshard.structure.TileOrderBeacon) ow.getTileEntity(b)).lit);
            check(off, "луч погас (светоч обрушен вместе с колонной)");
            check(ow.getBlockState(shrinePos).getBlock() == ModBlocks.DESECRATED_STONE, "святыня рядом погасла");
            ru.death.chaosshard.cutscene.Cutscene fs = ru.death.chaosshard.hero.HeroScenes.fall(ow, planned.origin(), me, ru.death.chaosshard.hero.Lightbearer.KAEL);
            check(fs.at(fs.length)[0].distanceTo(new Vec3d(me.posX, me.posY + me.getEyeHeight(), me.posZ)) < 0.2, "сцена падения кончается в глазах игрока");
        });
        until("волна Скверны прошла", () -> ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.KAEL).fallRing < 0,
                ChaosConfig.order.pillarDefileRadius * 4 + 100);
        step("край вокруг осквернён", () -> {
            BlockPos o = planned.origin();
            int r = ChaosConfig.order.pillarDefileRadius / 2, bad = 0, all = 0;
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI / 16;
                BlockPos t = ow.getHeight(new BlockPos(o.getX() + Math.cos(a) * r, 0, o.getZ() + Math.sin(a) * r));
                boolean any = false;
                for (int y = t.getY(); y >= t.getY() - 5; y--) {
                    if (ru.death.chaosshard.anchor.Corruption.isCorrupted(ow.getBlockState(new BlockPos(t.getX(), y, t.getZ())))) any = true;
                }
                all++;
                if (any) bad++;
            }
            check(bad * 2 >= all, "осквернённых столбцов на радиусе " + r + ": " + bad + " из " + all);
            check(!useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_KAEL, base), "Знамение павшей Опоры не срабатывает");
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        });

        section("Акт 4: Ирена и монастыри");
        step("Знамение Ирены: Опора и три монастыря", () -> {
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            me.rotationYaw = 90;   // на запад — в нетронутые земли
            check(useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_IRENA, base), "Знамение Ирены сработало после падения Каэля");
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA);
            check(p.state == ru.death.chaosshard.hero.HeroData.PLANNED, "Опора Ирены указана: " + p.x + " " + p.z);
            check(p.linked.size() == ru.death.chaosshard.hero.Pillars.MONASTERIES, "монастырей указано: " + p.linked.size());
            check(ru.death.chaosshard.hero.Pillars.lit(ow, ru.death.chaosshard.hero.Lightbearer.IRENA) == p.linked.size(), "все монастыри горят");
            monastery = p.linked.isEmpty() ? null : ru.death.chaosshard.structure.OrderStructureData.get(ow).byId(p.linked.get(0));
            if (monastery != null) {
                me.setPosition(monastery.x + 0.5, 200, monastery.z + 0.5);
            }
        });
        until("монастырь встал", () -> monastery != null && monastery.state == ru.death.chaosshard.structure.OrderStructureData.PLACED, 1200);
        step("Святой камень монастыря: горит, гаснет от Эссенции", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA);
            check(p.shrines.size() == 1, "святыня монастыря записана: " + p.shrines.size());
            check(ru.death.chaosshard.structure.BlueprintPlacer.FAILED.isEmpty(), "все блоки монастыря разобраны " + ru.death.chaosshard.structure.BlueprintPlacer.FAILED);
            if (p.shrines.isEmpty()) return;
            BlockPos st = p.shrines.get(0);
            check(ow.getBlockState(st).getBlock() == ModBlocks.SHRINE_STONE, "на алтаре Святой камень");
            int before = ru.death.chaosshard.hero.Pillars.lit(ow, ru.death.chaosshard.hero.Lightbearer.IRENA);
            boolean ok = ru.death.chaosshard.order.Shrines.desecrate(ow, st, me, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE));
            int after = ru.death.chaosshard.hero.Pillars.lit(ow, ru.death.chaosshard.hero.Lightbearer.IRENA);
            check(ok && after == before - 1, "монастырь погас: горит " + before + " → " + after);
            me.setPosition(p.x + 0.5, 200, p.z + 0.5);
        });
        until("Опора Ирены встала", () -> ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA).state
                == ru.death.chaosshard.hero.HeroData.STANDING, 1200);
        step("Ирена на Опоре, вокруг святая земля", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA);
            irena = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.IRENA, p);
            check(irena != null, "Ирена на Опоре");
            check(ru.death.chaosshard.order.Shrines.nearest(ow, p.pos(), 32) != null, "у Опоры Святой камень");
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(p.pos()).grow(24, 40, 24))) {
                k.setDead();
            }
            if (irena != null) {
                irena.setHealth(200);
                irenaHp = irena.getHealth();
            }
        });
        sleep(40);
        step("монастыри лечат Ирену", () -> {
            if (irena == null) return;
            float d = irena.getHealth() - irenaHp;
            check(d >= 20, "за 2 секунды при двух горящих монастырях: +" + d);
            ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA).linked.clear();
            irena.setHealth(200);
            irenaHp = irena.getHealth();
        });
        sleep(40);
        step("без монастырей лечения почти нет; Ирена пала — чётки у ног", () -> {
            if (irena == null) return;
            float d = irena.getHealth() - irenaHp;
            check(d < 15, "без монастырей за 2 секунды: +" + d);
            kill(irena);
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA);
            check(p.state == ru.death.chaosshard.hero.HeroData.FALLEN, "Опора Ирены пала");
            boolean rosary = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(p.pos()).grow(24, 40, 24),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.IRENA_ROSARY).isEmpty();
            check(rosary, "Чётки Ирены лежат у Опоры");
        });
        until("Опора Ирены рухнула, святая земля погасла", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.IRENA);
            return p.collapseAt == 0 && ru.death.chaosshard.order.Shrines.nearest(ow, p.pos(), 32) == null;
        }, ru.death.chaosshard.hero.Pillars.COLLAPSE_DELAY + 40);
        step("назад на площадку", () -> {
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        });

        section("Акт 4: Вельмор и печати отступления");
        step("Знамение Вельмора: Опора и печати", () -> {
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            me.rotationYaw = 270;   // на восток
            check(useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_VELMOR, base), "Знамение Вельмора сработало после падения Ирены");
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.VELMOR);
            check(p.state == ru.death.chaosshard.hero.HeroData.PLANNED, "Опора Вельмора указана: " + p.x + " " + p.z);
            check(p.linked.size() == ru.death.chaosshard.hero.Pillars.WARDS, "печатей запланировано: " + p.linked.size());
            me.setPosition(p.x + 0.5, 200, p.z + 0.5);
        });
        until("Опора Вельмора и печати встали", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.VELMOR);
            return p.state == ru.death.chaosshard.hero.HeroData.STANDING && p.shrines.size() >= 2;
        }, 1500);
        step("Вельмор на Опоре, печати целы", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.VELMOR);
            velmor = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.VELMOR, p);
            check(velmor != null, "Вельмор на Опоре");
            List<BlockPos> seals = ru.death.chaosshard.hero.Pillars.intactSeals(ow, ru.death.chaosshard.hero.Lightbearer.VELMOR);
            check(seals.size() == p.shrines.size() && !seals.isEmpty(), "целых печатей: " + seals.size());
            check(ru.death.chaosshard.structure.BlueprintPlacer.FAILED.isEmpty(), "все блоки разобраны " + ru.death.chaosshard.structure.BlueprintPlacer.FAILED);
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(p.pos()).grow(24, 40, 24))) {
                k.setDead();
            }
        });
        step("на 66% сбегает к печати и прячется за щитом", () -> {
            if (velmor == null) return;
            velmor.setHealth(velmor.getMaxHealth() * 0.68F);
            velmor.hurtResistantTime = 0;
            velmor.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            check(velmor.warded(), "сбежал к печати");
            BlockPos near = null;
            for (BlockPos sp : ru.death.chaosshard.hero.Pillars.intactSeals(ow, ru.death.chaosshard.hero.Lightbearer.VELMOR)) {
                if (velmor.getDistanceSq(sp) < 5 * 5) near = sp;
            }
            check(near != null, "стоит у печати");
            float h = velmor.getHealth();
            velmor.hurtResistantTime = 0;
            velmor.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            check(velmor.getHealth() == h, "за щитом неуязвим");
            sealHere = near;
        });
        step("разбил его печать — оглушён и открыт", () -> {
            if (velmor == null || sealHere == null) return;
            float h = velmor.getHealth();
            ow.destroyBlock(sealHere, false);
            check(!velmor.warded() && velmor.stunned(), "оглушён, щита нет");
            check(velmor.getHealth() < h, "печать ударила по нему: " + h + " → " + velmor.getHealth());
        });
        step("печатей не осталось — побег срывается", () -> {
            if (velmor == null) return;
            for (BlockPos sp : ru.death.chaosshard.hero.Pillars.intactSeals(ow, ru.death.chaosshard.hero.Lightbearer.VELMOR)) {
                ow.destroyBlock(sp, false);
            }
            check(ru.death.chaosshard.hero.Pillars.intactSeals(ow, ru.death.chaosshard.hero.Lightbearer.VELMOR).isEmpty(), "все печати разбиты");
            velmor.setHealth(velmor.getMaxHealth() * 0.35F);
            velmor.hurtResistantTime = 0;
            velmor.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            check(!velmor.warded() && velmor.stunned(), "побег сорвался — оглушён");
        });
        step("Вельмор пал — посох у ног", () -> {
            if (velmor == null) return;
            kill(velmor);
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.VELMOR);
            check(p.state == ru.death.chaosshard.hero.HeroData.FALLEN, "Опора Вельмора пала");
            boolean staff = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, velmor.getEntityBoundingBox().grow(8),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.VELMOR_STAFF).isEmpty();
            check(staff, "Посох Вельмора лежит у тела");
            check(ru.death.chaosshard.hero.HeroData.get(ow).fallenCount() == 3, "пало Опор: " + ru.death.chaosshard.hero.HeroData.get(ow).fallenCount());
        });
        until("Опора Вельмора рухнула", () -> ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.VELMOR).collapseAt == 0,
                ru.death.chaosshard.hero.Pillars.COLLAPSE_DELAY + 40);
        step("назад на площадку", () -> me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5));

        section("Акт 4: Лиан и Элис");
        step("Знамение Лиана", () -> {
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            me.rotationYaw = 180;   // на север
            check(useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_LIAN, base), "Знамение Лиана сработало после падения Вельмора");
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.LIAN);
            me.setPosition(p.x + 0.5, 200, p.z + 0.5);
        });
        until("Опора Лиана встала", () -> ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.LIAN).state
                == ru.death.chaosshard.hero.HeroData.STANDING, 1500);
        step("песнь сбивается уроном", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.LIAN);
            lian = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.LIAN, p);
            check(lian != null, "Лиан на Опоре");
            if (lian == null) return;
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(p.pos()).grow(24, 40, 24))) {
                k.setDead();
            }
            lian.startSong(ru.death.chaosshard.hero.EntityHero.SONG_VALOR);
            lian.hurtResistantTime = 0;
            lian.attackEntityFrom(DamageSource.causePlayerDamage(me), 8F);
            check(lian.singing(), "малый урон песнь не сбил");
            for (int i = 0; i < 6 && lian.singing(); i++) {
                lian.hurtResistantTime = 0;
                lian.attackEntityFrom(DamageSource.causePlayerDamage(me), 20F);
            }
            check(!lian.singing() && lian.silent(), "песнь сбита, Лиан молчит");
            lian.startSong(ru.death.chaosshard.hero.EntityHero.SONG_CHOIR);
        });
        until("хор допет", () -> {
            if (lian == null) return true;
            // объект мог устареть (чанк перезагрузился) — берём Лиана из мира заново
            ru.death.chaosshard.hero.EntityHero now = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.LIAN,
                    ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.LIAN));
            if (now != null && now != lian) {
                line("  (Лиан в мире — уже другой объект: чанк перезагружался)");
                lian = now;
            }
            return !lian.singing();
        }, ru.death.chaosshard.hero.EntityHero.SONG_LEN + 200);
        step("допетый хор — двое новых стражей; Лиан пал — лютня", () -> {
            if (lian == null) return;
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.LIAN);
            int guards = ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class, lian.getEntityBoundingBox().grow(16)).size();
            if (guards != 2) {
                line("  Лиан: в мире " + ow.loadedEntityList.contains(lian) + ", мёртв " + lian.isDead + ", поёт " + lian.singing()
                        + ", округа загружена " + ow.isAreaLoaded(lian.getPosition(), 32) + ", здоровье " + lian.getHealth());
            }
            check(guards == 2, "хор привёл стражей: " + guards);
            kill(lian);
            check(p.state == ru.death.chaosshard.hero.HeroData.FALLEN, "Опора Лиана пала");
            check(!ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, lian.getEntityBoundingBox().grow(8),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.LIAN_LUTE).isEmpty(), "Лютня Лиана у тела");
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(p.pos()).grow(24, 40, 24))) {
                k.setDead();
            }
        });
        step("Знамение Элис", () -> {
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            me.rotationYaw = 45;
            check(useSphere(ru.death.chaosshard.ritual.Ritual.OMEN_ELIS, base), "Знамение Элис сработало после падения Лиана");
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.ELIS);
            me.setPosition(p.x + 0.5, 200, p.z + 0.5);
        });
        until("Опора Элис встала", () -> ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.ELIS).state
                == ru.death.chaosshard.hero.HeroData.STANDING, 1500);
        until("Элис на своей Опоре (или вернулась ремонтом)", () -> ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.ELIS,
                ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.ELIS)) != null, 400);
        step("Светоч пропал — Опора ставит его снова", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.ELIS);
            ru.death.chaosshard.hero.EntityHero e = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.ELIS, p);
            check(p.heroAt != null && p.heroId != null, "Опора помнит точку и Светоча");
            if (e != null) {
                lostHero = e.getUniqueID();
                e.setDead();
            }
        });
        until("Элис встала снова", () -> {
            ru.death.chaosshard.hero.EntityHero e = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.ELIS,
                    ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.ELIS));
            return e != null && !e.getUniqueID().equals(lostHero);
        }, 400);
        step("королевская стража прикрывает Элис", () -> {
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.ELIS);
            ru.death.chaosshard.hero.EntityHero e = ru.death.chaosshard.hero.Pillars.heroOf(ow, ru.death.chaosshard.hero.Lightbearer.ELIS, p);
            check(e != null, "Элис на Опоре");
            if (e == null) return;
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(p.pos()).grow(24, 40, 24))) {
                k.setDead();
            }
            check(!e.guarded(), "без стражи не прикрыта");
            e.setHealth(e.getMaxHealth() * 0.76F);
            e.hurtResistantTime = 0;
            e.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            check(e.guarded(), "на 75% позвала королевскую стражу");
            float h = e.getHealth();
            e.hurtResistantTime = 0;
            e.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            float guardedLoss = h - e.getHealth();
            for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    e.getEntityBoundingBox().grow(30), k -> k.getTags().contains(ru.death.chaosshard.hero.EntityHero.ROYAL))) {
                k.setDead();
            }
            check(!e.guarded(), "стража пала");
            h = e.getHealth();
            e.hurtResistantTime = 0;
            e.attackEntityFrom(DamageSource.causePlayerDamage(me), 40F);
            float openLoss = h - e.getHealth();
            check(guardedLoss > 0 && guardedLoss < openLoss * 0.6F, "урон под стражей " + guardedLoss + ", без стражи " + openLoss);
            kill(e);
            check(p.state == ru.death.chaosshard.hero.HeroData.FALLEN, "Опора Элис пала");
            check(ru.death.chaosshard.hero.HeroData.get(ow).fallenCount() == 5, "пали все пять Опор");
            check(!ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, e.getEntityBoundingBox().grow(8),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.ELIS_SIGNET).isEmpty(), "Перстень Элис у тела");
        });
        step("трофеи Светочей в Логове: пассивки", () -> {
            BlockPos lp = base.add(-40, 1, 40);
            ow.getChunk(lp);
            for (BlockPos q : BlockPos.getAllInBox(lp.add(-4, -1, -4), lp.add(4, -1, 4))) ow.setBlockState(q, Blocks.STONE.getDefaultState(), 2);
            for (BlockPos q : BlockPos.getAllInBox(lp.add(-4, 0, -4), lp.add(4, 3, 4))) ow.setBlockToAir(q);
            ow.setBlockState(lp, ModBlocks.LAIR.getDefaultState(), 3);
            ru.death.chaosshard.lair.TileLair tl = (ru.death.chaosshard.lair.TileLair) ow.getTileEntity(lp);
            if (tl == null) { check(false, "нет Логова"); return; }
            tl.setOwner(me);
            BlockPos prevLair = ru.death.chaosshard.lair.LairData.get(ow).lairOf(me.getUniqueID(), 0);
            ru.death.chaosshard.lair.LairData.get(ow).set(me.getUniqueID(), 0, lp);
            me.setSneaking(true);
            int x = -3;
            for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) {
                me.setHeldItem(EnumHand.MAIN_HAND, ru.death.chaosshard.hero.Trophies.of(h));
                ru.death.chaosshard.hero.Trophies.place(me, ow, lp.add(x, -1, 2), EnumFacing.UP, EnumHand.MAIN_HAND);
                x += 1;
            }
            me.setSneaking(false);
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            int placed = 0;
            for (int dx = -3; dx <= 1; dx++) if (ow.getBlockState(lp.add(dx, 0, 2)).getBlock() == ModBlocks.TROPHY) placed++;
            check(placed == 5, "трофеев на подставках: " + placed);
            boolean all = true;
            for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) all &= ru.death.chaosshard.hero.TrophyData.has(me, h);
            check(all, "все пять в Логове — пассивки включены");
            // Каэль: первый удар слепит
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setPosition(lp.getX() + 2.5, lp.getY(), lp.getZ() + 0.5);
            ow.spawnEntity(z);
            new ru.death.chaosshard.hero.TrophyPassives().onHurt(new net.minecraftforge.event.entity.living.LivingHurtEvent(z, DamageSource.causePlayerDamage(me), 1F));
            check(z.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS), "Каэль: первый удар ослепил");
            z.setDead();
            // Лиан: скорость
            ru.death.chaosshard.hero.TrophyPassives.lian(me, true);
            check(me.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(
                    java.util.UUID.fromString("6b2c7d1e-4a7f-4c5e-9f33-1a2b3c4d5e6f")) != null, "Лиан: скорость +10%");
            ru.death.chaosshard.hero.TrophyPassives.lian(me, false);
            // Элис: житель дешевле
            net.minecraft.entity.passive.EntityVillager v = new net.minecraft.entity.passive.EntityVillager(ow, 0);
            v.setPosition(lp.getX() + 0.5, lp.getY(), lp.getZ() - 2.5);
            ow.spawnEntity(v);
            net.minecraft.village.MerchantRecipeList rl = v.getRecipes(me);
            int before = 0, after = 0;
            if (rl != null) for (net.minecraft.village.MerchantRecipe r : rl) before += r.getItemToBuy().getCount();
            ru.death.chaosshard.hero.TrophyPassives.discount(v, me);
            if (rl != null) for (net.minecraft.village.MerchantRecipe r : rl) after += r.getItemToBuy().getCount();
            check(rl != null && after < before, "Элис: цены жителя " + before + " → " + after);
            v.setDead();
            // убрать трофей — пассивка гаснет
            ow.destroyBlock(lp.add(-3, 0, 2), true);
            check(!ru.death.chaosshard.hero.TrophyData.has(me, ru.death.chaosshard.hero.Lightbearer.KAEL), "трофей убран — пассивка погасла");
            for (BlockPos q : BlockPos.getAllInBox(lp.add(-4, 0, -4), lp.add(4, 3, 4))) ow.setBlockToAir(q);
            for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) ru.death.chaosshard.hero.TrophyData.get(ow).set(me.getUniqueID(), h, false);
            ru.death.chaosshard.lair.LairData.get(ow).set(me.getUniqueID(), 0, prevLair);
        });

        section("Акт 4: дуэль с Гарротом и катсцена");
        step("Гаррот приходит: барабаны", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.clear(ow);
            ru.death.chaosshard.garrot.GarrotData gd = ru.death.chaosshard.garrot.GarrotData.get(ow);
            gd.submitted = false;
            gd.lastDuel = -1000000;
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            me.rotationYaw = me.rotationYawHead = 0;
            me.rotationPitch = 0;
            ru.death.chaosshard.garrot.GarrotDuel.start(me);
            check(ru.death.chaosshard.garrot.GarrotDuel.of(me) != null, "дуэль заведена");
        });
        until("катсцена началась", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.tick(me);
            ru.death.chaosshard.cutscene.CutsceneManager.tick(me);
            return ru.death.chaosshard.cutscene.CutsceneManager.playing(me);
        }, ru.death.chaosshard.garrot.GarrotDuel.WARN + 20);
        until("Гаррот вышел из портала", () -> {
            ru.death.chaosshard.cutscene.CutsceneManager.tick(me);
            ru.death.chaosshard.garrot.GarrotDuel.Duel d = ru.death.chaosshard.garrot.GarrotDuel.of(me);
            return d != null && d.lord != null && ow.getEntityFromUuid(d.lord) instanceof ru.death.chaosshard.garrot.EntityGarrot;
        }, 140);
        step("в сцене Гаррот не дерётся, меня держат на месте", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.Duel d = ru.death.chaosshard.garrot.GarrotDuel.of(me);
            ru.death.chaosshard.garrot.EntityGarrot g = (ru.death.chaosshard.garrot.EntityGarrot) ow.getEntityFromUuid(d.lord);
            check(g.scripted && g.getAttackTarget() == null, "Гаррот по сценарию");
            float h = g.getHealth();
            g.attackEntityFrom(DamageSource.causePlayerDamage(me), 50F);
            check(g.getHealth() == h, "в сцене Гаррот неуязвим");
            me.setPosition(me.posX + 5, me.posY, me.posZ);
            ru.death.chaosshard.cutscene.CutsceneManager.tick(me);
            check(Math.abs(me.posX - (base.getX() + 0.5)) < 0.5, "игрок стоит на месте: " + me.posX);
            ru.death.chaosshard.cutscene.Cutscene sc = ru.death.chaosshard.garrot.GarrotDuel.sceneFor(ow, d, me);
            net.minecraft.util.math.Vec3d[] end = sc.at(sc.length);
            check(end[0].distanceTo(me.getPositionEyes(1F)) < 0.2, "камера кончает сцену в глазах игрока");
            check(sc.lines.size() == 5, "реплик в сцене: " + sc.lines.size());
        });
        step("пропуск сцены — сразу дуэль", () -> {
            ru.death.chaosshard.cutscene.CutsceneManager.skip(me);
            ru.death.chaosshard.garrot.GarrotDuel.Duel d = ru.death.chaosshard.garrot.GarrotDuel.of(me);
            check(!ru.death.chaosshard.cutscene.CutsceneManager.playing(me), "сцена закрыта");
            check(d != null && d.state == ru.death.chaosshard.garrot.GarrotDuel.STATE_FIGHT, "дуэль началась");
            ru.death.chaosshard.garrot.EntityGarrot g = (ru.death.chaosshard.garrot.EntityGarrot) ow.getEntityFromUuid(d.lord);
            check(g != null && !g.scripted && g.getAttackTarget() == me, "Гаррот в бою и бьёт меня");
        });
        step("один на один: удары слуги не в счёт, мои — в счёт", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.Duel d = ru.death.chaosshard.garrot.GarrotDuel.of(me);
            ru.death.chaosshard.garrot.EntityGarrot g = (ru.death.chaosshard.garrot.EntityGarrot) ow.getEntityFromUuid(d.lord);
            EntityLiving sv = servants.get(Servants.Kind.EXECUTIONER);
            float h = g.getHealth();
            if (sv != null) {
                g.hurtResistantTime = 0;
                g.attackEntityFrom(DamageSource.causeMobDamage(sv), 50F);
                check(g.getHealth() == h, "удар слуги не прошёл");
            }
            g.hurtResistantTime = 0;
            g.attackEntityFrom(DamageSource.causePlayerDamage(me), 50F);
            check(g.getHealth() < h, "мой удар прошёл: " + h + " → " + g.getHealth());
        });
        step("круг не выпускает, секира бьёт перед собой", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.Duel d = ru.death.chaosshard.garrot.GarrotDuel.of(me);
            me.setPosition(d.center.x + ChaosConfig.order.duelRadius + 3, d.center.y, d.center.z);
            me.motionX = 0;
            ru.death.chaosshard.garrot.GarrotDuel.tick(me);
            check(me.motionX < 0, "вытолкнуло к центру: " + me.motionX);
            ru.death.chaosshard.garrot.EntityGarrot g = (ru.death.chaosshard.garrot.EntityGarrot) ow.getEntityFromUuid(d.lord);
            g.rotationYaw = g.renderYawOffset = g.rotationYawHead = 0;
            net.minecraft.entity.monster.EntityZombie front = new net.minecraft.entity.monster.EntityZombie(ow);
            net.minecraft.entity.monster.EntityZombie back = new net.minecraft.entity.monster.EntityZombie(ow);
            front.setNoAI(true);
            back.setNoAI(true);
            front.setPosition(g.posX, g.posY, g.posZ + 2);
            back.setPosition(g.posX, g.posY, g.posZ - 2);
            ow.spawnEntity(front);
            ow.spawnEntity(back);
            g.cleave(ow);
            check(front.getHealth() < front.getMaxHealth(), "секира задела стоящего впереди: " + front.getHealth());
            check(back.getHealth() == back.getMaxHealth(), "за спиной — мимо");
            front.setDead();
            back.setDead();
        });
        step("на 8% Гаррот склоняется, а не умирает", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.Duel d = ru.death.chaosshard.garrot.GarrotDuel.of(me);
            ru.death.chaosshard.garrot.EntityGarrot g = (ru.death.chaosshard.garrot.EntityGarrot) ow.getEntityFromUuid(d.lord);
            g.hurtResistantTime = 0;
            g.attackEntityFrom(DamageSource.causePlayerDamage(me), 100000F);
            check(g.isEntityAlive() && g.yielded && g.kneeling(), "жив и склонился");
            check(d.state == ru.death.chaosshard.garrot.GarrotDuel.STATE_YIELD, "дуэль — в поклоне");
            lordId = d.lord;
        });
        until("Гаррот поклялся и ушёл", () -> {
            ru.death.chaosshard.garrot.GarrotDuel.tick(me);
            return ru.death.chaosshard.garrot.GarrotDuel.of(me) == null;
        }, ru.death.chaosshard.garrot.GarrotDuel.YIELD_LEN + 40);
        step("клятва: набегов нет, Знамёна у ног", () -> {
            check(ru.death.chaosshard.garrot.GarrotData.get(ow).submitted, "Гаррот склонился (в мире)");
            Entity g = ow.getEntityFromUuid(lordId);
            check(g == null || !g.isEntityAlive(), "Гаррот ушёл");
            int banners = 0;
            for (net.minecraft.entity.item.EntityItem ei : ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, me.getEntityBoundingBox().grow(32),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.GARROT_BANNER)) {
                // капитаны набегов роняют по одному Знамени — считаем только связку от Гаррота
                banners = Math.max(banners, ei.getItem().getCount());
                ei.setDead();
            }
            check(banners == 6, "Знамён Мрачной армии от Гаррота: " + banners);
            Advancement a = server.getAdvancementManager().getAdvancement(new ResourceLocation("chaosshard", "m/garrot_duel"));
            check(a != null, "веха «Гаррот склонился» загружена");
        });
        step("Знамя на Якорь — солдаты Гаррота в гарнизоне", () -> {
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            if (a == null) { check(false, "Якоря нет"); return; }
            List<TileAnchor> c = ru.death.chaosshard.anchor.AnchorClusters.cluster(ow, a);
            int room = ru.death.chaosshard.anchor.AnchorClusters.garrisonLimit(c) - ru.death.chaosshard.anchor.AnchorClusters.garrison(ow, c).size();
            me.setPosition(anchorPos.getX() + 2.5, anchorPos.getY(), anchorPos.getZ() + 0.5);
            me.setHeldItem(EnumHand.MAIN_HAND, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.GARROT_BANNER, 2));
            PlayerInteractEvent.RightClickBlock ev = new PlayerInteractEvent.RightClickBlock(me, EnumHand.MAIN_HAND, anchorPos, EnumFacing.UP, new Vec3d(0.5, 1, 0.5));
            MinecraftForge.EVENT_BUS.post(ev);
            check(ev.isCanceled(), "Знамя по Якорю сработало");
            List<ru.death.chaosshard.garrot.EntityGarrot> vs = ow.getEntitiesWithinAABB(ru.death.chaosshard.garrot.EntityGarrot.class,
                    new AxisAlignedBB(anchorPos).grow(12), x -> x.variant() == ru.death.chaosshard.garrot.EntityGarrot.VASSAL);
            int want = Math.max(0, Math.min(2, room));
            check(vs.size() == want, "солдат Гаррота: " + vs.size() + " (места в гарнизоне было " + room + ")");
            check(vs.stream().allMatch(v -> Servants.isServantOf(v, me.getUniqueID())
                    && v.getEntityData().getLong(Servants.TAG_ANCHOR) == anchorPos.toLong()), "служат мне и стерегут Якорь");
            check(want == 0 || me.getHeldItemMainhand().getCount() == 1, "Знамя потрачено: осталось " + me.getHeldItemMainhand().getCount());
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        });

        section("Бой: зачарования, отголоски, чемпионы");
        step("дальше набеги только вручную: свои набеги Якоря мешают проверкам", () -> {
            ChaosConfig.order.raids = false;
            int n = 0;
            for (Entity x : new ArrayList<>(ow.loadedEntityList)) {
                if (x.getTags().contains(ru.death.chaosshard.garrot.GarrotRaids.TAG_ARMY)) {
                    ow.removeEntity(x);
                    n++;
                }
            }
            line("  убрано бойцов набегов: " + n);
        });
        step("зачарования Хаоса", () -> {
            check(ru.death.chaosshard.enchant.ChaosEnchants.BANE_OF_ORDER != null && ru.death.chaosshard.enchant.ChaosEnchants.SHARD_HARVEST != null,
                    "зачарования зарегистрированы");
            net.minecraft.item.ItemStack sword = new net.minecraft.item.ItemStack(net.minecraft.init.Items.WOODEN_SWORD);
            sword.addEnchantment(ru.death.chaosshard.enchant.ChaosEnchants.BANE_OF_ORDER, 4);
            // сам расчёт урона — без брони и аффиксов Champions, которые меняют итог
            ru.death.chaosshard.structure.EntityOrderKnight k = new ru.death.chaosshard.structure.EntityOrderKnight(ow);
            k.setPosition(base.getX() + 6.5, base.getY() + 1, base.getZ() + 0.5);
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            net.minecraftforge.event.entity.living.LivingHurtEvent plain =
                    new net.minecraftforge.event.entity.living.LivingHurtEvent(k, DamageSource.causePlayerDamage(me), 2F);
            ru.death.chaosshard.enchant.ChaosEnchants.onHurt(plain);
            me.setHeldItem(EnumHand.MAIN_HAND, sword);
            net.minecraftforge.event.entity.living.LivingHurtEvent bane =
                    new net.minecraftforge.event.entity.living.LivingHurtEvent(k, DamageSource.causePlayerDamage(me), 2F);
            ru.death.chaosshard.enchant.ChaosEnchants.onHurt(bane);
            check(plain.getAmount() == 2F && bane.getAmount() == 12F,
                    "«Погибель Порядка» IV по рыцарю: " + plain.getAmount() + " → " + bane.getAmount());
            net.minecraftforge.event.entity.living.LivingHurtEvent zombie = new net.minecraftforge.event.entity.living.LivingHurtEvent(
                    new net.minecraft.entity.monster.EntityZombie(ow), DamageSource.causePlayerDamage(me), 2F);
            ru.death.chaosshard.enchant.ChaosEnchants.onHurt(zombie);
            check(zombie.getAmount() == 2F, "по зомби без прибавки: " + zombie.getAmount());
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        });
        step("отголоски Светочей", () -> {
            for (ru.death.chaosshard.hero.Lightbearer h : ru.death.chaosshard.hero.Lightbearer.values()) {
                net.minecraft.item.ItemStack e = ru.death.chaosshard.hero.ItemHeroEcho.of(h);
                check(!e.isEmpty() && ru.death.chaosshard.hero.ItemHeroEcho.hero(e) == h, "отголосок " + h.id());
                if (Loader.isModLoaded("tconstruct")) {
                    check(slimeknights.tconstruct.library.TinkerRegistry.getModifier("chaosshard_echo_" + h.id()) != null,
                            "модификатор Tinkers «" + h.id() + "»");
                }
            }
        });
        if (Loader.isModLoaded("champions")) {
            step("Орден выставляет чемпионов, Светочи — никогда", () -> {
                int champs = 0;
                float was = ChaosConfig.compat.championBase;
                ChaosConfig.compat.championBase = 1F;
                for (int i = 0; i < 20; i++) {
                    ru.death.chaosshard.structure.EntityOrderKnight k = new ru.death.chaosshard.structure.EntityOrderKnight(ow);
                    k.setNoAI(true);
                    k.setPosition(base.getX() + 8.5, base.getY() + 1, base.getZ() + 0.5);
                    ow.spawnEntity(k);
                    if (ru.death.chaosshard.compat.champions.ChampionsCompat.tier(k) > 0) {
                        champs++;
                    }
                    k.setDead();
                }
                ChaosConfig.compat.championBase = was;
                line("  павших Опор " + ru.death.chaosshard.hero.HeroData.get(ow).fallenCount() + ", чемпионов из 20: " + champs);
                check(champs == 20, "при шансе 100% все рыцари — чемпионы: " + champs);
                ru.death.chaosshard.hero.EntityHero hero = new ru.death.chaosshard.hero.EntityHero(ow);
                hero.setPosition(base.getX() + 8.5, base.getY() + 1, base.getZ() + 0.5);
                ow.spawnEntity(hero);
                check(ru.death.chaosshard.compat.champions.ChampionsCompat.tier(hero) == 0, "Светоч не чемпион");
                hero.setDead();
            });
        }

        section("Техника: Скверный генератор, Индукция, гул машин");
        step("Сердце с «Индукцией» растёт быстрее, пока есть ток", () -> {
            BlockPos hp = base.add(-26, 1, -26);
            ow.setBlockState(hp, ModBlocks.ISLAND_HEART.getDefaultState(), 3);
            ru.death.chaosshard.heart.TileHeart h = (ru.death.chaosshard.heart.TileHeart) ow.getTileEntity(hp);
            if (h == null) { check(false, "Сердца нет"); return; }
            check(h.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, EnumFacing.NORTH), "Сердце принимает RF/FE");
            h.modules.setStackInSlot(0, new net.minecraft.item.ItemStack(
                    net.minecraft.item.Item.getByNameOrId("chaosshard:heart_module_engine"), 1));
            int t0 = h.getTimer();
            h.update();
            int idle = h.getTimer() - t0;
            int got = h.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, EnumFacing.NORTH).receiveEnergy(4000, false);
            check(got == 4000, "принято " + got + " RF");
            t0 = h.getTimer();
            h.update();
            int fast = h.getTimer() - t0;
            check(idle == 1 && fast == 2, "шаг роста без тока " + idle + ", с током " + fast);
            check(h.isPowered() && h.battery.getEnergyStored() == 4000 - ChaosConfig.heart.engineCost,
                    "списано " + (4000 - h.battery.getEnergyStored()) + " RF");
            ow.setBlockToAir(hp);
        });
        step("Скверный генератор: стоит без Скверны, на Скверне у Якоря — работает", () -> {
            genPos = anchorPos.add(3, 0, 0);
            ow.setBlockState(genPos.down(), Blocks.STONE.getDefaultState(), 3);
            ow.setBlockState(genPos, ModBlocks.DEFILED_GENERATOR.getDefaultState(), 3);
            ow.setBlockState(genPos.east(), ModBlocks.ISLAND_HEART.getDefaultState(), 3);
            ru.death.chaosshard.tech.TileDefiledGenerator g = (ru.death.chaosshard.tech.TileDefiledGenerator) ow.getTileEntity(genPos);
            if (g == null) { check(false, "генератора нет"); return; }
            check(g.reason() == 1, "на чистом камне стоит");
            check(g.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, EnumFacing.UP)
                    && !g.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, EnumFacing.UP).canReceive(), "только отдаёт ток");
            ow.setBlockState(genPos.down(), ModBlocks.CHAOS_STONE.getDefaultState(), 3);
            check(g.reason() == 0, "на Скверне в краю Якоря — работает");
        });
        until("генератор кормит соседа", () -> {
            net.minecraft.tileentity.TileEntity te = ow.getTileEntity(genPos.east());
            return te instanceof ru.death.chaosshard.heart.TileHeart && ((ru.death.chaosshard.heart.TileHeart) te).battery.getEnergyStored() > 0;
        }, 80);
        step("выработка по уровню Якоря", () -> {
            ru.death.chaosshard.tech.TileDefiledGenerator g = (ru.death.chaosshard.tech.TileDefiledGenerator) ow.getTileEntity(genPos);
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            check(g != null && a != null && g.rate() == a.level() * ChaosConfig.tech.generatorPerLevel,
                    "выработка " + (g == null ? -1 : g.rate()) + " RF/т при уровне " + (a == null ? -1 : a.level()));
            ru.death.chaosshard.heart.TileHeart h = (ru.death.chaosshard.heart.TileHeart) ow.getTileEntity(genPos.east());
            check(h != null && h.battery.getEnergyStored() > 0, "Сердце рядом получило " + (h == null ? 0 : h.battery.getEnergyStored()) + " RF");
            ow.setBlockToAir(genPos.east());
            ow.setBlockToAir(genPos);
        });
        step("гул машин: провода не машины, замер не падает", () -> {
            check(ru.death.chaosshard.tech.MachineHum.carrier("universal_cable") && ru.death.chaosshard.tech.MachineHum.carrier("energycube")
                    && !ru.death.chaosshard.tech.MachineHum.carrier("machine_pulverizer"), "провода и хранилища отсеяны");
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            if (a == null) { check(false, "Якоря нет"); return; }
            int n = a.hum.sample(ow, anchorPos);
            n = a.hum.sample(ow, anchorPos);
            check(n == 0, "без чужих машин гула нет: " + n);
        });

        section("Магия и лут: Reliquary, Bountiful Baubles");
        if (Loader.isModLoaded("xreliquary")) {
            step("ритуалы на добыче с Ордена", () -> {
                check(ru.death.chaosshard.ritual.Ritual.HOLY_DEFILE.available() && ru.death.chaosshard.ritual.Ritual.FALLEN_FEATHER.available(),
                        "ритуалы на святой воде и пере доступны");
                check(ru.death.chaosshard.ritual.Ritual.find(ru.death.chaosshard.ritual.Ritual.HOLY_DEFILE.offeringStacks())
                        == ru.death.chaosshard.ritual.Ritual.HOLY_DEFILE, "Чаша узнаёт «Порченую святость»");
                EntityLiving sv = Servants.spawn(me, Servants.Kind.HUSK, base.add(-6, 1, 6));
                if (sv == null) {
                    check(false, "слуга для пера не появился");
                    return;
                }
                sv.removePotionEffect(net.minecraft.init.MobEffects.SPEED);
                boolean ok = ru.death.chaosshard.ritual.RitualEffects.use(me, ow, sv.getPosition(), ru.death.chaosshard.ritual.Ritual.FALLEN_FEATHER);
                net.minecraft.potion.PotionEffect sp = sv.getActivePotionEffect(net.minecraft.init.MobEffects.SPEED);
                check(ok && sp != null && sp.getAmplifier() == 1, "«Падшее перо»: слуга получил Скорость II");
                sv.setDead();
            });
        }
        step("сундуки Ордена и реликвии Светочей", () -> {
            net.minecraft.world.storage.loot.LootTable t = ow.getLootTableManager().getLootTableFromLocation(
                    new ResourceLocation("chaosshard", "structures/spire"));
            boolean any = Loader.isModLoaded("xreliquary") || Loader.isModLoaded("bountifulbaubles");
            check((t.getPool("chaosshard_relics") != null) == any, "пул реликвий в сундуках Ордена: " + (t.getPool("chaosshard_relics") != null));
            BlockPos at = base.add(-20, 1, 20);
            ru.death.chaosshard.compat.loot.OrderLoot.heroRelics(ow, ru.death.chaosshard.hero.Lightbearer.KAEL, at);
            int n = 0;
            for (net.minecraft.entity.item.EntityItem ei : ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,
                    new AxisAlignedBB(at).grow(3))) {
                String id = String.valueOf(ei.getItem().getItem().getRegistryName());
                if (id.equals("xreliquary:mercy_cross") || id.equals("bountifulbaubles:shieldcobalt")) {
                    n++;
                }
                ei.setDead();
            }
            int want = (Loader.isModLoaded("xreliquary") ? 1 : 0) + (Loader.isModLoaded("bountifulbaubles") ? 1 : 0);
            check(n == want, "Каэль оставил реликвий: " + n + " из " + want);
        });

        section("Мир и мобы: Lycanite, подземелья");
        step("сундуки подземелий прячут вещи Хаоса", () -> {
            net.minecraft.world.storage.loot.LootTable t = ow.getLootTableManager().getLootTableFromLocation(
                    new ResourceLocation("minecraft", "chests/simple_dungeon"));
            check(t.getPool("chaosshard_dungeon") != null, "пул Хаоса в сундуке подземелья");
        });
        if (Loader.isModLoaded("lycanitesmobs")) {
            step("демоны Скверны: селятся у Якоря, Осколка не трогают, Орден бьют", () -> {
                TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
                if (a == null || !a.isCaptured()) { check(false, "нет захваченного Якоря"); return; }
                boolean made = false;
                for (int i = 0; i < 100 && !made; i++) {
                    made = ru.death.chaosshard.compat.world.WildChaos.populate(ow, a);
                }
                List<EntityLiving> ds = ow.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(anchorPos).grow(a.radius(), 32, a.radius()),
                        x -> x.getTags().contains(ru.death.chaosshard.compat.world.WildChaos.TAG_DEMON));
                check(!ds.isEmpty(), "демонов у Якоря: " + ds.size());
                if (ds.isEmpty()) return;
                EntityLiving d = ds.get(0);
                line("  демон: " + net.minecraft.entity.EntityList.getKey(d));
                d.setAttackTarget(me);
                check(d.getAttackTarget() == null, "на Осколка не нападает");
                ru.death.chaosshard.structure.EntityOrderKnight k = new ru.death.chaosshard.structure.EntityOrderKnight(ow);
                k.setPosition(d.posX + 2, d.posY, d.posZ);
                ow.spawnEntity(k);
                d.setAttackTarget(k);
                check(d.getAttackTarget() == k, "на рыцаря Ордена — нападает");
                d.setAttackTarget(null);
                k.setDead();
                for (EntityLiving x : ds) x.setDead();
            });
            step("капитан Ордена зовёт стражей света", () -> {
                ru.death.chaosshard.structure.EntityOrderKnight cap = new ru.death.chaosshard.structure.EntityOrderKnight(ow);
                cap.setPosition(base.getX() - 10.5, base.getY() + 1, base.getZ() - 10.5);
                ow.spawnEntity(cap);
                int n = ru.death.chaosshard.compat.world.WildChaos.callLight(cap, me);
                List<EntityLiving> ls = ow.getEntitiesWithinAABB(EntityLiving.class, cap.getEntityBoundingBox().grow(6),
                        x -> x.getTags().contains(ru.death.chaosshard.compat.world.WildChaos.TAG_LIGHT));
                check(n > 0 && ls.size() >= n, "стражей света: " + n + " (рядом " + ls.size() + ")");
                for (EntityLiving l : ls) {
                    l.setAttackTarget(cap);
                    check(l.getAttackTarget() == null, "страж не бьёт своих: " + net.minecraft.entity.EntityList.getKey(l));
                    l.setDead();
                }
                cap.setDead();
            });
        }

        section("Быт: еда слуг, путевые камни");
        step("слуга ест самое сытное из сундука и крепнет", () -> {
            BlockPos cp = base.add(-14, 1, 14);
            ow.setBlockState(cp, Blocks.CHEST.getDefaultState(), 3);
            net.minecraft.tileentity.TileEntityChest ch = (net.minecraft.tileentity.TileEntityChest) ow.getTileEntity(cp);
            net.minecraft.item.ItemStack best = new net.minecraft.item.ItemStack(net.minecraft.init.Items.COOKED_BEEF);
            int bestHeal = 8;
            for (net.minecraft.item.Item it : net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS) {
                if (it instanceof net.minecraft.item.ItemFood && it.getRegistryName() != null && "harvestcraft".equals(it.getRegistryName().getNamespace())) {
                    int h = ((net.minecraft.item.ItemFood) it).getHealAmount(new net.minecraft.item.ItemStack(it));
                    if (h > bestHeal) {
                        bestHeal = h;
                        best = new net.minecraft.item.ItemStack(it);
                    }
                }
            }
            ch.setInventorySlotContents(0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.BREAD, 4));
            ch.setInventorySlotContents(1, best.copy());
            EntityLiving sv = Servants.spawn(me, Servants.Kind.HUSK, cp.add(2, 0, 0));
            if (sv == null) { check(false, "слуга не появился"); return; }
            int ate = ru.death.chaosshard.compat.life.ServantMeals.tryEat(sv);
            line("  лучшая еда: " + best.getItem().getRegistryName() + " (" + bestHeal + ")");
            check(ate == bestHeal && ch.getStackInSlot(1).isEmpty() && ch.getStackInSlot(0).getCount() == 4, "съел самое сытное: " + ate);
            check((sv.isPotionActive(net.minecraft.init.MobEffects.REGENERATION) || sv.isPotionActive(net.minecraft.init.MobEffects.ABSORPTION))
                    && sv.isPotionActive(net.minecraft.init.MobEffects.STRENGTH), "сытый: Регенерация (у нежити Поглощение) и Сила");
            check(bestHeal < 10 || sv.isPotionActive(net.minecraft.init.MobEffects.RESISTANCE), "сытное блюдо — ещё и Сопротивление");
            check(ru.death.chaosshard.compat.life.ServantMeals.tryEat(sv) == 0, "сытый второй раз не ест");
            sv.setDead();
            ch.clear();
            ow.setBlockToAir(cp);
        });
        if (Loader.isModLoaded("waystones")) {
            step("путевые камни: у Якоря и по имени", () -> {
                BlockPos at = base.add(-30, 1, -30);
                boolean ok = ru.death.chaosshard.compat.life.ChaosWaystones.place(ow, at, "Проверка");
                check(ok && ru.death.chaosshard.compat.life.ChaosWaystones.nearby(ow, at, 6), "камень поставлен");
                String name = "?";
                for (BlockPos p : BlockPos.getAllInBox(at.add(-6, -4, -6), at.add(6, 6, 6))) {
                    net.minecraft.tileentity.TileEntity te = ow.getTileEntity(p);
                    if (te != null && te.getClass().getName().contains("Waystone")) {
                        try {
                            name = String.valueOf(te.getClass().getMethod("getWaystoneName").invoke(te));
                        } catch (Exception ex) {
                            name = ex.toString();
                        }
                        break;
                    }
                }
                check("Проверка".equals(name), "имя камня: " + name);
                TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
                if (a != null) {
                    a.growWaystone();
                    check(a.level() < ChaosConfig.compat.anchorWaystoneLevel || ru.death.chaosshard.compat.life.ChaosWaystones.nearby(ow, anchorPos, 8),
                            "Якорь " + a.level() + " ур. вырастил камень");
                }
            });
        }

        section("Нить: перелив сверх 5-го уровня");
        step("избыток Нити уходит зверям и в перки Astral", () -> {
            int cap = ru.death.chaosshard.thread.ThreadManager.cap();
            int keep = ru.death.chaosshard.thread.ThreadManager.getPoints(me);
            ru.death.chaosshard.servant.BeastData.Entry he = ru.death.chaosshard.servant.Beasts.entry(ow, me.getUniqueID(), ru.death.chaosshard.servant.BeastData.Beast.HARE);
            boolean wasKnown = he.known, wasAsc = he.ascended;
            int wasXp = he.xp;
            he.known = true;
            he.ascended = false;
            he.xp = 0;
            double astral0 = ChaosShard.astralLoaded ? ru.death.chaosshard.compat.astral.AstralOverflow.exp(me) : 0;
            ru.death.chaosshard.thread.ThreadManager.setPoints(me, cap - 10);
            ru.death.chaosshard.thread.ThreadManager.addPoints(me, 110);
            check(ru.death.chaosshard.thread.ThreadManager.getPoints(me) == cap, "Нить упёрлась в потолок: " + ru.death.chaosshard.thread.ThreadManager.getPoints(me));
            check(he.xp >= 100 * ChaosConfig.thread.overflowBeastXp, "зверю перелилось: " + he.xp);
            if (ChaosShard.astralLoaded) {
                double astral1 = ru.death.chaosshard.compat.astral.AstralOverflow.exp(me);
                check(astral1 - astral0 >= 100.0 * ChaosConfig.thread.overflowAstralExp, "перкам Astral: +" + (astral1 - astral0));
            }
            ru.death.chaosshard.thread.ThreadManager.addPoints(me, -50);
            check(ru.death.chaosshard.thread.ThreadManager.getPoints(me) == cap - 50, "потеря Нити ниже потолка работает как раньше");
            he.known = wasKnown;
            he.ascended = wasAsc;
            he.xp = wasXp;
            ru.death.chaosshard.thread.ThreadManager.setPoints(me, keep);
        });

        section("Заклинания: режимы Гримуара, формы, перки");
        step("режимы Гримуара: Книга → Магия → Привязка", () -> {
            net.minecraft.item.ItemStack g = new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.GRIMOIRE);
            check(ru.death.chaosshard.spell.SpellCaster.mode(g) == 0, "новый Гримуар — Книга");
            ru.death.chaosshard.spell.SpellCaster.toggleMode(me, g);
            check(ru.death.chaosshard.spell.SpellCaster.magicMode(g), "дальше Магия");
            ru.death.chaosshard.spell.SpellCaster.toggleMode(me, g);
            check(ru.death.chaosshard.spell.SpellCaster.bindMode(g), "дальше Привязка");
            ru.death.chaosshard.spell.SpellCaster.toggleMode(me, g);
            check(ru.death.chaosshard.spell.SpellCaster.mode(g) == 0, "и снова Книга");
            net.minecraft.nbt.NBTTagCompound old = new net.minecraft.nbt.NBTTagCompound();
            old.setBoolean("mode", true);
            g.setTagCompound(old);
            check(ru.death.chaosshard.spell.SpellCaster.magicMode(g), "старый Гримуар в Магии остаётся в Магии");
        });
        step("прокачка: очки по Нити, перки, формы, сброс", () -> {
            ru.death.chaosshard.spell.SpellData.reset(me);
            int pts = ru.death.chaosshard.spell.SpellData.points(me);
            check(pts == ru.death.chaosshard.thread.ThreadManager.getLevel(me), "очков на заклинание = уровень Нити: " + pts);
            check(!ru.death.chaosshard.spell.SpellData.formOpen(me, ru.death.chaosshard.spell.Spell.BOLT, 1), "Цепь закрыта без перка");
            check(ru.death.chaosshard.spell.SpellData.formOpen(me, ru.death.chaosshard.spell.Spell.DASH, 1), "Скачок открыт сразу");
            int chain = ru.death.chaosshard.spell.SpellPerks.index(ru.death.chaosshard.spell.Spell.BOLT, "chain");
            check(ru.death.chaosshard.spell.SpellData.buy(me, ru.death.chaosshard.spell.Spell.BOLT, chain), "куплен перк Цепь");
            check(ru.death.chaosshard.spell.SpellData.form(me, ru.death.chaosshard.spell.Spell.BOLT) == 1, "форма сразу стала Цепью");
            int bought = 1;
            while (ru.death.chaosshard.spell.SpellData.free(me, ru.death.chaosshard.spell.Spell.BOLT) > 0 && bought < 10) {
                boolean any = false;
                for (int k = 0; k < ru.death.chaosshard.spell.SpellPerks.perks(ru.death.chaosshard.spell.Spell.BOLT).length; k++) {
                    if (ru.death.chaosshard.spell.SpellData.buy(me, ru.death.chaosshard.spell.Spell.BOLT, k)) {
                        any = true;
                        bought++;
                        break;
                    }
                }
                if (!any) break;
            }
            check(ru.death.chaosshard.spell.SpellData.spent(me, ru.death.chaosshard.spell.Spell.BOLT) == Math.min(pts, 6),
                    "потрачено очков: " + ru.death.chaosshard.spell.SpellData.spent(me, ru.death.chaosshard.spell.Spell.BOLT));
            check(!ru.death.chaosshard.spell.SpellData.buy(me, ru.death.chaosshard.spell.Spell.BOLT, chain) || pts > 6, "больше очков нет");
            check(ru.death.chaosshard.spell.SpellData.spent(me, ru.death.chaosshard.spell.Spell.SPROUT) == 0, "у других заклинаний очки свои");
            check(ru.death.chaosshard.spell.SpellCaster.cost(me, ru.death.chaosshard.spell.Spell.BOLT) < ru.death.chaosshard.spell.Spell.BOLT.energy
                    || !ru.death.chaosshard.spell.SpellData.has(me, ru.death.chaosshard.spell.Spell.BOLT, "economy"), "Экономия снижает цену");
            ru.death.chaosshard.spell.SpellData.reset(me);
            check(ru.death.chaosshard.spell.SpellData.spent(me, ru.death.chaosshard.spell.Spell.BOLT) == 0
                    && ru.death.chaosshard.spell.SpellData.form(me, ru.death.chaosshard.spell.Spell.BOLT) == 0, "Чистые страницы всё сбросили");
        });
        step("заклинания по блокам и существам", () -> {
            BlockPos o = base.add(70, 0, 70);
            ow.getChunk(o);
            for (BlockPos q : BlockPos.getAllInBox(o.add(-12, 0, -12), o.add(12, 0, 12))) ow.setBlockState(q, Blocks.STONE.getDefaultState(), 2);
            for (BlockPos q : BlockPos.getAllInBox(o.add(-12, 1, -12), o.add(12, 8, 12))) ow.setBlockToAir(q);
            java.util.function.Consumer<ru.death.chaosshard.spell.Spell> ready = sp -> {
                ru.death.chaosshard.spell.SpellCaster.clearCooldowns(me);
                ru.death.chaosshard.spell.ChaosEnergy.set(me, 9999);
            };
            me.setPositionAndRotation(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0, 0);
            ru.death.chaosshard.spell.SpellData.reset(me);
            // Стрела: одиночная
            net.minecraft.entity.monster.EntityZombie z1 = new net.minecraft.entity.monster.EntityZombie(ow);
            z1.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() + 5.5);
            ow.spawnEntity(z1);
            ready.accept(ru.death.chaosshard.spell.Spell.BOLT);
            float hp = z1.getHealth();
            boolean ok = ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.BOLT, null, EnumFacing.UP, z1, null);
            check(ok && z1.getHealth() < hp, "Стрела ранила цель: " + hp + " → " + z1.getHealth());
            // Стрела: цепь
            ru.death.chaosshard.spell.SpellData.buy(me, ru.death.chaosshard.spell.Spell.BOLT, ru.death.chaosshard.spell.SpellPerks.index(ru.death.chaosshard.spell.Spell.BOLT, "chain"));
            net.minecraft.entity.monster.EntityZombie z2 = new net.minecraft.entity.monster.EntityZombie(ow);
            net.minecraft.entity.monster.EntityZombie z3 = new net.minecraft.entity.monster.EntityZombie(ow);
            z2.setPosition(o.getX() + 3.5, o.getY() + 1, o.getZ() + 5.5);
            z3.setPosition(o.getX() - 1.5, o.getY() + 1, o.getZ() + 6.5);
            ow.spawnEntity(z2);
            ow.spawnEntity(z3);
            z1.setHealth(z1.getMaxHealth());
            ready.accept(ru.death.chaosshard.spell.Spell.BOLT);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.BOLT, null, EnumFacing.UP, z1, null);
            check(z2.getHealth() < z2.getMaxHealth() && z3.getHealth() < z3.getMaxHealth(), "Цепь перескочила на двоих");
            // Стрела: взрыв по блоку
            z2.setHealth(z2.getMaxHealth());
            z3.setHealth(z3.getMaxHealth());
            ready.accept(ru.death.chaosshard.spell.Spell.BOLT);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.BOLT, o.add(3, 0, 6), EnumFacing.UP, null, null);
            check(z2.getHealth() < z2.getMaxHealth() && ow.getBlockState(o.add(3, 0, 6)).getBlock() == Blocks.STONE, "взрыв по блоку ранил, блок цел");
            for (net.minecraft.entity.monster.EntityZombie z : new net.minecraft.entity.monster.EntityZombie[]{z1, z2, z3}) z.setDead();
            // Росток: печь ускоряется
            BlockPos fur = o.add(-4, 1, 0);
            ow.setBlockState(fur, Blocks.FURNACE.getDefaultState(), 3);
            ready.accept(ru.death.chaosshard.spell.Spell.SPROUT);
            int was = ru.death.chaosshard.spell.SpellAccel.active();
            check(ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.SPROUT, fur, EnumFacing.UP, null, null)
                    && ru.death.chaosshard.spell.SpellAccel.active() > was, "Росток ускорил печь");
            ow.setBlockToAir(fur);
            // Мост: вперёд 8 блоков (смотрим на юг)
            ready.accept(ru.death.chaosshard.spell.Spell.BRIDGE);
            BlockPos from = o.add(0, 3, 0);
            ow.setBlockState(from, Blocks.STONE.getDefaultState());
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.BRIDGE, from, EnumFacing.UP, null, null);
            int bridge = 0;
            for (int i = 1; i <= 8; i++) if (ow.getBlockState(from.offset(me.getHorizontalFacing(), i)).getBlock() == ru.death.chaosshard.init.ModBlocks.CHAOS_STONE) bridge++;
            check(bridge == 8, "Мост: блоков " + bridge);
            for (int i = 0; i <= 8; i++) ow.setBlockToAir(from.offset(me.getHorizontalFacing(), i));
            // Источник: вода, водяной удар
            ready.accept(ru.death.chaosshard.spell.Spell.SPRING);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.SPRING, o.add(5, 0, -5), EnumFacing.UP, null, null);
            check(ow.getBlockState(o.add(5, 1, -5)).getMaterial() == net.minecraft.block.material.Material.WATER, "Источник: вода");
            ow.setBlockToAir(o.add(5, 1, -5));
            net.minecraft.entity.monster.EntityZombie zw = new net.minecraft.entity.monster.EntityZombie(ow);
            zw.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() + 3.5);
            ow.spawnEntity(zw);
            ready.accept(ru.death.chaosshard.spell.Spell.SPRING);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.SPRING, null, EnumFacing.UP, zw, null);
            check(zw.motionZ > 0.5, "водяной удар отбросил цель: " + zw.motionZ);
            zw.setDead();
            // Разлом: куб 3×3×3 в камне
            BlockPos rc = o.add(8, -3, 8);
            for (BlockPos q : BlockPos.getAllInBox(rc.add(-1, -1, -1), rc.add(1, 1, 1))) ow.setBlockState(q, Blocks.STONE.getDefaultState(), 2);
            ow.setBlockState(rc.add(1, 1, 1), Blocks.OBSIDIAN.getDefaultState(), 2);
            ready.accept(ru.death.chaosshard.spell.Spell.RIFT);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.RIFT, rc, EnumFacing.UP, null, null);
            check(ow.isAirBlock(rc) && ow.isAirBlock(rc.add(-1, -1, -1)) && ow.isAirBlock(rc.add(1, 1, 1)), "Разлом раздробил камень и обсидиан");
            check(!ru.death.chaosshard.spell.SpellCaster.breakable(ow, new BlockPos(o.getX(), 0, o.getZ())), "коренную породу не берёт");
            // Рывок: скачок и бросок к цели
            ready.accept(ru.death.chaosshard.spell.Spell.DASH);
            ru.death.chaosshard.spell.SpellData.setForm(me, ru.death.chaosshard.spell.Spell.DASH, 1);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.DASH, o.add(6, 0, 0), EnumFacing.UP, null, null);
            check(Math.abs(me.posX - (o.getX() + 6.5)) < 0.01 && Math.abs(me.posY - (o.getY() + 1)) < 0.01, "Скачок перенёс на блок");
            net.minecraft.entity.monster.EntityZombie zd = new net.minecraft.entity.monster.EntityZombie(ow);
            zd.setPosition(o.getX() - 6.5, o.getY() + 1, o.getZ() + 0.5);
            ow.spawnEntity(zd);
            me.setHeldItem(EnumHand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_SWORD));
            ready.accept(ru.death.chaosshard.spell.Spell.DASH);
            float zh = zd.getHealth();
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.DASH, null, EnumFacing.UP, zd, null);
            check(me.getDistance(zd) < 2.5 && zd.getHealth() < zh, "бросок к цели и удар: " + zh + " → " + zd.getHealth());
            zd.setDead();
            me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            // Звёздный взор: руды
            ru.death.chaosshard.spell.SpellData.buy(me, ru.death.chaosshard.spell.Spell.STARSIGHT,
                    ru.death.chaosshard.spell.SpellPerks.index(ru.death.chaosshard.spell.Spell.STARSIGHT, "ores"));
            ow.setBlockState(o.add(2, -2, 2), Blocks.DIAMOND_ORE.getDefaultState(), 2);
            check(ru.death.chaosshard.spell.SpellCaster.oreTier(Blocks.DIAMOND_ORE.getDefaultState()) == 2
                    && ru.death.chaosshard.spell.SpellCaster.oreTier(Blocks.COAL_ORE.getDefaultState()) == 0
                    && ru.death.chaosshard.spell.SpellCaster.oreTier(Blocks.STONE.getDefaultState()) < 0, "ценность руд");
            ready.accept(ru.death.chaosshard.spell.Spell.STARSIGHT);
            check(ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.STARSIGHT, null, EnumFacing.UP, null, null),
                    "взгляд сквозь камень работает");
            // Форма Хаоса: перки длительности
            ru.death.chaosshard.spell.SpellData.buy(me, ru.death.chaosshard.spell.Spell.FORM, ru.death.chaosshard.spell.SpellPerks.index(ru.death.chaosshard.spell.Spell.FORM, "long"));
            ready.accept(ru.death.chaosshard.spell.Spell.FORM);
            ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.FORM, null, EnumFacing.UP, null, null);
            net.minecraft.potion.PotionEffect st = me.getActivePotionEffect(net.minecraft.init.MobEffects.STRENGTH);
            check(st != null && st.getDuration() > 20 * 40, "Долгая форма: " + (st == null ? 0 : st.getDuration()));
            // Зов: без своего Якоря — не работает
            ru.death.chaosshard.spell.SpellData.setForm(me, ru.death.chaosshard.spell.Spell.RECALL, 2);
            ready.accept(ru.death.chaosshard.spell.Spell.RECALL);
            BlockPos before = me.getPosition();
            boolean rec = ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.RECALL, null, EnumFacing.UP, null, null);
            check(rec ? me.getPosition().distanceSq(before) > 4 : true, "Зов к Якорю: " + (rec ? "перенёс" : "Якоря нет — остался"));
            ru.death.chaosshard.spell.SpellData.reset(me);
            me.clearActivePotions();
            ru.death.chaosshard.spell.SpellCaster.clearCooldowns(me);
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        });

        if (Loader.isModLoaded("tconstruct")) {
            step("материалы Хаоса: уровни и модификаторы заклинаний", () -> {
                java.util.List<slimeknights.tconstruct.library.materials.Material> mats = java.util.Arrays.asList(
                        slimeknights.tconstruct.library.TinkerRegistry.getMaterial(ru.death.chaosshard.compat.tinkers.ChaosMaterials.BLOOD),
                        slimeknights.tconstruct.library.TinkerRegistry.getMaterial(ru.death.chaosshard.compat.tinkers.ChaosMaterials.CRYSTAL),
                        slimeknights.tconstruct.library.TinkerRegistry.getMaterial(ru.death.chaosshard.compat.tinkers.ChaosMaterials.CHAOS));
                net.minecraft.item.ItemStack sword = slimeknights.tconstruct.tools.melee.TinkerMeleeWeapons.broadSword.buildItem(mats);
                check(ru.death.chaosshard.compat.tinkers.ChaosMaterials.materials(sword).size() == 3, "в мече три материала Хаоса");
                check(ru.death.chaosshard.compat.tinkers.ChaosMaterials.level(sword, ru.death.chaosshard.compat.tinkers.ChaosMaterials.CRYSTAL) == 1, "новый — 1-й уровень");
                ru.death.chaosshard.compat.tinkers.ChaosMaterials.addXp(sword, 150, me);
                check(ru.death.chaosshard.compat.tinkers.ChaosMaterials.level(sword, ru.death.chaosshard.compat.tinkers.ChaosMaterials.BLOOD) == 2, "опыт 150 — 2-й уровень");
                me.setHeldItem(EnumHand.MAIN_HAND, sword);
                me.setHeldItem(EnumHand.OFF_HAND, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.GRIMOIRE));
                int base = ru.death.chaosshard.spell.ChaosEnergy.max(me);
                me.setHeldItem(EnumHand.OFF_HAND, net.minecraft.item.ItemStack.EMPTY);
                int without = ru.death.chaosshard.spell.ChaosEnergy.max(me);
                me.setHeldItem(EnumHand.OFF_HAND, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.GRIMOIRE));
                check(base - without == 20, "Хаосит 2-го уровня: +20 к запасу энергии (" + without + " → " + base + ")");
                ru.death.chaosshard.compat.tinkers.ChaosMaterials.setStore(sword, 100);
                check(ru.death.chaosshard.compat.tinkers.ChaosMaterials.store(sword) == 40, "кристалл держит 20 за уровень: " + ru.death.chaosshard.compat.tinkers.ChaosMaterials.store(sword));
                net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
                z.setPosition(me.posX + 3, me.posY, me.posZ);
                ow.spawnEntity(z);
                ru.death.chaosshard.spell.SpellCaster.clearCooldowns(me);
                ru.death.chaosshard.spell.ChaosEnergy.set(me, 50);
                ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.BOLT, null, EnumFacing.UP, z, null);
                check(ru.death.chaosshard.compat.tinkers.ChaosMaterials.store(sword) < 40 && ru.death.chaosshard.spell.ChaosEnergy.get(me) >= 49,
                        "заклинание взяло энергию из кристалла: " + ru.death.chaosshard.compat.tinkers.ChaosMaterials.store(sword));
                ru.death.chaosshard.compat.tinkers.ChaosMaterials.setStore(sword, 0);
                ru.death.chaosshard.spell.SpellCaster.clearCooldowns(me);
                ru.death.chaosshard.spell.ChaosEnergy.set(me, 0);
                me.setHealth(me.getMaxHealth());
                boolean ok = ru.death.chaosshard.spell.SpellCaster.castAt(me, ru.death.chaosshard.spell.Spell.BOLT, null, EnumFacing.UP, z, null);
                check(ok && me.getHealth() < me.getMaxHealth(), "Кровавая платина: без энергии заплатил кровью, здоровье " + me.getHealth());
                z.setDead();
                me.setHealth(me.getMaxHealth());
                me.setHeldItem(EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
                me.setHeldItem(EnumHand.OFF_HAND, net.minecraft.item.ItemStack.EMPTY);
                ru.death.chaosshard.spell.SpellCaster.clearCooldowns(me);
            });
        }

        section("Цитадель Логова");
        step("Цитадель жжёт врагов, чинит снаряжение, не пускает тварей", () -> {
            BlockPos lp = base.add(40, 1, -40);
            ow.getChunk(lp);
            ow.setBlockState(lp, ru.death.chaosshard.init.ModBlocks.LAIR.getDefaultState(), 3);
            ru.death.chaosshard.lair.TileLair tl = (ru.death.chaosshard.lair.TileLair) ow.getTileEntity(lp);
            if (tl == null) { check(false, "нет сердца логова"); return; }
            tl.setOwner(me);
            tl.setLevel(3);
            ru.death.chaosshard.lair.LairData.get(ow).setCitadel(me.getUniqueID(), 0, lp);
            Object[] c = ru.death.chaosshard.lair.LairData.get(ow).citadelOf(me.getUniqueID());
            check(c != null && lp.equals(c[1]), "Цитадель записана за хозяином");
            check(ru.death.chaosshard.lair.CitadelEvents.inCitadel(ow, lp.getX() + 10, lp.getZ() - 10)
                    && !ru.death.chaosshard.lair.CitadelEvents.inCitadel(ow, lp.getX() + 40, lp.getZ()), "святилище: в радиусе 24 твари не родятся");
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setPosition(lp.getX() + 4.5, lp.getY(), lp.getZ() + 0.5);
            ow.spawnEntity(z);
            float hp = z.getHealth();
            int hit = tl.citadelGuard();
            check(hit >= 1 && z.getHealth() < hp, "дыхание Цитадели: задето " + hit + ", " + hp + " → " + z.getHealth());
            z.setDead();
            net.minecraft.item.ItemStack sw = new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_SWORD);
            sw.setItemDamage(10);
            ru.death.chaosshard.lair.TileLair.mend(sw);
            check(sw.getItemDamage() == 8, "починка снаряжения: " + sw.getItemDamage());
            ow.setBlockToAir(lp);
        });

        section("Малые планы: Незер, Край, карманы");
        step("огонь Незера роняет Эссенцию, Край — Осколки Завесы", () -> {
            WorldServer nether = net.minecraftforge.common.DimensionManager.getWorld(-1);
            if (nether == null) {
                net.minecraftforge.common.DimensionManager.initDimension(-1);
                nether = net.minecraftforge.common.DimensionManager.getWorld(-1);
            }
            if (nether == null) { check(false, "Незер не загрузился"); return; }
            float was = ChaosConfig.plans.netherEssenceChance;
            ChaosConfig.plans.netherEssenceChance = 1F;
            net.minecraft.entity.monster.EntityBlaze blaze = new net.minecraft.entity.monster.EntityBlaze(nether);
            blaze.setPosition(0, 70, 0);
            ArrayList<net.minecraft.entity.item.EntityItem> drops = new ArrayList<>();
            net.minecraftforge.event.entity.living.LivingDropsEvent ev = new net.minecraftforge.event.entity.living.LivingDropsEvent(blaze,
                    DamageSource.causePlayerDamage(me), drops, 0, true);
            ru.death.chaosshard.plans.MinorPlans.onDrops(ev);
            ChaosConfig.plans.netherEssenceChance = was;
            check(drops.stream().anyMatch(d -> d.getItem().getItem() == ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE), "ифрит в Незере уронил Эссенцию");
            drops.clear();
            net.minecraft.entity.monster.EntityBlaze home = new net.minecraft.entity.monster.EntityBlaze(ow);
            ru.death.chaosshard.plans.MinorPlans.onDrops(new net.minecraftforge.event.entity.living.LivingDropsEvent(home,
                    DamageSource.causePlayerDamage(me), drops, 0, true));
            check(drops.isEmpty(), "вне Незера — ничего");
            check(ru.death.chaosshard.plans.MinorPlans.shardMultiplier(ow) == 1.0, "в Лучезарии Осколки как обычно");
        });
        step("Иссушитель: Нить и веха", () -> {
            int before = ru.death.chaosshard.thread.ThreadManager.getPoints(me);
            long over0 = ru.death.chaosshard.thread.ThreadManager.overflowed;
            ru.death.chaosshard.plans.MinorPlans.slain(me, false);
            long got = ru.death.chaosshard.thread.ThreadManager.getPoints(me) - before + ru.death.chaosshard.thread.ThreadManager.overflowed - over0;
            check(got >= ChaosConfig.plans.witherThread, "Нить +" + got + " (с переливом)");
        });
        step("«Нюх Нити»", () -> {
            check(ru.death.chaosshard.ritual.Ritual.SCENT.isSphere() && ru.death.chaosshard.ritual.Ritual.SCENT.available(), "ритуал-сфера есть");
            boolean found = ru.death.chaosshard.plans.MinorPlans.scent(me, ow, base);
            line("  крепость с порталом в Лучезарии " + (found ? "найдена" : "не найдена (мир без крепостей)"));
            check(true, "поиск не падает");
        });

        section("Акт 5: наступление Ордена");
        step("Орден идёт сам: отряд, знаменосец, капитан с печатью", () -> {
            check(ru.death.chaosshard.act5.OrderRaids.active(ow), "наступление идёт (Опоры пали, Гаррот склонился)");
            TileAnchor a = (TileAnchor) ow.getTileEntity(anchorPos);
            if (a == null) { check(false, "Якоря нет"); return; }
            me.setPosition(anchorPos.getX() + 3.5, anchorPos.getY(), anchorPos.getZ() + 0.5);
            ru.death.chaosshard.structure.EntityOrderKnight cap = ru.death.chaosshard.act5.OrderRaids.spawn(ow, anchorPos, me, 3, 0);
            check(cap != null && cap.getTags().contains(ru.death.chaosshard.act5.OrderRaids.TAG_CAPTAIN), "капитан набега");
            // рядом с капитаном — только его отряд (настоящий набег Якоря мог прийти сам и стоять в другом месте)
            List<ru.death.chaosshard.structure.EntityOrderKnight> squad = ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    cap.getEntityBoundingBox().grow(8, 256, 8), x -> x.getTags().contains(ru.death.chaosshard.act5.OrderRaids.TAG_RAID));
            // площадка теста висит в воздухе: рыцари с края встают на землю внизу — считаем по всей высоте
            if (squad.size() < 7) {
                for (ru.death.chaosshard.structure.EntityOrderKnight k : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                        cap.getEntityBoundingBox().grow(64, 64, 64), x -> x.getTags().contains(ru.death.chaosshard.act5.OrderRaids.TAG_RAID))) {
                    line("  рыцарь " + k.getTags() + " в " + k.getPosition() + " (капитан " + cap.getPosition() + "), жив " + k.isEntityAlive());
                }
            }
            check(squad.size() >= 1 + 5 + 1, "в отряде " + squad.size() + " (капитан, 5 рыцарей, знаменосец)");
            check(squad.stream().allMatch(k -> k.getTags().contains(ru.death.chaosshard.garrot.GarrotRaids.TAG_ARMY)
                    && k.getEntityData().getLong(ru.death.chaosshard.garrot.GarrotRaids.TAG_RAID) == anchorPos.toLong()), "все идут на Якорь как армия набега");
            check(squad.stream().anyMatch(k -> k.getTags().contains(ru.death.chaosshard.garrot.GarrotRaids.TAG_BANNER)), "есть знаменосец Света");
            cap.attackEntityFrom(DamageSource.causePlayerDamage(me), 100000F);
            check(!cap.isEntityAlive(), "капитан пал");
            boolean seal = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, cap.getEntityBoundingBox().grow(3),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.ORDER_SEAL).isEmpty();
            check(seal, "с капитана упала Печать Ордена");
        });
        until("отряд Ордена ушёл", () -> ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                new AxisAlignedBB(anchorPos).grow(48), x -> x.isEntityAlive() && x.getTags().contains(ru.death.chaosshard.act5.OrderRaids.TAG_RAID)).isEmpty(), 40);

        section("Акт 5: столица и Король");
        step("столица встаёт: маяки, барьер, трон, место Разлома", () -> {
            capPos = ru.death.chaosshard.structure.OrderStructures.ground(ow, base.getX() + 260, base.getZ());
            ru.death.chaosshard.structure.OrderStructureData.Entry e = ru.death.chaosshard.structure.OrderStructures.placeNow(ow, "capital", capPos, 0, 7L);
            ru.death.chaosshard.act5.CapitalData cd = ru.death.chaosshard.act5.CapitalData.get(ow);
            check(e != null && cd.state == ru.death.chaosshard.act5.CapitalData.STANDING, "столица встала");
            check(cd.beacons.size() == 4 && ru.death.chaosshard.act5.Crown.lit(ow) == 4, "маяков горит: " + ru.death.chaosshard.act5.Crown.lit(ow) + " из " + cd.beacons.size());
            check(cd.barrier.size() == 25 && cd.barrier.stream().allMatch(p -> ow.getBlockState(p).getBlock() == ModBlocks.CAPITAL_BARRIER), "барьер во входе: " + cd.barrier.size());
            check(cd.king != null && cd.rift != null, "есть место Короля и Разлома");
        });
        step("маяк не погасить при страже; без стражи — Эссенцией", () -> {
            ru.death.chaosshard.act5.CapitalData cd = ru.death.chaosshard.act5.CapitalData.get(ow);
            BlockPos b0 = cd.beacons.get(0);
            net.minecraft.item.ItemStack ess = new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 64);
            check(ru.death.chaosshard.act5.Crown.snuff(me, b0, ess) == 1, "стража держит свет");
            for (BlockPos b : cd.beacons) {
                for (ru.death.chaosshard.structure.EntityOrderKnight k : ru.death.chaosshard.act5.Crown.keepers(ow, b)) {
                    k.setDead();
                }
            }
            check(ru.death.chaosshard.act5.Crown.snuff(me, b0, new net.minecraft.item.ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 2)) == 2, "двух Эссенций мало");
            check(ru.death.chaosshard.act5.Crown.snuff(me, b0, ess) == 0 && ess.getCount() == 64 - ChaosConfig.order.beaconEssence, "маяк погас, Эссенция потрачена");
            check(ru.death.chaosshard.act5.Crown.lit(ow) == 3, "горит три");
            for (int i = 1; i < cd.beacons.size(); i++) {
                ru.death.chaosshard.act5.Crown.snuff(me, cd.beacons.get(i), ess);
            }
            check(cd.state == ru.death.chaosshard.act5.CapitalData.OPEN, "все погасли — дворец открыт");
            check(cd.barrier.stream().noneMatch(p -> ow.getBlockState(p).getBlock() == ModBlocks.CAPITAL_BARRIER), "барьер рассыпался");
            check(ru.death.chaosshard.act5.Crown.kingOf(ow) != null, "Король встал");
        });
        step("Король: Указ, стража, приёмы Светочей, Ключ Разлома", () -> {
            ru.death.chaosshard.act5.EntityKing k = ru.death.chaosshard.act5.Crown.kingOf(ow);
            if (k == null) { check(false, "Короля нет"); return; }
            k.scripted = false;
            me.setPosition(k.posX + 3, k.posY, k.posZ);
            // подставного игрока поиск по области находит ненадёжно — проверяем на зомби
            net.minecraft.entity.monster.EntityZombie z = new net.minecraft.entity.monster.EntityZombie(ow);
            z.setNoAI(true);
            z.setPosition(k.posX + 2, k.posY, k.posZ);
            ow.spawnEntity(z);
            k.decree(ow);
            check(z.isPotionActive(net.minecraft.init.MobEffects.SLOWNESS) && z.isPotionActive(net.minecraft.init.MobEffects.WEAKNESS),
                    "Указ: на колени (Медлительность и Слабость)");
            z.setDead();
            k.startDecree();
            check(k.charging(), "Указ заряжается");
            k.hurtResistantTime = 0;
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), 60F);
            check(!k.charging() && k.staggered(), "сбит уроном — оглушён");
            k.hurtResistantTime = 0;
            k.setHealth(k.getMaxHealth() * 0.8F);
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), k.getMaxHealth() * 0.1F);
            int royal = ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class, k.getEntityBoundingBox().grow(8),
                    x -> x.getTags().contains(ru.death.chaosshard.act5.EntityKing.TAG_ROYAL)).size();
            check(royal == 2, "на 75% — королевская стража: " + royal);
            k.hurtResistantTime = 0;
            k.setHealth(k.getMaxHealth() * 0.52F);
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), k.getMaxHealth() * 0.05F);
            check(k.phase2(), "с половины — вторая фаза");
            k.hurtResistantTime = 0;
            k.attackEntityFrom(DamageSource.causePlayerDamage(me), 100000F);
            check(!k.isEntityAlive(), "Король пал");
            ru.death.chaosshard.act5.CapitalData cd = ru.death.chaosshard.act5.CapitalData.get(ow);
            check(cd.state == ru.death.chaosshard.act5.CapitalData.FALLEN && cd.riftOpen
                    && ow.getBlockState(cd.rift).getBlock() == ModBlocks.RIFT_GATE, "за троном — врата Разлома");
            boolean key = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, k.getEntityBoundingBox().grow(4),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.RIFT_KEY).isEmpty();
            check(key, "Ключ Разлома упал");
            for (ru.death.chaosshard.structure.EntityOrderKnight x : ow.getEntitiesWithinAABB(ru.death.chaosshard.structure.EntityOrderKnight.class,
                    new AxisAlignedBB(capPos).grow(48, 32, 48))) {
                x.setDead();
            }
            me.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        });

        section("Акт 5: Разлом и Архонт");
        step("Разлом: арена и пять пилонов, Архонт под их светом", () -> {
            WorldServer rw = ru.death.chaosshard.act5.Rift.riftWorld();
            if (rw == null) { check(false, "Разлом не загрузился"); return; }
            for (int cx = -3; cx <= 2; cx++) {
                for (int cz = -3; cz <= 2; cz++) {
                    rw.getChunk(cx, cz);
                }
            }
            ru.death.chaosshard.act5.Rift.forget(rw);
            check(ru.death.chaosshard.act5.Rift.intactPylons(rw) == 5, "ядер пилонов: " + ru.death.chaosshard.act5.Rift.intactPylons(rw));
            check(!rw.isAirBlock(ru.death.chaosshard.act5.RiftDimension.center().down()), "пол арены есть");
            ru.death.chaosshard.act5.EntityArchon a = ru.death.chaosshard.act5.Rift.spawnArchon(rw);
            a.scripted = false;
            float h = a.getHealth();
            a.attackEntityFrom(DamageSource.causePlayerDamage(me), 100F);
            check(a.getHealth() == h && a.shielded(), "пока целы ядра — неуязвим");
            for (int i = 0; i < 5; i++) {
                ru.death.chaosshard.act5.Rift.pylonStrike(rw, i);
                rw.setBlockToAir(ru.death.chaosshard.act5.RiftDimension.pylon(i));
            }
            check(ru.death.chaosshard.act5.Rift.intactPylons(rw) == 0 && a.phase() == 2, "ядра разбиты — вторая фаза");
            a.hurtResistantTime = 0;
            a.attackEntityFrom(DamageSource.causePlayerDamage(me), 100F);
            check(a.getHealth() < h, "теперь урон проходит: " + h + " → " + a.getHealth());
            a.beam(a);
            check(a.pendingBeams() == 1, "луч заряжается");
            int before = ru.death.chaosshard.act5.Rift.tearsActive();
            ru.death.chaosshard.act5.Rift.tear(rw);
            for (int i = 0; i < ru.death.chaosshard.act5.EntityArchon.TEAR_WARN + 2; i++) {
                ru.death.chaosshard.act5.Rift.tickTears(rw);
            }
            check(ru.death.chaosshard.act5.Rift.tearsActive() == before + 1, "край арены разорван");
            ru.death.chaosshard.act5.Rift.reset(rw, a);
            check(ru.death.chaosshard.act5.Rift.intactPylons(rw) == 5 && a.phase() == 1 && a.getHealth() == a.getMaxHealth()
                    && ru.death.chaosshard.act5.Rift.tearsActive() == 0, "сброс: пилоны встали, Архонт исцелён, арена цела");
        });
        step("Архонт пал — узел развязан", () -> {
            WorldServer rw = ru.death.chaosshard.act5.Rift.riftWorld();
            ru.death.chaosshard.act5.EntityArchon a = ru.death.chaosshard.act5.Rift.archon(rw);
            if (a == null) { check(false, "Архонта нет"); return; }
            for (int i = 0; i < 5; i++) {
                rw.setBlockToAir(ru.death.chaosshard.act5.RiftDimension.pylon(i));
            }
            a.hurtResistantTime = 0;
            a.attackEntityFrom(DamageSource.causePlayerDamage(me), 1000000F);
            check(!a.isEntityAlive() && ru.death.chaosshard.act5.Rift.data(rw).defeated, "Архонт пал, Разлом затих");
            ru.death.chaosshard.act5.Rift.forget(rw);
        });

        section("Справка: реестры модов сборки");
        step("набеги снова включены", () -> ChaosConfig.order.raids = true);
        step("рецепты с зельями", () -> {
            int n = 0;
            for (net.minecraft.item.crafting.IRecipe r : net.minecraft.item.crafting.CraftingManager.REGISTRY) {
                StringBuilder ing = new StringBuilder();
                boolean potion = false;
                int idx = 0;
                for (net.minecraft.item.crafting.Ingredient in : r.getIngredients()) {
                    net.minecraft.item.ItemStack[] ms = in.getMatchingStacks();
                    String d = ms.length == 0 ? "-" : ms[0].getItem().getRegistryName() + "@" + ms[0].getMetadata()
                            + (ms[0].hasTagCompound() ? ms[0].getTagCompound().toString() : "") + (ms.length > 1 ? "(+" + (ms.length - 1) + ")" : "");
                    for (net.minecraft.item.ItemStack m : ms) {
                        if (m.getItem() instanceof net.minecraft.item.ItemPotion || String.valueOf(m.getItem().getRegistryName()).contains("potion")) potion = true;
                    }
                    ing.append(" [").append(idx++).append("]").append(d);
                }
                if (potion) {
                    line("  [ЗЕЛЬЕ] " + r.getRegistryName() + " (" + r.getClass().getSimpleName() + ") -> " + r.getRecipeOutput().getItem().getRegistryName()
                            + "@" + r.getRecipeOutput().getMetadata() + " x" + r.getRecipeOutput().getCount() + " w=" + (r instanceof net.minecraftforge.common.crafting.IShapedRecipe ? ((net.minecraftforge.common.crafting.IShapedRecipe) r).getRecipeWidth() : 0) + " :" + ing);
                    n++;
                }
            }
            line("  рецептов с зельями: " + n);
        });
        step("выписать предметы, блоки, тайлы и существ", () -> {
            // все пространства имён на сервере: по ним скрипт сверки проверяет рецепты, квесты и .zs
            java.util.TreeSet<String> modSet = new java.util.TreeSet<>();
            for (net.minecraft.item.Item it : net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS) {
                if (it.getRegistryName() != null) modSet.add(it.getRegistryName().getNamespace());
            }
            for (net.minecraft.block.Block b : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS) {
                if (b.getRegistryName() != null) modSet.add(b.getRegistryName().getNamespace());
            }
            for (net.minecraftforge.fml.common.registry.EntityEntry en : net.minecraftforge.fml.common.registry.ForgeRegistries.ENTITIES) {
                if (en.getRegistryName() != null) modSet.add(en.getRegistryName().getNamespace());
            }
            StringBuilder sb = new StringBuilder();
            for (String m : modSet) {
                sb.append("=== ").append(m).append('\n');
                for (net.minecraft.item.Item it : net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS) {
                    if (it.getRegistryName() != null && m.equals(it.getRegistryName().getNamespace())) {
                        sb.append("item ").append(it.getRegistryName());
                        if (it.getHasSubtypes()) {
                            net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> subs = net.minecraft.util.NonNullList.create();
                            try {
                                it.getSubItems(net.minecraft.creativetab.CreativeTabs.SEARCH, subs);
                            } catch (Throwable ignored) {
                                // на сервере может не выйти
                            }
                            for (net.minecraft.item.ItemStack st : subs) {
                                sb.append(" | ").append(st.getMetadata()).append('=').append(st.getTranslationKey());
                            }
                        }
                        sb.append('\n');
                    }
                }
                for (net.minecraft.block.Block b : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS) {
                    if (b.getRegistryName() != null && m.equals(b.getRegistryName().getNamespace())) {
                        sb.append("block ").append(b.getRegistryName()).append('\n');
                    }
                }
                for (net.minecraftforge.fml.common.registry.EntityEntry en : net.minecraftforge.fml.common.registry.ForgeRegistries.ENTITIES) {
                    if (en.getRegistryName() != null && m.equals(en.getRegistryName().getNamespace())) {
                        sb.append("entity ").append(en.getRegistryName()).append('\n');
                    }
                }
                net.minecraft.util.registry.RegistryNamespaced<ResourceLocation, Class<? extends net.minecraft.tileentity.TileEntity>> reg =
                        net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.tileentity.TileEntity.class, null, "field_190562_f");
                for (ResourceLocation id : reg.getKeys()) {
                    if (m.equals(id.getNamespace())) {
                        sb.append("tile ").append(id).append('\n');
                    }
                }
            }
            for (String fl : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().keySet()) {
                sb.append("fluid ").append(fl).append('\n');
            }
            for (ResourceLocation id : net.minecraft.world.storage.loot.LootTableList.getAll()) {
                sb.append("loot ").append(id).append('\n');
            }
            try {
                java.nio.file.Files.write(new java.io.File("chaosshard-registry.txt").toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                check(true, "реестры выписаны");
            } catch (Exception ex) {
                check(false, "реестры: " + ex);
            }
        });

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
    private long overSnap;

    /** Нить выросла — или упёрлась в потолок и перелилась. */
    private boolean nitGrew(int before) {
        return ru.death.chaosshard.thread.ThreadManager.getPoints(me) > before || ru.death.chaosshard.thread.ThreadManager.overflowed > overSnap;
    }

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
            liveTicks = 0;
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
            case COLLAPSE:
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
        liveTicks++;
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
                if (type == TrialRoom.GUARD && t.conditions != 0) {
                    EntityLivingBase g = mobs.stream().filter(e -> e.getEntityData().getBoolean(TrialManager.TAG_GUARD)).findFirst().orElse(null);
                    if (liveTicks == 2 && g != null) {
                        check(g.getCustomNameTag().contains("("), "Страж с повадками: " + g.getCustomNameTag());
                    }
                    if (liveTicks == 3 && g != null) {
                        kill(g);
                        return false;
                    }
                    if (liveTicks == 5 && TrialCondition.has(t.conditions, TrialCondition.ECHO)) {
                        check(mobs.stream().filter(e -> e.getEntityData().getBoolean(TrialManager.TAG_RISEN)).count() >= 2,
                                "Мир эха: Страж распался на эхо");
                    }
                    if (liveTicks < 6) {
                        return false;
                    }
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
            case MIRROR:
                if (t.showStep >= t.seq.length * 2 && t.seqPos == 0) {
                    BlockPos[] p = TrialLayout.sequencePositions(t, room);
                    int[] q = t.seq.clone();
                    for (int i = q.length - 1; i >= 0; i--) TrialManager.onRune(archive, p[q[i]], meA);
                }
                return false;
            case LIGHTS:
                if (roomTicks == 10) {
                    BlockPos[] p = TrialLayout.lightsPositions(t, room);
                    // перебор: какие руны нажать, чтобы горели все пять
                    for (int mask = 0; mask < 32; mask++) {
                        int[] st = t.seq.clone();
                        for (int i = 0; i < 5; i++) {
                            if ((mask >> i & 1) == 1) {
                                for (int k = i - 1; k <= i + 1; k++) if (k >= 0 && k < 5) st[k] ^= 1;
                            }
                        }
                        if (java.util.Arrays.stream(st).allMatch(v -> v == 1)) {
                            for (int i = 0; i < 5; i++) if ((mask >> i & 1) == 1) TrialManager.onRune(archive, p[i], meA);
                            break;
                        }
                    }
                }
                return false;
            case COLLAPSE:
                if (liveTicks == 5) {
                    meA.setPosition(x, y, o.getZ() + (b[0] + b[1]) / 2.0 + 0.5);
                    meA.onGround = true;
                    TrialManager.tick(meA, false);
                    check(TrialManager.collapsing() > 0, "Обвал: плитка под ногами пошла вниз");
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
