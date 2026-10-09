package ru.death.chaosshard.mixin.astral;

import hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.death.chaosshard.stars.SkyRules;

/**
 * «Ночность» неба: в Преддверии всегда глубокая ночь (1.0) — для коллекторов,
 * алтарей, колодцев, ритуалов, заряда игрока и всего прочего, что её спрашивает.
 */
@Mixin(value = ConstellationSkyHandler.class, remap = false)
public abstract class MixinConstellationSkyHandler {

    @Inject(method = "getCurrentDaytimeDistribution", at = @At("HEAD"), cancellable = true)
    private void chaosshard$eternalNight(World world, CallbackInfoReturnable<Float> cir) {
        if (SkyRules.maxStarlight(world)) {
            cir.setReturnValue(1.0F);
        }
    }
}
