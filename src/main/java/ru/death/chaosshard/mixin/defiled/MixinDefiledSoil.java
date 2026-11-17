package ru.death.chaosshard.mixin.defiled;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Скверная трава и земля Defiled Lands держат обычные саженцы и цветы, как трава и земля: на захваченной земле
 * можно сажать деревья. Камень, песок и прочая скверная порода — как раньше.
 */
@Pseudo
@Mixin(targets = "lykrast.defiledlands.common.block.BlockCorrupted", remap = false)
public abstract class MixinDefiledSoil extends Block {

    protected MixinDefiledSoil(Material m) {
        super(m);
    }

    @Override
    public boolean canSustainPlant(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing direction, IPlantable plantable) {
        if (ru.death.chaosshard.anchor.DefiledFarming.soil(this)) {
            EnumPlantType t = plantable.getPlantType(world, pos.offset(direction));
            if (t == EnumPlantType.Plains) {
                return true;
            }
        }
        return super.canSustainPlant(state, world, pos, direction, plantable);
    }
}
