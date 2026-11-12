package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;

import java.util.List;

/**
 * Общие детали окон мода в духе Гримуара (Стол Хаоса, Якорь): кнопки, вкладки, места, значки, полосы.
 * Текстура chaos_widgets.png рисуется docs/sprites/stol_haosa_gui.py.
 */
public final class ChaosUi {

    public static final ResourceLocation WIDGETS = new ResourceLocation(Tags.MOD_ID, "textures/gui/chaos_widgets.png");

    // цвета текста (контраст к фону окна ≥ 4.5:1)
    public static final int TEXT = 0xECE6F8;
    public static final int SOFT = 0xB9AED2;
    public static final int DIM = 0x8C80A8;
    public static final int ACCENT = 0xD7A6FF;
    public static final int GOLD = 0xECB064;
    public static final int GOOD = 0x8FE3A6;
    public static final int BAD = 0xFF8080;
    public static final int OFF = 0x6E6680;

    // значки 9x9: ряд y=140
    public static final int I_LAPIS = 0, I_XP = 1, I_CLOCK = 2, I_LOCK = 3, I_CHECK = 4, I_LEFT = 5, I_RIGHT = 6, I_STAR = 7;
    // ряд y=150
    public static final int I_DROP = 10, I_SWORD = 11, I_EYE = 12, I_PORTAL = 13, I_LAND = 14, I_MAP = 15, I_CHEST = 16,
            I_BOLT = 17, I_SHIELD = 18, I_SEAL = 19;

    public static final int SLOT_OPEN = 0, SLOT_LOCKED = 1, SLOT_RO = 2;

    private ChaosUi() {
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private static void bind() {
        mc().getTextureManager().bindTexture(WIDGETS);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
    }

    private static void blit(int x, int y, int u, int v, int w, int h) {
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, 256, 256);
    }

    public static void icon(int x, int y, int idx) {
        bind();
        blit(x, y, (idx % 10) * 10, idx < 10 ? 140 : 150, 9, 9);
    }

    public static void icon(int x, int y, int idx, float alpha) {
        bind();
        GlStateManager.color(1, 1, 1, alpha);
        blit(x, y, (idx % 10) * 10, idx < 10 ? 140 : 150, 9, 9);
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Место 18x18 (предмет рисуется в x+1, y+1). */
    public static void slot(int x, int y, int kind) {
        bind();
        blit(x, y, 40 + kind * 18, 160, 18, 18);
    }

    /** Кнопка шириной w (до 200), высотой 16. Состояние: 0 — обычная, 1 — под мышью, 2 — недоступна. */
    public static void button(FontRenderer font, int x, int y, int w, String label, int state, int disabledColor) {
        bind();
        int v = state == 2 ? 32 : state == 1 ? 16 : 0;
        int half = w / 2;
        blit(x, y, 0, v, half, 16);
        blit(x + half, y, 100 - (w - half), v, w - half, 16);
        String t = font.trimStringToWidth(label, w - 6);
        font.drawString(t, x + (w - font.getStringWidth(t)) / 2, y + 4, state == 2 ? disabledColor : state == 1 ? 0xFFFFFF : TEXT);
    }

    /** Вкладка 52 px. Активная (24 px) рисуется поверх окна и сливается с ним, неактивная (20 px) — под ним. */
    public static void smallTab(int x, int y, boolean active) {
        bind();
        if (active) {
            blit(x, y - 20, 0, 180, 52, 24);
        } else {
            blit(x, y - 17, 52, 180, 52, 20);
        }
    }

    public static void item(ItemStack st, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        mc().getRenderItem().renderItemAndEffectIntoGUI(st, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Углубление (рамка темнее сверху-слева, светлее снизу-справа). */
    public static void well(int x, int y, int w, int h) {
        Gui.drawRect(x, y, x + w, y + h, 0xFF0D0A13);
        Gui.drawRect(x, y, x + w, y + 1, 0xFF06040A);
        Gui.drawRect(x, y, x + 1, y + h, 0xFF06040A);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF3C2F54);
        Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFF3C2F54);
    }

    /** Полоса прогресса в углублении: высота 7, пустая часть заметна, у заливки блик сверху. */
    public static void bar(int x, int y, int w, double part, int color) {
        well(x, y, w, 7);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + 6, 0xFF1A1426);
        int f = (int) Math.round((w - 2) * Math.max(0, Math.min(1, part)));
        if (f > 0) {
            Gui.drawRect(x + 1, y + 1, x + 1 + f, y + 6, color);
            Gui.drawRect(x + 1, y + 1, x + 1 + f, y + 2, 0x55FFFFFF);
            Gui.drawRect(x + 1, y + 5, x + 1 + f, y + 6, 0x40000000);
        }
    }

    /** Пустое состояние: значок, заголовок и пояснение по центру области. */
    public static void empty(FontRenderer font, ItemStack icon, String title, String text, int cx, int y, int w) {
        if (icon != null) {
            item(icon, cx - 8, y);
            y += 22;
        }
        if (title != null) {
            font.drawString(title, cx - font.getStringWidth(title) / 2, y, TEXT);
            y += 13;
        }
        List<String> lines = font.listFormattedStringToWidth(text, w);
        for (String l : lines) {
            font.drawString(l, cx - font.getStringWidth(l) / 2, y, SOFT);
            y += 10;
        }
    }

    /** Полоса прокрутки справа в списке. */
    public static void scrollbar(int x, int y, int h, int first, int visible, int total) {
        if (total <= visible) {
            return;
        }
        int bar = Math.max(10, h * visible / total);
        int by = y + (h - bar) * first / Math.max(1, total - visible);
        Gui.drawRect(x, y, x + 3, y + h, 0xFF0B0812);
        Gui.drawRect(x, by, x + 3, by + bar, 0xFFAAAEBC);
        Gui.drawRect(x, by, x + 1, by + bar, 0xFFE1E4EE);
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
