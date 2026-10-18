package ru.death.chaosshard.client.book;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.Tags;

/** Разворот Гримуара и его спрайты (textures/gui/book/book.png, pack/tools/book_assets.py). */
public final class BookArt {

    public static final ResourceLocation TEX = new ResourceLocation(Tags.MOD_ID, "textures/gui/book/book.png");
    /** Размер книги в пикселях интерфейса. */
    public static final int W = 440, H = 272;
    /** Края страниц и корешок (от левого верхнего угла книги). */
    public static final int PL = 10, PT = 9, PR = W - 10, PB = H - 9, MID = W / 2;

    // чернила на тёмном пергаменте
    public static final int INK = 0xDCCDEB, SOFT = 0x9C88B4, DIM = 0x5F536E, ACCENT = 0xECB064, RED = 0xE88E6A,
            VIOLET = 0xC496FF, GOOD = 0x8FD07E, BAD = 0xEE7A6E, NOTE = 0xD8B66E;

    private BookArt() {
    }

    private static void bind() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    private static void sprite(int x, int y, int u, int v, int w, int h) {
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, 512, 512);
    }

    /** Тень и сам разворот. */
    public static void book(int x, int y) {
        Gui.drawRect(x + 3, y + 4, x + W + 3, y + H + 4, 0x70000000);
        bind();
        sprite(x, y, 0, 0, W, H);
    }

    /** Закладка-лента i (0 — фиолетовая, 1 — красная, 2 — бирюзовая). Низ ленты заходит под край страниц. */
    public static void ribbon(int i, int x, int bottom, boolean selected, boolean hover) {
        bind();
        int len = selected ? 30 : hover ? 26 : 23;
        sprite(x, bottom - len, i * 30, 280, 28, len);
    }

    /** Сургучная печать 26×26: 0 — обычная, 1 — под мышью, 2 — недоступна. */
    public static void seal(int x, int y, int state) {
        bind();
        sprite(x, y, 92 + state * 28, 280, 26, 26);
    }

    /** Ленточка на полях у выбранного пункта (7×13). */
    public static void marker(int x, int y) {
        bind();
        sprite(x, y, 176, 280, 7, 13);
    }

    /** Разделитель: тонкая черта и ромбик посередине. */
    public static void divider(int x, int y, int w) {
        Gui.drawRect(x, y, x + w, y + 1, 0x708F78A8);
        bind();
        sprite(x + w / 2 - 4, y - 4, 186, 280, 9, 9);
    }

    /** Точки-отточие между названием и номером страницы. */
    public static void leader(int x0, int x1, int y) {
        for (int x = x0 + 2; x < x1 - 2; x += 4) {
            Gui.drawRect(x, y, x + 1, y + 1, 0x907A6894);
        }
    }

    /**
     * Перелистывание: лист поднимается с одной страницы и ложится на другую.
     * p — 0..1; forward — справа налево (вперёд по книге).
     */
    public static void flip(int bx, int by, float p, boolean forward) {
        if (p <= 0 || p >= 1) {
            return;
        }
        float c = (float) Math.cos(p * Math.PI);          // 1 → -1
        int mid = bx + MID;
        float half = MID - PL;
        float w = Math.abs(c) * half;
        boolean onRight = forward ? c > 0 : c < 0;
        float x0 = onRight ? mid : mid - w, x1 = onRight ? mid + w : mid;
        int top = by + PT, bot = by + PB;
        float lift = (1 - Math.abs(c)) * 6;                // лист приподнят над разворотом
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        // тень под листом
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(x0 + 2, bot + 2, 0).color(0, 0, 0, 0.35F).endVertex();
        b.pos(x1 + 2, bot + 2, 0).color(0, 0, 0, 0.35F).endVertex();
        b.pos(x1 + 2, top + 2, 0).color(0, 0, 0, 0.35F).endVertex();
        b.pos(x0 + 2, top + 2, 0).color(0, 0, 0, 0.35F).endVertex();
        t.draw();
        // сам лист: темнее у сгиба, светлее у свободного края
        float near = 0.09F, far = 0.19F;
        float lx = onRight ? near : far, rx = onRight ? far : near;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(x0, bot + (onRight ? 0 : lift), 0).color(lx, lx * 0.7F, lx * 1.35F, 1F).endVertex();
        b.pos(x1, bot + (onRight ? lift : 0), 0).color(rx, rx * 0.7F, rx * 1.35F, 1F).endVertex();
        b.pos(x1, top - (onRight ? lift : 0), 0).color(rx, rx * 0.7F, rx * 1.35F, 1F).endVertex();
        b.pos(x0, top - (onRight ? 0 : lift), 0).color(lx, lx * 0.7F, lx * 1.35F, 1F).endVertex();
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        float edge = onRight ? x1 : x0;
        Gui.drawRect((int) edge - 1, top, (int) edge, bot, 0x60E8D8F8);
    }
}
