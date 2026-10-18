#!/usr/bin/env python3
"""Хаск — модель слуги (своё существо: высохшая оболочка, выеденная Хаосом; из пасти — членистые лапки).

Одно описание частей даёт сразу:
  - src/main/java/ru/death/chaosshard/servant/client/ModelChaosHusk.java (геометрия + анимация),
  - textures/entity/servant/husk.png и husk_glow.png (развёртка 64×64, свечение — отдельным слоем),
  - servant_husk.bbmodel для Blockbench (путь — первым аргументом, по умолчанию рядом со скриптом).

Координаты — как у ModelRenderer в 1.12: y вниз, земля на y=24, -x — правая сторона модели.
Запуск: python3 pack/tools/husk_model.py [путь/к/servant_husk.bbmodel]
"""
import json
import math
import os
import random
import sys
import uuid

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEX = os.path.join(ROOT, 'src/main/resources/assets/chaosshard/textures/entity/servant')
JAVA = os.path.join(ROOT, 'src/main/java/ru/death/chaosshard/servant/client/ModelChaosHusk.java')

# имя, родитель, (u, v), смещение коробки (x, y, z), размер (w, h, d), точка вращения (от родителя), покой (rx, ry, rz), вид краски
PARTS = [
    ('torso', None, (16, 16), (-4, -12, -2), (8, 12, 4), (0, 12, 0.5), (0.42, 0, 0), 'torso'),
    ('head', 'torso', (0, 0), (-4, -6, -6), (8, 6, 8), (0, -12, -0.5), (-0.25, 0, 0), 'head'),
    ('jaw', 'head', (32, 0), (-3.5, 0, -6.5), (7, 2, 7), (0, 0, 0.5), (0.35, 0, 0), 'jaw'),
    # лапки из пасти: верхняя пара — от нёба, нижняя — от челюсти; по три членика
    ('mr1', 'head', (16, 32), (-0.5, -0.5, -4), (1, 1, 4), (-1.3, -0.4, -5.6), (-0.35, 0.85, 0), 'leg1'),
    ('mr2', 'mr1', (26, 32), (-0.5, -0.5, -3), (1, 1, 3), (0, 0, -4), (0.25, 1.05, 0), 'leg2'),
    ('mr3', 'mr2', (34, 32), (-0.5, -0.5, -2), (1, 1, 2), (0, 0, -3), (0.2, 0.7, 0), 'leg3'),
    ('ml1', 'head', (16, 32), (-0.5, -0.5, -4), (1, 1, 4), (1.3, -0.4, -5.6), (-0.35, -0.85, 0), 'leg1'),
    ('ml2', 'ml1', (26, 32), (-0.5, -0.5, -3), (1, 1, 3), (0, 0, -4), (0.25, -1.05, 0), 'leg2'),
    ('ml3', 'ml2', (34, 32), (-0.5, -0.5, -2), (1, 1, 2), (0, 0, -3), (0.2, -0.7, 0), 'leg3'),
    ('lr1', 'jaw', (16, 32), (-0.5, -0.5, -4), (1, 1, 4), (-1.6, 0.6, -5.8), (0.3, 1.0, 0), 'leg1'),
    ('lr2', 'lr1', (26, 32), (-0.5, -0.5, -3), (1, 1, 3), (0, 0, -4), (-0.35, 0.95, 0), 'leg2'),
    ('lr3', 'lr2', (34, 32), (-0.5, -0.5, -2), (1, 1, 2), (0, 0, -3), (-0.2, 0.7, 0), 'leg3'),
    ('ll1', 'jaw', (16, 32), (-0.5, -0.5, -4), (1, 1, 4), (1.6, 0.6, -5.8), (0.3, -1.0, 0), 'leg1'),
    ('ll2', 'll1', (26, 32), (-0.5, -0.5, -3), (1, 1, 3), (0, 0, -4), (-0.35, -0.95, 0), 'leg2'),
    ('ll3', 'll2', (34, 32), (-0.5, -0.5, -2), (1, 1, 2), (0, 0, -3), (-0.2, -0.7, 0), 'leg3'),
    # кристаллы Хаоса из спины, плеча и головы
    ('cr1', 'torso', (16, 40), (-1, -5, -1), (2, 5, 2), (-1.5, -9, 2), (-0.7, 0, 0.35), 'crystal'),
    ('cr2', 'torso', (24, 40), (-0.5, -4, -0.5), (1, 4, 1), (2, -7, 2), (-0.9, 0, -0.4), 'crystal'),
    ('cr3', 'torso', (28, 40), (-1, -3, -1), (2, 3, 2), (3.5, -11.5, 0.5), (-0.2, 0, -0.9), 'crystal'),
    ('cr4', 'head', (24, 40), (-0.5, -4, -0.5), (1, 4, 1), (-2.5, -5.5, -1), (-0.5, 0, 0.5), 'crystal'),
    # длинные руки почти до колен и ноги
    ('armR', None, (40, 16), (-1.5, -1, -1.5), (3, 15, 3), (-5.5, 3.0, -4.0), (-0.15, 0, 0.1), 'arm'),
    ('armL', None, (40, 34), (-1.5, -1, -1.5), (3, 15, 3), (5.5, 3.0, -4.0), (-0.15, 0, -0.1), 'arm'),
    ('legR', None, (0, 16), (-2, 0, -2), (4, 12, 4), (-2.1, 12, 0.5), (0, 0, 0), 'leg'),
    ('legL', None, (0, 32), (-2, 0, -2), (4, 12, 4), (2.1, 12, 0.5), (0, 0, 0), 'leg'),
]

# ---------------------------------------------------------------------------------------- текстура

SKIN = [(0xa6, 0x9c, 0x80), (0xb8, 0xae, 0x92), (0xc4, 0xba, 0x9c), (0x96, 0x8c, 0x72)]
CRACK = (0x3a, 0x26, 0x38)
GLOW = (0xd0, 0x90, 0xff)
MAW = (0x2a, 0x0c, 0x1e)
TOOTH = (0xd9, 0xcf, 0xb4)
CHITIN = [(0x2e, 0x1f, 0x2a), (0x3a, 0x28, 0x34), (0x26, 0x18, 0x22)]
CHITIN_HI = (0x62, 0x44, 0x58)
BONE = (0xd8, 0xcc, 0xb0)


def faces(u, v, w, h, d):
    """Грани коробки на развёртке 1.12: x, y, ширина, высота."""
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def paint():
    rnd = random.Random(1924)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()

    def fill(rect, pal, jitter=6):
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = rnd.choice(pal)
                j = rnd.randint(-jitter, jitter)
                px[x, y] = (max(0, min(255, c[0] + j)), max(0, min(255, c[1] + j)), max(0, min(255, c[2] + j)), 255)

    def shade(rect, top_dark=0, bottom_dark=0):
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            k = 0
            if h > 1:
                t = (y - y0) / (h - 1)
                k = int(top_dark * (1 - t) + bottom_dark * t)
            for x in range(x0, x0 + w):
                r, g, b, a = px[x, y]
                px[x, y] = (max(0, r - k), max(0, g - k), max(0, b - k), a)

    def crack(rect, n, glow_chance=0.6, vertical=True):
        x0, y0, w, h = rect
        for _ in range(n):
            x, y = x0 + rnd.randrange(w), y0 + rnd.randrange(h)
            lit = rnd.random() < glow_chance
            for _ in range(rnd.randint(3, max(4, (h if vertical else w)))):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    px[x, y] = (0x5a, 0x30, 0x72) + (255,) if lit else CRACK + (255,)
                    if lit:
                        gx[x, y] = GLOW + (255,)
                if vertical:
                    y += 1
                    x += rnd.choice((-1, 0, 0, 1))
                else:
                    x += 1
                    y += rnd.choice((-1, 0, 0, 1))

    for name, parent, (u, v), off, (w, h, d), piv, rot, kind in PARTS:
        f = faces(u, v, w, h, d)
        if kind in ('torso', 'head', 'jaw', 'arm', 'leg'):
            for r in f.values():
                fill(r, SKIN)
        if kind == 'torso':
            # рёбра под высохшей кожей, трещины со светом
            fx, fy, fw, fh = f['north']
            for ry in range(fy + 3, fy + 9, 2):
                for x in range(fx + 1, fx + fw - 1):
                    if x not in (fx + 3, fx + 4):
                        r, g, b, a = px[x, ry]
                        px[x, ry] = (r - 22, g - 22, b - 20, 255)
            for side in ('north', 'south', 'east', 'west'):
                crack(f[side], 2 if side in ('north', 'south') else 1)
            shade(f['north'], 0, 18)
        elif kind == 'head':
            fx, fy, fw, fh = f['north']
            # узкие провалы глазниц и широкий светящийся разлом через лицо
            for (ex, ey) in ((fx + 1, fy + 2), (fx + 6, fy + 2)):
                px[ex, ey] = (0x1a, 0x10, 0x18, 255)
                px[ex, ey + 1] = (0x2a, 0x1c, 0x24, 255)
            for x in range(fx, fx + fw):              # надбровье темнее
                r, g, b, a = px[x, fy + 1]
                px[x, fy + 1] = (r - 26, g - 26, b - 22, 255)
            x = fx + 3
            for y in range(fy, fy + fh - 1):
                for xx in (x, x + 1):
                    px[xx, y] = (0x52, 0x2a, 0x6a, 255)
                    gx[xx, y] = GLOW + (255,)
                x += (1, 0, -1, 0)[y % 4] if fx + 2 <= x <= fx + 5 else 0
            # нижний ряд — зубы верхней челюсти
            for x in range(fx + 1, fx + fw - 1):
                px[x, fy + fh - 1] = TOOTH + (255,) if (x - fx) % 2 else (0x9a, 0x90, 0x78, 255)
            # нёбо — тёмная пасть с искрами
            fill(f['down'], [MAW, (0x35, 0x10, 0x26)], 4)
            dx0, dy0, dw, dh = f['down']
            for _ in range(5):
                gx[dx0 + rnd.randrange(dw), dy0 + rnd.randrange(dh)] = (0x90, 0x40, 0xd0, 255)
            crack(f['up'], 2)
            crack(f['east'], 1)
            crack(f['west'], 1)
            shade(f['north'], 10, 0)
        elif kind == 'jaw':
            fill(f['up'], [MAW, (0x35, 0x10, 0x26)], 4)
            ux, uy, uw, uh = f['up']
            for x in range(ux, ux + uw):        # передний край — нижние зубы
                px[x, uy + uh - 1] = TOOTH + (255,) if (x - ux) % 2 == 0 else (0x9a, 0x90, 0x78, 255)
            for _ in range(4):
                gx[ux + rnd.randrange(uw), uy + rnd.randrange(uh - 1)] = (0x90, 0x40, 0xd0, 255)
            shade(f['down'], 0, 14)
        elif kind == 'arm':
            for side in ('north', 'south', 'east', 'west'):
                shade(f[side], 0, 34)          # кисти темнее
            crack(f['north'], 1)
            crack(f['west'], 1)
        elif kind == 'leg':
            for side in ('north', 'south', 'east', 'west'):
                shade(f[side], 6, 30)
            crack(f['north'], 1, glow_chance=0.4)
        elif kind.startswith('leg'):
            for r in f.values():
                fill(r, CHITIN, 5)
            ux, uy, uw, uh = f['up']
            for x in range(ux, ux + uw):
                for y in range(uy, uy + uh):
                    px[x, y] = CHITIN_HI + (255,)
            if kind == 'leg3':                 # кончик — костяной коготь
                for r in f.values():
                    x0, y0, w0, h0 = r
                    for x in range(x0, x0 + w0):
                        for y in range(y0, y0 + h0):
                            if r is f['north'] or rnd.random() < 0.5:
                                px[x, y] = BONE + (255,)
            else:                              # сустав у основания светлее
                for side in ('east', 'west', 'north', 'south'):
                    x0, y0, w0, h0 = f[side]
                    for y in range(y0, y0 + h0):
                        px[x0 + (w0 - 1 if side == 'west' else 0), y] = CHITIN_HI + (255,)
        elif kind == 'crystal':
            for key, r in f.items():
                x0, y0, w0, h0 = r
                for y in range(y0, y0 + h0):
                    t = (y - y0) / max(1, h0 - 1)
                    for x in range(x0, x0 + w0):
                        c = (int(0x5b + (0xc9 - 0x5b) * (1 - t)), int(0x2a + (0xa6 - 0x2a) * (1 - t)), int(0x9a + (0xff - 0x9a) * (1 - t)))
                        if key == 'up':
                            c = (0xe0, 0xc8, 0xff)
                        px[x, y] = c + (255,)
                        g = 0.55 + 0.45 * (1 - t)
                        gx[x, y] = (int(0x90 * g), int(0x50 * g), int(0xe0 * g), 255)
    # прозрачное свечение — чёрное, чтобы сложение цветов его не показывало
    for y in range(64):
        for x in range(64):
            if gx[x, y][3] == 0:
                gx[x, y] = (0, 0, 0, 0)
    img.save(os.path.join(TEX, 'husk.png'))
    glow.save(os.path.join(TEX, 'husk_glow.png'))


# ---------------------------------------------------------------------------------------- Java

def f(v):
    s = ('%.4f' % v).rstrip('0')
    if s.endswith('.'):
        s += '0'
    return s + 'F'


def java():
    names = [p[0] for p in PARTS]
    out = []
    w = out.append
    w('package ru.death.chaosshard.servant.client;')
    w('')
    w('import net.minecraft.client.model.ModelBiped;')
    w('import net.minecraft.client.model.ModelRenderer;')
    w('import net.minecraft.client.renderer.GlStateManager;')
    w('import net.minecraft.entity.Entity;')
    w('import net.minecraft.util.EnumHandSide;')
    w('import net.minecraft.util.math.MathHelper;')
    w('')
    w('/**')
    w(' * Хаск — своё существо: сгорбленная высохшая оболочка, выеденная Хаосом, из пасти — четыре членистые лапки.')
    w(' * Геометрия сгенерирована pack/tools/husk_model.py (там же текстура и .bbmodel) — правки геометрии делать там.')
    w(' * Наследует ModelBiped ради предмета в руке (LayerHeldItem); броня на эту модель не надевается.')
    w(' */')
    w('public class ModelChaosHusk extends ModelBiped {')
    w('')
    for n in names:
        w('    public final ModelRenderer %s;' % n)
    w('    /** Лапки из пасти: [лапка][членик]. */')
    w('    private final ModelRenderer[][] maw;')
    w('    private final float[][] rest;')
    w('')
    w('    public ModelChaosHusk() {')
    w('        super(0.0F, 0.0F, 64, 64);')
    for n, parent, (u, v), off, size, piv, rot, kind in PARTS:
        w('        %s = new ModelRenderer(this, %d, %d);' % (n, u, v))
        w('        %s.addBox(%s, %s, %s, %d, %d, %d);' % (n, f(off[0]), f(off[1]), f(off[2]), size[0], size[1], size[2]))
        w('        %s.setRotationPoint(%s, %s, %s);' % (n, f(piv[0]), f(piv[1]), f(piv[2])))
    for n, parent, *_ in PARTS:
        if parent:
            w('        %s.addChild(%s);' % (parent, n))
    w('        bipedRightArm = armR;')
    w('        bipedLeftArm = armL;')
    w('        bipedRightLeg = legR;')
    w('        bipedLeftLeg = legL;')
    w('        bipedHead = head;')
    w('        bipedBody = torso;')
    w('        maw = new ModelRenderer[][]{{mr1, mr2, mr3}, {ml1, ml2, ml3}, {lr1, lr2, lr3}, {ll1, ll2, ll3}};')
    w('        rest = new float[][]{')
    for n, parent, uv, off, size, piv, rot, kind in PARTS:
        w('                {%s, %s, %s},   // %s' % (f(rot[0]), f(rot[1]), f(rot[2]), n))
    w('        };')
    w('    }')
    w('')
    w('    private void pose(ModelRenderer r, int i) {')
    w('        r.rotateAngleX = rest[i][0];')
    w('        r.rotateAngleY = rest[i][1];')
    w('        r.rotateAngleZ = rest[i][2];')
    w('    }')
    w('')
    w('    @Override')
    w('    public void render(Entity entity, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, float scale) {')
    w('        setRotationAngles(limbSwing, limbSwingAmount, age, netHeadYaw, headPitch, scale, entity);')
    w('        torso.render(scale);')
    w('        armR.render(scale);')
    w('        armL.render(scale);')
    w('        legR.render(scale);')
    w('        legL.render(scale);')
    w('    }')
    w('')
    w('    @Override')
    w('    public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch, float scale, Entity entity) {')
    for i, (n, *_r) in enumerate(PARTS):
        w('        pose(%s, %d);' % (n, i))
    w(ANIM)
    w('    }')
    w('')
    w('    /** Предмет в руке — в кисти длинной руки, а не у локтя. */')
    w('    @Override')
    w('    public void postRenderArm(float scale, EnumHandSide side) {')
    w('        super.postRenderArm(scale, side);')
    w('        GlStateManager.translate(0.0F, 0.42F, 0.0F);')
    w('    }')
    w('}')
    with open(JAVA, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(out) + '\n')


ANIM = '''        float walk = Math.min(1.0F, limbSwingAmount);
        float step = limbSwing * 0.6662F;
        float attack = swingProgress > 0 ? MathHelper.sin(swingProgress * (float) Math.PI) : 0.0F;
        // шаркающая походка: правая нога шагает, левая подволакивается
        legR.rotateAngleX = MathHelper.cos(step) * 1.1F * walk;
        legL.rotateAngleX = MathHelper.cos(step + (float) Math.PI) * 0.5F * walk + 0.12F * walk;
        legL.rotateAngleZ = -0.06F * walk;
        // дыхание и раскачка сгорбленного тела
        torso.rotateAngleX += MathHelper.sin(age * 0.06F) * 0.025F + walk * 0.08F;
        torso.rotateAngleZ = MathHelper.cos(step) * 0.06F * walk;
        // голова: взгляд и редкие резкие подёргивания
        head.rotateAngleY = netHeadYaw * 0.017453292F * 0.7F;
        head.rotateAngleX += headPitch * 0.017453292F * 0.6F;
        float twitch = MathHelper.sin(age * 0.13F) * MathHelper.sin(age * 0.071F + 1.0F);
        if (twitch > 0.82F) {
            head.rotateAngleZ = (twitch - 0.82F) * 3.5F * MathHelper.sin(age * 2.6F);
        }
        // пасть приоткрыта, на ударе распахивается
        jaw.rotateAngleX += MathHelper.sin(age * 0.09F) * 0.06F + attack * 0.6F;
        // руки болтаются; на ударе — выброс вперёд
        armR.rotateAngleX += MathHelper.cos(step + (float) Math.PI) * 0.45F * walk - attack * 1.5F;
        armL.rotateAngleX += MathHelper.cos(step) * 0.45F * walk - attack * 1.3F;
        armR.rotateAngleZ += MathHelper.sin(age * 0.05F) * 0.03F;
        armL.rotateAngleZ -= MathHelper.sin(age * 0.05F) * 0.03F;
        // лапки из пасти: шевелятся сами по себе, иногда ощупывают воздух, на ударе выстреливают вперёд
        float probe = Math.max(0.0F, MathHelper.sin(age * 0.045F) * MathHelper.sin(age * 0.029F + 2.0F) - 0.5F) * 2.0F;
        float reach = Math.max(attack, probe * 0.6F);
        for (int i = 0; i < 4; i++) {
            float ph = i * 1.7F;
            ModelRenderer[] l = maw[i];
            l[0].rotateAngleX = (l[0].rotateAngleX + MathHelper.sin(age * 0.3F + ph) * 0.18F) * (1.0F - reach);
            l[0].rotateAngleY = (l[0].rotateAngleY + MathHelper.cos(age * 0.23F + ph) * 0.15F) * (1.0F - reach * 0.7F);
            l[1].rotateAngleX = (l[1].rotateAngleX + MathHelper.sin(age * 0.4F + ph) * 0.25F) * (1.0F - reach);
            l[2].rotateAngleX = (l[2].rotateAngleX + MathHelper.sin(age * 0.5F + ph) * 0.3F) * (1.0F - reach * 0.8F);
        }'''


# ---------------------------------------------------------------------------------------- Blockbench

def bbmodel(path):
    by = {p[0]: p for p in PARTS}

    def absolute(name):
        x = y = z = 0.0
        n = name
        while n:
            p = by[n]
            x, y, z = x + p[5][0], y + p[5][1], z + p[5][2]
            n = p[1]
        return x, y, z

    def to_bb(x, y, z):
        return [-x, 24 - y, z]

    elements, groups, nodes = [], [], {}
    for n, parent, (u, v), off, (w, h, d), piv, rot, kind in PARTS:
        px_, py_, pz_ = absolute(n)
        a = to_bb(px_ + off[0], py_ + off[1], pz_ + off[2])
        b = to_bb(px_ + off[0] + w, py_ + off[1] + h, pz_ + off[2] + d)
        frm = [min(a[i], b[i]) for i in range(3)]
        to = [max(a[i], b[i]) for i in range(3)]
        eid = str(uuid.uuid4())
        fc = faces(u, v, w, h, d)
        bbfaces = {}
        for key, (x0, y0, fw, fh) in fc.items():
            bbfaces[key] = {'uv': [x0, y0, x0 + fw, y0 + fh], 'texture': 0}
        elements.append({'name': n, 'box_uv': True, 'rescale': False, 'locked': False, 'from': frm, 'to': to,
                         'autouv': 0, 'color': 0, 'origin': to_bb(px_, py_, pz_), 'uv_offset': [u, v],
                         'faces': bbfaces, 'type': 'cube', 'uuid': eid})
        gid = str(uuid.uuid4())
        groups.append({'name': n, 'uuid': gid, 'export': True, 'origin': to_bb(px_, py_, pz_),
                       'rotation': [-math.degrees(rot[0]), -math.degrees(rot[1]), math.degrees(rot[2])],
                       'color': 0, 'children': [], 'isOpen': True})
        nodes[n] = {'uuid': gid, 'isOpen': True, 'children': [eid]}
    outliner = []
    for n, parent, *_ in PARTS:
        (nodes[parent]['children'] if parent else outliner).append(nodes[n])
    with open(os.path.join(TEX, 'husk.png'), 'rb') as fh:
        import base64
        src = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    model = {'meta': {'format_version': '4.5', 'model_format': 'modded_entity', 'box_uv': True},
             'name': 'servant_husk', 'model_identifier': 'husk', 'modded_entity_version': '1.12',
             'modded_entity_flip_y': True, 'visible_box': [1, 1, 0], 'resolution': {'width': 64, 'height': 64},
             'elements': elements, 'groups': groups, 'outliner': outliner,
             'textures': [{'name': 'husk.png', 'id': '0', 'width': 64, 'height': 64, 'uv_width': 64, 'uv_height': 64,
                           'particle': False, 'render_mode': 'default', 'visible': True, 'mode': 'bitmap',
                           'saved': True, 'uuid': str(uuid.uuid4()), 'source': src}]}
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(model, fh, ensure_ascii=False, indent=1)


if __name__ == '__main__':
    paint()
    java()
    bbmodel(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), 'servant_husk.bbmodel'))
    print('Хаск: модель, текстуры и bbmodel готовы')
