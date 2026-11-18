"""Значок сборки «Осколок Хаоса» для лаунчера: тёмный гранёный осколок с трещиной, 32x32, увеличенный до 128x128.

python3 znachok_sborki.py <выход.png>
"""
import random
import sys
from PIL import Image, ImageDraw

random.seed(3)
N = 32


def hx(s, a=255):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


OUT = hx('14061f')
LEFT = [hx('2a0c40'), hx('36114f'), hx('42165e')]
RIGHT = [hx('5a1a7c'), hx('6c2392'), hx('7d2ca6')]
BAND = hx('a24ad0')
RIDGE = hx('e2a3ff')
CRACK, CORE = hx('ff5cf0'), hx('fff0ff')


def mask(poly):
    m = Image.new('L', (N, N), 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return m.load()


TIP, BOT, MID = (19, 1), (13, 30), (16, 15)
shape = [TIP, (25, 9), (24, 18), (18, 28), BOT, (8, 22), (7, 12), (12, 5)]
left = [TIP, (12, 5), (7, 12), (8, 22), BOT, MID]
band = [TIP, (22, 8), (19, 17), (15, 27), BOT, MID]

S, L, B = mask(shape), mask(left), mask(band)
img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
p = img.load()
for y in range(N):
    for x in range(N):
        if not S[x, y]:
            continue
        shade = 2 - min(2, y // 11)          # вниз темнее
        if random.random() < 0.18:
            shade = max(0, shade - 1)        # зернистость камня
        if L[x, y]:
            p[x, y] = LEFT[shade]
        elif B[x, y]:
            p[x, y] = BAND if y < 12 else RIGHT[2]
        else:
            p[x, y] = RIGHT[shade]

# ребро от верхушки вниз
d = ImageDraw.Draw(img)
d.line([TIP, MID], fill=RIDGE)
d.line([(19, 2), (22, 8)], fill=hx('c77cf0'))

# контур
inside = [[S[x, y] > 0 for y in range(N)] for x in range(N)]
for y in range(N):
    for x in range(N):
        if inside[x][y] and any(not (0 <= x + dx < N and 0 <= y + dy < N and inside[x + dx][y + dy])
                                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            p[x, y] = OUT

# светящаяся трещина
crack = [(11, 8), (13, 11), (12, 14), (15, 18), (13, 21), (15, 25)]
d.line(crack, fill=CRACK)
for pt in crack[1:-1]:
    p[pt] = CORE

# мелкие осколки вокруг
for cx, cy in ((4, 5), (28, 24), (27, 3)):
    for dx, dy in ((0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
        p[cx + dx, cy + dy] = RIGHT[2] if (dx, dy) != (0, 0) else BAND
    p[cx, cy - 1] = RIDGE

# сияние: ореол вокруг осколка, бледнеет наружу
glow = Image.new('RGBA', (N, N), (0, 0, 0, 0))
g = glow.load()
for y in range(N):
    for x in range(N):
        if p[x, y][3]:
            continue
        dist = min((abs(x - i) + abs(y - j) for i in range(max(0, x - 3), min(N, x + 4))
                    for j in range(max(0, y - 3), min(N, y + 4)) if inside[i][j]), default=9)
        if dist <= 3:
            g[x, y] = (200, 60, 255, (110, 60, 25)[dist - 1])
glow.alpha_composite(img)
glow.resize((128, 128), Image.NEAREST).save(sys.argv[1])
print('ok')
