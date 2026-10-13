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
        if (FMLCommonHandler.instance().getSide().isClient()) {
            chaos.setRenderInfo(COLOR);
            distorted.setRenderInfo(DISTORTED_COLOR);
        }
        EchoModifiers.preInit();
        ChaosShard.LOG.info("Tinkers: материал «Хаосит» и отголоски Светочей зарегистрированы");
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
    }

    private TinkersCompat() {
    }
}
