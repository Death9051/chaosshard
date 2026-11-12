"""Свои значки 32x32: заклинания Гримуара, вкладки окна Якоря и Стола Хаоса.

python3 znachki.py <папка textures/gui> [<превью.png>]
Рисует icons.png (атлас 512x512, 16 значков в ряд, по 32 px) — порядок в ICONS ниже, его же знает ChaosUi.
В окнах значок рисуется в 16 единиц интерфейса: при масштабе интерфейса 2 и выше видны все 32 пикселя.

Как рисуется: каждая часть — заливка фигуры; свет сверху-слева и тень снизу-справа ставятся сами по краю фигуры,
вокруг всего значка — тёмная обводка в 1 пиксель. Без подложки: читается и на тёмном окне, и на светлой бумаге.
(Первый черновик 16x16 — znachki16.py.)
"""
import math
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
N = 32
OUTLINE = (22, 12, 30, 255)

VIOLET = ((88, 38, 150), (160, 92, 236), (226, 192, 255))
DVIOLET = ((46, 20, 80), (90, 44, 150), (150, 92, 220))
GREEN = ((36, 100, 46), (92, 186, 78), (186, 244, 146))
GOLD = ((150, 96, 30), (236, 176, 100), (255, 230, 168))
RED = ((120, 20, 36), (206, 56, 74), (255, 150, 150))
BLUE = ((40, 70, 150), (92, 142, 230), (190, 216, 255))
SILVER = ((92, 90, 112), (172, 176, 190), (236, 238, 246))
PAPER = ((168, 146, 120), (224, 208, 184), (252, 246, 232))
BROWN = ((78, 48, 20), (146, 96, 44), (200, 150, 84))
PINK = ((120, 20, 90), (220, 70, 180), (255, 170, 230))
WHITE = (255, 255, 255)


class Icon:
    def __init__(self):
        self.img = Image.new('RGBA', (N, N), (0, 0, 0, 0))

    def part(self, draw, rmp, bevel=True):
        """Фигура одной заливкой; свет и тень по краю — сами."""
        m = Image.new('L', (N, N), 0)
        draw(ImageDraw.Draw(m))
        mp = m.load()
        px = self.img.load()

        def inside(x, y):
            return 0 <= x < N and 0 <= y < N and mp[x, y] > 127
        for y in range(N):
            for x in range(N):
                if not inside(x, y):
                    continue
                c = rmp[1]
                if bevel:
                    if not inside(x + 1, y) or not inside(x, y + 1):
                        c = rmp[0]
                    elif not inside(x - 1, y) or not inside(x, y - 1):
                        c = rmp[2]
                px[x, y] = tuple(c) + (255,)
        return self

    def ink(self, draw, color):
        """Детали прямым цветом поверх (линии, точки)."""
        m = Image.new('L', (N, N), 0)
        draw(ImageDraw.Draw(m))
        mp = m.load()
        px = self.img.load()
        for y in range(N):
            for x in range(N):
                if mp[x, y] > 127:
                    px[x, y] = tuple(color[:3]) + (255,)
        return self

    def done(self):
        px = self.img.load()
        a = [[px[x, y][3] > 0 for x in range(N)] for y in range(N)]
        for y in range(N):
            for x in range(N):
                if a[y][x]:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < N and 0 <= yy < N and a[yy][xx]:
                        px[x, y] = OUTLINE
                        break
        return self.img


def sparkle(ic, x, y, r=2, color=WHITE, core=WHITE):
    ic.ink(lambda d: (d.line([(x - r, y), (x + r, y)], fill=255), d.line([(x, y - r), (x, y + r)], fill=255)), color)
    ic.ink(lambda d: d.point((x, y), fill=255), core)


# ------------------------------------------------------------------ заклинания

def book():
    ic = Icon()
    ic.part(lambda d: d.polygon([(2, 24), (15, 27), (17, 27), (30, 24), (30, 27), (17, 30), (15, 30), (2, 27)], fill=255), VIOLET)
    ic.part(lambda d: d.polygon([(3, 7), (15, 10), (15, 26), (3, 23)], fill=255), PAPER)
    ic.part(lambda d: d.polygon([(17, 10), (29, 7), (29, 23), (17, 26)], fill=255), PAPER)
    for i, w in enumerate([9, 7, 9, 5, 8]):
        y = 12 + i * 2.6
        ic.ink(lambda d, y=y, w=w: d.line([(5, y), (5 + w, y + 2)], fill=255), PAPER[0])
        ic.ink(lambda d, y=y, w=w: d.line([(19, y + 2.5), (19 + w, y + 0.5)], fill=255), PAPER[0])
    ic.ink(lambda d: d.line([(16, 10), (16, 28)], fill=255), VIOLET[0])
    return ic.done()


def sprout():
    ic = Icon()
    ic.part(lambda d: d.ellipse((6, 23, 26, 31), fill=255), DVIOLET)
    ic.part(lambda d: d.line([(16, 26), (16, 13)], fill=255, width=3), GREEN)
    ic.part(lambda d: d.polygon([(15, 17), (10, 15), (5, 11), (3, 6), (9, 6), (13, 9), (15, 14)], fill=255), GREEN)
    ic.part(lambda d: d.polygon([(17, 14), (19, 8), (24, 4), (30, 3), (29, 9), (25, 13), (18, 16)], fill=255), GREEN)
    ic.ink(lambda d: d.line([(6, 8), (13, 13)], fill=255), GREEN[0])
    ic.ink(lambda d: d.line([(27, 5), (20, 12)], fill=255), GREEN[0])
    sparkle(ic, 26, 20, 2, VIOLET[2])
    sparkle(ic, 6, 19, 1, VIOLET[2])
    return ic.done()


def bridge():
    ic = Icon()
    for (x, y) in [(2, 23), (11, 15), (20, 7)]:
        ic.part(lambda d, x=x, y=y: d.rectangle((x, y + 2, x + 9, y + 5), fill=255), DVIOLET)
        ic.part(lambda d, x=x, y=y: d.rectangle((x, y, x + 9, y + 2), fill=255), VIOLET)
    for (x, y) in [(5, 30), (14, 23), (23, 15), (9, 27), (18, 19)]:
        ic.ink(lambda d, x=x, y=y: d.point((x, y), fill=255), VIOLET[2])
    sparkle(ic, 28, 3, 2, VIOLET[2])
    return ic.done()


def spring():
    ic = Icon()

    def spiral(d):
        pts = []
        for i in range(0, 420, 6):
            a = math.radians(i)
            r = 13 - i / 420 * 10
            pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
        d.line(pts, fill=255, width=3)
    ic.part(spiral, VIOLET)
    ic.part(lambda d: d.ellipse((13, 13, 19, 19), fill=255), DVIOLET)
    ic.ink(lambda d: d.ellipse((15, 15, 17, 17), fill=255), RED[1])
    ic.part(lambda d: d.rectangle((24, 2, 29, 7), fill=255), SILVER)
    for (x, y) in [(22, 9), (20, 11)]:
        ic.ink(lambda d, x=x, y=y: d.point((x, y), fill=255), SILVER[2])
    return ic.done()


def rift():
    ic = Icon()
    ic.part(lambda d: d.polygon([(16, 3), (29, 9), (16, 15), (3, 9)], fill=255), (SILVER[1], SILVER[2], WHITE))
    ic.part(lambda d: d.polygon([(3, 9), (16, 15), (16, 30), (3, 23)], fill=255), SILVER)
    ic.part(lambda d: d.polygon([(16, 15), (29, 9), (29, 23), (16, 30)], fill=255), (SILVER[0], (120, 118, 140), SILVER[1]))
    ic.ink(lambda d: d.line([(18, 4), (14, 9), (19, 12), (13, 18), (18, 22), (14, 29)], fill=255, width=2), OUTLINE)
    ic.ink(lambda d: d.line([(19, 12), (24, 15)], fill=255), OUTLINE)
    ic.ink(lambda d: d.line([(13, 18), (8, 17)], fill=255), OUTLINE)
    ic.ink(lambda d: d.point((15, 20), fill=255), VIOLET[1])
    return ic.done()


def starsight():
    ic = Icon()
    ic.part(lambda d: d.polygon([(1, 16), (6, 10), (12, 7), (20, 7), (26, 10), (31, 16), (26, 22), (20, 25), (12, 25), (6, 22)], fill=255),
            (BLUE[1], BLUE[2], WHITE))
    ic.part(lambda d: d.ellipse((9, 9, 23, 23), fill=255), BLUE)
    ic.part(lambda d: d.ellipse((12, 12, 20, 20), fill=255), DVIOLET, bevel=False)
    ic.ink(lambda d: d.polygon([(16, 11), (17, 15), (21, 16), (17, 17), (16, 21), (15, 17), (11, 16), (15, 15)], fill=255), WHITE)
    sparkle(ic, 27, 4, 2, BLUE[2])
    return ic.done()


def recall():
    ic = Icon()
    ic.part(lambda d: d.arc((4, 3, 28, 27), 200, 500, fill=255, width=3), GOLD)
    ic.part(lambda d: d.polygon([(4, 10), (11, 11), (6, 17)], fill=255), GOLD)
    ic.part(lambda d: d.ellipse((12, 23, 20, 31), fill=255), VIOLET)
    ic.ink(lambda d: d.line([(16, 26), (16, 18)], fill=255), GOLD[2])
    sparkle(ic, 16, 15, 1, GOLD[2])
    return ic.done()


def dash():
    ic = Icon()
    ic.part(lambda d: d.polygon([(12, 4), (18, 4), (30, 16), (18, 28), (12, 28), (24, 16)], fill=255), SILVER)
    ic.part(lambda d: d.polygon([(4, 9), (9, 9), (16, 16), (9, 23), (4, 23), (11, 16)], fill=255), VIOLET)
    for (x0, y) in [(0, 12), (0, 20)]:
        ic.ink(lambda d, x0=x0, y=y: d.line([(x0, y), (x0 + 3, y)], fill=255), VIOLET[2])
    return ic.done()


def bolt():
    ic = Icon()
    ic.part(lambda d: d.line([(5, 27), (22, 10)], fill=255, width=3), VIOLET)
    ic.part(lambda d: d.polygon([(30, 2), (27, 15), (23, 12), (20, 9), (17, 5)], fill=255), (VIOLET[1], VIOLET[2], WHITE))
    ic.part(lambda d: d.polygon([(3, 22), (8, 24), (9, 29), (5, 30), (2, 27)], fill=255), DVIOLET)
    sparkle(ic, 9, 9, 2, VIOLET[2])
    sparkle(ic, 23, 24, 2, VIOLET[2])
    sparkle(ic, 15, 25, 1, VIOLET[2])
    return ic.done()


def smooth(pts, n=8):
    """Плавная кривая через точки (Катмулл — Ром)."""
    out = []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(0, i - 1)], pts[i], pts[i + 1], pts[min(len(pts) - 1, i + 2)]
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1)))
    out.append(pts[-1])
    return out


def tube(pts, w0, w1):
    """Трубка по кривой: толщина от w0 к w1."""
    def draw(d):
        n = len(pts)
        for i in range(n - 1):
            w = w0 + (w1 - w0) * i / max(1, n - 2)
            d.line([pts[i], pts[i + 1]], fill=255, width=max(1, round(w)))
            r = w / 2
            d.ellipse((pts[i][0] - r, pts[i][1] - r, pts[i][0] + r, pts[i][1] + r), fill=255)
    return draw


def part_outlined(ic, draw, rmp):
    """Часть с обводкой поверх уже нарисованного (чтобы витки и голова отделялись)."""
    from PIL import ImageFilter
    m = Image.new('L', (N, N), 0)
    draw(ImageDraw.Draw(m))
    dil = m.filter(ImageFilter.MaxFilter(3))
    px, dp, mp = ic.img.load(), dil.load(), m.load()
    for y in range(N):
        for x in range(N):
            if dp[x, y] > 127 and mp[x, y] <= 127:
                px[x, y] = OUTLINE
    ic.part(draw, rmp)


def scales(ic, pts, step, color):
    """Ромбики-чешуя по хребту."""
    for i in range(step // 2, len(pts) - 2, step):
        x, y = pts[i]
        dx, dy = pts[i + 1][0] - x, pts[i + 1][1] - y
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        ic.ink(lambda d, x=x, y=y, nx=nx, ny=ny, dx=dx, dy=dy, L=L: d.polygon(
            [(x + nx * 1.6, y + ny * 1.6), (x + dx / L * 1.6, y + dy / L * 1.6),
             (x - nx * 1.6, y - ny * 1.6), (x - dx / L * 1.6, y - dy / L * 1.6)], fill=255), color)


def form():
    """Форма Хаоса — змей Хаоса: виток внизу, шея с головой поверх."""
    ic = Icon()
    coil = smooth([(3, 23), (6, 28), (14, 30), (22, 29), (28, 25), (28, 20), (23, 17)])
    neck = smooth([(23, 17), (15, 19), (9, 16), (8, 11), (12, 8), (17, 8)])
    ic.part(tube(coil, 2, 6), VIOLET)
    scales(ic, coil, 6, VIOLET[0])
    part_outlined(ic, tube(neck, 6, 5), VIOLET)
    scales(ic, neck, 6, VIOLET[0])
    part_outlined(ic, lambda d: d.polygon([(15, 6), (22, 3), (28, 5), (30, 9), (27, 12), (21, 12), (16, 11)], fill=255), VIOLET)
    ic.ink(lambda d: d.line([(18, 10), (27, 11)], fill=255), DVIOLET[1])
    ic.ink(lambda d: d.rectangle((22, 6, 23, 7), fill=255), GOLD[2])
    ic.ink(lambda d: d.point((23, 7), fill=255), OUTLINE)
    ic.ink(lambda d: d.line([(30, 10), (31, 10)], fill=255), PINK[1])
    ic.ink(lambda d: d.point((31, 9), fill=255), PINK[1])
    ic.ink(lambda d: d.point((31, 11), fill=255), PINK[1])
    sparkle(ic, 4, 5, 2, VIOLET[2])
    sparkle(ic, 30, 30, 1, VIOLET[2])
    return ic.done()


def curse():
    ic = Icon()
    ic.part(lambda d: d.ellipse((5, 3, 27, 23), fill=255), RED)
    ic.part(lambda d: d.rectangle((10, 18, 22, 27), fill=255), RED)
    ic.ink(lambda d: d.ellipse((9, 10, 15, 16), fill=255), DVIOLET[0])
    ic.ink(lambda d: d.ellipse((17, 10, 23, 16), fill=255), DVIOLET[0])
    ic.ink(lambda d: d.point((12, 13), fill=255), VIOLET[2])
    ic.ink(lambda d: d.point((20, 13), fill=255), VIOLET[2])
    ic.ink(lambda d: d.polygon([(16, 17), (14, 20), (18, 20)], fill=255), RED[0])
    for x in (12, 15, 18, 21):
        ic.ink(lambda d, x=x: d.line([(x, 23), (x, 27)], fill=255), RED[0])
    return ic.done()


def shield():
    ic = Icon()
    ic.part(lambda d: d.polygon([(4, 4), (28, 4), (28, 16), (24, 24), (16, 30), (8, 24), (4, 16)], fill=255), SILVER)
    ic.part(lambda d: d.polygon([(7, 7), (25, 7), (25, 16), (22, 22), (16, 27), (10, 22), (7, 16)], fill=255), DVIOLET)
    ic.part(lambda d: d.polygon([(16, 9), (21, 15), (16, 21), (11, 15)], fill=255), VIOLET)
    ic.ink(lambda d: d.point((16, 15), fill=255), WHITE)
    return ic.done()


# ------------------------------------------------------------------ вкладки

def tab_anchor():
    ic = Icon()
    ic.part(lambda d: d.polygon([(4, 30), (6, 25), (26, 25), (28, 30)], fill=255), SILVER)
    ic.part(lambda d: d.rectangle((10, 22, 22, 25), fill=255), SILVER)
    ic.part(lambda d: d.polygon([(16, 1), (22, 8), (21, 18), (16, 23), (11, 18), (10, 8)], fill=255), VIOLET)
    ic.ink(lambda d: d.line([(16, 3), (16, 21)], fill=255), VIOLET[0])
    ic.ink(lambda d: d.line([(13, 7), (13, 15)], fill=255), VIOLET[2])
    sparkle(ic, 26, 6, 2, VIOLET[2])
    return ic.done()


def tab_net():
    ic = Icon()
    ic.ink(lambda d: d.line([(16, 7), (6, 24), (26, 24), (16, 7)], fill=255, width=2), GOLD[1])
    for (x, y) in [(16, 7), (6, 24), (26, 24)]:
        ic.part(lambda d, x=x, y=y: d.ellipse((x - 5, y - 5, x + 5, y + 5), fill=255), VIOLET)
        ic.ink(lambda d, x=x, y=y: d.point((x - 2, y - 2), fill=255), WHITE)
    return ic.done()


def tab_path():
    ic = Icon()
    ic.part(lambda d: d.rectangle((14, 4, 18, 30), fill=255), BROWN)
    ic.part(lambda d: d.polygon([(5, 6), (25, 6), (29, 10), (25, 14), (5, 14)], fill=255), GOLD)
    ic.part(lambda d: d.polygon([(27, 16), (7, 16), (3, 20), (7, 24), (27, 24)], fill=255), GOLD)
    ic.ink(lambda d: d.line([(8, 10), (22, 10)], fill=255), GOLD[0])
    ic.ink(lambda d: d.line([(10, 20), (24, 20)], fill=255), GOLD[0])
    ic.part(lambda d: d.ellipse((10, 27, 22, 31), fill=255), DVIOLET)
    return ic.done()


def tab_store():
    ic = Icon()
    ic.part(lambda d: d.rectangle((3, 13, 28, 28), fill=255), BROWN)
    ic.part(lambda d: d.polygon([(3, 13), (5, 6), (26, 6), (28, 13)], fill=255), BROWN)
    ic.ink(lambda d: d.line([(3, 13), (28, 13)], fill=255, width=2), BROWN[0])
    for x in (8, 23):
        ic.ink(lambda d, x=x: d.line([(x, 7), (x, 27)], fill=255), BROWN[0])
    ic.part(lambda d: d.rectangle((13, 11, 18, 18), fill=255), GOLD)
    ic.ink(lambda d: d.point((15, 15), fill=255), OUTLINE)
    return ic.done()


def tab_enchant():
    ic = Icon()
    ic.part(lambda d: d.polygon([(4, 6), (21, 4), (23, 26), (6, 28)], fill=255), VIOLET)
    ic.part(lambda d: d.polygon([(7, 8), (20, 6), (21, 24), (8, 26)], fill=255), PAPER)
    for i in range(5):
        y = 10 + i * 3
        ic.ink(lambda d, y=y, i=i: d.line([(10, y + 1), (17 - (i % 2) * 3, y)], fill=255), PAPER[0])
    sparkle(ic, 26, 6, 3, VIOLET[2])
    sparkle(ic, 28, 22, 2, VIOLET[2])
    return ic.done()


def tab_research():
    ic = Icon()
    ic.part(lambda d: d.polygon([(3, 3), (19, 2), (20, 23), (4, 25)], fill=255), VIOLET)
    ic.part(lambda d: d.polygon([(6, 5), (17, 4), (18, 21), (7, 22)], fill=255), PAPER)
    for i in range(4):
        y = 8 + i * 3
        ic.ink(lambda d, y=y: d.line([(9, y + 1), (15, y)], fill=255), PAPER[0])
    ic.part(lambda d: d.line([(24, 24), (30, 30)], fill=255, width=3), BROWN)
    ic.part(lambda d: d.ellipse((13, 13, 27, 27), fill=255), SILVER)
    ic.part(lambda d: d.ellipse((15, 15, 25, 25), fill=255), (BLUE[1], BLUE[2], WHITE))
    ic.ink(lambda d: d.line([(17, 18), (19, 16)], fill=255), WHITE)
    return ic.done()


ICONS = [
    ('book', book), ('sprout', sprout), ('bridge', bridge), ('spring', spring), ('rift', rift), ('starsight', starsight),
    ('recall', recall), ('dash', dash), ('bolt', bolt), ('form', form), ('curse', curse), ('shield', shield),
    ('tab_anchor', tab_anchor), ('tab_net', tab_net), ('tab_path', tab_path), ('tab_store', tab_store),
    ('tab_enchant', tab_enchant), ('tab_research', tab_research),
]

atlas = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
for i, (name, fn) in enumerate(ICONS):
    atlas.paste(fn(), ((i % 16) * N, (i // 16) * N))
atlas.save(OUT + '/icons.png')

if PREVIEW:
    S = 4
    n = len(ICONS)
    cell = N * S
    pv = Image.new('RGBA', (n * (cell + 8) + 8, 2 * (cell + 8) + 8), (0, 0, 0, 255))
    for row, bg in enumerate([(22, 17, 31, 255), (236, 222, 196, 255)]):
        y0 = 8 + row * (cell + 8)
        for i in range(n):
            tile = Image.new('RGBA', (cell, cell), bg)
            ic = atlas.crop(((i % 16) * N, (i // 16) * N, (i % 16) * N + N, (i // 16) * N + N)).resize((cell, cell), 0)
            tile.paste(ic, (0, 0), ic)
            pv.paste(tile, (8 + i * (cell + 8), y0))
    pv.save(PREVIEW)
print('ok', len(ICONS))
