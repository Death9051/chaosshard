package ru.death.chaosshard.init;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.item.ItemGrimoire;

import java.util.ArrayList;
import java.util.List;

/**
 * Предметы мода (кроме блоков-предметов).
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModItems {

    /** Гримуар — артефакт Лорда для Осколков; открывает книгу квестов. */
    public static Item GRIMOIRE;

    /** Осколок Завесы — выпадает из корки Преддверия. */
    public static Item VEIL_SHARD;
    /** Обрывок памяти — выпадает из тварей в Кладовках и чужих мирах. */
    public static Item MEMORY_FRAGMENT;
    /** Страница памяти «Прорыв». */
    public static Item MEMORY_PAGE;
    /** Книга Прорыва. */
    public static Item BREACH_BOOK;

    public static final List<Item> ALL = new ArrayList<>();

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        GRIMOIRE = new ItemGrimoire();
        GRIMOIRE.setRegistryName(Tags.MOD_ID, "grimoire");
        GRIMOIRE.setTranslationKey(Tags.MOD_ID + ".grimoire");
        ALL.add(GRIMOIRE);
        VEIL_SHARD = add(new ru.death.chaosshard.item.ItemLore("chaosshard.veil_shard.lore", false), "veil_shard");
        MEMORY_FRAGMENT = add(new ru.death.chaosshard.item.ItemLore("chaosshard.memory_fragment.lore", false), "memory_fragment");
        MEMORY_PAGE = add(new ru.death.chaosshard.item.ItemLore("chaosshard.memory_page.lore", true).setMaxStackSize(1), "memory_page");
        BREACH_BOOK = add(new ru.death.chaosshard.item.ItemBreachBook(), "breach_book");
        for (ru.death.chaosshard.heart.ItemHeartModule.Type t : ru.death.chaosshard.heart.ItemHeartModule.Type.values()) {
            add(new ru.death.chaosshard.heart.ItemHeartModule(t), "heart_module_" + t.name().toLowerCase());
        }
        for (Item item : ALL) {
            event.getRegistry().register(item);
        }
    }

    private static Item add(Item item, String name) {
        item.setRegistryName(Tags.MOD_ID, name);
        item.setTranslationKey(Tags.MOD_ID + "." + name);
        ALL.add(item);
        return item;
    }

    private ModItems() {
    }
}
