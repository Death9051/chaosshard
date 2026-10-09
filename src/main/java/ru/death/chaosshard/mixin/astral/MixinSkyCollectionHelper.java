package ru.death.chaosshard.mixin.astral;

import hellfirepvp.astralsorcery.common.util.SkyCollectionHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.death.chaosshard.stars.SkyRules;

/**
 * «Шум неба» по координатам: в обычных мирах удачное место для алтаря нужно искать,
 * в Преддверии любое место — лучшее (1.0).
 */
@Mixin(value = SkyCollectionHelper.class, remap = false)
public abstract class MixinSkyCollectionHelper {

    @Inject(method = "getSkyNoiseDistribution", at = @At("HEAD"), cancellable = true)
    private static void chaosshard$maxNoise(World world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (SkyRules.maxStarlight(world)) {
            cir.setReturnValue(1.0F);
        }
    }
}
