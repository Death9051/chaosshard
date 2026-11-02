package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;
import ru.death.chaosshard.ChaosShard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;

/**
 * Подписи страниц Mystcraft по-русски.
 * <ul>
 * <li>Mystcraft переводит подпись страницы один раз и запоминает её навсегда. Если это случилось раньше, чем загрузился
 * русский язык, страница так и остаётся с английским названием («Iron Ore Block»). Поэтому после каждой загрузки
 * языка и при входе в мир запомненные подписи сбрасываются, и Mystcraft переводит их заново.</li>
 * <li>Страницы с биомом подписаны «Биом (Extreme Hills)»: в скобках Mystcraft ставит внутреннее имя биома, а оно в
 * 1.12 есть только по-английски. Такая подпись собирается из нашего перевода имени биома.</li>
 * </ul>
 * Только клиент и только текст: страница по-прежнему опознаётся по номеру биома, сохранения не меняются. Перевод
 * берётся по ключу {@code chaosshard.biome.<мод>.<биом>} (а если такого нет — по английскому имени
 * {@code chaosshard.biome.name.<имя>}); без перевода подпись остаётся как в Mystcraft. Пересчитывается при каждой
 * перезагрузке ресурсов, так что смена языка тоже подхватывается.
 */
public final class MystBiomeNames implements IResourceManagerReloadListener {

    private static final String SYMBOL_MANAGER = "com.xcompwiz.mystcraft.symbol.SymbolManager";
    private static final String SYMBOL_BIOME = "com.xcompwiz.mystcraft.symbol.modifiers.SymbolBiome";
    private static final String SYMBOL_BASE = "com.xcompwiz.mystcraft.symbol.SymbolBase";

    private static boolean failed;

    private MystBiomeNames() {
    }

    public static void register() {
        if (!Loader.isModLoaded("mystcraft")) {
            return;
        }
        MystBiomeNames l = new MystBiomeNames();
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(l);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(l);
        apply();
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        apply();
    }

    /** При входе в мир — ещё раз: на случай, если язык догрузился без перезагрузки ресурсов. */
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote && event.getEntity() == Minecraft.getMinecraft().player) {
            apply();
        }
    }

    /** Ключ перевода биома по имени в реестре. */
    public static String key(Biome b) {
        ResourceLocation id = b.getRegistryName();
        return id == null ? null : "chaosshard.biome." + id.getNamespace() + "." + id.getPath();
    }

    /** Переведённое имя биома или null, если перевода нет. */
    public static String translated(Biome b) {
        String k = key(b);
        if (k != null && I18n.hasKey(k)) {
            return I18n.format(k);
        }
        String n = "chaosshard.biome.name." + b.getBiomeName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return I18n.hasKey(n) ? I18n.format(n) : null;
    }

    /** Переписать подписи всех страниц-биомов. Возвращает, сколько переведено. */
    public static int apply() {
        if (failed) {
            return 0;
        }
        try {
            Class<?> manager = Class.forName(SYMBOL_MANAGER);
            Class<?> symbolBiome = Class.forName(SYMBOL_BIOME);
            Field biomeField = symbolBiome.getDeclaredField("biome");
            biomeField.setAccessible(true);
            Field nameField = Class.forName(SYMBOL_BASE).getDeclaredField("localizedName");   // кэш подписи
            nameField.setAccessible(true);
            Class<?> symbolBase = Class.forName(SYMBOL_BASE);
            Method all = manager.getMethod("getAgeSymbols");
            int n = 0;
            for (Object s : (Collection<?>) all.invoke(null)) {
                if (!symbolBase.isInstance(s)) {
                    continue;
                }
                if (!symbolBiome.isInstance(s)) {
                    nameField.set(s, null);   // переведётся заново на текущем языке
                    continue;
                }
                Object b = biomeField.get(s);
                String ru = b instanceof Biome ? translated((Biome) b) : null;
                // без перевода — сбросить, Mystcraft соберёт подпись сам
                nameField.set(s, ru == null ? null : I18n.format("myst.symbol.biome.wrapper", ru));
                if (ru != null) {
                    n++;
                }
            }
            return n;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            ChaosShard.LOG.warn("Перевод биомов на страницах Mystcraft не подключился: {}", e.toString());
            return 0;
        }
    }
}
