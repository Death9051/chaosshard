package ru.death.chaosshard.mixin.astral;

import hellfirepvp.astralsorcery.common.util.SkyCollectionHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.death.chaosshard.stars.SkyRules;

import java.util.Optional;

/**
 * Клиентская версия «шума неба» (метод есть только на клиенте) — для подсказок резонатора.
 */
@Mixin(value = SkyCollectionHelper.class, remap = false)
public abstract class MixinSkyCollectionHelperClient {

    @Inject(method = "getSkyNoiseDistributionClient", at = @At("HEAD"), cancellable = true)
    private static void chaosshard$maxNoiseClient(World world, BlockPos pos, CallbackInfoReturnable<Optional<Float>> cir) {
        if (SkyRules.maxStarlight(world)) {
            cir.setReturnValue(Optional.of(1.0F));
        }
    }
}
