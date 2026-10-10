# =====================================================================
#  Осколок Хаоса — связки модов: переработка материалов Хаоса и Astral Sorcery
#  в машинах Mekanism и Thermal (через ModTweaker), звёздный насыщатель Astral.
# =====================================================================

val chaosStone  = <chaosshard:chaos_stone>;
val chaosCrust  = <chaosshard:chaos_crust>;
val veilShard   = <chaosshard:veil_shard>;
val starOre     = <astralsorcery:blockcustomore:1>;
val aquaOre     = <astralsorcery:blockcustomsandore>;
val aquamarine  = <astralsorcery:itemcraftingcomponent:0>;
val starmetal   = <astralsorcery:itemcraftingcomponent:1>;
val stardust    = <astralsorcery:itemcraftingcomponent:2>;

# ---- Mekanism ----
# Обогатитель: руды Astral дают вдвое больше
mods.mekanism.enrichment.addRecipe(starOre, starmetal * 2);
mods.mekanism.enrichment.addRecipe(aquaOre, aquamarine * 4);
# Дробитель: звёздный металл в пыль, материя Хаоса в гравий и песок
mods.mekanism.crusher.addRecipe(starmetal, stardust * 2);
mods.mekanism.crusher.addRecipe(chaosStone, <minecraft:gravel>);
mods.mekanism.crusher.addRecipe(chaosCrust, <minecraft:sand>);

# ---- Thermal Expansion ----
# Измельчитель: те же руды, плюс шанс побочного продукта
mods.thermalexpansion.Pulverizer.addRecipe(starmetal * 2, starOre, 4000, stardust, 15);
mods.thermalexpansion.Pulverizer.addRecipe(aquamarine * 4, aquaOre, 3000);
mods.thermalexpansion.Pulverizer.addRecipe(<minecraft:gravel>, chaosStone, 2000);
# Корка в измельчителе иногда отдаёт Осколок Завесы — так он становится возобновляемым
mods.thermalexpansion.Pulverizer.addRecipe(<minecraft:sand>, chaosCrust, 2000, veilShard, 10);
# Наполнитель: хаосит, пропитанный жидким звёздным светом, становится коркой Преддверия
mods.thermalexpansion.Transposer.addFillRecipe(chaosCrust, chaosStone, <liquid:astralsorcery.liquidstarlight> * 100, 2000);

# ---- Astral Sorcery ----
# Звёздный насыщатель: тот же переход хаосита в корку, без машин
mods.astralsorcery.StarlightInfusion.addInfusion(chaosStone, chaosCrust, false, 0.5, 100);

# =====================================================================
#  Техномагия: Хаос + звёздный свет
# =====================================================================
val essence   = <chaosshard:chaos_essence>;
val distorted = <chaosshard:distorted_starmetal>;

# Эссенция Хаоса — Осколок Завесы в дробилке/измельчителе (руками — Преобразованием, 1 шт.)
mods.mekanism.crusher.addRecipe(veilShard, essence * 2);
mods.thermalexpansion.Pulverizer.addRecipe(essence * 2, veilShard, 3000, essence, 25);

# Искажённый звёздный металл: звёздный металл + Эссенция
# руками (Гримуар + слиток + 2 эссенции) — 1 шт., в машинах выгоднее
mods.thermalexpansion.InductionSmelter.addRecipe(distorted * 2, starmetal * 2, essence * 2, 8000);
mods.mekanism.combiner.addRecipe(starmetal, essence, distorted);

# Жидкий звёздный свет — топливо для компрессионного динамо Thermal
mods.thermalexpansion.CompressionDynamo.addFuel(<liquid:astralsorcery.liquidstarlight>, 400000);

# Звёздный насыщатель Astral: Эссенция Хаоса, очищенная звёздным светом, снова становится Осколком Завесы
mods.astralsorcery.StarlightInfusion.addInfusion(essence, veilShard, false, 0.7, 200);

# =====================================================================
#  Подсказки в JEI
# =====================================================================
mods.jei.JEI.addDescription(<chaosshard:island_heart>,
    "Сердце Острова стоит в центре Преддверия и выращивает вокруг себя наросты руды.",
    "ПКМ — окно: таймер роста, выбор руды, гнёзда модулей и пробуждение на следующую ступень.",
    "Наросты при добыче выпадают блоком руды.");
mods.jei.JEI.addDescription(<chaosshard:veil_shard>,
    "Выпадает с шансом из корки Преддверия. Возобновляемо: корка в измельчителе Thermal,",
    "или Эссенция Хаоса в звёздном насыщателе Astral.");
mods.jei.JEI.addDescription(<chaosshard:memory_fragment>,
    "Выпадает с монстров в чужих мирах: Кладовках Mystcraft, Трещинах и т.п. В Лучезарии и Преддверии — нет.");
mods.jei.JEI.addDescription(<chaosshard:breach_altar>,
    "Ставится в Преддверии под открытым небом. Нужны четыре столба по диагоналям через клетку:",
    "хаосит внизу, корка сверху. ПКМ без книги — подсветка постройки. ПКМ Книгой Прорыва — ритуал (Нить 2).");
mods.jei.JEI.addDescription(<chaosshard:chaos_essence>,
    "Перемолотый Осколок Завесы. Нужна для Искажённого звёздного металла и модуля «Звёздный».");
mods.jei.JEI.addDescription(<chaosshard:distorted_starmetal>,
    "Материал Tinkers' Construct «Искажённый звёздный металл»: детали делаются на столе деталей.",
    "Свойства: «Нить Хаоса» и «Искажение».");
mods.jei.JEI.addDescription(<chaosshard:grimoire>,
    "ПКМ — записи (квесты) или выбранное заклинание. Shift+ПКМ — сменить заклинание.",
    "В сетке крафта — катализатор Преобразования, не тратится.");
