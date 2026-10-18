package ru.death.chaosshard.init;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;

/** Звуки мода. */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModSounds {

    /** Шелест страницы Гримуара (три варианта, sounds/book/page1..3). */
    public static final SoundEvent BOOK_PAGE = make("book.page");

    private static SoundEvent make(String name) {
        ResourceLocation id = new ResourceLocation(Tags.MOD_ID, name);
        return new SoundEvent(id).setRegistryName(id);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().register(BOOK_PAGE);
    }

    private ModSounds() {
    }
}
