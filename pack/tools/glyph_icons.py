#!/usr/bin/env python3
"""Иконки глифов Хаоса (16×16): тёмная табличка, светящаяся руна своего цвета. И модели предметов.
Запуск: python3 pack/tools/glyph_icons.py"""
import json
import os
import random

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEX = os.path.join(ROOT, 'src/main/resources/assets/chaosshard/textures/items')
MOD = os.path.join(ROOT, 'src/main/resources/assets/chaosshard/models/item')

# порядок как в SpellGlyph
GLYPHS = [
    ('wide', 0x8AD0FF, ['.......', '.#...#.', '..#.#..', '###.###', '..#.#..', '.#...#.', '.......']),
    ('potent', 0xFF7A5A, ['...#...', '..###..', '.#.#.#.', '...#...', '...#...', '..###..', '.......']),
    ('thrift', 0x9FE07A, ['.......', '..###..', '.#...#.', '.#.#.#.', '.#...#.', '..###..', '.......']),
    ('haste', 0xFFE070, ['....#..', '...#...', '..####.', '...#...', '..#....', '.#.....', '.......']),
    ('echo', 0xC890FF, ['.......', '.#..#..', '#..#...', '.#..#..', '..#..#.', '.#..#..', '.......']),
    ('blood', 0xD03040, ['...#...', '..#.#..', '.#...#.', '.#...#.', '..###..', '.......', '.......']),
    ('kindling', 0xFF9A30, ['...#...', '..##...', '..#.#..', '.#..#..', '.#.##..', '..##...', '.......']),
]


def main():
    for i, (name, col, rune) in enumerate(GLYPHS):
        rnd = random.Random(i + 7)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = im.load()
        # табличка: скруглённый прямоугольник 12×14
        for y in range(1, 15):
            for x in range(2, 14):
                if (y in (1, 14) and x in (2, 13)):
                    continue
                edge = x in (2, 13) or y in (1, 14)
                v = rnd.randint(-6, 6)
                base = (46, 36, 56) if not edge else (24, 18, 30)
                if y <= 2 and not edge:
                    base = (70, 58, 84)
                px[x, y] = (base[0] + v, base[1] + v, base[2] + v, 255)
        r, g, b = col >> 16 & 255, col >> 8 & 255, col & 255
        for yy, row in enumerate(rune):
            for xx, ch in enumerate(row):
                if ch == '#':
                    X, Y = 4 + xx, 4 + yy
                    px[X, Y] = (r, g, b, 255)
                    # мягкое свечение вокруг
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        q = px[X + dx, Y + dy]
                        if q[3] and q[:3] != (r, g, b):
                            px[X + dx, Y + dy] = ((q[0] + r) // 3 + 8, (q[1] + g) // 3 + 4, (q[2] + b) // 3 + 8, 255)
        im.save(os.path.join(TEX, 'spell_glyph_%s.png' % name))
        with open(os.path.join(MOD, 'spell_glyph_%s.json' % name), 'w') as f:
            json.dump({'parent': 'item/generated', 'textures': {'layer0': 'chaosshard:items/spell_glyph_%s' % name}}, f)
    with open(os.path.join(MOD, 'spell_glyph.json'), 'w') as f:
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'chaosshard:items/spell_glyph_wide'}}, f)
    print('глифы: иконки и модели готовы')


if __name__ == '__main__':
    main()
