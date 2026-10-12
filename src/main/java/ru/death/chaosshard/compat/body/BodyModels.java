package ru.death.chaosshard.compat.body;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.model.ModelLoader;

/** Модели имплантов Хаоса (только клиент, только с Robotic Parts). */
public final class BodyModels {

    public static void register() {
        if (!Body.cyber() || BodyCompat.IMPLANT == null) {
            return;
        }
        for (int i = 0; i < ItemChaosImplant.SUBS.length; i++) {
            ModelLoader.setCustomModelResourceLocation(BodyCompat.IMPLANT, i,
                    new ModelResourceLocation("chaosshard:chaos_implant_" + ItemChaosImplant.SUBS[i], "inventory"));
        }
    }

    private BodyModels() {
    }
}
