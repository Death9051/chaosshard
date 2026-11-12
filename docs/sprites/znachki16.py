"""Свои значки 16x16: заклинания Гримуара, вкладки окна Якоря и Стола Хаоса.

python3 znachki.py <папка textures/gui> [<превью.png>]
Рисует icons.png (атлас 256x256, по 16 значков в ряд) — порядок в ICONS ниже, его же знает ChaosUi.
Стиль: цветной знак с тёмной обводкой в 1 пиксель и бликом, без подложки — читается и на тёмном окне, и на
светлой бумаге книги.
"""
import sys

from PIL import Image

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

O = (22, 12, 30)        # обводка
W = (255, 255, 255)


def ramp(d, m, l):
    return {'d': d, 'm': m, 'l': l}


VIOLET = ramp((88, 38, 150), (160, 92, 236), (222, 186, 255))
GREEN = ramp((40, 110, 50), (96, 190, 80), (180, 240, 140))
GOLD = ramp((150, 96, 30), (236, 176, 100), (255, 226, 160))
RED = ramp((120, 20, 36), (210, 50, 70), (255, 140, 140))
BLUE = ramp((40, 70, 150), (90, 140, 230), (180, 210, 255))
SILVER = ramp((90, 88, 110), (170, 174, 188), (230, 232, 242))
PAPER = ramp((150, 130, 110), (220, 204, 180), (250, 242, 226))
BROWN = ramp((80, 50, 20), (150, 100, 45), (200, 150, 80))
TEAL = ramp((20, 100, 110), (60, 190, 190), (170, 250, 240))

# Каждый значок: имя, строки 16x16, словарь цветов. Буквы: O обводка, d/m/l тона основного цвета,
# D/M/L тона второго цвета, w белый блик, . пусто.
ICONS = [
    # ---------------- заклинания (порядок как в Spell)
    ('book', [
        "................",
        "................",
        "...OOOO..OOOO...",
        "..OLLLLOOLLLLO..",
        ".OLLDDLLOLDDLLO.",
        ".OLLLLLLOLLLLLO.",
        ".OLDDDLLOLDDDLO.",
        ".OLLLLLLOLLLLLO.",
        ".OLDDLLLOLLDDLO.",
        ".OLLLLLLOLLLLLO.",
        ".OmmLLLLOLLLLmmO",
        ".OdmmmmLOLmmmmdO",
        "..OddddmOmddddO.",
        "...OOOOOOOOOOO..",
        "................",
        "................",
    ], {'O': O, **VIOLET, 'D': PAPER['d'], 'L': PAPER['l']}),
    ('sprout', [
        "................",
        "..........OOO...",
        ".........OlllO..",
        "..OOO...OlmmmO..",
        ".OlllO..OmmmdO..",
        ".OmmmlO.OmmdO...",
        ".OdmmmmOOmdO....",
        "..OddmmOmdO.....",
        "...OOOdmdO......",
        "......OmO.......",
        "......OmO...w...",
        ".....OOmOO.wMw..",
        "....OMMMMMO.w...",
        "...OMDDDDDMO....",
        "...OOOOOOOOO....",
        "................",
    ], {'O': O, **GREEN, 'D': VIOLET['d'], 'M': VIOLET['m'], 'w': VIOLET['l']}),
    ('bridge', [
        "................",
        "................",
        "..........OOOOO.",
        ".........OllllmO",
        ".........OmmmmdO",
        "..........OOOOO.",
        "......OOOOO.....",
        ".....OllllmO....",
        ".....OmmmmdO....",
        "......OOOOO.....",
        "..OOOOO.........",
        ".OllllmO........",
        ".OmmmmdO........",
        "..OOOOO.........",
        "................",
        "................",
    ], {'O': O, **VIOLET, 'w': VIOLET['l']}),
    ('spring', [
        "................",
        ".....OOOOOO.....",
        "...OOmmmmmmOO...",
        "..OmmOOOOOOmmO..",
        ".OmlO......OmmO.",
        ".OmO..OOOO..OmO.",
        "OmlO.OllllO.OmmO",
        "OmO.OlOOOmO..OmO",
        "OmO.OmODOmO..OmO",
        "OmO.OmmOOO..OmO.",
        "OmlO.OmmmO..OmO.",
        ".OmO..OOO..OmO..",
        ".OmlO.....OmlO..",
        "..OmmOOOOOmmO...",
        "...OOmmmmmOO....",
        ".....OOOOO......",
    ], {'O': O, **VIOLET, 'D': RED['m']}),
    ('rift', [     # Разлом: треснувший куб
        "................",
        "......OOOO......",
        "....OOllllOO....",
        "..OOllllOlllOO..",
        ".OlllllOOllllmO.",
        ".OmmllOOmllmmdO.",
        ".OmmmmOmmmmmddO.",
        ".OmmmOOdmmmdddO.",
        ".OdmmmOOdmdddDO.",
        ".OddmmmOOddddDO.",
        ".OdddmOOddddDDO.",
        "..OOddOddDDDOO..",
        "....OOdOdDOO....",
        "......OOOO......",
        "................",
        "................",
    ], {'O': O, **SILVER, 'D': SILVER['d']}),
    ('starsight', [
        "................",
        "................",
        "................",
        ".....OOOOOO.....",
        "...OOllllllOO...",
        "..OllOOOOOOllO..",
        ".OllOddwddOdllO.",
        "OllOdddwwddOdllO",
        ".OllOwwwwwwdllO.",
        "..OllOddwdOllO..",
        "...OOllwllOO....",
        ".....OOOOOO.....",
        "................",
        "................",
        "................",
        "................",
    ], {'O': O, **BLUE}),
    ('recall', [   # Зов Нити: петля нити, возвращающаяся к точке
        "................",
        "......OOOO......",
        "....OOllllOO....",
        "...OlmOOOOmlO...",
        "..OlmO....OmlO..",
        "..OmO......OmO..",
        ".OlmO......OmlO.",
        ".OmO........OO..",
        ".OmO....O.......",
        ".OmmO..OmO......",
        "..OmmOOmmmO.....",
        "...OmmmmmmmO....",
        "....OOmmmmO.....",
        "......OmmO......",
        ".......OO.......",
        "................",
    ], {'O': O, **GOLD}),
    ('dash', [
        "................",
        "................",
        "........OOO.....",
        "........OlmO....",
        "..OOOO...OlmO...",
        "..OddO....OlmO..",
        "...........OlmO.",
        ".OOOOOOOOOOOllmO",
        ".OdddmmmmmmmlmO.",
        ".OOOOOOOOOOOlmO.",
        "...........OmO..",
        "..OOOO....OmO...",
        "..OddO...OmO....",
        "........OmO.....",
        "........OO......",
        "................",
    ], {'O': O, **SILVER}),
    ('bolt', [
        "................",
        "...........OOOO.",
        "..........OlwlO.",
        ".........OllllO.",
        "..........OlllO.",
        "..w......OmOlO..",
        ".wMw....OmO.O...",
        "..w....OmO......",
        "......OmO.......",
        ".....OmO........",
        "..O.OmO.........",
        ".OMOmO.....w....",
        "..OMMO....wMw...",
        ".OMMMMO....w....",
        "..OO.OO.........",
        "................",
    ], {'O': O, **VIOLET, 'M': VIOLET['d'], 'w': VIOLET['l']}),
    ('form', [     # Форма Хаоса: фигура в сиянии
        "................",
        ".w....OOO.....w.",
        "wMw..OlllO...wMw",
        ".w...OlmlO....w.",
        "......OOO.......",
        "...OOOOmOOOO....",
        "..OlmmmmmmmlO...",
        ".OlO.OmmmO.OlO..",
        ".OO..OmmmO..OO..",
        ".....OmdmO......",
        "....OmdOdmO.....",
        "....OmO.OmO.....",
        "...OmdO.OdmO....",
        "...OOO...OOO....",
        "..w.........w...",
        ".wMw.......wMw..",
    ], {'O': O, **VIOLET, 'M': GOLD['m'], 'w': GOLD['l']}),
    ('curse', [
        "................",
        "................",
        "....OOOOOOO.....",
        "...OllllllmO....",
        "..OllllllllmO...",
        "..OlOOOlOOOmO...",
        "..OlOddlOddmO...",
        "..OlOOOlOOOmO...",
        "..OlllllllmmO...",
        "...OlllOllmO....",
        "....OmlOlmO.....",
        "....OlOlOlO.....",
        ".....OOOOO......",
        "................",
        "................",
        "................",
    ], {'O': O, **RED}),
    ('shield', [
        "................",
        "..OOOOOOOOOOOO..",
        ".OlllllllllllmO.",
        ".OlLLLLLLLLLLmO.",
        ".OlLLLLMMLLLLmO.",
        ".OlLLLMwwMLLLmO.",
        ".OlLLMwwwwMLLmO.",
        ".OlLLLMwwMLLLmO.",
        "..OlLLLMMLLLmO..",
        "..OlLLLLLLLLmO..",
        "...OlLLLLLLmO...",
        "....OlLLLLmO....",
        ".....OlLLmO.....",
        "......OmmO......",
        ".......OO.......",
        "................",
    ], {'O': O, **SILVER, 'L': VIOLET['d'], 'M': VIOLET['m'], 'w': VIOLET['l']}),
    # ---------------- вкладки окна Якоря
    ('tab_anchor', [   # кристалл Якоря на постаменте
        "................",
        ".......OO.......",
        "......OlwO......",
        ".....OlllmO.....",
        ".....OllmmO.....",
        "....OllmmmdO....",
        "....OlmmmddO....",
        "....OlmmmddO....",
        ".....OmmddO.....",
        ".....OmmddO.....",
        "......OmdO......",
        "....OOOOOOOO....",
        "...OLLLLLLLMO...",
        "..OLLMMMMMMMDO..",
        "..OOOOOOOOOOOO..",
        "................",
    ], {'O': O, **VIOLET, 'L': SILVER['l'], 'M': SILVER['m'], 'D': SILVER['d']}),
    ('tab_net', [      # три связанных узла
        "................",
        "......OOOO......",
        ".....OlwlmO.....",
        ".....OlmmdO.....",
        "......OmdO......",
        ".....O.OO.O.....",
        "....OMO..OMO....",
        "...OMO....OMO...",
        "..OMO......OMO..",
        ".OOOO......OOOO.",
        "OlwlmO....OlwlmO",
        "OlmmdOOOOOOlmmdO",
        "OmmddMMMMMMOmddO",
        ".OddO.OOOO..OddO",
        "..OO.........OO.",
        "................",
    ], {'O': O, **VIOLET, 'M': GOLD['m']}),
    ('tab_path', [     # развилка-указатель
        "................",
        ".......OO.......",
        "......OmmO......",
        ".OOOOOOmmOOOOO..",
        ".OlllllllllllmO.",
        ".OmmmmmmmmmmmmdO",
        ".OOOOOOmmOOOOOO.",
        "...OOOOmmOOOOOO.",
        "..OLLLLLLLLLLLO.",
        ".OMMMMMMMMMMMLO.",
        "..OOOOOmmOOOOOO.",
        "......OmmO......",
        "......OmmO......",
        ".....OddddO.....",
        ".....OOOOOO.....",
        "................",
    ], {'O': O, **BROWN, 'L': GOLD['l'], 'M': GOLD['m']}),
    ('tab_store', [    # сундук
        "................",
        "................",
        "................",
        "..OOOOOOOOOOOO..",
        ".OllllllllllllO.",
        ".OlmmmmmmmmmmdO.",
        ".OmmmmmmmmmmmdO.",
        ".OOOOOOMMOOOOOO.",
        ".OmmmmOLLOmmmdO.",
        ".OmmmmmOOmmmmdO.",
        ".OmmmmmmmmmmmdO.",
        ".OddddddddddddO.",
        ".OOOOOOOOOOOOOO.",
        "................",
        "................",
        "................",
    ], {'O': O, **BROWN, 'L': GOLD['l'], 'M': GOLD['m']}),
    # ---------------- вкладки Стола Хаоса
    ('tab_enchant', [  # книга с искрой
        "...........w....",
        "..........wWw...",
        "...........w....",
        "..OOOOOOOOO.....",
        ".OmmmmmmmmlO....",
        ".OmLLLLLLLmO....",
        ".OmLDDDDDLmO....",
        ".OmLLLLLLLmO....",
        ".OmLDDDDLLmO....",
        ".OmLLLLLLLmO....",
        ".OmLDDDLLLmO..w.",
        ".OmLLLLLLLmO.wWw",
        ".OdmmmmmmmmO..w.",
        "..OOOOOOOOO.....",
        "................",
        "................",
    ], {'O': O, **VIOLET, 'L': PAPER['l'], 'D': PAPER['d'], 'w': VIOLET['l'], 'W': W}),
    ('tab_research', [  # книга и лупа
        "................",
        "..OOOOOOOOO.....",
        ".OmmmmmmmmlO....",
        ".OmLLLLLLLmO....",
        ".OmLDDDDDLmO....",
        ".OmLLLLLLLmO....",
        ".OmLDDDDLLmO....",
        ".OmLLLLLOOOO....",
        ".OmLDDDOSSSSO...",
        ".OmLLLOSwBBBSO..",
        ".OdmmmOSBBBBSO..",
        "..OOOOOSBBBBSO..",
        "......OOSSSSOO..",
        "...........OSO..",
        "............OSO.",
        ".............OO.",
    ], {'O': O, **VIOLET, 'L': PAPER['l'], 'D': PAPER['d'], 'S': SILVER['m'], 'B': BLUE['l'], 'w': W}),
]

atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
for i, (name, rows, pal) in enumerate(ICONS):
    assert len(rows) == 16, (name, len(rows))
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, len(row), row)
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            c = pal.get(ch, W if ch in 'wW' else None)
            if c is None:
                raise SystemExit(f'{name}: нет цвета для «{ch}»')
            atlas.putpixel(((i % 16) * 16 + x, (i // 16) * 16 + y), tuple(c) + (255,))
atlas.save(OUT + '/icons.png')

if PREVIEW:
    S = 6
    n = len(ICONS)
    pv = Image.new('RGBA', (n * (16 * S + 8) + 8, 2 * (16 * S + 8) + 8), (0, 0, 0, 255))
    for row, bg in enumerate([(22, 17, 31, 255), (236, 222, 196, 255)]):
        y0 = 8 + row * (16 * S + 8)
        for i in range(n):
            x0 = 8 + i * (16 * S + 8)
            tile = Image.new('RGBA', (16 * S, 16 * S), bg)
            ic = atlas.crop(((i % 16) * 16, (i // 16) * 16, (i % 16) * 16 + 16, (i // 16) * 16 + 16)).resize((16 * S, 16 * S), 0)
            tile.paste(ic, (0, 0), ic)
            pv.paste(tile, (x0, y0))
    pv.save(PREVIEW)
print('ok', len(ICONS))
