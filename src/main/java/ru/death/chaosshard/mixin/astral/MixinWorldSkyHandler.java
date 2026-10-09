package ru.death.chaosshard.mixin.astral;

import hellfirepvp.astralsorcery.common.constellation.ConstellationRegistry;
import hellfirepvp.astralsorcery.common.constellation.IConstellation;
import hellfirepvp.astralsorcery.common.constellation.IMajorConstellation;
import hellfirepvp.astralsorcery.common.constellation.IMinorConstellation;
import hellfirepvp.astralsorcery.common.constellation.IWeakConstellation;
import hellfirepvp.astralsorcery.common.constellation.distribution.WorldSkyHandler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.death.chaosshard.stars.AstralCompat;
import ru.death.chaosshard.stars.SkyRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Правила неба по мирам. Срабатывает в конце ежедневного расчёта неба Astral Sorcery
 * (на сервере и на клиенте). Список активных созвездий и их сила дальше читаются
 * телескопом, обсерваторией, алтарями, коллекторами и кристаллами.
 */
@Mixin(value = WorldSkyHandler.class, remap = false)
public abstract class MixinWorldSkyHandler {

    @Shadow
    private LinkedList<IConstellation> activeConstellations;

    @Shadow
    private Map<IConstellation, Float> activeDistributions;

    @Shadow
    public int lastRecordedDay;

    @Inject(method = "doConstellationIteration", at = @At("TAIL"))
    private void chaosshard$applySkyRules(World world, CallbackInfo ci) {
        if (AstralCompat.CHAOS == null) {
            return;
        }
        if (SkyRules.allConstellations(world)) {
            // Преддверие: Хаос первым (чтобы точно попал на небо), потом главные, яркие, малые.
            // Телескоп показывает только первые 8 ярких созвездий из списка, поэтому
            // остальные каждую ночь сдвигаем по кругу — за несколько ночей видно все.
            Set<IConstellation> rest = new LinkedHashSet<>();
            for (IMajorConstellation c : ConstellationRegistry.getMajorConstellations()) {
                rest.add(c);
            }
            for (IWeakConstellation c : ConstellationRegistry.getWeakConstellations()) {
                rest.add(c);
            }
            for (IMinorConstellation c : ConstellationRegistry.getMinorConstellations()) {
                rest.add(c);
            }
            rest.remove(AstralCompat.CHAOS);
            List<IConstellation> rotated = new ArrayList<>(rest);
            if (!rotated.isEmpty()) {
                Collections.rotate(rotated, Math.floorMod(this.lastRecordedDay, rotated.size()));
            }
            List<IConstellation> ordered = new ArrayList<>();
            ordered.add(AstralCompat.CHAOS);
            ordered.addAll(rotated);
            this.activeConstellations.clear();
            this.activeConstellations.addAll(ordered);
            for (IConstellation c : ordered) {
                this.activeDistributions.put(c, 1.0F);
            }
        } else if (SkyRules.chaosOnly(world)) {
            // Кладовки и прочие миры: только метка Лорда
            this.activeConstellations.clear();
            this.activeConstellations.add(AstralCompat.CHAOS);
            for (IConstellation c : ConstellationRegistry.getAllConstellations()) {
                this.activeDistributions.put(c, AstralCompat.isChaos(c) ? 1.0F : 0.0F);
            }
        }
        // Лучезария: ничего не трогаем, Хаос и так появляется как «особое» созвездие
    }
}
