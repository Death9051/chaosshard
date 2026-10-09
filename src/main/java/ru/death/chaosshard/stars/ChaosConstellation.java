package ru.death.chaosshard.stars;

import hellfirepvp.astralsorcery.common.constellation.ConstellationBase;
import hellfirepvp.astralsorcery.common.constellation.distribution.WorldSkyHandler;
import hellfirepvp.astralsorcery.common.constellation.star.StarLocation;
import net.minecraft.world.World;

import java.awt.Color;

/**
 * Созвездие Хаоса — метка Лорда Death на небе любого мира.
 *
 * «Особое» (как Horologium): само решает, видно ли оно сегодня, и всегда отвечает «да».
 * «Яркое», а не «главное»: главные созвездия привязаны к корням дерева перков,
 * и новое главное сломало бы дерево. Яркое работает в ритуалах, кристаллах и линзах.
 *
 * Имя в реестре: astralsorcery.constellation.chaos (префикс ставит Astral Sorcery,
 * потому что событие регистрации идёт внутри его загрузки).
 */
public class ChaosConstellation extends ConstellationBase.WeakSpecial {

    public static final String NAME = "chaos";
    public static final Color COLOR = new Color(0x9B2CFF);

    public ChaosConstellation() {
        super(NAME, COLOR);
        // Восьмилучевая звезда Хаоса на сетке 31×31: центр и восемь концов
        StarLocation center = addStar(15, 15);
        int[][] tips = {
                {15, 2}, {24, 6}, {28, 15}, {24, 24},
                {15, 28}, {6, 24}, {2, 15}, {6, 6}
        };
        for (int[] tip : tips) {
            StarLocation star = addStar(tip[0], tip[1]);
            addConnection(center, star);
        }
    }

    @Override
    public boolean doesShowUp(WorldSkyHandler handle, World world, long day) {
        return true;
    }

    @Override
    public float getDistribution(WorldSkyHandler handle, World world, long day, boolean showingUp) {
        return 1.0F;
    }
}
