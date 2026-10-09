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
