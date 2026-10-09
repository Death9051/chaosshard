package ru.death.chaosshard.stars;

import hellfirepvp.astralsorcery.common.constellation.IMinorConstellation;
import hellfirepvp.astralsorcery.common.constellation.effect.ConstellationEffect;
import hellfirepvp.astralsorcery.common.constellation.effect.ConstellationEffectProperties;
import hellfirepvp.astralsorcery.common.util.ILocatable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nullable;

/**
 * Ритуальный эффект созвездия Хаоса.
 *
 * Пока заглушка: ритуал на пьедестале с кристаллом Хаоса работает, но ничего не делает.
 * Без зарегистрированного эффекта Astral Sorcery падает с NullPointerException
 * в тике ритуального пьедестала, поэтому эффект нужен уже сейчас.
 * Настоящий ритуал Хаоса — в следующих вехах.
 */
public class CEffectChaos extends ConstellationEffect {

    public CEffectChaos(@Nullable ILocatable origin) {
        super(origin, AstralCompat.CHAOS, "chaos");
    }

    @Override
    public boolean playEffect(World world, BlockPos pos, float percStrength, ConstellationEffectProperties modified, @Nullable IMinorConstellation possibleTraitEffect) {
        return false;
    }

    @Override
    public ConstellationEffectProperties provideProperties(int mirrorCount) {
        return new ConstellationEffectProperties(8 + mirrorCount * 2);
    }

    @Override
    public void loadFromConfig(Configuration cfg) {
    }
}
