"""Текстуры окна Стола Хаоса (в духе окна Гримуара: серебряная рамка, звёздочки в углах, тёмный фиолет).

python3 stol_haosa_gui.py <папка textures/gui>
Рисует chaos_enchanter.png (фон 214x246) и chaos_enchanter_widgets.png (кнопки, вкладки, значки).
Разметка совпадает с GuiChaosEnchanter / ContainerChaosEnchanter.
"""
import random
import sys

from PIL import Image

OUT = sys.argv[1]
random.seed(7)

W, H = 214, 250

SILVER_HI = (225, 228, 238)
SILVER = (170, 174, 188)
SILVER_LO = (110, 108, 128)
EDGE = (14, 10, 20)
FILL = (22, 17, 31)
VIOLET = (58, 42, 85)
WELL_FILL = (13, 10, 19)
WELL_DARK = (6, 4, 10)
WELL_LIGHT = (60, 47, 84)
GOLD = (236, 176, 100)


def img(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def put(im, x, y, c, a=255):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c[:3]) + (a,))


def rect(im, x0, y0, x1, y1, c, a=255):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(im, x, y, c, a)


def noise_fill(im, x0, y0, x1, y1, base, amp=3, vignette=True):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rx, ry = max(1, (x1 - x0) / 2), max(1, (y1 - y0) / 2)
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = random.randint(-amp, amp)
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 if vignette else 0
            v = -int(min(1.0, d) * 5)
            put(im, x, y, (base[0] + n + v + 2, base[1] + n + v, base[2] + n + v + 4))


def sparkle(im, x, y, c=SILVER_HI, mid=SILVER):
    put(im, x, y - 1, c)
    put(im, x - 1, y, c)
    put(im, x + 1, y, c)
    put(im, x, y + 1, c)
    put(im, x, y, mid)


def panel(im, x, y, w, h):
    """Рамка как у Гримуара: тёмный край, двойное серебро, тёмная линия, фиолетовая нить внутри."""
    rect(im, x, y, x + w, y + h, EDGE)
    rect(im, x + 1, y + 1, x + w - 1, y + 3, SILVER_HI)          # верх
    rect(im, x + 1, y + 1, x + 3, y + h - 1, SILVER_HI)          # лево
    rect(im, x + 1, y + h - 3, x + w - 1, y + h - 1, SILVER)     # низ
    rect(im, x + w - 3, y + 1, x + w - 1, y + h - 1, SILVER)     # право
    put(im, x + 2, y + h - 2, SILVER)
    put(im, x + w - 2, y + 2, SILVER_HI)
    rect(im, x + 3, y + 3, x + w - 3, y + h - 3, EDGE)
    rect(im, x + 4, y + 4, x + w - 4, y + 5, VIOLET)
    rect(im, x + 4, y + h - 5, x + w - 4, y + h - 4, VIOLET)
    rect(im, x + 4, y + 4, x + 5, y + h - 4, VIOLET)
    rect(im, x + w - 5, y + 4, x + w - 4, y + h - 4, VIOLET)
    noise_fill(im, x + 5, y + 5, x + w - 5, y + h - 5, FILL)
    for (sx, sy) in [(x + 8, y + 8), (x + w - 9, y + 8), (x + 8, y + h - 9), (x + w - 9, y + h - 9)]:
        sparkle(im, sx, sy)


def well(im, x, y, w, h, fill=WELL_FILL):
    rect(im, x, y, x + w, y + 1, WELL_DARK)
    rect(im, x, y, x + 1, y + h, WELL_DARK)
    rect(im, x, y + h - 1, x + w, y + h, WELL_LIGHT)
    rect(im, x + w - 1, y, x + w, y + h, WELL_LIGHT)
    rect(im, x + 1, y + 1, x + w - 1, y + h - 1, fill)


def slot(im, x, y):
    """Место 18x18; предмет рисуется в (x+1, y+1)."""
    well(im, x, y, 18, 18, (11, 8, 16))
    rect(im, x + 1, y + 1, x + 17, y + 2, (8, 6, 12))


def big_slot(im, x, y):
    """Место предмета: серебряное кольцо 24x24 вокруг обычного места."""
    rect(im, x, y, x + 24, y + 24, EDGE)
    rect(im, x + 1, y + 1, x + 23, y + 2, SILVER_HI)
    rect(im, x + 1, y + 1, x + 2, y + 23, SILVER_HI)
    rect(im, x + 1, y + 22, x + 23, y + 23, SILVER)
    rect(im, x + 22, y + 1, x + 23, y + 23, SILVER)
    rect(im, x + 2, y + 2, x + 22, y + 22, (40, 28, 62))
    slot(im, x + 3, y + 3)
    for (sx, sy) in [(x, y), (x + 23, y), (x, y + 23), (x + 23, y + 23)]:
        put(im, sx, sy, SILVER_HI)


def hline_ornament(im, x0, x1, y):
    for x in range(x0, x1):
        put(im, x, y, SILVER_LO)
    mid = (x0 + x1) // 2
    for dx, c in [(0, SILVER_HI), (-1, SILVER), (1, SILVER), (-2, SILVER_LO), (2, SILVER_LO)]:
        put(im, mid + dx, y, c)
    put(im, mid, y - 1, SILVER)
    put(im, mid, y + 1, SILVER)
    put(im, x0 - 1, y, SILVER)
    put(im, x1, y, SILVER)


# ---------------------------------------------------------------- фон
bg = img(256, 256)
panel(bg, 0, 0, W, H)
# заголовок: орнамент по сторонам названия
hline_ornament(bg, 12, 62, 10)
hline_ornament(bg, 152, 202, 10)
# место предмета и лазурита
big_slot(bg, 12, 22)          # предмет в (16, 26)
slot(bg, 15, 52)              # лазурит в (16, 53)
# шкала силы: трубка 14x58, отметка «15 обычных полок» на половине
well(bg, 17, 78, 12, 58, (9, 6, 14))
for y in range(79, 135, 4):
    put(bg, 18, y, (24, 18, 34))
for x in (15, 16, 29, 30):
    put(bg, x, 107, GOLD)
# список (7 строк по 12) и панель выбранного зачарования
well(bg, 44, 20, 162, 86)
well(bg, 44, 108, 162, 50)
# разделитель над инвентарём
hline_ornament(bg, 12, 202, 162)
# инвентарь
for r in range(3):
    for c in range(9):
        slot(bg, 25 + c * 18, 166 + r * 18)
for c in range(9):
    slot(bg, 25 + c * 18, 224)
bg.save(OUT + '/chaos_enchanter.png')

# ---------------------------------------------------------------- виджеты
wd = img(256, 256)


def button(y, border, top, bottom, hi):
    w, h = 100, 16
    rect(wd, 0, y, w, y + h, EDGE)
    rect(wd, 1, y + 1, w - 1, y + h - 1, border)
    for yy in range(y + 2, y + h - 2):
        t = (yy - y - 2) / (h - 5)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        rect(wd, 2, yy, w - 2, yy + 1, c)
    rect(wd, 2, y + 2, w - 2, y + 3, hi)


button(0, (143, 130, 170), (62, 44, 94), (36, 25, 56), (98, 80, 140))          # обычная
button(16, SILVER_HI, (92, 60, 140), (56, 36, 92), (150, 120, 205))          # под мышью
button(32, (70, 66, 84), (36, 32, 44), (28, 25, 35), (44, 40, 54))            # недоступна

# вкладки 92x22: активная сливается с окном (без нижней кромки), неактивная — темнее
def tab(y, h, active):
    w = 92
    rect(wd, 0, y, w, y + h, EDGE)
    rect(wd, 1, y + 1, w - 1, y + 3 if active else y + 2, SILVER_HI if active else SILVER_LO)
    rect(wd, 1, y + 1, 3 if active else 2, y + h, SILVER_HI if active else SILVER_LO)
    rect(wd, w - 3 if active else w - 2, y + 1, w - 1, y + h, SILVER if active else SILVER_LO)
    inner = (FILL if active else (12, 9, 17))
    x0, x1 = (3, w - 3) if active else (2, w - 2)
    rect(wd, x0, (y + 3) if active else (y + 2), x1, y + h, inner)
    if active:
        rect(wd, x0, y + 3, x1, y + 4, EDGE)
        rect(wd, x0 + 1, y + 4, x1 - 1, y + 5, VIOLET)
        sparkle(wd, 7, y + 8)
        sparkle(wd, w - 8, y + 8)
    else:
        rect(wd, 1, y + h - 1, w - 1, y + h, SILVER_LO)


tab(54, 24, True)
tab(80, 20, False)

# шкала силы 10x56: снизу глубокий фиолет, сверху почти белое
for yy in range(56):
    t = 1 - yy / 55
    if t < 0.5:
        a, b, k = (60, 22, 110), (157, 77, 255), t / 0.5
    else:
        a, b, k = (157, 77, 255), (246, 214, 255), (t - 0.5) / 0.5
    c = tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))
    for xx in range(10):
        edge = xx in (0, 9)
        put(wd, 100 + xx, 54 + yy, tuple(max(0, v - 30) for v in c) if edge else c)
    put(wd, 102, 54 + yy, tuple(min(255, v + 40) for v in c))

# полоса изучения 120x5
for xx in range(120):
    t = xx / 119
    c = (int(120 + 110 * t), int(60 + 40 * t), int(220 + 30 * t))
    for yy in range(5):
        put(wd, 112 + xx, 54 + yy, tuple(min(255, v + (35 if yy == 1 else 0) - (30 if yy == 4 else 0)) for v in c))

# значки 9x9 (y 140)
ICONS = {
    'lapis': (0, [
        "....b....",
        "...bLb...",
        "..bLLmb..",
        ".bLLmmmb.",
        "bLLmmmmdb",
        ".bmmmmdb.",
        "..bmmdb..",
        "...bdb...",
        "....b....",
    ], {'b': (20, 30, 90), 'L': (140, 180, 255), 'm': (50, 90, 220), 'd': (30, 50, 150)}),
    'xp': (10, [
        "..bbbbb..",
        ".bLLggGb.",
        "bLLggggGb",
        "bLggggggb",
        "bgggggggb",
        "bggggggdb",
        "bGggggddb",
        ".bGgdddb.",
        "..bbbbb..",
    ], {'b': (30, 60, 10), 'L': (230, 255, 160), 'g': (150, 230, 40), 'G': (110, 200, 30), 'd': (80, 150, 20)}),
    'clock': (20, [
        "..sssss..",
        ".sdddddS.",
        "sdddWdddS",
        "sdddWdddS",
        "sdddWWWdS",
        "sdddddddS",
        "sdddddddS",
        ".SdddddS.",
        "..SSSSS..",
    ], {'s': SILVER_HI, 'S': SILVER_LO, 'd': (40, 30, 60), 'W': (236, 176, 100)}),
    'lock': (30, [
        "...sss...",
        "..s...s..",
        "..s...s..",
        ".SSSSSSS.",
        ".SgggggS.",
        ".Sgg.ggS.",
        ".SgggggS.",
        ".SgggggS.",
        ".SSSSSSS.",
    ], {'s': SILVER, 'S': (90, 80, 110), 'g': (150, 140, 170), '.': None}),
    'check': (40, [
        ".........",
        "........g",
        ".......gG",
        "......gG.",
        "g....gG..",
        "Gg..gG...",
        ".GggG....",
        "..GG.....",
        ".........",
    ], {'g': (160, 240, 170), 'G': (60, 140, 80)}),
    'left': (50, [
        ".........",
        "....s....",
        "...ss....",
        "..sss....",
        ".ssss....",
        "..sss....",
        "...ss....",
        "....s....",
        ".........",
    ], {'s': SILVER_HI}),
    'right': (60, [
        ".........",
        "....s....",
        "....ss...",
        "....sss..",
        "....ssss.",
        "....sss..",
        "....ss...",
        "....s....",
        ".........",
    ], {'s': SILVER_HI}),
    'star': (70, [
        "....w....",
        "....w....",
        "...vwv...",
        "wwwwWwwww",
        "...vwv...",
        "....w....",
        "....w....",
        ".........",
        ".........",
    ], {'w': (230, 200, 255), 'W': (255, 255, 255), 'v': (150, 90, 230)}),
}
for name, (x0, rows, pal) in ICONS.items():
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            c = pal.get(ch) if ch != '.' else None
            if c:
                put(wd, x0 + xx, 140 + yy, c)

# призраки пустых мест 16x16 (y 160): книга и лазурит
GHOST = (52, 42, 72)
book = [
    "................",
    "...GGGGGGGGGG...",
    "..G.........GG..",
    "..G..GGGGG..G.G.",
    "..G.........G.G.",
    "..G..GGGG...G.G.",
    "..G.........G.G.",
    "..G..GGGGG..G.G.",
    "..G.........G.G.",
    "..G..GGG....G.G.",
    "..G.........G.G.",
    "..G.........G.G.",
    "..GGGGGGGGGGG.G.",
    "...G..........G.",
    "....GGGGGGGGGGG.",
    "................",
]
gem = [
    "................",
    "................",
    ".......GG.......",
    "......G..G......",
    ".....G....G.....",
    "....G......G....",
    "...G........G...",
    "..G....GG....G..",
    "..G....GG....G..",
    "...G........G...",
    "....G......G....",
    ".....G....G.....",
    "......G..G......",
    ".......GG.......",
    "................",
    "................",
]
for x0, rows in ((0, book), (16, gem)):
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            if ch == 'G':
                put(wd, x0 + xx, 160 + yy, GHOST)
wd.save(OUT + '/chaos_enchanter_widgets.png')
print('ok')
