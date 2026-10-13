# =====================================================================
#  Осколок Хаоса × EvilCraft: Скверна и здешняя тьма.
#  Отдельный файл: без EvilCraft сломается только он.
# =====================================================================
#modloaded evilcraft

val essence = <chaosshard:chaos_essence>;
val veil = <chaosshard:veil_shard>;

# Кровяной насытитель: Эссенция Хаоса, напоенная кровью, твердеет в Тёмный самоцвет
mods.evilcraft.BloodInfuser.addRecipe(essence, <liquid:evilcraftblood> * 500, 0, <evilcraft:dark_gem>, 100, 5);
# Осколок Завесы в крови распадается на Эссенцию — ещё один путь к ней
mods.evilcraft.BloodInfuser.addRecipe(veil, <liquid:evilcraftblood> * 1000, 1, essence * 3, 200, 8);

mods.jei.JEI.addDescription(<evilcraft:dark_gem>,
    "На земле Якоря Хаоса в осквернённом камне вызревает тёмная руда.",
    "Кровяной насытитель делает Тёмный самоцвет из Эссенции Хаоса.");
mods.jei.JEI.addDescription(<forge:bucketfilled>.withTag({FluidName: "evilcraftblood", Amount: 1000}),
    "На захваченной Скверной земле стоячая вода понемногу темнеет и становится кровью.");
mods.jei.JEI.addDescription(<chaosshard:anchor_seal_blood>,
    "Печать Якоря: любая смерть в пределах Якоря — зверя, жителя, твари, солдата Гаррота — питает Скверну.",
    "ПКМ по Якорю — навесить. Одна печать на уровень Якоря.");
