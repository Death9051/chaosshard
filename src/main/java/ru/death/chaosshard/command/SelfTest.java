package ru.death.chaosshard.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.heart.HeartPlacer;
import ru.death.chaosshard.heart.HeartTiers;
import ru.death.chaosshard.heart.TileHeart;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.thread.ThreadManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * /oh selftest — автоматическая проверка сборки. Мир не меняет.
 * Пишет chaosshard-selftest.txt: по каждой системе ✔/✘/⚠ и общий процент.
 */
public final class SelfTest {

    public static final String FILE_NAME = "chaosshard-selftest.txt";

    private final StringBuilder out = new StringBuilder();
    private final Map<String, int[]> bySection = new LinkedHashMap<>();
    private String section = "";
    private int pass, fail, warn;

    public static SelfTest run(MinecraftServer server, EntityPlayerMP player) {
        SelfTest t = new SelfTest();
        t.line("=== Осколок Хаоса — самопроверка ===");
        t.line("Время: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ", мод " + Tags.VERSION);
        t.section("Моды сборки", () -> t.mods());
        t.section("Реестр мода", () -> t.registry());
        t.section("Рецепты", () -> t.recipes());
        t.section("Преддверие", () -> t.antechamber(server));
        t.section("Нить и Game Stages", () -> t.thread(player));
        t.section("Сердце Острова", () -> t.heart(server));
        t.section("Прорыв", () -> t.breach());
        t.section("Якоря и Скверна", () -> t.anchors());
        t.section("Заклинания", () -> t.spells());
        t.section("Astral Sorcery", () -> t.astral(player));
        t.section("Mystcraft", () -> t.mystcraft());
        t.section("Tinkers", () -> t.tinkers());
        t.section("Квесты (DefaultQuests.json)", () -> t.quests());
        t.section("Скрипты CraftTweaker", () -> t.crafttweaker());
        t.section("Русификация", () -> t.translation());
        t.summary();
        return t;
    }

    // ------------------------------------------------------------------ проверки

    private void mods() {
        String[] required = {"astralsorcery", "baubles", "mixinbooter", "gamestages", "crafttweaker", "betterquesting",
                "bq_standard", "mystcraft", "tconstruct", "mekanism", "thermalexpansion", "cyberware", "metamorph",
                "defiledlands", "evilcraft", "dimdoors", "lycanitesmobs", "packagedastral", "tconevo", "modtweaker", "jei"};
        for (String id : required) {
            check(Loader.isModLoaded(id), "мод " + id);
        }
    }

    private void registry() {
        check(ModBlocks.CHAOS_STONE != null && ModBlocks.CHAOS_CRUST != null, "блоки хаосит и корка");
        check(ModBlocks.BREACH_ALTAR != null, "Алтарь Прорыва");
        check(ModBlocks.ISLAND_HEART != null, "Сердце Острова");
        for (String name : new String[]{"grimoire", "veil_shard", "world_shard", "memory_page", "breach_book",
                "heart_module_pulse", "heart_module_capacity", "heart_module_rich", "heart_module_fertile", "heart_harvester",
                "chaos_essence", "distorted_starmetal", "chaos_anchor"}) {
            check(ForgeRegistries.ITEMS.containsKey(new ResourceLocation(Tags.MOD_ID, name)), "предмет " + name);
        }
        check(TileEntity.getKey(TileHeart.class) != null, "тайл Сердца зарегистрирован");
        check(ModBlocks.CHAOS_STONE.getDefaultState().getMaterial().isToolNotRequired(), "хаосит ломается рукой");
    }

    private void recipes() {
        int ours = 0;
        Set<String> names = new TreeSet<>();
        for (IRecipe r : ForgeRegistries.RECIPES) {
            if (r.getRegistryName() != null && Tags.MOD_ID.equals(r.getRegistryName().getNamespace())) {
                ours++;
                names.add(r.getRegistryName().getPath());
            }
        }
        line("Рецептов мода загружено: " + ours);
        File dir = null;
        String[] expect = {"transmute_log", "transmute_cobblestone", "transmute_dirt", "transmute_sand", "transmute_paper",
                "transmute_leather", "transmute_ink", "transmute_feather", "transmute_string", "transmute_water",
                "memory_page", "breach_book", "breach_altar", "heart_module_pulse", "heart_module_capacity",
                "heart_module_rich", "heart_module_fertile", "heart_harvester", "chaos_anchor", "transmute_essence", "transmute_distorted"};
        for (String e : expect) {
            if ("chaos_anchor".equals(e) && Loader.isModLoaded("evilcraft")) {
                e = "chaos_anchor_dark";
            }
            check(names.contains(e), "рецепт " + e);
        }
        if (Loader.isModLoaded("mystcraft")) {
            int pages = 0;
            for (String n : names) {
                if (n.startsWith("page_")) {
                    pages++;
                }
            }
            check(pages >= 20, "рецепты страниц ресурсов Mystcraft: " + pages);
        }
        // каждый файл рецепта, чьи условия выполнены, должен загрузиться (иначе — ошибка разбора в журнале)
        final int[] files = {0};
        final List<String> lost = new ArrayList<>();
        final List<String> moved = new ArrayList<>();
        net.minecraftforge.fml.common.ModContainer mod = Loader.instance().getIndexedModList().get(Tags.MOD_ID);
        net.minecraftforge.common.crafting.JsonContext ctx = new net.minecraftforge.common.crafting.JsonContext(Tags.MOD_ID);
        net.minecraftforge.common.crafting.CraftingHelper.findFiles(mod, "assets/" + Tags.MOD_ID + "/recipes", null, (root, file) -> {
            String rel = root.relativize(file).toString().replace('\\', '/');
            if (!rel.endsWith(".json") || rel.startsWith("_")) {
                return true;
            }
            try (java.io.Reader rd = java.nio.file.Files.newBufferedReader(file)) {
                com.google.gson.JsonObject json = net.minecraft.util.JsonUtils.fromJson(new com.google.gson.Gson(), rd, com.google.gson.JsonObject.class);
                if (json != null && json.has("conditions")
                        && !net.minecraftforge.common.crafting.CraftingHelper.processConditions(json.getAsJsonArray("conditions"), ctx)) {
                    return true;
                }
            } catch (Exception e) {
                // битый файл — его и так не будет в реестре
            }
            files[0]++;
            String name = rel.substring(0, rel.length() - 5);
            if (!names.contains(name)) {
                String other = replacement(file);
                if (other != null) {
                    moved.add(name + " -> " + other);
                } else {
                    lost.add(name);
                }
            }
            return true;
        }, true, true);
        check(lost.isEmpty(), "все файлы рецептов загружены (" + files[0] + ")" + (lost.isEmpty() ? "" : ", не загрузились: " + lost));
        if (!moved.isEmpty()) {
            warn("рецепты перерегистрировал другой мод (работают под другим именем): " + moved);
        }
        for (IRecipe r : ForgeRegistries.RECIPES) {
            if (r.getRegistryName() != null && Tags.MOD_ID.equals(r.getRegistryName().getNamespace())
                    && r.getRecipeOutput().isEmpty()) {
                check(false, "рецепт " + r.getRegistryName() + " с пустым результатом");
            }
        }
    }

    /** Рецепт мода, которого нет под своим именем: другой мод (UniDict) мог переписать его под своим. Имя замены или null. */
    private static String replacement(java.nio.file.Path file) {
        try (Reader rd = Files.newBufferedReader(file)) {
            JsonObject json = new JsonParser().parse(rd).getAsJsonObject();
            JsonObject res = json.getAsJsonObject("result");
            Item out = Item.getByNameOrId(res.get("item").getAsString());
            int meta = res.has("data") ? res.get("data").getAsInt() : 0;
            int count = res.has("count") ? res.get("count").getAsInt() : 1;
            int n = json.has("ingredients") ? json.getAsJsonArray("ingredients").size() : -1;
            for (IRecipe r : ForgeRegistries.RECIPES) {
                ItemStack o = r.getRecipeOutput();
                if (o.getItem() != out || o.getMetadata() != meta || o.getCount() != count || r.getRegistryName() == null) {
                    continue;
                }
                int k = 0;
                boolean grimoire = false;
                for (net.minecraft.item.crafting.Ingredient in : r.getIngredients()) {
                    if (in != net.minecraft.item.crafting.Ingredient.EMPTY && in.getMatchingStacks().length > 0) {
                        k++;
                        grimoire |= in.apply(new ItemStack(ModItems.GRIMOIRE));
                    }
                }
                if ((n < 0 || k == n) && (grimoire || !json.toString().contains("chaosshard:grimoire"))) {
                    return r.getRegistryName().toString();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void antechamber(MinecraftServer server) {
        check(DimensionManager.isDimensionRegistered(AntechamberDimension.id()), "измерение Преддверия зарегистрировано");
        World w = server.getWorld(AntechamberDimension.id());
        check(w != null, "мир Преддверия загружается");
        if (w == null) {
            return;
        }
        BlockPos spawn = AntechamberDimension.spawnPoint();
        check(!w.isAirBlock(spawn.down()), "под точкой появления есть блок");
        check(w.getRainStrength(1F) < 0.01F, "в Преддверии нет дождя");
        check(w.provider.hasSkyLight(), "в Преддверии есть небесный свет");
        if (ChaosConfig.antechamber.ambientLight) {
            check(w.calculateSkylightSubtracted(1F) <= 1, "земля освещена (свет неба не гаснет ночью)");
        }
    }

    private void thread(EntityPlayerMP player) throws Exception {
        int[] t = ChaosConfig.thread.pointsPerLevel;
        boolean sorted = true;
        for (int i = 1; i < t.length; i++) {
            sorted &= t[i] > t[i - 1];
        }
        check(sorted, "пороги Нити возрастают");
        check(ThreadManager.levelFor(0) == 0 && ThreadManager.levelFor(t[t.length - 1]) == t.length, "расчёт уровня Нити");
        int level = ThreadManager.getLevel(player);
        line("Нить игрока: уровень " + level + ", очков " + ThreadManager.getPoints(player));
        if (Loader.isModLoaded("gamestages")) {
            Class<?> helper = Class.forName("net.darkhax.gamestages.GameStageHelper");
            Object data = helper.getMethod("getPlayerData", net.minecraft.entity.player.EntityPlayer.class).invoke(null, player);
            java.util.Collection<?> stages = (java.util.Collection<?>) data.getClass().getMethod("getStages").invoke(data);
            boolean all = true;
            for (int l = 0; l <= level; l++) {
                all &= stages.contains(ChaosConfig.thread.stagePrefix + l);
            }
            if (all || player.getServer() == null || player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID()) != null) {
                check(all, "этапы Game Stages выданы до уровня " + level);
            } else {
                warn("этапы Game Stages: подставной игрок не в списке игроков, команда gamestage его не видит");
            }
        }
    }

    private void heart(MinecraftServer server) {
        if (!ChaosConfig.heart.enabled) {
            warn("Сердце выключено в конфиге");
            return;
        }
        World w = server.getWorld(AntechamberDimension.id());
        TileEntity te = w.getTileEntity(HeartPlacer.heartPos());
        if (te instanceof TileHeart || HeartPlacer.Data.get(w).placed) {
            check(te instanceof TileHeart, "Сердце стоит на месте " + HeartPlacer.heartPos());
        } else {
            warn("Сердце ещё не поставлено: в Преддверие никто не входил");
        }
        for (int tier = 1; tier <= HeartTiers.maxTier(); tier++) {
            String[] entries = ChaosConfig.heart.tierOres[tier - 1].split(",");
            int resolved = HeartTiers.pool(tier).size() - (tier > 1 ? HeartTiers.pool(tier - 1).size() : 0);
            if (resolved == entries.length) {
                check(true, "ступень " + tier + ": все " + entries.length + " руд найдены");
            } else {
                StringBuilder miss = new StringBuilder();
                for (String entry : entries) {
                    String e = entry.trim();
                    String name = e.contains("*") ? e.substring(0, e.lastIndexOf('*')) : e;
                    boolean found = name.startsWith("ore:")
                            ? !net.minecraftforge.oredict.OreDictionary.getOres(name.substring(4)).isEmpty()
                            : ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(name.split(":")[0], name.split(":")[1]));
                    if (!found) {
                        miss.append(' ').append(name);
                    }
                }
                warn("ступень " + tier + ": найдено " + resolved + " из " + entries.length + " руд, нет:" + miss);
            }
            if (tier >= 2) {
                String[] costs = ChaosConfig.heart.upgradeCosts[tier - 2].split(",");
                int ok = 0;
                StringBuilder missing = new StringBuilder();
                for (HeartTiers.Cost c : HeartTiers.costFor(tier)) {
                    if (c.ore == null || !net.minecraftforge.oredict.OreDictionary.getOres(c.ore).isEmpty()) {
                        ok++;
                    } else {
                        missing.append(' ').append(c.ore);
                    }
                }
                check(ok == costs.length, "подношение ступени " + tier + ": доступно " + ok + "/" + costs.length + missing);
            }
        }
        if (te instanceof TileHeart) {
            TileHeart h = (TileHeart) te;
            line("Ступень " + h.getTier() + ", наростов " + h.countGrowths() + "/" + h.maxGrowths() + ", интервал " + h.interval());
        }
    }

    private void breach() {
        check(ModItems.BREACH_BOOK != null && ModBlocks.BREACH_ALTAR != null, "предметы Прорыва");
        check(ChaosConfig.breach.threadLevel <= ThreadManager.maxLevel(), "уровень Нити для Прорыва достижим");
        check(ChaosConfig.breach.veilShardChance > 0, "Осколки Завесы выпадают из корки");
        check(ChaosConfig.breach.worldShardChance > 0, "Осколки мира выпадают с монстров");
    }

    private void anchors() {
        int[] r = ru.death.chaosshard.anchor.Corruption.resolved();
        check(r[0] == r[1], "правила Скверны: найдено " + r[0] + " из " + r[1]);
        check(!ru.death.chaosshard.anchor.Corruption.rules().isEmpty(), "Скверне есть что осквернять");
        if (Loader.isModLoaded("defiledlands")) {
            check(ForgeRegistries.BIOMES.containsKey(new ResourceLocation("defiledlands", "plains_defiled")), "биомы Defiled Lands доступны");
            File cfg = new File("config/defiled_lands.cfg");
            boolean noGen = false;
            try {
                String text = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
                noGen = !text.matches("(?s).*I:weight(Desert|Forest|Hills|IcePlains|Plains|Swamp)\\w*=[1-9].*");
            } catch (Exception ignored) {
            }
            check(noGen, "биомы Скверны не генерируются в мире сами (веса = 0 в defiled_lands.cfg)");
        }
        check(ChaosConfig.anchor.threadLevel <= ThreadManager.maxLevel(), "уровень Нити для Якоря достижим");
    }

    private void spells() {
        for (Spell s : Spell.values()) {
            check(s.minThread <= ThreadManager.maxLevel(), "заклинание " + s.name() + " доступно на Нити " + s.minThread);
        }
    }

    private void astral(EntityPlayerMP player) throws Exception {
        if (!ChaosShard.astralLoaded) {
            warn("Astral Sorcery не установлен");
            return;
        }
        Class<?> compat = Class.forName("ru.death.chaosshard.stars.AstralCompat");
        check(compat.getField("CHAOS").get(null) != null, "созвездие Хаоса зарегистрировано");
        for (String cls : new String[]{"constellation.distribution.WorldSkyHandler", "constellation.distribution.ConstellationSkyHandler",
                "util.SkyCollectionHelper", "constellation.perk.PlayerAttributeMap", "constellation.perk.PerkLevelManager"}) {
            boolean mixed = false;
            for (java.lang.reflect.Method m : Class.forName("hellfirepvp.astralsorcery.common." + cls).getDeclaredMethods()) {
                mixed |= m.getName().contains("chaosshard$");
            }
            check(mixed, "миксин в " + cls.substring(cls.lastIndexOf('.') + 1));
        }
        Class<?> bridge = Class.forName("ru.death.chaosshard.stars.PerkBridge");
        int cap = bridge.getField("appliedLevelCap").getInt(null);
        check(ChaosConfig.perks.levelCap <= 0 || cap == ChaosConfig.perks.levelCap, "потолок перков Astral = " + cap);
        int perks = (Integer) bridge.getMethod("perksToBuy").invoke(null);
        check(cap >= perks, "потолка хватает на всё древо (" + perks + " перков)");
    }

    private void mystcraft() throws Exception {
        if (!Loader.isModLoaded("mystcraft")) {
            warn("Mystcraft не установлен");
            return;
        }
        Class<?> mgr = Class.forName("com.xcompwiz.mystcraft.symbol.SymbolManager");
        java.lang.reflect.Method get = null;
        for (java.lang.reflect.Method m : mgr.getMethods()) {
            if (m.getName().equals("getAgeSymbol") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == ResourceLocation.class) {
                get = m;
            }
        }
        if (get == null) {
            warn("не нашёл SymbolManager.getAgeSymbol — символы страниц не проверены");
            return;
        }
        for (IRecipe r : ForgeRegistries.RECIPES) {
            if (r.getRegistryName() == null || !r.getRegistryName().getPath().startsWith("page_")
                    || !Tags.MOD_ID.equals(r.getRegistryName().getNamespace())) {
                continue;
            }
            ItemStack outStack = r.getRecipeOutput();
            String symbol = outStack.hasTagCompound() ? outStack.getTagCompound().getString("symbol") : "";
            Object sym = symbol.isEmpty() ? null : get.invoke(null, new ResourceLocation(symbol));
            check(sym != null, "страница " + r.getRegistryName().getPath() + " → символ " + symbol);
        }
    }

    private void tinkers() throws Exception {
        if (!Loader.isModLoaded("tconstruct") || !ChaosConfig.compat.tinkers) {
            warn("Tinkers или связка выключены");
            return;
        }
        Object mat = Class.forName("slimeknights.tconstruct.library.TinkerRegistry")
                .getMethod("getMaterial", String.class).invoke(null, "chaosshard_chaos");
        Object unknown = Class.forName("slimeknights.tconstruct.library.materials.Material").getField("UNKNOWN").get(null);
        check(mat != null && mat != unknown, "материал «Хаосит» зарегистрирован");
        Object dist = Class.forName("slimeknights.tconstruct.library.TinkerRegistry")
                .getMethod("getMaterial", String.class).invoke(null, "chaosshard_distorted");
        check(dist != null && dist != unknown, "материал «Искажённый звёздный металл» зарегистрирован");
    }

    private void quests() throws Exception {
        File file = new File("config/betterquesting/DefaultQuests.json");
        if (!file.exists()) {
            check(false, "DefaultQuests.json есть");
            return;
        }
        JsonObject root;
        try (Reader r = new InputStreamReader(new FileInputStreamCompat(file), StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(r).getAsJsonObject();
        }
        List<String> ids = new ArrayList<>();
        collectIds(root, ids);
        Set<String> missing = new TreeSet<>();
        for (String id : ids) {
            ResourceLocation rl = new ResourceLocation(id);
            if (!ForgeRegistries.ITEMS.containsKey(rl) && !ForgeRegistries.BLOCKS.containsKey(rl)) {
                missing.add(id);
            }
        }
        // задачи-достижения: такое достижение должно быть в игре
        Set<String> advMissing = new TreeSet<>();
        net.minecraft.server.MinecraftServer srv = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (srv != null) {
            List<String> advs = new ArrayList<>();
            collectKey(root, "advancement_id:8", advs);
            for (String a : advs) {
                if (srv.getAdvancementManager().getAdvancement(new ResourceLocation(a)) == null) {
                    advMissing.add(a);
                }
            }
            line("Задач-достижений в квестах: " + advs.size());
            check(advMissing.isEmpty(), "все достижения квестов есть в игре" + (advMissing.isEmpty() ? "" : ": нет " + advMissing));
            List<String> ents = new ArrayList<>();
            collectKey(root, "target:8", ents);
            Set<String> entMissing = new TreeSet<>();
            for (String en : ents) {
                if (en.contains(":") && !ForgeRegistries.ENTITIES.containsKey(new ResourceLocation(en))) {
                    entMissing.add(en);
                }
            }
            check(entMissing.isEmpty(), "все существа охоты есть в игре" + (entMissing.isEmpty() ? "" : ": нет " + entMissing));
        }
        line("Ссылок на предметы в квестах: " + ids.size());
        check(missing.isEmpty(), "все предметы квестов существуют" + (missing.isEmpty() ? "" : ": нет " + missing));
        if (Loader.isModLoaded("betterquesting")) {
            Object db = Class.forName("betterquesting.questing.QuestDatabase").getField("INSTANCE").get(null);
            int inWorld = (Integer) db.getClass().getMethod("size").invoke(db);
            int inFile = root.getAsJsonObject("questDatabase:9").entrySet().size();
            check(inWorld == inFile, "квесты в мире совпадают с файлом (" + inWorld + "/" + inFile + ")"
                    + (inWorld == inFile ? "" : " — выполните /bq_admin default load"));
        }
    }

    private static void collectKey(JsonElement e, String key, List<String> out) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals(key) && en.getValue().isJsonPrimitive()) {
                    out.add(en.getValue().getAsString());
                } else {
                    collectKey(en.getValue(), key, out);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                collectKey(c, key, out);
            }
        }
    }

    private static void collectIds(JsonElement e, List<String> out) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals("id:8") && en.getValue().isJsonPrimitive()) {
                    String v = en.getValue().getAsString();
                    if (v.contains(":")) {
                        out.add(v);
                    }
                } else {
                    collectIds(en.getValue(), out);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                collectIds(c, out);
            }
        }
    }

    private void crafttweaker() throws Exception {
        File log = new File("crafttweaker.log");
        if (!log.exists()) {
            warn("crafttweaker.log не найден");
            return;
        }
        int errors = 0;
        for (String l : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
            if (l.contains("[ERROR]") || l.contains("ERROR:")) {
                errors++;
                if (errors <= 15) {
                    line("  " + l.trim());
                }
            }
        }
        check(errors == 0, "ошибок в скриптах: " + errors);
        for (String s : new String[]{"chaosshard_stars.zs", "chaosshard_compat.zs"}) {
            check(new File("scripts/" + s).exists(), "скрипт " + s);
        }
    }

    private void translation() {
        File dir = new File("resourcepacks");
        File[] packs = dir.listFiles((d, n) -> n.toLowerCase().contains("oskolok") && n.toLowerCase().contains("ru"));
        check(packs != null && packs.length > 0, "ресурс-пак русификации лежит в resourcepacks");
    }

    // ------------------------------------------------------------------ служебное

    private interface Part {
        void run() throws Exception;
    }

    private void section(String title, Part part) {
        section = title;
        bySection.put(title, new int[3]);
        line("");
        line("--- " + title + " ---");
        try {
            part.run();
        } catch (Throwable t) {
            check(false, "раздел упал: " + t);
        }
    }

    private void check(boolean ok, String what) {
        int[] s = bySection.get(section);
        if (ok) {
            pass++;
            s[0]++;
            line("  [OK]   " + what);
        } else {
            fail++;
            s[1]++;
            line("  [FAIL] " + what);
        }
    }

    private void warn(String what) {
        warn++;
        bySection.get(section)[2]++;
        line("  [WARN] " + what);
    }

    private void summary() {
        line("");
        line("=== Итог ===");
        for (Map.Entry<String, int[]> e : bySection.entrySet()) {
            int[] s = e.getValue();
            int total = s[0] + s[1];
            line(String.format("  %-30s %3d%%  (ок %d, ошибок %d, предупреждений %d)", e.getKey(),
                    total == 0 ? 100 : s[0] * 100 / total, s[0], s[1], s[2]));
        }
        int total = pass + fail;
        line(String.format("Всего: %d%% проверок пройдено (ок %d, ошибок %d, предупреждений %d)",
                total == 0 ? 100 : pass * 100 / total, pass, fail, warn));
    }

    public int failures() {
        return fail;
    }

    public int percent() {
        int total = pass + fail;
        return total == 0 ? 100 : pass * 100 / total;
    }

    public File write() throws Exception {
        File file = new File(FILE_NAME).getAbsoluteFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(out.toString());
        }
        return file;
    }

    private void line(String s) {
        out.append(s).append('\n');
    }

    /** FileInputStream под коротким именем (чтобы не тянуть лишний импорт в сигнатуры). */
    private static final class FileInputStreamCompat extends java.io.FileInputStream {
        FileInputStreamCompat(File f) throws java.io.FileNotFoundException {
            super(f);
        }
    }

    private SelfTest() {
    }
}
