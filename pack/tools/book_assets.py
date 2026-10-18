#!/usr/bin/env python3
"""Ассеты Гримуара-книги (замечание №26).

Генерирует:
  - textures/gui/book/font_<лицо>.png + font_<лицо>.json — атласы гладкого шрифта
    (Alegreya обычный/жирный/курсив — основной текст, Bad Script — рукописные пометки),
    символов нет в шрифте — берутся из DejaVu Sans;
  - textures/gui/book/book.png — разворот (переплёт, тёмный пергамент, корешок, уголки) и спрайты
    (закладки-ленты, сургучная печать, ленточка у выбранного пункта, ромбик разделителя);
  - sounds/book/page1..3.ogg — шелест страницы (синтез: шум с огибающей, без чужих записей).

Шрифты — SIL Open Font License 1.1 (pack/tools/fonts/OFL-*.txt, копия кладётся рядом с атласами).
Запуск: python3 pack/tools/book_assets.py
"""
import json
import math
import os
import random
import shutil
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/chaosshard')
GUI = os.path.join(ASSETS, 'textures/gui/book')
SOUNDS = os.path.join(ASSETS, 'sounds/book')
FONTS = os.path.join(HERE, 'fonts')
FALLBACK = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

# книга в «пикселях интерфейса»
W, H = 440, 272
PL, PT, PR, PB = 10, 9, W - 10, H - 9
MID = W // 2

# ---------------------------------------------------------------------------------------- шрифты

SCALE = 4          # пикселей атласа на пиксель интерфейса
CHARS = [chr(c) for c in range(32, 127)] + [chr(c) for c in range(0x400, 0x460)] + list(
    '«»—–…×·•№°’‘“”„←→↑↓↘↗▲▼✓✔★✦✧❖‸§©')
FACES = {
    'regular': ('alegreya-400.ttf', 36),
    'bold': ('alegreya-700.ttf', 36),
    'italic': ('alegreya-400-italic.ttf', 36),
    'script': ('bad-script-400.ttf', 34),
}


def build_font(name, file, size):
    font = ImageFont.truetype(os.path.join(FONTS, file), size)
    fb = ImageFont.truetype(FALLBACK, int(size * 0.85))
    cmap = set()
    from fontTools.ttLib import TTFont
    cmap = set(TTFont(os.path.join(FONTS, file)).getBestCmap())
    asc, desc = font.getmetrics()
    glyphs = {}
    atlas = Image.new('RGBA', (1024, 1024), (255, 255, 255, 0))
    x = y = 1
    row_h = 0
    pad = 2
    for ch in CHARS:
        f = font if ord(ch) in cmap else fb
        adv = f.getlength(ch)
        if ch == ' ':
            glyphs[str(ord(ch))] = [0, 0, 0, 0, 0, 0, round(adv, 2)]
            continue
        box = f.getbbox(ch)                # относительно верха строки (якорь la)
        if f is fb:                        # чужой символ — по центру строки основного шрифта
            dy = (asc - (fb.getmetrics()[0])) // 2 + int(size * 0.06)
        else:
            dy = 0
        gw, gh = box[2] - box[0], box[3] - box[1]
        if gw <= 0 or gh <= 0:
            glyphs[str(ord(ch))] = [0, 0, 0, 0, 0, 0, round(adv, 2)]
            continue
        if x + gw + pad > 1024:
            x, y, row_h = 1, y + row_h + pad, 0
        tile = Image.new('L', (gw + 2, gh + 2), 0)
        ImageDraw.Draw(tile).text((1 - box[0], 1 - box[1]), ch, font=f, fill=255)
        a = Image.new('RGBA', tile.size, (255, 255, 255, 0))
        a.putalpha(tile)
        atlas.alpha_composite(a, (x, y))
        glyphs[str(ord(ch))] = [x, y, gw + 2, gh + 2, box[0] - 1, box[1] - 1 + dy, round(adv, 2)]
        x += gw + 2 + pad
        row_h = max(row_h, gh + 2)
    used = y + row_h + 1
    hgt = 1 << (used - 1).bit_length()
    atlas = atlas.crop((0, 0, 1024, max(64, hgt)))
    atlas.save(os.path.join(GUI, 'font_%s.png' % name))
    meta = {'scale': SCALE, 'ascent': asc, 'descent': desc, 'width': 1024, 'height': atlas.height, 'glyphs': glyphs}
    with open(os.path.join(GUI, 'font_%s.json' % name), 'w', encoding='utf-8') as fh:
        json.dump(meta, fh, separators=(',', ':'))


# ---------------------------------------------------------------------------------------- разворот

def noise(px, box, amt, rnd):
    x0, y0, x1, y1 = box
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            r, g, b, a = px[xx, yy]
            d = rnd.randint(-amt, amt)
            px[xx, yy] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)


def build_book():
    rnd = random.Random(26)
    img = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    px = img.load()
    # переплёт
    d.rectangle((0, 0, W - 1, H - 1), fill=(26, 15, 32, 255))
    noise(px, (0, 0, W, H), 7, rnd)
    for xx in range(5, W - 5, 4):
        px[xx, 3] = (150, 110, 70, 255)
        px[xx, H - 4] = (150, 110, 70, 255)
    for yy in range(5, H - 5, 4):
        px[3, yy] = (150, 110, 70, 255)
        px[W - 4, yy] = (150, 110, 70, 255)
    # обрез страниц
    edge = (16, 10, 22)
    for k in range(3):
        d.rectangle((PL - 1 + k, PT + 1 + k, PR + 1 - k, PB + 2 - k), fill=(edge[0] + 18 - 6 * k, edge[1] + 14 - 5 * k, edge[2] + 20 - 6 * k, 255))
    # тёмный пергамент
    d.rectangle((PL, PT, PR - 1, PB - 1), fill=(34, 24, 46, 255))
    noise(px, (PL, PT, PR, PB), 4, rnd)
    for yy in range(PT, PB):
        for xx in range(PL, PR):
            dx = min(xx - PL, PR - 1 - xx)
            dy = min(yy - PT, PB - 1 - yy)
            dg = abs(xx - MID)
            s = 0
            if dx < 10:
                s += (10 - dx) * 2
            if dy < 8:
                s += (8 - dy) * 2
            if dg < 14:
                s += (14 - dg) * 3
            if rnd.random() < 0.003:
                s -= 10                     # редкие светлые волокна
            r, g, b, a = px[xx, yy]
            px[xx, yy] = (max(0, min(255, r - s)), max(0, min(255, g - s)), max(0, min(255, b - int(s * 1.1))), 255)
    d.line((MID, PT, MID, PB - 1), fill=(8, 3, 12, 255))
    # бледный круг-водяной знак внизу правой страницы
    cx, cy, rr = PR - 52, PB - 54, 38
    wm = (44, 32, 58, 255)
    for r in (rr, rr - 6):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=wm)
    for a in range(8):
        t = a * math.pi / 4
        d.line((cx + rr * math.cos(t), cy + rr * math.sin(t),
                cx + rr * math.cos(t + math.pi * 3 / 4), cy + rr * math.sin(t + math.pi * 3 / 4)), fill=wm)
    # металлические уголки с камнем Хаоса
    gold, gd = (212, 172, 96, 255), (120, 84, 40, 255)
    for cx0, cy0, sx, sy in ((0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)):
        d.polygon([(cx0, cy0), (cx0 + 16 * sx, cy0), (cx0, cy0 + 16 * sy)], fill=gd)
        d.polygon([(cx0 + sx, cy0 + sy), (cx0 + 13 * sx, cy0 + sy), (cx0 + sx, cy0 + 13 * sy)], fill=gold)
        gx, gy = cx0 + 4 * sx, cy0 + 4 * sy
        d.rectangle((min(gx - sx, gx + sx), min(gy - sy, gy + sy), max(gx - sx, gx + sx), max(gy - sy, gy + sy)), fill=(150, 80, 220, 255))
    # ---- спрайты, y = 280
    # закладки-ленты: фиолетовая, красная, бирюзовая; 28×32, низ — «ласточкин хвост»
    for i, col in enumerate(((120, 40, 150), (150, 40, 46), (40, 110, 100))):
        x0, y0 = i * 30, 280
        for yy in range(32):
            for xx in range(28):
                tail = yy >= 26 and (abs(xx - 13.5) < (yy - 25) * 2.3)
                if tail:
                    continue
                c = col
                if xx < 2:
                    c = tuple(min(255, v + 45) for v in col)
                elif xx > 25:
                    c = tuple(max(0, v - 45) for v in col)
                j = rnd.randint(-5, 5)
                px[x0 + xx, y0 + yy] = (max(0, min(255, c[0] + j)), max(0, min(255, c[1] + j)), max(0, min(255, c[2] + j)), 255)
        for xx in range(28):                       # шов
            if xx % 3 == 0:
                px[x0 + xx, y0 + 3] = (230, 200, 140, 255)
    # сургучная печать 26×26: обычная, под мышью, недоступна
    for k, (base, hi) in enumerate((((110, 50, 170), 60), ((140, 70, 205), 80), ((60, 46, 72), 30))):
        x0, y0 = 92 + k * 28, 280
        for yy in range(26):
            for xx in range(26):
                r = math.hypot(xx - 12.5, yy - 12.5)
                wob = 12.2 + 0.9 * math.sin(math.atan2(yy - 12.5, xx - 12.5) * 7)
                if r > wob:
                    continue
                c = base
                if 8.5 < r < 10.0:
                    c = tuple(min(255, v + hi) for v in base)
                shade = int((yy - 12.5) * 1.5)
                px[x0 + xx, y0 + yy] = (max(0, min(255, c[0] - shade)), max(0, min(255, c[1] - shade)), max(0, min(255, c[2] - shade)), 255)
        # ромб в центре
        for yy in range(-6, 7):
            for xx in range(-6, 7):
                if abs(xx) + abs(yy) <= 5 - (abs(yy) > 3):
                    c = tuple(min(255, v + hi + 30) for v in base)
                    px[x0 + 13 + xx, y0 + 13 + yy] = c + (255,)
    # ленточка у выбранного пункта 7×13
    for yy in range(13):
        for xx in range(7):
            if yy >= 9 and abs(xx - 3) < (yy - 8):
                continue
            px[176 + xx, 280 + yy] = (236, 176, 100, 255) if xx > 0 else (255, 214, 150, 255)
    # ромбик разделителя 9×9
    for yy in range(9):
        for xx in range(9):
            if abs(xx - 4) + abs(yy - 4) <= 4:
                px[186 + xx, 280 + yy] = (236, 176, 100, 255)
    img.save(os.path.join(GUI, 'book.png'))


# ---------------------------------------------------------------------------------------- шелест

def build_sounds():
    os.makedirs(SOUNDS, exist_ok=True)
    sr = 44100
    for n in range(1, 4):
        rng = np.random.default_rng(100 + n)
        dur = 0.42 + 0.06 * n
        t = np.arange(int(sr * dur)) / sr
        white = rng.standard_normal(t.size)
        # «бумажный» спектр: ВЧ шума через разность соседних отсчётов + мягкое сглаживание
        hp = np.diff(white, prepend=0.0)
        k = np.ones(3) / 3
        paper = np.convolve(hp, k, mode='same')
        # огибающая: быстрый подъём, дрожание листа, затухание; плюс пара хрустов
        env = np.minimum(1.0, t / 0.03) * np.exp(-t / (0.14 + 0.02 * n))
        flutter = 0.65 + 0.35 * np.sin(2 * math.pi * (17 + 3 * n) * t + rng.uniform(0, 6)) ** 2
        sig = paper * env * flutter
        for _ in range(3 + n):
            c = rng.uniform(0.02, dur * 0.6)
            idx = (t > c) & (t < c + 0.012)
            sig[idx] += rng.standard_normal(idx.sum()) * 0.9 * np.exp(-(t[idx] - c) / 0.004)
        # второй, тише — лист ложится
        late = (t > dur * 0.55)
        sig[late] += paper[late] * 0.35 * np.exp(-(t[late] - dur * 0.55) / 0.05)
        sig = sig / np.max(np.abs(sig)) * 0.6
        fade = int(sr * 0.02)
        sig[-fade:] *= np.linspace(1, 0, fade)
        pcm = (sig * 32767).astype(np.int16)
        tmp = tempfile.NamedTemporaryFile(suffix='.wav', delete=False)
        tmp.close()
        with wave.open(tmp.name, 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(sr)
            wf.writeframes(pcm.tobytes())
        out = os.path.join(SOUNDS, 'page%d.ogg' % n)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp.name, '-c:a', 'libvorbis', '-q:a', '4', out], check=True)
        os.unlink(tmp.name)


if __name__ == '__main__':
    os.makedirs(GUI, exist_ok=True)
    for name, (file, size) in FACES.items():
        build_font(name, file, size)
    for lic in ('OFL-Alegreya.txt', 'OFL-BadScript.txt'):
        shutil.copy(os.path.join(FONTS, lic), os.path.join(GUI, lic))
    build_book()
    build_sounds()
    print('книга: шрифты, разворот, звуки готовы')
