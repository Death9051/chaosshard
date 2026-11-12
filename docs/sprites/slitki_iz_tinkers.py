"""Слитки Осколка Хаоса на основе слитков Tinkers' Construct (MIT, (c) SlimeKnights).

Форма и светотень берутся из слитка TC: каждый пиксель переводится по яркости в ступень
своей палитры (порядок яркостей сохраняется), потом добавляются немногие свои детали.
"""
import sys
from PIL import Image

SRC = sys.argv[1]   # папка с ingot_*.png из TC
OUT = sys.argv[2]


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


# от контура к блику; тени уходят в холодный/тёмный оттенок
PAL = {
    'crystal_chaos_ingot': ('ingot_manyullyn', ['1e0f3d', '3a1f6e', '5b3aa0', '8461cc', 'a98ae6', 'c8b0fa', 'e2d4ff', 'f8f2ff']),
    'blood_platinum_ingot': ('ingot_ardite', ['24070d', '4a0f18', '7a1824', 'a8283a', 'cc4250', 'e46a72', 'f2a0a2', 'ffd8d4']),
    'starlight_ingot': ('ingot_cobalt', ['0b1736', '1c3366', '31559a', '4f7cc4', '79a5e0', 'a6c8f5', 'd2e6ff', 'ffffff']),
    'distorted_starmetal': ('ingot_alubrass', ['120822', '26113f', '40205f', '5d3486', '7c4caa', '9c6cc8', 'bf94e2', 'e4ccff']),
}

# свои детали: (x, y, цвет)
ACCENT = {
    'crystal_chaos_ingot': [(12, 8, 'f8f2ff'), (4, 10, 'c8b0fa')],             # искры в гранях
    'blood_platinum_ingot': [],
    'starlight_ingot': [(13, 9, 'ffffff'), (5, 11, 'd2e6ff'), (9, 8, 'a6c8f5')],  # звёзды в тени
    'distorted_starmetal': [(10, 7, 'ff5ce1'), (11, 7, 'ff5ce1'), (4, 9, 'ff5ce1'),
                            (7, 10, 'c23bb0'), (12, 8, 'c23bb0')],            # трещины Хаоса
}


GAMMA = {'ingot_alubrass': 1.35, 'ingot_ardite': 0.85}


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def recolor(base, pal):
    im = Image.open(f'{SRC}/{base}.png').convert('RGBA')
    px = [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 0]
    levels = sorted({round(lum(im.getpixel(p))) for p in px})
    n = len(levels)
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cols = [hx(c) for c in pal]
    for p in px:
        lo, hi = levels[0], levels[-1]
        t = min(1.0, max(0.0, (lum(im.getpixel(p)) - lo) / (hi - lo)))
        t = t ** GAMMA.get(base, 1.0)
        i = min(len(cols) - 1, int(round(t * (len(cols) - 1))))
        out.putpixel(p, cols[i] + (im.getpixel(p)[3],))
    return out, levels


if __name__ == '__main__':
    for name, (base, pal) in PAL.items():
        im, levels = recolor(base, pal)
        for x, y, c in ACCENT[name]:
            if im.getpixel((x, y))[3] > 0:
                im.putpixel((x, y), hx(c) + (255,))
        im.save(f'{OUT}/{name}.png')
        print(name, base, len(levels), 'levels')
