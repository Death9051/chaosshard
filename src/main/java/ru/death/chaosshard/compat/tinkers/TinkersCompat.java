package ru.death.chaosshard.compat.tinkers;

import net.minecraft.item.Item;
import net.minecraftforge.fml.common.FMLCommonHandler;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.init.ModBlocks;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.materials.ExtraMaterialStats;
import slimeknights.tconstruct.library.materials.HandleMaterialStats;
import slimeknights.tconstruct.library.materials.HeadMaterialStats;
import slimeknights.tconstruct.library.materials.Material;

/**
 * Tinkers' Construct: материал «Хаосит».
 * Детали делаются прямо из хаосита в столе деталей. Свойство «Нить Хаоса» — урон растёт с уровнем Нити.
 * Класс загружается только если Tinkers установлен.
 */
public final class TinkersCompat {

    public static final int COLOR = 0x6A2AA0;
    private static Material chaos;
    private static TraitThread thread;
    private static Material distorted;
    public static final int DISTORTED_COLOR = 0x9B3CFF;
    public static final int CRYSTAL_COLOR = 0xC9A8FF, BLOOD_COLOR = 0xB0303A, STAR_COLOR = 0x8FB8FF;
    private static Material crystal, blood, star;
    /** Жидкие металлы Хаоса для плавильни Tinkers. */
    private static net.minecraftforge.fluids.Fluid moltenChaos, moltenCrystal, moltenBlood, moltenStar;

    public static void preInit() {
        thread = new TraitThread();
        chaos = new Material("chaosshard_chaos", COLOR);
        chaos.setCraftable(true);
        moltenChaos = molten("chaosshard_chaos", COLOR, 900);
        chaos.setFluid(moltenChaos);
        chaos.setCastable(true);
        chaos.addTrait(thread);
        TinkerRegistry.addMaterial(chaos);
        TinkerRegistry.addMaterialStats(chaos,
                new HeadMaterialStats(220, 5.0F, 3.5F, 1),
                new HandleMaterialStats(0.9F, 40),
                new ExtraMaterialStats(60));
        distorted = new Material("chaosshard_distorted", DISTORTED_COLOR);
        distorted.setCraftable(true);
        distorted.setCastable(false);
        distorted.addTrait(thread);
        distorted.addTrait(new TraitDistortion());
        TinkerRegistry.addMaterial(distorted);
        TinkerRegistry.addMaterialStats(distorted,
                new HeadMaterialStats(650, 7.5F, 6.0F, 3),
                new HandleMaterialStats(1.1F, 120),
                new ExtraMaterialStats(150));
        // Сплавы Хаоса (уровень добычи: 3 — обсидиан, 4 — кобальт)
        moltenCrystal = molten("chaosshard_crystal", CRYSTAL_COLOR, 1000);
        moltenBlood = molten("chaosshard_bloodplatinum", BLOOD_COLOR, 1100);
        moltenStar = molten("chaosshard_starlight", STAR_COLOR, 1200);
        crystal = alloy(ChaosMaterials.CRYSTAL, CRYSTAL_COLOR, moltenCrystal, new AlloyTraits.Crystal(),
                new HeadMaterialStats(480, 7.0F, 5.0F, 3), new HandleMaterialStats(1.0F, 80), new ExtraMaterialStats(100));
        blood = alloy(ChaosMaterials.BLOOD, BLOOD_COLOR, moltenBlood, new AlloyTraits.Blood(),
                new HeadMaterialStats(900, 6.5F, 6.5F, 4), new HandleMaterialStats(1.05F, 150), new ExtraMaterialStats(180));
        star = alloy(ChaosMaterials.STAR, STAR_COLOR, moltenStar, new AlloyTraits.Star(),
                new HeadMaterialStats(750, 8.5F, 5.5F, 4), new HandleMaterialStats(1.1F, 120), new ExtraMaterialStats(150));
        if (FMLCommonHandler.instance().getSide().isClient()) {
            chaos.setRenderInfo(COLOR);
            distorted.setRenderInfo(DISTORTED_COLOR);
            crystal.setRenderInfo(CRYSTAL_COLOR);
            blood.setRenderInfo(BLOOD_COLOR);
            star.setRenderInfo(STAR_COLOR);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new MaterialTooltips());
        }
        ChaosMaterials hook = new ChaosMaterials();
        ru.death.chaosshard.spell.SpellMods.register(hook);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(hook);
        EchoModifiers.preInit();
        ChaosShard.LOG.info("Tinkers: материал «Хаосит» и отголоски Светочей зарегистрированы");
    }

    private static Material alloy(String id, int color, net.minecraftforge.fluids.Fluid fluid, slimeknights.tconstruct.library.traits.ITrait trait,
                                  HeadMaterialStats head, HandleMaterialStats handle, ExtraMaterialStats extra) {
        Material m = new Material(id, color);
        m.setCraftable(true);
        // детали и отливаются (жидкий сплав), и собираются на столе деталей из слитков
        m.setFluid(fluid);
        m.setCastable(true);
        m.addTrait(thread);
        m.addTrait(trait);
        TinkerRegistry.addMaterial(m);
        TinkerRegistry.addMaterialStats(m, head, handle, extra);
        return m;
    }

    /** Жидкий металл (вид — как у металлов Tinkers, ведро — универсальное). */
    private static net.minecraftforge.fluids.Fluid molten(String name, int color, int temperature) {
        net.minecraftforge.fluids.Fluid f = new slimeknights.tconstruct.library.fluid.FluidMolten(name, color);
        f.setTemperature(temperature);
        if (!net.minecraftforge.fluids.FluidRegistry.registerFluid(f)) {
            f = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        }
        net.minecraftforge.fluids.FluidRegistry.addBucketForFluid(f);
        return f;
    }

    /**
     * Плавильня: слитки и камень Хаоса плавятся и отливаются; два сплава собираются из жидкостей.
     * Кровавая платина: 144 платины + 500 крови EvilCraft → 144. Свет звезды: 144 звёздного металла + 250 звёздного света → 144.
     * Кристаллический Хаос из жидкостей не собирается (горный хрусталь не плавится) — только верстак.
     */
    public static void postInit() {
        if (chaos == null) {
            return;
        }
        Item stone = Item.getItemFromBlock(ModBlocks.CHAOS_STONE);
        slimeknights.tconstruct.library.TinkerRegistry.registerMelting(stone, moltenChaos, Material.VALUE_Ingot);
        oreCasting("CrystalChaos", ru.death.chaosshard.init.ModItems.CRYSTAL_CHAOS_INGOT, moltenCrystal);
        oreCasting("BloodPlatinum", ru.death.chaosshard.init.ModItems.BLOOD_PLATINUM_INGOT, moltenBlood);
        oreCasting("Starlight", ru.death.chaosshard.init.ModItems.STARLIGHT_INGOT, moltenStar);
        alloy(moltenBlood, "platinum", 144, "evilcraftblood", 500);
        alloy(moltenStar, "starmetal", 144, "astralsorcery.liquidstarlight", 250);
    }

    private static void oreCasting(String ore, Item ingot, net.minecraftforge.fluids.Fluid fluid) {
        if (ingot == null || fluid == null) {
            return;
        }
        net.minecraftforge.oredict.OreDictionary.registerOre("ingot" + ore, ingot);
        slimeknights.tconstruct.smeltery.TinkerSmeltery.registerOredictMeltingCasting(fluid, ore);
    }

    private static void alloy(net.minecraftforge.fluids.Fluid out, String a, int amountA, String b, int amountB) {
        net.minecraftforge.fluids.Fluid fa = net.minecraftforge.fluids.FluidRegistry.getFluid(a);
        net.minecraftforge.fluids.Fluid fb = net.minecraftforge.fluids.FluidRegistry.getFluid(b);
        if (out == null || fa == null || fb == null) {
            ChaosShard.LOG.info("Tinkers: сплав {} не добавлен — нет жидкости {}", out == null ? "?" : out.getName(), fa == null ? a : b);
            return;
        }
        slimeknights.tconstruct.library.TinkerRegistry.registerAlloy(new net.minecraftforge.fluids.FluidStack(out, 144),
                new net.minecraftforge.fluids.FluidStack(fa, amountA), new net.minecraftforge.fluids.FluidStack(fb, amountB));
    }

    /** После регистрации блоков: какой предмет считается материалом. */
    public static void init() {
        if (chaos == null) {
            return;
        }
        EchoModifiers.init();
        Item stone = Item.getItemFromBlock(ModBlocks.CHAOS_STONE);
        chaos.addItem(stone, 1, Material.VALUE_Ingot);
        // Модификатор «Нить Хаоса» для любого инструмента: хаосит (1) или Эссенция Хаоса (16)
        thread.addItem(stone, 1, 1);
        thread.addItem(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, 1, 16);
        chaos.setRepresentativeItem(stone);
        if (distorted != null) {
            distorted.addItem(ru.death.chaosshard.init.ModItems.DISTORTED_STARMETAL, 1, Material.VALUE_Ingot);
            distorted.setRepresentativeItem(ru.death.chaosshard.init.ModItems.DISTORTED_STARMETAL);
        }
        bind(crystal, ru.death.chaosshard.init.ModItems.CRYSTAL_CHAOS_INGOT);
        bind(blood, ru.death.chaosshard.init.ModItems.BLOOD_PLATINUM_INGOT);
        bind(star, ru.death.chaosshard.init.ModItems.STARLIGHT_INGOT);
    }

    private static void bind(Material m, Item item) {
        if (m != null && item != null) {
            m.addItem(item, 1, Material.VALUE_Ingot);
            m.setRepresentativeItem(item);
        }
    }

    private TinkersCompat() {
    }
}
