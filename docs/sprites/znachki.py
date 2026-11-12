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


# ------------------------------------------------------------------ ритуалы (Гримуар, вкладка «Ритуалы»)

TEAL = ((20, 100, 110), (60, 190, 190), (170, 250, 240))
ORANGE = ((150, 60, 20), (236, 130, 50), (255, 200, 140))
BONE = ((140, 132, 116), (214, 206, 186), (248, 244, 232))
ROT = ((60, 84, 40), (104, 140, 66), (160, 196, 110))
SKY = ((60, 100, 170), (120, 170, 236), (210, 232, 255))


def crystal_at(ic, cx, top, bottom, w, rmp, outlined=False):
    mid = top + (bottom - top) * 0.35
    pts = [(cx, top), (cx + w, mid), (cx + w * 0.8, bottom - (bottom - top) * 0.2), (cx, bottom),
           (cx - w * 0.8, bottom - (bottom - top) * 0.2), (cx - w, mid)]
    draw = lambda d: d.polygon(pts, fill=255)
    if outlined:
        part_outlined(ic, draw, rmp)
    else:
        ic.part(draw, rmp)
    ic.ink(lambda d: d.line([(cx, top + 2), (cx, bottom - 2)], fill=255), rmp[0])


def r_howto():
    ic = Icon()
    ic.ink(lambda d: d.ellipse((3, 3, 28, 28), outline=255, width=2), PAPER[2])
    for i in range(8):
        a = math.radians(i * 45)
        x, y = 16 + 12 * math.cos(a), 16 + 12 * math.sin(a)
        ic.part(lambda d, x=x, y=y: d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=255), VIOLET)
    ic.part(lambda d: d.ellipse((11, 13, 21, 21), fill=255), GOLD)
    ic.ink(lambda d: d.ellipse((13, 14, 19, 17), fill=255), GOLD[0])
    return ic.done()


def r_growth():
    ic = Icon()
    ic.part(lambda d: d.ellipse((3, 25, 29, 31), fill=255), BROWN)
    for x, h in [(9, 12), (16, 6), (23, 12)]:
        ic.part(lambda d, x=x, h=h: d.line([(x, 27), (x, h)], fill=255, width=2), GREEN)
        for k in range(3):
            y = h + 2 + k * 4
            ic.part(lambda d, x=x, y=y: d.ellipse((x - 3, y, x + 3, y + 3), fill=255), GOLD)
    sparkle(ic, 28, 4, 2, GREEN[2])
    return ic.done()


def r_sky():
    ic = Icon()
    ic.part(lambda d: d.ellipse((2, 2, 16, 16), fill=255), GOLD)
    for a in range(0, 360, 45):
        x, y = 9 + 10 * math.cos(math.radians(a)), 9 + 10 * math.sin(math.radians(a))
        ic.ink(lambda d, x=x, y=y: d.point((x, y), fill=255), GOLD[1])
    ic.part(lambda d: (d.ellipse((8, 10, 22, 22), fill=255), d.ellipse((16, 8, 30, 20), fill=255), d.rectangle((10, 16, 29, 22), fill=255)), SILVER)
    for x in (11, 17, 23):
        ic.ink(lambda d, x=x: d.line([(x, 25), (x - 2, 30)], fill=255, width=1), BLUE[1])
    return ic.done()


def r_ward():
    ic = Icon()
    ic.part(lambda d: d.pieslice((2, 4, 30, 44), 180, 360, fill=255), (VIOLET[0], DVIOLET[2], VIOLET[2]))
    ic.part(lambda d: d.pieslice((7, 10, 25, 38), 180, 360, fill=255), DVIOLET, bevel=False)
    ic.part(lambda d: d.rectangle((1, 24, 31, 28), fill=255), BROWN)
    ic.part(lambda d: d.polygon([(16, 13), (20, 18), (16, 23), (12, 18)], fill=255), VIOLET)
    return ic.done()


def r_storm():
    ic = Icon()
    ic.part(lambda d: (d.ellipse((3, 3, 17, 15), fill=255), d.ellipse((11, 1, 29, 15), fill=255), d.rectangle((5, 9, 28, 15), fill=255)), DVIOLET)
    ic.part(lambda d: d.polygon([(17, 13), (11, 22), (16, 22), (12, 31), (22, 19), (17, 19), (21, 13)], fill=255), (VIOLET[1], VIOLET[2], WHITE))
    sparkle(ic, 5, 24, 1, VIOLET[2])
    sparkle(ic, 27, 25, 2, VIOLET[2])
    return ic.done()


def r_summon():
    ic = Icon()
    ic.part(lambda d: d.ellipse((1, 6, 31, 30), fill=255), DVIOLET, bevel=False)
    for (x, y) in [(8, 13), (20, 11), (13, 21)]:
        ic.ink(lambda d, x=x, y=y: (d.rectangle((x, y, x + 2, y + 1), fill=255), d.rectangle((x + 5, y, x + 7, y + 1), fill=255)), RED[2])
    ic.part(lambda d: d.polygon([(6, 8), (3, 1), (10, 6)], fill=255), SILVER)
    ic.part(lambda d: d.polygon([(26, 8), (29, 1), (22, 6)], fill=255), SILVER)
    return ic.done()


def r_corruption():
    ic = Icon()
    for r in (14, 10):
        ic.ink(lambda d, r=r: d.arc((16 - r, 24 - r * 0.5, 16 + r, 24 + r * 0.5), 0, 360, fill=255, width=1), VIOLET[1] if r == 14 else VIOLET[2])
    crystal_at(ic, 16, 2, 24, 6, VIOLET, outlined=True)
    sparkle(ic, 4, 8, 1, VIOLET[2])
    sparkle(ic, 28, 12, 2, VIOLET[2])
    return ic.done()


def r_dusk():
    ic = Icon()
    ic.part(lambda d: d.pieslice((4, 8, 28, 32), 180, 360, fill=255), ORANGE)
    ic.part(lambda d: d.rectangle((1, 20, 31, 23), fill=255), DVIOLET)
    for y, w in [(26, 22), (29, 14)]:
        ic.ink(lambda d, y=y, w=w: d.line([(16 - w // 2, y), (16 + w // 2, y)], fill=255), VIOLET[1])
    sparkle(ic, 5, 4, 1, GOLD[2])
    sparkle(ic, 27, 3, 2, GOLD[2])
    return ic.done()


def heart(d, x, y, s):
    d.ellipse((x, y, x + s, y + s), fill=255)
    d.ellipse((x + s - 1, y, x + 2 * s - 1, y + s), fill=255)
    d.polygon([(x, y + s * 0.6), (x + s - 0.5, y + 2 * s), (x + 2 * s - 1, y + s * 0.6)], fill=255)


def r_breed():
    ic = Icon()
    ic.part(lambda d: heart(d, 2, 4, 9), (RED[0], (236, 110, 150), (255, 190, 210)))
    part_outlined(ic, lambda d: heart(d, 13, 12, 8), (RED[0], (236, 110, 150), (255, 190, 210)))
    sparkle(ic, 27, 5, 2, (255, 190, 210))
    return ic.done()


def r_pull():
    ic = Icon()
    ic.part(lambda d: d.arc((4, 2, 28, 26), 0, 180, fill=255, width=6), RED)
    ic.part(lambda d: (d.rectangle((4, 4, 9, 14), fill=255), d.rectangle((23, 4, 28, 14), fill=255)), RED)
    ic.part(lambda d: (d.rectangle((4, 0, 9, 4), fill=255), d.rectangle((23, 0, 28, 4), fill=255)), SILVER)
    for (x, y) in [(10, 30), (16, 27), (22, 30)]:
        ic.part(lambda d, x=x, y=y: d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=255), GREEN)
    return ic.done()


def r_sight():
    ic = Icon()
    for r in (14, 10, 6):
        ic.ink(lambda d, r=r: d.ellipse((16 - r, 16 - r, 16 + r, 16 + r), outline=255, width=1), TEAL[1])
    ic.ink(lambda d: d.line([(16, 16), (27, 6)], fill=255, width=2), TEAL[2])
    for (x, y) in [(8, 10), (23, 21), (11, 24)]:
        ic.part(lambda d, x=x, y=y: d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=255), RED)
    ic.part(lambda d: d.ellipse((14, 14, 18, 18), fill=255), TEAL)
    return ic.done()


def paw(d, x, y):
    d.ellipse((x + 3, y + 8, x + 13, y + 16), fill=255)
    for (dx, dy) in [(0, 3), (4, 0), (9, 0), (13, 3)]:
        d.ellipse((x + dx, y + dy, x + dx + 3, y + dy + 4), fill=255)


def r_recall():
    ic = Icon()
    ic.part(lambda d: paw(d, 2, 12), GOLD)
    ic.part(lambda d: d.arc((12, 2, 30, 20), 270, 540, fill=255, width=2), VIOLET)
    ic.part(lambda d: d.polygon([(19, 0), (23, 3), (19, 6)], fill=255), VIOLET)
    return ic.done()


def r_blood_moon():
    ic = Icon()
    ic.part(lambda d: d.ellipse((4, 2, 28, 26), fill=255), RED)
    ic.ink(lambda d: d.ellipse((9, 8, 13, 12), fill=255), RED[0])
    ic.ink(lambda d: d.ellipse((17, 15, 22, 19), fill=255), RED[0])
    for x, h in [(11, 30), (18, 31), (24, 28)]:
        ic.part(lambda d, x=x, h=h: d.line([(x, 24), (x, h)], fill=255, width=2), RED)
    return ic.done()


def r_great_surge():
    ic = Icon()
    pts = []
    for i in range(16):
        a = math.radians(i * 22.5 - 90)
        r = 15 if i % 2 == 0 else 7
        pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
    ic.part(lambda d: d.polygon(pts, fill=255), VIOLET)
    crystal_at(ic, 16, 8, 24, 4, (VIOLET[1], VIOLET[2], WHITE), outlined=True)
    return ic.done()


def r_challenge():
    ic = Icon()
    ic.part(lambda d: d.line([(4, 4), (26, 26)], fill=255, width=3), SILVER)
    ic.part(lambda d: d.line([(28, 4), (6, 26)], fill=255, width=3), SILVER)
    ic.part(lambda d: (d.line([(20, 25), (26, 19)], fill=255, width=2), d.line([(6, 19), (12, 25)], fill=255, width=2)), GOLD)
    ic.part(lambda d: (d.ellipse((25, 25, 30, 30), fill=255), d.ellipse((2, 25, 7, 30), fill=255)), RED)
    return ic.done()


def omen(sym, rmp):
    """Знамение: опора (колонна) с символом героя над ней."""
    def make():
        ic = Icon()
        ic.part(lambda d: d.rectangle((11, 14, 21, 29), fill=255), SILVER)
        ic.part(lambda d: d.rectangle((8, 27, 24, 31), fill=255), SILVER)
        ic.part(lambda d: d.rectangle((9, 12, 23, 15), fill=255), SILVER)
        for x in (14, 18):
            ic.ink(lambda d, x=x: d.line([(x, 16), (x, 26)], fill=255), SILVER[0])
        sym(ic, rmp)
        return ic.done()
    return make


def sym_blade(ic, rmp):
    ic.part(lambda d: d.polygon([(16, 0), (18, 3), (18, 9), (14, 9), (14, 3)], fill=255), rmp)
    ic.part(lambda d: d.rectangle((11, 9, 21, 10), fill=255), rmp)


def sym_ring(ic, rmp):
    ic.part(lambda d: d.ellipse((10, 0, 22, 12), fill=255), rmp)
    ic.ink(lambda d: d.ellipse((13, 3, 19, 9), fill=255), (0, 0, 0))
    ic.img.putpixel((16, 6), (0, 0, 0, 0))
    m = ic.img.load()
    for y in range(4, 9):
        for x in range(14, 19):
            if m[x, y][:3] == (0, 0, 0):
                m[x, y] = (0, 0, 0, 0)


def sym_seal(ic, rmp):
    ic.part(lambda d: d.polygon([(16, 0), (22, 5), (16, 11), (10, 5)], fill=255), rmp)
    ic.ink(lambda d: d.point((16, 5), fill=255), WHITE)


def sym_lute(ic, rmp):
    ic.part(lambda d: d.ellipse((10, 3, 20, 11), fill=255), rmp)
    ic.part(lambda d: d.line([(19, 5), (25, 0)], fill=255, width=2), rmp)
    ic.ink(lambda d: d.ellipse((14, 6, 16, 8), fill=255), rmp[0])


def sym_star(ic, rmp):
    pts = []
    for i in range(10):
        a = math.radians(i * 36 - 90)
        r = 6 if i % 2 == 0 else 2.5
        pts.append((16 + r * math.cos(a), 6 + r * math.sin(a)))
    ic.part(lambda d: d.polygon(pts, fill=255), rmp)


def r_holy_defile():
    ic = Icon()
    ic.part(lambda d: d.polygon([(16, 2), (24, 16), (24, 22), (20, 28), (12, 28), (8, 22), (8, 16)], fill=255), SKY)
    for (x, y) in [(13, 18), (19, 22), (17, 14), (11, 24)]:
        ic.ink(lambda d, x=x, y=y: d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=255), VIOLET[0])
    sparkle(ic, 26, 6, 2, VIOLET[2])
    return ic.done()


def r_fallen_feather():
    ic = Icon()
    ic.part(lambda d: d.polygon([(26, 2), (29, 6), (20, 20), (9, 28), (5, 27), (12, 17)], fill=255), (SILVER[1], SILVER[2], WHITE))
    ic.part(lambda d: d.polygon([(12, 17), (20, 20), (9, 28), (5, 27)], fill=255), VIOLET)
    ic.ink(lambda d: d.line([(27, 4), (4, 29)], fill=255), SILVER[0])
    for (x, y) in [(22, 24), (26, 19)]:
        sparkle(ic, x, y, 1, VIOLET[2])
    return ic.done()


def r_scent():
    ic = Icon()
    for k, y0 in enumerate((8, 16, 24)):
        pts = [(2 + x, y0 + 2.5 * math.sin((x + k * 3) / 3.2)) for x in range(0, 21)]
        ic.part(lambda d, pts=pts: d.line(pts, fill=255, width=2), TEAL)
    ic.part(lambda d: d.polygon([(22, 9), (31, 16), (22, 23)], fill=255), GOLD)
    return ic.done()


def r_omen_crown():
    ic = Icon()
    ic.part(lambda d: d.polygon([(3, 26), (3, 10), (9, 17), (16, 5), (23, 17), (29, 10), (29, 26)], fill=255), GOLD)
    ic.part(lambda d: d.rectangle((3, 24, 29, 28), fill=255), GOLD)
    ic.part(lambda d: d.polygon([(16, 15), (19, 19), (16, 23), (13, 19)], fill=255), VIOLET)
    for x in (3, 16, 29):
        ic.ink(lambda d, x=x: d.point((x, 9 if x != 16 else 4), fill=255), WHITE)
    return ic.done()


def r_spell_reset():
    ic = Icon()
    ic.part(lambda d: d.polygon([(6, 5), (22, 3), (24, 27), (8, 29)], fill=255), PAPER)
    ic.part(lambda d: d.arc((14, 12, 30, 28), 200, 470, fill=255, width=2), VIOLET)
    ic.part(lambda d: d.polygon([(14, 16), (19, 18), (15, 22)], fill=255), VIOLET)
    sparkle(ic, 11, 10, 2, VIOLET[2])
    return ic.done()


def r_material_temper():
    ic = Icon()
    ic.part(lambda d: d.polygon([(3, 12), (27, 12), (29, 15), (21, 17), (21, 22), (25, 27), (7, 27), (11, 22), (11, 17), (6, 16)], fill=255), SILVER)
    for (x, y, r) in [(9, 5, 2), (18, 3, 2), (25, 7, 1), (14, 8, 1)]:
        sparkle(ic, x, y, r, ORANGE[1], GOLD[2])
    return ic.done()


def r_anchor_reshape():
    ic = Icon()
    crystal_at(ic, 16, 6, 26, 5, VIOLET)
    ic.part(lambda d: d.arc((3, 3, 29, 29), 200, 330, fill=255, width=2), GOLD)
    ic.part(lambda d: d.arc((3, 3, 29, 29), 20, 150, fill=255, width=2), GOLD)
    ic.part(lambda d: d.polygon([(27, 8), (30, 14), (24, 13)], fill=255), GOLD)
    ic.part(lambda d: d.polygon([(5, 24), (2, 18), (8, 19)], fill=255), GOLD)
    return ic.done()


def r_anchor_alpha():
    ic = Icon()
    crystal_at(ic, 16, 9, 30, 6, VIOLET)
    ic.part(lambda d: d.polygon([(8, 7), (8, 2), (12, 5), (16, 0), (20, 5), (24, 2), (24, 7)], fill=255), GOLD)
    sparkle(ic, 4, 16, 1, GOLD[2])
    sparkle(ic, 28, 18, 2, GOLD[2])
    return ic.done()


def r_reclaim():
    ic = Icon()
    ic.part(lambda d: (d.ellipse((6, 6, 26, 24), fill=255), d.rectangle((6, 15, 26, 30), fill=255)), SILVER)
    ic.ink(lambda d: (d.line([(16, 11), (16, 24)], fill=255, width=2), d.line([(11, 15), (21, 15)], fill=255, width=2)), SILVER[0])
    ic.part(lambda d: d.polygon([(23, 1), (29, 7), (26, 7), (26, 12), (20, 12), (20, 7), (17, 7)], fill=255), GREEN)
    return ic.done()


# ------------------------------------------------------------------ призывы (вкладка «Слуги»)

def s_husk():
    ic = Icon()
    ic.part(lambda d: d.rounded_rectangle((6, 4, 26, 26), radius=4, fill=255), ROT)
    ic.ink(lambda d: (d.rectangle((10, 12, 13, 14), fill=255), d.rectangle((19, 12, 22, 14), fill=255)), VIOLET[2])
    ic.ink(lambda d: d.line([(11, 20), (21, 21)], fill=255), ROT[0])
    ic.ink(lambda d: (d.point((13, 21), fill=255), d.point((17, 21), fill=255)), BONE[2])
    ic.part(lambda d: d.rectangle((10, 26, 22, 30), fill=255), BROWN)
    ic.ink(lambda d: d.line([(8, 6), (12, 9)], fill=255), ROT[0])
    return ic.done()


def s_bone():
    ic = Icon()
    ic.part(lambda d: d.ellipse((6, 2, 26, 22), fill=255), BONE)
    ic.part(lambda d: d.rectangle((10, 17, 22, 26), fill=255), BONE)
    ic.ink(lambda d: (d.ellipse((9, 9, 14, 14), fill=255), d.ellipse((18, 9, 23, 14), fill=255)), (30, 16, 40))
    ic.ink(lambda d: (d.point((11, 11), fill=255), d.point((20, 11), fill=255)), VIOLET[2])
    for x in (12, 15, 18, 21):
        ic.ink(lambda d, x=x: d.line([(x, 22), (x, 26)], fill=255), BONE[0])
    return ic.done()


def s_executioner():
    ic = Icon()
    ic.part(lambda d: d.line([(8, 30), (22, 6)], fill=255, width=3), BROWN)
    part_outlined(ic, lambda d: d.polygon([(16, 4), (28, 2), (31, 12), (26, 20), (20, 14)], fill=255), SILVER)
    ic.ink(lambda d: d.line([(29, 4), (30, 12)], fill=255), WHITE)
    ic.part(lambda d: d.rectangle((14, 16, 18, 18), fill=255), VIOLET)
    return ic.done()


def s_reaper():
    ic = Icon()
    ic.part(lambda d: d.line([(6, 30), (20, 3)], fill=255, width=2), BROWN)
    part_outlined(ic, lambda d: d.polygon([(19, 2), (29, 4), (31, 9), (26, 6), (18, 6)], fill=255), SILVER)
    for (x, y) in [(4, 18), (8, 22), (6, 14)]:
        ic.part(lambda d, x=x, y=y: d.ellipse((x - 2, y - 1, x + 2, y + 2), fill=255), GOLD)
    return ic.done()


def s_lumberjack():
    ic = Icon()
    ic.part(lambda d: d.ellipse((1, 19, 27, 31), fill=255), BROWN)
    ic.ink(lambda d: d.ellipse((5, 22, 15, 28), outline=255), BROWN[2])
    ic.ink(lambda d: d.ellipse((8, 24, 12, 26), fill=255), BROWN[0])
    part_outlined(ic, lambda d: d.line([(18, 24), (27, 3)], fill=255, width=3), BROWN)
    part_outlined(ic, lambda d: d.polygon([(25, 2), (12, 0), (10, 11), (16, 14), (23, 9)], fill=255), SILVER)
    ic.ink(lambda d: d.line([(11, 2), (10, 10)], fill=255), WHITE)
    return ic.done()


def s_miner():
    ic = Icon()
    ic.part(lambda d: d.line([(6, 28), (22, 10)], fill=255, width=3), BROWN)
    part_outlined(ic, lambda d: d.arc((4, 2, 30, 24), 200, 340, fill=255, width=4), SILVER)
    sparkle(ic, 6, 10, 1, GOLD[2])
    sparkle(ic, 26, 22, 2, GOLD[2])
    return ic.done()


def s_hare():
    ic = Icon()
    ic.part(lambda d: (d.ellipse((7, 1, 13, 16), fill=255), d.ellipse((17, 1, 23, 16), fill=255)), (BROWN[1], BROWN[2], (230, 200, 160)))
    ic.ink(lambda d: (d.ellipse((9, 4, 11, 13), fill=255), d.ellipse((19, 4, 21, 13), fill=255)), (230, 160, 170))
    part_outlined(ic, lambda d: d.ellipse((5, 12, 25, 29), fill=255), (BROWN[1], BROWN[2], (230, 200, 160)))
    ic.ink(lambda d: (d.rectangle((10, 18, 11, 19), fill=255), d.rectangle((19, 18, 20, 19), fill=255)), VIOLET[1])
    ic.ink(lambda d: d.point((15, 23), fill=255), (230, 160, 170))
    return ic.done()


def s_cat():
    ic = Icon()
    ic.part(lambda d: (d.polygon([(5, 14), (6, 2), (14, 9)], fill=255), d.polygon([(27, 14), (26, 2), (18, 9)], fill=255)), DVIOLET)
    part_outlined(ic, lambda d: d.ellipse((4, 7, 28, 29), fill=255), (DVIOLET[0], (60, 40, 80), (110, 80, 140)))
    ic.ink(lambda d: (d.ellipse((9, 14, 13, 18), fill=255), d.ellipse((19, 14, 23, 18), fill=255)), GOLD[1])
    ic.ink(lambda d: (d.line([(11, 14), (11, 18)], fill=255), d.line([(21, 14), (21, 18)], fill=255)), OUTLINE)
    ic.ink(lambda d: d.point((16, 21), fill=255), (230, 160, 170))
    ic.ink(lambda d: (d.line([(2, 21), (9, 22)], fill=255), d.line([(23, 22), (30, 21)], fill=255)), SILVER[2])
    return ic.done()


def s_tiger():
    ic = Icon()
    ic.part(lambda d: (d.ellipse((3, 3, 11, 11), fill=255), d.ellipse((21, 3, 29, 11), fill=255)), ORANGE)
    part_outlined(ic, lambda d: d.ellipse((3, 5, 29, 29), fill=255), ORANGE)
    for pts in ([(16, 6), (16, 11)], [(10, 7), (12, 12)], [(22, 7), (20, 12)], [(4, 16), (8, 17)], [(28, 16), (24, 17)]):
        ic.ink(lambda d, pts=pts: d.line(pts, fill=255, width=2), (40, 20, 30))
    ic.ink(lambda d: (d.rectangle((9, 15, 12, 17), fill=255), d.rectangle((20, 15, 23, 17), fill=255)), VIOLET[2])
    ic.part(lambda d: d.ellipse((10, 19, 22, 28), fill=255), BONE)
    ic.ink(lambda d: d.polygon([(14, 20), (18, 20), (16, 22)], fill=255), (40, 20, 30))
    return ic.done()


ICONS = [
    ('book', book), ('sprout', sprout), ('bridge', bridge), ('spring', spring), ('rift', rift), ('starsight', starsight),
    ('recall', recall), ('dash', dash), ('bolt', bolt), ('form', form), ('curse', curse), ('shield', shield),
    ('tab_anchor', tab_anchor), ('tab_net', tab_net), ('tab_path', tab_path), ('tab_store', tab_store),
    ('tab_enchant', tab_enchant), ('tab_research', tab_research),
    # 18..46 — ритуалы (порядок знает ChaosUi.ritualIcon)
    ('r_howto', r_howto), ('r_growth', r_growth), ('r_sky', r_sky), ('r_ward', r_ward), ('r_storm', r_storm),
    ('r_summon', r_summon), ('r_corruption', r_corruption), ('r_dusk', r_dusk), ('r_breed', r_breed), ('r_pull', r_pull),
    ('r_sight', r_sight), ('r_recall', r_recall), ('r_blood_moon', r_blood_moon), ('r_great_surge', r_great_surge),
    ('r_challenge', r_challenge),
    ('r_omen_kael', omen(sym_blade, GOLD)), ('r_omen_irena', omen(sym_ring, SKY)), ('r_omen_velmor', omen(sym_seal, GREEN)),
    ('r_omen_lian', omen(sym_lute, TEAL)), ('r_omen_elis', omen(sym_star, (RED[0], (236, 110, 150), (255, 190, 210)))),
    ('r_holy_defile', r_holy_defile), ('r_fallen_feather', r_fallen_feather), ('r_scent', r_scent), ('r_omen_crown', r_omen_crown),
    ('r_spell_reset', r_spell_reset), ('r_material_temper', r_material_temper), ('r_anchor_reshape', r_anchor_reshape),
    ('r_anchor_alpha', r_anchor_alpha), ('r_reclaim', r_reclaim),
    # 47..55 — призывы: слуги и звери
    ('s_husk', s_husk), ('s_bone', s_bone), ('s_executioner', s_executioner), ('s_reaper', s_reaper),
    ('s_lumberjack', s_lumberjack), ('s_miner', s_miner), ('s_hare', s_hare), ('s_cat', s_cat), ('s_tiger', s_tiger),
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
