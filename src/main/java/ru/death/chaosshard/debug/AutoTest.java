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
    private BlockPos bowlPos, spherePos, pullChest, altarPos, moonPos, shrinePos;
    private UUID lordId;
    private ru.death.chaosshard.structure.OrderStructureData.Entry monastery;
    private ru.death.chaosshard.hero.EntityHero irena;
    private float irenaHp;
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
            check(ru.death.chaosshard.thread.ThreadManager.getPoints(me) > challengeNit, "Нить выросла: " + challengeNit + " → " + ru.death.chaosshard.thread.ThreadManager.getPoints(me));
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
            me.managedPosX = me.posX;
            me.managedPosZ = me.posZ;
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
            kill(hs.get(0));
            ru.death.chaosshard.hero.HeroData.Pillar p = ru.death.chaosshard.hero.HeroData.get(ow).of(ru.death.chaosshard.hero.Lightbearer.KAEL);
            check(p.state == ru.death.chaosshard.hero.HeroData.FALLEN, "Опора пала");
            BlockPos o = planned.origin();
            check(!ow.isAirBlock(o.up(ru.death.chaosshard.structure.bp.VeilPillar.FLOOR + ru.death.chaosshard.structure.bp.VeilPillar.COLUMN_H)),
                    "колонна ещё стоит — рухнет под камерой");
            boolean blade = !ow.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(o).grow(24, 40, 24),
                    x -> x.getItem().getItem() == ru.death.chaosshard.init.ModItems.KAEL_BLADE).isEmpty();
            check(blade, "Клинок Каэля лежит у Опоры");
            check(ru.death.chaosshard.thread.ThreadManager.getPoints(me) > nit, "Нить окрепла: " + nit + " → " + ru.death.chaosshard.thread.ThreadManager.getPoints(me));
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
            me.managedPosX = me.posX;
            me.managedPosZ = me.posZ;
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
                me.managedPosX = me.posX;
                me.managedPosZ = me.posZ;
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
            me.managedPosX = me.posX;
            me.managedPosZ = me.posZ;
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
            me.managedPosX = me.posX;
            me.managedPosZ = me.posZ;
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
                banners += ei.getItem().getCount();
                ei.setDead();
            }
            check(banners == 6, "Знамён Мрачной армии: " + banners);
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
