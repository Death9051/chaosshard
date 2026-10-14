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

    public static void preInit() {
        thread = new TraitThread();
        chaos = new Material("chaosshard_chaos", COLOR);
        chaos.setCraftable(true);
        chaos.setCastable(false);
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
        crystal = alloy(ChaosMaterials.CRYSTAL, CRYSTAL_COLOR, new AlloyTraits.Crystal(),
                new HeadMaterialStats(480, 7.0F, 5.0F, 3), new HandleMaterialStats(1.0F, 80), new ExtraMaterialStats(100));
        blood = alloy(ChaosMaterials.BLOOD, BLOOD_COLOR, new AlloyTraits.Blood(),
                new HeadMaterialStats(900, 6.5F, 6.5F, 4), new HandleMaterialStats(1.05F, 150), new ExtraMaterialStats(180));
        star = alloy(ChaosMaterials.STAR, STAR_COLOR, new AlloyTraits.Star(),
                new HeadMaterialStats(750, 8.5F, 5.5F, 4), new HandleMaterialStats(1.1F, 120), new ExtraMaterialStats(150));
        if (FMLCommonHandler.instance().getSide().isClient()) {
            chaos.setRenderInfo(COLOR);
            distorted.setRenderInfo(DISTORTED_COLOR);
            crystal.setRenderInfo(CRYSTAL_COLOR);
            blood.setRenderInfo(BLOOD_COLOR);
            star.setRenderInfo(STAR_COLOR);
        }
        ChaosMaterials hook = new ChaosMaterials();
        ru.death.chaosshard.spell.SpellMods.register(hook);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(hook);
        EchoModifiers.preInit();
        ChaosShard.LOG.info("Tinkers: материал «Хаосит» и отголоски Светочей зарегистрированы");
    }

    private static Material alloy(String id, int color, slimeknights.tconstruct.library.traits.ITrait trait,
                                  HeadMaterialStats head, HandleMaterialStats handle, ExtraMaterialStats extra) {
        Material m = new Material(id, color);
        m.setCraftable(true);
        m.setCastable(false);
        m.addTrait(thread);
        m.addTrait(trait);
        TinkerRegistry.addMaterial(m);
        TinkerRegistry.addMaterialStats(m, head, handle, extra);
        return m;
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
