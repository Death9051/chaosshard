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

    public static void preInit() {
        chaos = new Material("chaosshard_chaos", COLOR);
        chaos.setCraftable(true);
        chaos.setCastable(false);
        chaos.addTrait(new TraitThread());
        TinkerRegistry.addMaterial(chaos);
        TinkerRegistry.addMaterialStats(chaos,
                new HeadMaterialStats(220, 5.0F, 3.5F, 1),
                new HandleMaterialStats(0.9F, 40),
                new ExtraMaterialStats(60));
        if (FMLCommonHandler.instance().getSide().isClient()) {
            chaos.setRenderInfo(COLOR);
        }
        ChaosShard.LOG.info("Tinkers: материал «Хаосит» зарегистрирован");
    }

    /** После регистрации блоков: какой предмет считается материалом. */
    public static void init() {
        if (chaos == null) {
            return;
        }
        Item stone = Item.getItemFromBlock(ModBlocks.CHAOS_STONE);
        chaos.addItem(stone, 1, Material.VALUE_Ingot);
        chaos.setRepresentativeItem(stone);
    }

    private TinkersCompat() {
    }
}
