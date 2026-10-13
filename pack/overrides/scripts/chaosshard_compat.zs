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
#  Скверна в машинах: руды и земля Defiled Lands
# =====================================================================
val umbriumOre = <defiledlands:umbrium_ore>;
val scarliteOre = <defiledlands:scarlite_ore>;
val hephOre = <defiledlands:hephaestite_ore>;
val powder = <defiledlands:defilement_powder>;
mods.mekanism.enrichment.addRecipe(umbriumOre, <defiledlands:umbrium_ingot> * 2);
mods.mekanism.enrichment.addRecipe(scarliteOre, <defiledlands:scarlite> * 2);
mods.mekanism.enrichment.addRecipe(hephOre, <defiledlands:hephaestite> * 3);
mods.mekanism.crusher.addRecipe(<defiledlands:stone_defiled>, <defiledlands:gravel_defiled>);
mods.mekanism.crusher.addRecipe(<defiledlands:gravel_defiled>, <defiledlands:sand_defiled>);
mods.thermalexpansion.Pulverizer.addRecipe(<defiledlands:umbrium_ingot> * 2, umbriumOre, 4000, powder, 15);
mods.thermalexpansion.Pulverizer.addRecipe(<defiledlands:scarlite> * 2, scarliteOre, 4000, powder, 15);
mods.thermalexpansion.Pulverizer.addRecipe(<defiledlands:hephaestite> * 3, hephOre, 4000, powder, 25);
mods.thermalexpansion.Pulverizer.addRecipe(<defiledlands:gravel_defiled>, <defiledlands:stone_defiled>, 2000, powder, 5);
# Порошок осквернения в измельчителе даёт Эссенцию Хаоса — Скверна кормит машины
mods.thermalexpansion.Pulverizer.addRecipe(essence, powder * 4, 3000);
mods.mekanism.crusher.addRecipe(powder * 4, essence);

# =====================================================================
#  Подсказки в JEI
# =====================================================================
mods.jei.JEI.addDescription(<chaosshard:island_heart>,
    "Сердце Острова стоит в центре Преддверия и выращивает вокруг себя наросты руды.",
    "ПКМ — окно: таймер роста, выбор руды, гнёзда модулей и пробуждение на следующую ступень.",
    "Наросты при добыче выпадают блоком руды.");
mods.jei.JEI.addDescription(<chaosshard:veil_shard>,
    "Выпадает с шансом из природной (светящейся) корки Преддверия; поставленная руками корка пустая.",
    "Хаосит под открытым небом рядом с коркой сам ею обрастает — так корка возобновляется.",
    "Позже: корка в измельчителе Thermal или Эссенция Хаоса в звёздном насыщателе Astral.");
mods.jei.JEI.addDescription(<chaosshard:world_shard>,
    "Осколок выбивают из любых созданий этого мира — зверей и монстров — в любом мире, кроме Преддверия.",
    "Чем крепче создание, тем выше шанс; Добыча повышает шанс; боссы роняют по нескольку.");
mods.jei.JEI.addDescription(<chaosshard:breach_altar>,
    "Ставится в Преддверии под открытым небом. Нужны четыре Ритуальных шпиля по диагоналям через клетку.",
    "ПКМ без книги — подсветка постройки. ПКМ Книгой Прорыва — ритуал (Нить 2).");
mods.jei.JEI.addDescription(<chaosshard:chaos_essence>,
    "Перемолотый Осколок Завесы. Нужна для Искажённого звёздного металла и модуля «Звёздный».");
mods.jei.JEI.addDescription(<chaosshard:distorted_starmetal>,
    "Материал Tinkers' Construct «Искажённый звёздный металл»: детали делаются на столе деталей.",
    "Свойства: «Нить Хаоса» и «Искажение».");
mods.jei.JEI.addDescription(<chaosshard:grimoire>,
    "ПКМ — записи (квесты) или выбранное заклинание. Shift+ПКМ — сменить заклинание.",
    "В сетке крафта — катализатор Преобразования, не тратится.");

# ---- Ритуалы Хаоса ----
mods.jei.JEI.addDescription(<chaosshard:ritual_bowl>,
    "Центр ритуального круга. Вокруг — 8 знаков Мела Хаоса через клетку: по сторонам и диагоналям.",
    "ПКМ предметом — подношение (Shift — стопка), пустой рукой — забрать, ПКМ Гримуаром — начать ритуал.",
    "Все ритуалы и подношения — в окне магии Гримуара (клавиша P).",
    "Подношения принимает и от воронок и труб (AE2, RS, Thermal), забрать их так нельзя.",
    "Сигнал редстоуна повторяет ритуал от имени того, кто последним начинал его здесь Гримуаром (он должен быть в игре).",
    "Так не запускаются только призывы. Сферы по сигналу ложатся в сундук под Чашей.");
mods.jei.JEI.addDescription(<chaosshard:heart_module_engine>,
    "Сердце принимает ток RF/FE с любой стороны: кабели Thermal, Mekanism, Extra Utilities 2, Cyclic.",
    "Пока идёт ток, каждый модуль добавляет ещё один шаг роста за тик. Расход — 40 RF/т на модуль.");
mods.jei.JEI.addDescription(<chaosshard:anchor_seal_engine>,
    "Подведи кабель к основанию Якоря. С током каждая печать удваивает попытки Скверны.",
    "Расход — 50 RF/т на печать. Без тока печать спит.");
mods.jei.JEI.addDescription(<chaosshard:chaos_chalk>,
    "ПКМ по верху блока — нарисовать знак круга. Хватает на 64 знака. Знак стирается одним ударом.");

# ---- Захват и тело ----
mods.jei.JEI.addDescription(<chaosshard:ritual_spire>,
    "Двойной блок-колонна. Четыре шпиля по диагоналям от Алтаря Прорыва, через клетку, — опоры ритуала.");
mods.jei.JEI.addDescription(<chaosshard:chaos_anchor>,
    "Вбивается в Лучезарии. Пять уровней по числу осквернённых блоков (300, 800, 1600, 3000): с каждым Якорь растёт, а Скверна шире.",
    "ПКМ — сведения. ПКМ печатью — навесить, Shift+ПКМ пустой рукой — снять. Одна печать на уровень.");
mods.jei.JEI.addDescription(<chaosshard:patron_sigil>,
    "Ставится в деревне. Дань раз в день, Скверна обходит деревню. Опыт — за отбитые попытки Скверны.",
    "На 5-м уровне деревня становится Обителью Хаоса: жители — Адепты. Shift+ПКМ — уровень Знака.");
