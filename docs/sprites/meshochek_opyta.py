"""Мешочек опыта (16x16): набухшее брюшко насекомого, светящееся опытом.

Кольца хитина Жучка Тяги (фиолетовые) стягивают зелёно-жёлтый светящийся опыт,
сверху — обрывок стебелька, где брюшко держалось. Свет сверху слева.
"""
import sys
from PIL import Image

OUT = sys.argv[1]


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


GLOW = [hx(c) for c in ('2f6b12', '4d9a17', '74c41f', '9fe03a', 'cbf26a', 'f2ffb8')]  # опыт: тень → блик
CHITIN = [hx(c) for c in ('1c0d30', '351a58', '52308a', '7652b8')]                  # кольца: тень → блик
OUTLINE = hx('140822')

img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = img.load()

cx, cy = 7.5, 8.2
rx, ry = 5.4, 6.6
inside = {}
for y in range(16):
    for x in range(16):
        dx, dy = (x + 0.5 - 0.5 - cx) / rx, (y - cy) / ry
        # хвостик книзу чуть заострён
        if dy > 0:
            dx *= 1 + dy * dy * 1.5
        r = dx * dx + dy * dy
        if r <= 1.0:
            inside[(x, y)] = (dx, dy, r)

# сегменты: кольца хитина выгнуты по брюшку (к краям поднимаются — смотрим чуть сверху)
RINGS = (5.4, 8.4, 11.1, 13.3)
ring_px = set()
for x in range(16):
    d = (x - cx) / rx
    for r0 in RINGS:
        ring_px.add((x, int(round(r0 - 0.8 * d * d))))
for (x, y), (dx, dy, r) in inside.items():
    light = 0.55 - 0.45 * dx - 0.35 * dy - 0.35 * r      # свет сверху слева, к краю темнее
    if (x, y) in ring_px:
        k = max(0, min(3, int((light + 0.2) * 3.2)))
        px[x, y] = CHITIN[k]
    else:
        k = max(0, min(5, int((light + 0.25) * 5.2)))
        px[x, y] = GLOW[k]

# контур
for (x, y) in list(inside):
    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
        if (nx, ny) not in inside and 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] == 0:
            px[nx, ny] = OUTLINE

# хитиновая «крышечка» сверху (где брюшко держалось) с обрывком перемычки
for x in range(5, 11):
    if px[x, 2][3] and px[x, 2] != OUTLINE:
        px[x, 2] = CHITIN[3] if x < 7 else CHITIN[2] if x < 9 else CHITIN[1]
for x in (6, 7, 8, 9):
    px[x, 1] = OUTLINE
px[7, 0] = OUTLINE
px[8, 0] = OUTLINE
# блик и искры опыта
px[4, 5] = GLOW[5]
px[4, 6] = GLOW[5]
px[5, 5] = GLOW[4]
px[9, 9] = GLOW[5]
px[7, 12] = GLOW[4]
img.save(OUT)
big = img.resize((256, 256), Image.NEAREST)
bg = Image.new('RGBA', big.size, (139, 139, 139, 255))
bg.alpha_composite(big)
bg.save(OUT.replace('.png', '_x16.png'))
