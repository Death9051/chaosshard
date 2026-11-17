package ru.death.chaosshard.mixin;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * Миксины в классы других модов (Astral Sorcery, Defiled Lands). Работает с MixinBooter 10.x и 11.x.
 * Если Astral Sorcery не установлен, миксины не подключаются.
 */
@SuppressWarnings("deprecation")
public class ChaosMixinLoader implements ILateMixinLoader {

    public static final String ASTRAL_CONFIG = "mixins.chaosshard.astral.json";
    public static final String DEFILED_CONFIG = "mixins.chaosshard.defiled.json";

    @Override
    public List<String> getMixinConfigs() {
        return java.util.Arrays.asList(ASTRAL_CONFIG, DEFILED_CONFIG);
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if (ASTRAL_CONFIG.equals(mixinConfig)) {
            return Loader.isModLoaded("astralsorcery");
        }
        if (DEFILED_CONFIG.equals(mixinConfig)) {
            return Loader.isModLoaded("defiledlands");
        }
        return true;
    }
}
