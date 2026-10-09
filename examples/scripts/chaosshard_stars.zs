# =====================================================================
#  Осколок Хаоса — связь Нити и Astral Sorcery
#  Нужны: CraftTweaker, Game Stages, Astral Sorcery, мод chaosshard.
#
#  Мод выдаёт этапы nit_0 (первый вход) ... nit_5 (уровни Нити).
#  Astral Sorcery берёт самый высокий потолок из этапов игрока.
#  Общий потолок (320 — хватает на всё древо) и кривая опыта
#  настраиваются в config/chaosshard.cfg, раздел perks.
# =====================================================================

import mods.astralsorcery.GameStages;

GameStages.addLevelCap("nit_0", 15);
GameStages.addLevelCap("nit_1", 50);
GameStages.addLevelCap("nit_2", 100);
GameStages.addLevelCap("nit_3", 160);
GameStages.addLevelCap("nit_4", 230);
GameStages.addLevelCap("nit_5", 320);

# Созвездие Хаоса можно открыть в телескопе с первого уровня Нити:
GameStages.addConstellationDiscoveryStage("nit_1", "astralsorcery.constellation.chaos");
