"""Торговец: скин 64x64 (развёртка игрока, со вторым слоем), рюкзак 64x32 и Колокольчик 16x16.

Мрачный странник: глубокий капюшон, лицо в тени, светятся только глаза, нижняя половина лица под шарфом.
Тёмный плащ с рваным подолом, лямки рюкзака через грудь, пояс с латунной пряжкой, кожаные наручи и сапоги.
Рюкзак: потёртая кожа со стёжкой, скатка из шерсти сверху, карман и закопчённый котелок сбоку.

python3 torgovets.py <папка textures/entity/merchant> <путь к items/merchant_bell.png>
"""
import random
import sys
from PIL import Image

OUT_DIR, BELL_OUT = sys.argv[1], sys.argv[2]
random.seed(7)


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


T = (0, 0, 0, 0)
CLOTH = [hx(c) for c in ('15121a', '1f1a26', '2a2432', '362e40')]     # плащ: тень → блик
HOOD_IN = hx('08070b')
SKIN = [hx(c) for c in ('6e625c', '8e8078', 'a89a90')]
EYE, EYE_HOT = hx('d07a1c'), hx('ffc25a')
SCARF = [hx(c) for c in ('2a1a1c', '3a2427', '4a2e31')]
LEATHER = [hx(c) for c in ('2a1c12', '3e2a1a', '5a3d26', '72502f')]
BRASS = [hx(c) for c in ('6a4e1e', 'a07a30', 'd8ad52')]
TROUSER = [hx(c) for c in ('1c1a1c', '262326', '302c2f')]
BOOT = [hx(c) for c in ('140f0c', '1f1813', '2c221b')]


def noisy(pal, i, j, base=1, k=0.35):
    """Цвет ткани с лёгким шумом и складками."""
    v = base + (random.random() - 0.5) * 2 * k
    return pal[max(0, min(len(pal) - 1, int(round(v))))]


def fill(img, x, y, w, h, fn):
    px = img.load()
    for j in range(h):
        for i in range(w):
            c = fn(i, j)
            if c is not None:
                px[x + i, y + j] = c


def box(img, u, v, w, h, d, faces):
    """Развёртка коробки как в ModelRenderer: faces — dict side → fn(i, j) (i, j внутри грани)."""
    rects = {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }
    for side, (x, y, fw, fh) in rects.items():
        fn = faces.get(side, faces.get('*'))
        if fn is not None:
            fill(img, x, y, fw, fh, fn)


skin = Image.new('RGBA', (64, 64), T)


def cloth(i, j):
    # вертикальные складки: каждый 3-й столбец темнее
    base = 1.2 - (0.6 if i % 3 == 0 else 0) + (0.5 if i % 3 == 1 else 0)
    return noisy(CLOTH, i, j, base, 0.3)


# ---- голова: под капюшоном всё в тени, видны глаза и шарф
def face(i, j):
    if j <= 2:
        return HOOD_IN if j < 2 else hx('14111a')
    if j == 3:
        if i in (1, 5):
            return EYE
        if i in (2, 6):
            return EYE_HOT
        return SKIN[0]
    if j == 4:
        return SKIN[1] if 2 <= i <= 5 else SKIN[0]
    return noisy(SCARF, i, j, 1.4 if j == 5 else 1.0, 0.3) if j >= 5 else SKIN[0]


box(skin, 0, 0, 8, 8, 8, {'front': face, '*': lambda i, j: HOOD_IN})


# второй слой головы: капюшон
def hood_front(i, j):
    if j == 0 or i == 0 or i == 7:
        return noisy(CLOTH, i, j, 2.2 if j == 0 else 1.6, 0.3)
    if j == 1 and i in (1, 6):
        return CLOTH[1]
    return T


def hood(i, j):
    # шов посередине и складки к затылку
    if i == 3 or i == 4:
        return CLOTH[1] if j % 2 else CLOTH[2]
    return noisy(CLOTH, i, j, 2.0 - j * 0.12, 0.35)


box(skin, 32, 0, 8, 8, 8, {'front': hood_front, 'top': hood, 'left': hood, 'right': hood, 'back': hood, 'bottom': lambda i, j: T})


# ---- тело: туника, лямки рюкзака крест-накрест, пояс с пряжкой и подсумок
def body_front(i, j):
    if j in (8, 9):
        if i in (3, 4):
            return BRASS[2] if j == 8 else BRASS[1]
        return LEATHER[2] if j == 8 else LEATHER[1]
    # лямки: от плеч вниз к поясу
    if j < 8 and (i == 1 + j // 4 or i == 6 - j // 4):
        return LEATHER[3] if j % 3 == 0 else LEATHER[2]
    if j == 3 and i in (1, 6):
        return BRASS[1]
    if j >= 10 and i in (6, 7):
        return LEATHER[2] if j == 10 else LEATHER[1]   # подсумок справа
    return cloth(i, j)


def body_back(i, j):
    if j in (8, 9):
        return LEATHER[1]
    if j < 8 and (i == 1 or i == 6):
        return LEATHER[2]
    return cloth(i, j)


box(skin, 16, 16, 8, 12, 4, {'front': body_front, 'back': body_back,
                             'left': lambda i, j: LEATHER[1] if j in (8, 9) else cloth(i, j),
                             'right': lambda i, j: LEATHER[1] if j in (8, 9) else cloth(i, j),
                             'top': lambda i, j: CLOTH[2], 'bottom': lambda i, j: CLOTH[0]})


# второй слой тела: плащ — воротник спереди, полы по бокам и сзади, подол рваный
def ragged(i, j, h=12):
    return j == h - 1 and random.random() < 0.45


def cloak_front(i, j):
    if j <= 1 and i not in (3, 4):
        return noisy(CLOTH, i, j, 2.4, 0.3)          # воротник
    if i in (0, 7) and not ragged(i, j):
        return noisy(CLOTH, i, j, 1.8, 0.3)           # полы плаща распахнуты
    return T


def cloak(i, j):
    if ragged(i, j):
        return T
    return noisy(CLOTH, i, j, 1.9 - (0.7 if i % 2 else 0), 0.3)


box(skin, 16, 32, 8, 12, 4, {'front': cloak_front, 'back': cloak, 'left': cloak, 'right': cloak,
                             'top': lambda i, j: CLOTH[3], 'bottom': lambda i, j: T})


# ---- руки: рукава, кожаные наручи, перчатки
def arm(i, j):
    if 6 <= j <= 8:
        if j == 7 and i % 2 == 0:
            return BRASS[0]
        return LEATHER[2] if j != 8 else LEATHER[1]
    if j >= 10:
        return LEATHER[0] if j == 11 else LEATHER[1]
    return cloth(i, j)


for u, v in ((40, 16), (32, 48)):
    box(skin, u, v, 4, 12, 4, {'*': arm, 'top': lambda i, j: CLOTH[2], 'bottom': lambda i, j: LEATHER[0]})


def sleeve(i, j):
    if j <= 3 and not (j == 3 and random.random() < 0.5):
        return noisy(CLOTH, i, j, 2.2, 0.3)          # наплечье плаща
    return T


for u, v in ((40, 32), (48, 48)):
    box(skin, u, v, 4, 12, 4, {'*': sleeve, 'top': lambda i, j: CLOTH[3], 'bottom': lambda i, j: T})


# ---- ноги: штаны и высокие сапоги с отворотом
def leg(i, j):
    if j == 6:
        return BOOT[2]
    if j > 6:
        return BOOT[0] if j == 11 else noisy(BOOT, i, j, 1.2, 0.3)
    return noisy(TROUSER, i, j, 1.0 + (0.5 if i % 2 else 0), 0.25)


for u, v in ((0, 16), (16, 48)):
    box(skin, u, v, 4, 12, 4, {'*': leg, 'top': lambda i, j: TROUSER[1], 'bottom': lambda i, j: BOOT[0]})


def hem(i, j):
    # подол плаща свисает на бёдра
    if j <= 2 and not (j == 2 and random.random() < 0.6):
        return noisy(CLOTH, i, j, 1.6, 0.3)
    return T


for u, v in ((0, 32), (0, 48)):
    box(skin, u, v, 4, 12, 4, {'*': hem, 'top': lambda i, j: T, 'bottom': lambda i, j: T})

skin.save(f'{OUT_DIR}/merchant.png')

# ---- рюкзак 64x32
pack = Image.new('RGBA', (64, 32), T)


def leather(i, j, w, h):
    edge = i == 0 or j == 0 or i == w - 1 or j == h - 1
    if edge:
        return LEATHER[0]
    if (i == 1 or i == w - 2 or j == 1 or j == h - 2) and (i + j) % 2 == 0:
        return LEATHER[3]                                 # стёжка по краю
    return noisy(LEATHER, i, j, 1.6, 0.4)


def pack_back(i, j):
    # клапан сверху, две пряжки и потёртости
    if j <= 3:
        if j == 3:
            return LEATHER[0]
        return noisy(LEATHER, i, j, 2.3, 0.35)
    if i in (2, 5) and 3 <= j <= 6:
        return BRASS[2] if j == 5 else LEATHER[0]
    return leather(i, j, 8, 10)


box(pack, 0, 0, 8, 10, 4, {'back': pack_back, 'front': lambda i, j: LEATHER[0],
                           '*': lambda i, j: leather(i, j, 4, 10), 'top': lambda i, j: leather(i, j, 8, 4),
                           'bottom': lambda i, j: LEATHER[0]})

WOOL = [hx(c) for c in ('2a1616', '3e2020', '552c2a', '6a3a36')]


def roll(i, j):
    if i in (1, 7):
        return LEATHER[0]                                 # ремешки скатки
    return WOOL[(i + j) % 2 + 1] if j % 2 == 0 else noisy(WOOL, i, j, 1.5, 0.4)


box(pack, 0, 14, 9, 3, 3, {'*': roll, 'right': lambda i, j: WOOL[2] if (i + j) % 2 else WOOL[1],
                           'left': lambda i, j: WOOL[2] if (i + j) % 2 else WOOL[1]})


def pocket(i, j):
    if j == 0:
        return LEATHER[3]
    if i == 2 or i == 3:
        return BRASS[1] if j == 1 else leather(i, j, 6, 5)
    return leather(i, j, 6, 5)


box(pack, 24, 0, 6, 5, 2, {'back': pocket, '*': lambda i, j: LEATHER[1]})

IRON = [hx(c) for c in ('141416', '24242a', '3a3a42', '55555e')]
box(pack, 40, 0, 2, 3, 2, {'*': lambda i, j: IRON[0] if j == 2 else IRON[2 - (i + j) % 2], 'top': lambda i, j: IRON[0]})
pack.save(f'{OUT_DIR}/backpack.png')

# ---- Колокольчик 16x16: латунный колокол на кожаном шнурке, тёмный язычок
bell = Image.new('RGBA', (16, 16), T)
p = bell.load()
OUT = hx('1a1008')
for y in range(0, 4):
    p[7, y] = LEATHER[2] if y % 2 else LEATHER[1]
    p[8, y] = LEATHER[1]
p[6, 1] = LEATHER[2]
p[9, 2] = LEATHER[2]
# купол колокола: ширина растёт книзу
widths = {4: 2, 5: 4, 6: 4, 7: 6, 8: 6, 9: 6, 10: 8, 11: 8, 12: 10}
for y, w in widths.items():
    x0 = 8 - w // 2
    for x in range(x0, x0 + w):
        k = (x - x0) / max(1, w - 1)
        c = BRASS[2] if k < 0.3 else BRASS[1] if k < 0.75 else BRASS[0]
        p[x, y] = c
    p[x0 - 1, y] = OUT
    p[x0 + w, y] = OUT
for x in range(6, 10):
    p[x, 3] = OUT if x in (6, 9) else p[x, 3]
for x in range(2, 14):
    p[x, 13] = OUT
p[4, 6], p[5, 9] = hx('fff0b0'), hx('f0d488')           # блики
p[7, 13], p[8, 13], p[7, 14], p[8, 14] = IRON[2], IRON[1], IRON[1], IRON[0]   # язычок
bell.save(BELL_OUT)
print('ok')
