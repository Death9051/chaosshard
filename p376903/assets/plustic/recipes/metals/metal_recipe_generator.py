#!/usr/bin/env python3
for metal in ['osmiridium', 'alumite', 'mirion', 'invar', 'osgloglas']:
	with open(metal+'_ingot_nugget.json', 'w', encoding='utf-8') as file:
		file.write('''{
\t"conditions": [
\t\t{
\t\t\t"type": "minecraft:item_exists",
\t\t\t"item": "plustic:%(lower)singot"
\t\t}
\t],
\t"type": "forge:ore_shaped",
\t"pattern": [
\t\t"III",
\t\t"III",
\t\t"III"
\t],
\t"key": {
\t\t"I": {
\t\t\t"type": "forge:ore_dict",
\t\t\t"ore": "nugget%(upper)s"
\t\t}
\t},
\t"result": {
\t\t"item": "plustic:%(lower)singot"
\t}
}''' % { 'upper': metal.capitalize(), 'lower': metal })
	with open(metal+'_nugget.json', 'w', encoding='utf-8') as file:
		file.write('''{
\t"conditions": [
\t\t{
\t\t\t"type": "minecraft:item_exists",
\t\t\t"item": "plustic:%(lower)singot"
\t\t}
\t],
\t"type": "forge:ore_shapeless",
\t"ingredients": [
\t\t{
\t\t\t"item": "plustic:%(lower)singot"
\t\t}
\t],
\t"result": {
\t\t"item": "plustic:%(lower)snugget",
\t\t"count": 9
\t}
}''' % { 'upper': metal.capitalize(), 'lower': metal })
	with open(metal+'_ingot.json', 'w', encoding='utf-8') as file:
		file.write('''{
\t"conditions": [
\t\t{
\t\t\t"type": "minecraft:item_exists",
\t\t\t"item": "plustic:%(lower)singot"
\t\t}
\t],
\t"type": "forge:ore_shapeless",
\t"ingredients": [
\t\t{
\t\t\t"item": "plustic:%(lower)sblock"
\t\t}
\t],
\t"result": {
\t\t"item": "plustic:%(lower)singot",
\t\t"count": 9
\t}
}''' % { 'upper': metal.capitalize(), 'lower': metal })
	with open(metal+'_block.json', 'w', encoding='utf-8') as file:
		file.write('''{
\t"conditions": [
\t\t{
\t\t\t"type": "minecraft:item_exists",
\t\t\t"item": "plustic:%(lower)singot"
\t\t}
\t],
\t"type": "forge:ore_shaped",
\t"pattern": [
\t\t"III",
\t\t"III",
\t\t"III"
\t],
\t"key": {
\t\t"I": {
\t\t\t"type": "forge:ore_dict",
\t\t\t"ore": "ingot%(upper)s"
\t\t}
\t},
\t"result": {
\t\t"item": "plustic:%(lower)sblock"
\t}
}''' % { 'upper': metal.capitalize(), 'lower': metal })