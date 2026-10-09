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

    public static final List<Item> ALL = new ArrayList<>();

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        GRIMOIRE = new ItemGrimoire();
        GRIMOIRE.setRegistryName(Tags.MOD_ID, "grimoire");
        GRIMOIRE.setTranslationKey(Tags.MOD_ID + ".grimoire");
        ALL.add(GRIMOIRE);
        for (Item item : ALL) {
            event.getRegistry().register(item);
        }
    }

    private ModItems() {
    }
}
