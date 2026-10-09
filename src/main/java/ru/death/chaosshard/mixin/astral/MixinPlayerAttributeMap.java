package ru.death.chaosshard.mixin.astral;

import hellfirepvp.astralsorcery.common.constellation.perk.PlayerAttributeMap;
import hellfirepvp.astralsorcery.common.constellation.perk.attribute.AttributeTypeRegistry;
import hellfirepvp.astralsorcery.common.data.research.PlayerProgress;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.death.chaosshard.thread.ThreadBonus;

/**
 * Сила всех перков. Astral умножает каждый модификатор перка на значение атрибута
 * «perkeffect»; мы домножаем это значение на бонус Нити. Работает и на клиенте —
 * в подсказках древа цифры уже с учётом Нити.
 */
@Mixin(value = PlayerAttributeMap.class, remap = false)
public abstract class MixinPlayerAttributeMap {

    @Inject(method = "modifyValue", at = @At("RETURN"), cancellable = true)
    private void chaosshard$threadPerkEffect(EntityPlayer player, PlayerProgress progress, String type, float value,
                                             CallbackInfoReturnable<Float> cir) {
        if (AttributeTypeRegistry.ATTR_TYPE_INC_PERK_EFFECT.equals(type)) {
            cir.setReturnValue(cir.getReturnValueF() * ThreadBonus.perkEffect(player));
        }
    }
}
