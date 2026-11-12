import sys, math, random
from PIL import Image
T = sys.argv[1]; OUT = sys.argv[2]
stone = Image.open(T + '/chaos_stone.png').convert('RGBA')
def px(im, x, y, c): im.putpixel((x, y), c + (255,) if len(c) == 3 else c)
# верх: камень Хаоса + светящийся круг рун и камень в центре
top = stone.copy()
cx = cy = 7.5
for y in range(16):
    for x in range(16):
        d = math.hypot(x - cx, y - cy)
        if 5.0 <= d < 6.0:
            a = math.atan2(y - cy, x - cx)
            on = int((a + math.pi) / (2 * math.pi) * 12) % 2 == 0
            px(top, x, y, (200, 140, 255) if on else (120, 70, 170))
        elif d < 1.6:
            px(top, x, y, (255, 200, 255) if d < 0.8 else (190, 90, 230))
# четыре луча к центру
for (x, y) in [(7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)]:
    px(top, x, y, (150, 90, 210))
# рамка
for i in range(16):
    for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
        px(top, x, y, (70, 50, 90))
top.save(OUT + '/chaos_enchanter_top.png')
# бок: сверху полоса металла, ниже камень с вертикальной руной
side = stone.copy()
for x in range(16):
    px(side, x, 4, (70, 50, 90))
    px(side, x, 5, (200, 170, 230) if x % 4 else (148, 118, 168))
    px(side, x, 6, (110, 80, 140))
    px(side, x, 15, (22, 15, 30))
rune = [(7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10), (7, 11), (8, 11), (6, 12), (9, 12), (7, 13), (8, 13)]
for (x, y) in rune:
    px(side, x, y, (180, 110, 240))
for (x, y) in [(7, 9), (8, 9), (7, 12), (8, 12)]:
    px(side, x, y, (90, 40, 130))
side.save(OUT + '/chaos_enchanter_side.png')
