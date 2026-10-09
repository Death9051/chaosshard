# =====================================================================
#  Осколок Хаоса — связь Нити и Astral Sorcery (пример для папки scripts/)
#  Нужны: CraftTweaker, Game Stages, Astral Sorcery, мод chaosshard.
#
#  Мод выдаёт этапы nit_0 (первый вход) ... nit_5 (уровни Нити).
#  Astral Sorcery берёт самый высокий лимит из этапов игрока.
#  Если у игрока нет ни одного этапа с лимитом — потолок полный (30),
#  поэтому nit_0 выдаётся сразу при первом входе.
# =====================================================================

import mods.astralsorcery.GameStages;

GameStages.addLevelCap("nit_0", 5);
GameStages.addLevelCap("nit_1", 10);
GameStages.addLevelCap("nit_2", 15);
GameStages.addLevelCap("nit_3", 20);
GameStages.addLevelCap("nit_4", 25);
GameStages.addLevelCap("nit_5", 30);

# Созвездие Хаоса можно открыть в телескопе с первого уровня Нити:
GameStages.addConstellationDiscoveryStage("nit_1", "astralsorcery.constellation.chaos");
