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
    /** Осколок мира — выпадает из тварей в Кладовках и чужих мирах. */
    public static Item WORLD_SHARD;
    /** Страница памяти «Прорыв». */
    public static Item MEMORY_PAGE;
    /** Книга Прорыва. */
    public static Item BREACH_BOOK;
    /** Эссенция Хаоса — перемолотый Осколок Завесы. */
    public static Item CHAOS_ESSENCE;
    /** Искажённый звёздный металл — звёздный металл, пропитанный Хаосом. */
    public static Item DISTORTED_STARMETAL;
    /** Знамя Мрачной Армии — трофей с капитана Гаррота. */
    public static Item GARROT_BANNER;
    /** Мел Хаоса — рисует знаки ритуального круга. */
    public static Item CHALK;
    /** Ключ обломка — вход в Испытание. */
    public static Item TRIAL_KEY;
    /** Чешуя дракона — падает с Эндер-дракона. */
    public static Item DRAGON_SCALE;
    /** Улов Хаоса — еда от Кошки 5-го уровня. */
    public static Item CHAOS_CATCH;
    /** Печати Якоря по типам. */
    public static final java.util.Map<ru.death.chaosshard.anchor.ItemAnchorSeal.Type, Item> SEALS = new java.util.EnumMap<>(ru.death.chaosshard.anchor.ItemAnchorSeal.Type.class);

    /** Сфера ритуала (метаданные — номер ритуала в Ritual.SPHERES). */
    public static Item RITUAL_SPHERE;
    /** Сфера Вызова (метаданные — сторона, состав — в NBT). */
    public static Item CHALLENGE_SPHERE;

    /** Клинок Каэля — трофей первой Опоры. */
    public static Item KAEL_BLADE;

    public static final List<Item> ALL = new ArrayList<>();

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        GRIMOIRE = new ItemGrimoire();
        GRIMOIRE.setRegistryName(Tags.MOD_ID, "grimoire");
        GRIMOIRE.setTranslationKey(Tags.MOD_ID + ".grimoire");
        ALL.add(GRIMOIRE);
        VEIL_SHARD = add(new ru.death.chaosshard.item.ItemLore("chaosshard.veil_shard.lore", false), "veil_shard");
        WORLD_SHARD = add(new ru.death.chaosshard.item.ItemLore("chaosshard.world_shard.lore", false), "world_shard");
        MEMORY_PAGE = add(new ru.death.chaosshard.item.ItemLore("chaosshard.memory_page.lore", true).setMaxStackSize(1), "memory_page");
        BREACH_BOOK = add(new ru.death.chaosshard.item.ItemBreachBook(), "breach_book");
        CHAOS_ESSENCE = add(new ru.death.chaosshard.item.ItemLore("chaosshard.chaos_essence.lore", false), "chaos_essence");
        GARROT_BANNER = add(new ru.death.chaosshard.item.ItemLore("chaosshard.garrot_banner.lore", false), "garrot_banner");
        DISTORTED_STARMETAL = add(new ru.death.chaosshard.item.ItemLore("chaosshard.distorted_starmetal.lore", true), "distorted_starmetal");
        CHALK = add(new ru.death.chaosshard.ritual.ItemChalk(), "chaos_chalk");
        TRIAL_KEY = add(new ru.death.chaosshard.trial.ItemTrialKey(), "trial_key");
        DRAGON_SCALE = add(new ru.death.chaosshard.item.ItemLore("chaosshard.dragon_scale.lore", true), "dragon_scale");
        CHAOS_CATCH = add(new ru.death.chaosshard.servant.ItemChaosCatch(), "chaos_catch");
        RITUAL_SPHERE = add(new ru.death.chaosshard.ritual.ItemRitualSphere(), "ritual_sphere");
        RITUAL_SPHERE.setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
        CHALLENGE_SPHERE = add(new ru.death.chaosshard.challenge.ItemChallengeSphere(), "challenge_sphere");
        CHALLENGE_SPHERE.setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
        KAEL_BLADE = add(new ru.death.chaosshard.hero.ItemKaelBlade(), "kael_blade");
        KAEL_BLADE.setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
        for (ru.death.chaosshard.anchor.ItemAnchorSeal.Type t : ru.death.chaosshard.anchor.ItemAnchorSeal.Type.values()) {
            SEALS.put(t, add(new ru.death.chaosshard.anchor.ItemAnchorSeal(t), "anchor_seal_" + t.name().toLowerCase()));
        }
        for (ru.death.chaosshard.heart.ItemHeartModule.Type t : ru.death.chaosshard.heart.ItemHeartModule.Type.values()) {
            add(new ru.death.chaosshard.heart.ItemHeartModule(t), "heart_module_" + t.name().toLowerCase());
        }
        for (Item item : ALL) {
            event.getRegistry().register(item);
        }
        // Импланты Хаоса (Robotic Parts) регистрируются сами в конструкторе
        ru.death.chaosshard.compat.body.Body.registerItems();
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
