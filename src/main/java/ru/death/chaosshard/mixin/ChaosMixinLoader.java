package ru.death.chaosshard.mixin;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * Миксины в классы других модов (Astral Sorcery). Работает с MixinBooter 10.x и 11.x.
 * Если Astral Sorcery не установлен, миксины не подключаются.
 */
@SuppressWarnings("deprecation")
public class ChaosMixinLoader implements ILateMixinLoader {

    public static final String ASTRAL_CONFIG = "mixins.chaosshard.astral.json";

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList(ASTRAL_CONFIG);
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if (ASTRAL_CONFIG.equals(mixinConfig)) {
            return Loader.isModLoaded("astralsorcery");
        }
        return true;
    }
}
