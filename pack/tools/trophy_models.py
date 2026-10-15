"""3D-модели трофеев Светочей (для подставки) и их палитра.

Запуск: python3 trophy_models.py <корень репозитория>
Пишет assets/chaosshard/models/item/trophy/<герой>.json и textures/items/trophy_palette.png.
Каждая модель — набор коробок; грань окрашена цветом из палитры 16x16 (16 образцов 4x4 с бликом).
"""
import json
import math
import os
import sys

from PIL import Image

PALETTE = [
    # 0..3: сталь светлая, сталь, сталь тёмная, золото
    (232, 228, 220), (184, 178, 168), (126, 120, 110), (242, 196, 58),
    # 4..7: золото тёмное, голубой камень, синяя рукоять, тёмная кожа
    (176, 126, 28), (127, 216, 255), (58, 74, 160), (42, 26, 16),
    # 8..11: посох тёмный, посох, аметист, аметист светлый
    (52, 38, 84), (90, 69, 136), (172, 132, 255), (230, 216, 255),
    # 12..15: дерево, дерево тёмное, рубин, бусины
    (200, 133, 58), (106, 58, 24), (208, 42, 58), (142, 200, 240),
]
STEEL_L, STEEL, STEEL_D, GOLD, GOLD_D, GEM_BLUE, GRIP, LEATHER, STAFF_D, STAFF, AMETH, AMETH_L, WOOD, WOOD_D, RUBY, BEAD = range(16)


def palette_png(path):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for i, (r, g, b) in enumerate(PALETTE):
        ox, oy = (i % 4) * 4, (i // 4) * 4
        for y in range(4):
            for x in range(4):
                k = 1.0
                if x == 0 or y == 0:
                    k = 1.12   # мягкий блик по краю образца
                elif x == 3 or y == 3:
                    k = 0.88
                px[ox + x, oy + y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)
    im.save(path)


def uv(c):
    ox, oy = (c % 4) * 4, (c // 4) * 4
    return [ox + 0.5, oy + 0.5, ox + 3.5, oy + 3.5]


class Model:
    def __init__(self):
        self.el = []

    def box(self, a, b, color, rot=None):
        faces = {f: {"texture": "#p", "uv": uv(color)} for f in ("north", "south", "east", "west", "up", "down")}
        e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": faces}
        if rot:
            e["rotation"] = rot
        self.el.append(e)

    def centered(self, cx, cy, cz, sx, sy, sz, color, rot=None):
        self.box([cx - sx / 2, cy - sy / 2, cz - sz / 2], [cx + sx / 2, cy + sy / 2, cz + sz / 2], color, rot)

    def shift_y(self, dy):
        for e in self.el:
            e["from"][1] = round(e["from"][1] + dy, 3)
            e["to"][1] = round(e["to"][1] + dy, 3)
            if "rotation" in e:
                e["rotation"]["origin"][1] = round(e["rotation"]["origin"][1] + dy, 3)

    def center(self):
        lo = min(e["from"][1] for e in self.el)
        hi = max(e["to"][1] for e in self.el)
        self.shift_y(8 - (lo + hi) / 2)
        return hi - lo

    def json(self):
        return {"textures": {"p": "chaosshard:items/trophy_palette", "particle": "chaosshard:items/trophy_palette"},
                "elements": self.el}


def kael():
    """Меч Каэля: широкий клинок с долом, золотая гарда с голубым камнем, синяя рукоять, навершие."""
    m = Model()
    m.box([6.6, 6, 7.3], [9.4, 20, 8.7], STEEL_L)           # клинок
    m.box([7.6, 6.5, 7.15], [8.4, 18.5, 8.85], STEEL)       # дол
    m.box([6.6, 6, 7.3], [7.1, 20, 8.7], STEEL_D)           # кромки
    m.box([8.9, 6, 7.3], [9.4, 20, 8.7], STEEL_D)
    m.box([7.1, 20, 7.4], [8.9, 21.3, 8.6], STEEL_L)        # остриё
    m.box([7.6, 21.3, 7.6], [8.4, 22.2, 8.4], STEEL)
    m.box([3, 4.6, 7], [13, 6, 9], GOLD)                     # гарда
    m.box([2, 4.2, 6.8], [3.2, 6.6, 9.2], GOLD_D)
    m.box([12.8, 4.2, 6.8], [14, 6.6, 9.2], GOLD_D)
    m.box([7.1, 4.3, 6.6], [8.9, 6.3, 9.4], GEM_BLUE)       # камень
    m.box([7.2, -0.5, 7.2], [8.8, 4.6, 8.8], GRIP)          # рукоять
    m.box([7.1, 0.6, 7.1], [8.9, 1.1, 8.9], GOLD_D)         # обмотка
    m.box([7.1, 2.4, 7.1], [8.9, 2.9, 8.9], GOLD_D)
    m.box([6.7, -2, 6.7], [9.3, -0.5, 9.3], GOLD)           # навершие
    m.box([7.4, -2.6, 7.4], [8.6, -2, 8.6], GEM_BLUE)
    return m


def velmor():
    """Посох Вельмора: тёмное древко с кольцами, когти держат большой аметист."""
    m = Model()
    m.box([7.2, -9, 7.2], [8.8, 15, 8.8], STAFF_D)          # древко
    for y in (-6, 0, 6, 12):
        m.box([6.9, y, 6.9], [9.1, y + 1, 9.1], STAFF)       # кольца
    m.box([6.8, -10, 6.8], [9.2, -9, 9.2], STAFF)            # пятка
    m.box([6.4, 15, 6.4], [9.6, 16.2, 9.6], STAFF)           # чаша
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):        # когти
        cx, cz = 8 + dx * 2.6, 8 + dz * 2.6
        m.centered(cx, 18, cz, 1, 5, 1, STAFF_D)
        m.centered(8 + dx * 2.1, 20.8, 8 + dz * 2.1, 0.8, 1.2, 0.8, STAFF)
    # аметист: ступенчатый ромб
    m.centered(8, 16.8, 8, 2.4, 1.2, 2.4, AMETH)
    m.centered(8, 18.4, 8, 4, 2, 4, AMETH)
    m.centered(8, 20.2, 8, 3.2, 1.6, 3.2, AMETH)
    m.centered(8, 21.6, 8, 2, 1.2, 2, AMETH_L)
    m.centered(8, 22.6, 8, 0.9, 0.8, 0.9, AMETH_L)
    m.centered(8, 18.8, 8, 4.3, 0.8, 1.2, AMETH_L)           # грань-блик
    return m


def lian():
    """Лютня Лиана: округлый корпус с розеткой, грифель, отогнутая головка с колками, струны."""
    m = Model()
    rows = [(0, 1.5, 5, 11), (1.5, 3, 3.5, 12.5), (3, 7.5, 2.5, 13.5), (7.5, 9.5, 3.5, 12.5), (9.5, 11, 5, 11)]
    for y0, y1, x0, x1 in rows:
        m.box([x0, y0, 6], [x1, y1, 10], WOOD)               # корпус
        m.box([x0 + 0.3, y0, 5.6], [x1 - 0.3, y1, 6], WOOD_D)   # спинка
    m.box([6.3, 4.8, 9.9], [9.7, 7.6, 10.15], LEATHER)       # розетка
    m.box([6, 4.5, 9.95], [10, 4.8, 10.1], WOOD_D)
    m.box([6, 7.6, 9.95], [10, 7.9, 10.1], WOOD_D)
    m.box([5.5, 1.8, 10], [10.5, 2.6, 10.6], WOOD_D)         # подставка струн
    m.box([7, 11, 7.4], [9, 22, 9.4], WOOD_D)                # шейка
    m.box([7.2, 11, 9.4], [8.8, 21.5, 9.8], LEATHER)         # грифель
    for i in range(4):
        m.box([7.5, 12.5 + i * 2.5, 9.75], [8.5, 12.8 + i * 2.5, 9.95], GOLD_D)   # лады
    m.box([6.8, 22, 6.6], [9.2, 25.5, 8.8], WOOD_D,
          rot={"origin": [8, 22, 8], "axis": "x", "angle": -22.5})   # головка, отогнута
    for y in (22.8, 24, 25.2):
        m.box([5.6, y, 7.2], [6.8, y + 0.6, 8.0], GOLD, rot={"origin": [8, 22, 8], "axis": "x", "angle": -22.5})
        m.box([9.2, y, 7.2], [10.4, y + 0.6, 8.0], GOLD, rot={"origin": [8, 22, 8], "axis": "x", "angle": -22.5})
    for x in (7.55, 7.95, 8.35):
        m.box([x, 2.2, 9.95], [x + 0.12, 21.8, 10.05], STEEL_L)   # струны
    return m


def elis():
    """Перстень Элис: восьмигранное кольцо, высокий каст с рубином огранки."""
    m = Model()
    r_out, r_in, cy = 5.2, 3.8, 6
    for i in range(8):
        a = i * math.pi / 4
        cx, cyy = 8 + math.cos(a) * (r_out + r_in) / 2, cy + math.sin(a) * (r_out + r_in) / 2
        length = 2 * r_out * math.tan(math.pi / 8) + 0.2
        horiz = abs(math.sin(a)) > 0.9
        vert = abs(math.cos(a)) > 0.9
        if horiz:
            m.centered(cx, cyy, 8, length, r_out - r_in, 2.4, GOLD)
        elif vert:
            m.centered(cx, cyy, 8, r_out - r_in, length, 2.4, GOLD)
        else:
            m.centered(cx, cyy, 8, length, r_out - r_in, 2.4, GOLD,
                       rot={"origin": [cx, cyy, 8], "axis": "z", "angle": -45 if math.sin(a) * math.cos(a) > 0 else 45})
    m.box([5.2, cy + 4.6, 6.4], [10.8, cy + 6.2, 9.6], GOLD_D)   # каст
    m.box([5.6, cy + 6.2, 6.8], [10.4, cy + 7, 9.2], GOLD)
    m.box([6.2, cy + 7, 7], [9.8, cy + 8.8, 9], RUBY)             # рубин
    m.box([6.8, cy + 8.8, 7.4], [9.2, cy + 9.5, 8.6], RUBY)
    m.box([7.4, cy + 9.5, 7.8], [8.6, cy + 9.9, 8.2], STEEL_L)    # блик
    for x in (5.8, 9.6):                                          # лапки
        m.box([x, cy + 6.8, 7.6], [x + 0.6, cy + 8.4, 8.4], GOLD)
    return m


def irena():
    """Чётки Ирены: кольцо бусин (голубые и белые), подвеска с золотым крестом."""
    m = Model()
    n, r, cy = 16, 5.6, 9
    for i in range(n):
        a = math.pi / 2 + i * 2 * math.pi / n
        if abs(math.cos(a - math.pi / 2) + 1) < 0.02:
            continue  # нижняя бусина — там подвеска
        x, y = 8 + math.cos(a) * r, cy + math.sin(a) * r
        size = 1.7 if i % 4 else 2.1
        m.centered(x, y, 8, size, size, size, BEAD if i % 2 else STEEL_L)
    m.centered(8, cy - r - 0.2, 8, 1.4, 1.4, 1.4, GOLD)           # узел
    m.centered(8, cy - r - 1.6, 8, 0.5, 1.6, 0.5, GOLD_D)         # цепочка
    m.centered(8, cy - r - 5.4, 8, 1.6, 6.2, 1.2, GOLD)           # крест: стойка
    m.centered(8, cy - r - 3.8, 8, 4.6, 1.5, 1.2, GOLD)           # перекладина
    m.centered(8, cy - r - 3.8, 8.7, 0.9, 0.9, 0.4, BEAD)         # камень в перекрестье
    return m


HEROES = {"kael": kael, "velmor": velmor, "lian": lian, "elis": elis, "irena": irena}


def main(root):
    res = os.path.join(root, "src/main/resources/assets/chaosshard")
    palette_png(os.path.join(res, "textures/items/trophy_palette.png"))
    out = os.path.join(res, "models/item/trophy")
    os.makedirs(out, exist_ok=True)
    for name, build in HEROES.items():
        m = build()
        h = m.center()
        for e in m.el:
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (name, e)
        with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(m.json(), f, ensure_ascii=False, indent=1)
        print(name, "коробок", len(m.el), "высота", round(h, 1))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "../..")
