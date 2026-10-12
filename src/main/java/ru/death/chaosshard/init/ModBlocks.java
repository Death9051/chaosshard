package ru.death.chaosshard.init;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
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

    public static Block BREACH_ALTAR;
    public static Block RITUAL_SPIRE;
    /** Знак мела — без своего предмета (рисуется Мелом Хаоса). */
    public static Block CHALK;
    public static Block RITUAL_BOWL;
    public static Block ISLAND_HEART;
    public static Block CHAOS_ANCHOR;
    public static Block HEART_HARVESTER;
    /** Святой камень Ордена и он же после осквернения. */
    public static Block SHRINE_STONE;
    public static Block DESECRATED_STONE;
    /** Знак покровителя деревни. */
    public static Block PATRON_SIGIL;
    /** Жила Завесы — нарост Сердца с модулем «Богатство». */
    public static Block VEIL_VEIN;

    /** Тестовые блоки-структуры (только творческий режим). */
    public static final List<Block> SEEDS = new ArrayList<>();

    public static final List<Block> ALL = new ArrayList<>();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        CHAOS_STONE = add(new SimpleBlock(Material.GROUND, SoundType.STONE, 1.2F, 12.0F, 0.0F), "chaos_stone");
        CHAOS_CRUST = add(new ru.death.chaosshard.breach.BlockCrust(), "chaos_crust");
        ISLAND_HEART = add(new ru.death.chaosshard.heart.BlockHeart(), "island_heart");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(ru.death.chaosshard.heart.TileHeart.class,
                new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "island_heart"));
        HEART_HARVESTER = add(new ru.death.chaosshard.heart.BlockHarvester(), "heart_harvester");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(ru.death.chaosshard.heart.TileHarvester.class,
                new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "heart_harvester"));
        VEIL_VEIN = add(new ru.death.chaosshard.heart.BlockVeilVein(), "veil_vein");
        SHRINE_STONE = add(new ru.death.chaosshard.order.BlockShrineStone(), "shrine_stone");
        DESECRATED_STONE = add(new SimpleBlock(Material.ROCK, SoundType.STONE, 3.0F, 30.0F, 0.3F), "desecrated_stone");
        PATRON_SIGIL = add(new ru.death.chaosshard.village.BlockPatronSigil(), "patron_sigil");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(ru.death.chaosshard.village.TilePatronSigil.class,
                new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "patron_sigil"));
        CHAOS_ANCHOR = add(new ru.death.chaosshard.anchor.BlockAnchor(), "chaos_anchor");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(ru.death.chaosshard.anchor.TileAnchor.class,
                new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "chaos_anchor"));
        BREACH_ALTAR = add(new ru.death.chaosshard.breach.BlockBreachAltar(), "breach_altar");
        RITUAL_SPIRE = add(new ru.death.chaosshard.breach.BlockRitualSpire(), "ritual_spire");
        for (ru.death.chaosshard.debug.StructureBuilder.Kind k : ru.death.chaosshard.debug.StructureBuilder.Kind.values()) {
            SEEDS.add(add(new ru.death.chaosshard.debug.BlockStructureSeed(k), "seed_" + k.name().toLowerCase()));
        }
        RITUAL_BOWL = add(new ru.death.chaosshard.ritual.BlockRitualBowl(), "ritual_bowl");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(ru.death.chaosshard.ritual.TileRitualBowl.class,
                new net.minecraft.util.ResourceLocation(Tags.MOD_ID, "ritual_bowl"));
        for (Block block : ALL) {
            event.getRegistry().register(block);
        }
        CHALK = new ru.death.chaosshard.ritual.BlockChalk();
        CHALK.setRegistryName(Tags.MOD_ID, "chalk_rune");
        CHALK.setTranslationKey(Tags.MOD_ID + ".chalk_rune");
        event.getRegistry().register(CHALK);
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
        block.setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
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
