package ru.death.chaosshard.init;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * Блоки мода.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModBlocks {

    /** Хаосит — застывшая материя Плана Хаоса, из него сложен остров Преддверия. */
    public static Block CHAOS_STONE;
    /** Корка Преддверия — верхний слой острова, в ней мерцает звёздная пыль. */
    public static Block CHAOS_CRUST;

    public static final List<Block> ALL = new ArrayList<>();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        CHAOS_STONE = add(new SimpleBlock(Material.GROUND, SoundType.STONE, 1.2F, 12.0F, 0.0F), "chaos_stone");
        CHAOS_CRUST = add(new SimpleBlock(Material.GROUND, SoundType.STONE, 0.8F, 8.0F, 0.2F), "chaos_crust");
        for (Block block : ALL) {
            event.getRegistry().register(block);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        for (Block block : ALL) {
            Item item = new ItemBlock(block).setRegistryName(block.getRegistryName());
            event.getRegistry().register(item);
        }
    }

    private static Block add(Block block, String name) {
        block.setRegistryName(Tags.MOD_ID, name);
        block.setTranslationKey(Tags.MOD_ID + "." + name);
        block.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        ALL.add(block);
        return block;
    }

    /** Простой блок с настройками в конструкторе (setSoundType в 1.12 защищённый). */
    public static class SimpleBlock extends Block {
        public SimpleBlock(Material material, SoundType sound, float hardness, float resistance, float light) {
            super(material);
            setSoundType(sound);
            setHardness(hardness);
            setResistance(resistance);
            setLightLevel(light);
            // Ломается рукой (материал не требует инструмента), кирка — быстрее
            setHarvestLevel("pickaxe", 0);
        }
    }

    private ModBlocks() {
    }
}
