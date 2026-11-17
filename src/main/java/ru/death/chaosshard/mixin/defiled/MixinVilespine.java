package ru.death.chaosshard.mixin.defiled;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/** Мерзошип растёт реже: проходит только доля попыток роста (ChaosConfig.anchor.vilespineGrowth). */
@Pseudo
@Mixin(targets = "lykrast.defiledlands.common.block.BlockVilespine", remap = false)
public abstract class MixinVilespine {

    @Inject(method = "func_180650_b", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void chaosshard$slowGrowth(World world, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        if (rand.nextDouble() >= ru.death.chaosshard.ChaosConfig.anchor.vilespineGrowth) {
            ci.cancel();
        }
    }
}
