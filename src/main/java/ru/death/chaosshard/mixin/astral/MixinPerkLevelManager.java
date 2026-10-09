package ru.death.chaosshard.mixin.astral;

import hellfirepvp.astralsorcery.common.constellation.perk.PerkLevelManager;
import net.minecraftforge.common.config.Configuration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.stars.PerkBridge;

import java.util.Map;

/**
 * Уровни перков Astral Sorcery:
 *  - потолок выше сотни (Astral в своём конфиге не даёт поставить больше 100);
 *  - пологая кривая опыта: уровень N стоит (база + шаг × N) вместо 150 + 2^(N/2+3).
 */
@Mixin(value = PerkLevelManager.class, remap = false)
public abstract class MixinPerkLevelManager {

    @Shadow
    private static int LEVEL_CAP;

    @Shadow
    private Map<Integer, Long> totalExpLevelRequired;

    @Inject(method = "loadFromConfig", at = @At("TAIL"))
    private void chaosshard$raiseCap(Configuration cfg, CallbackInfo ci) {
        chaosshard$applyCap();
    }

    @Inject(method = "ensureLevels", at = @At("HEAD"), cancellable = true)
    private void chaosshard$gentleCurve(CallbackInfo ci) {
        chaosshard$applyCap();
        if (totalExpLevelRequired.isEmpty()) {
            long total = 0;
            for (int i = 1; i <= LEVEL_CAP; i++) {
                total += ChaosConfig.perks.expCurveBase + (long) ChaosConfig.perks.expCurveStep * i;
                totalExpLevelRequired.put(i, total);
            }
        }
        ci.cancel();
    }

    private static void chaosshard$applyCap() {
        if (ChaosConfig.perks.levelCap > 0) {
            LEVEL_CAP = ChaosConfig.perks.levelCap;
        }
        PerkBridge.appliedLevelCap = LEVEL_CAP;
    }
}
