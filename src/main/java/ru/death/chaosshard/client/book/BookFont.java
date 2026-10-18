package ru.death.chaosshard.client.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.Tags;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Гладкий шрифт Гримуара: Alegreya (обычный, §l — жирный, §o — курсив) или рукописный Bad Script.
 * Атласы — textures/gui/book/font_*.png/json (pack/tools/book_assets.py), в 4 раза крупнее интерфейса, с линейной фильтрацией.
 * Наследует FontRenderer, поэтому подходит везде, где ждут шрифт: поля ввода, подсказки, обрезка и перенос строк.
 */
public class BookFont extends FontRenderer {

    private static BookFont body;
    private static BookFont script;

    /** Основной шрифт книги. */
    public static BookFont body() {
        if (body == null) {
            body = new BookFont(new Face("regular"), new Face("bold"), new Face("italic"), 1.0F);
        }
        return body;
    }

    /** Рукописные пометки. */
    public static BookFont script() {
        if (script == null) {
            Face s = new Face("script");
            script = new BookFont(s, s, s, 1.05F);
        }
        return script;
    }

    /** Палитра кодов § на тёмном пергаменте. */
    private static final int[] CODES = {
            0x1A1020, 0x4060D0, 0x50B050, 0x40B0B0, 0xC04040, 0xB050D0, 0xE0A040, 0xB8B0C4,
            0x6E6680, 0x8090FF, 0x8FD07E, 0x80E0E0, 0xF07070, 0xE090FF, 0xFFD27A, 0xF4EEFA};

    private final Face regular, bold, italic;
    private final float size;

    private BookFont(Face regular, Face bold, Face italic, float size) {
        super(Minecraft.getMinecraft().gameSettings, new ResourceLocation("textures/font/ascii.png"),
                Minecraft.getMinecraft().getTextureManager(), false);
        this.regular = regular;
        this.bold = bold;
        this.italic = italic;
        this.size = size;
        this.FONT_HEIGHT = 10;
    }

    // ------------------------------------------------------------------ ширина

    @Override
    public int getCharWidth(char c) {
        if (c == '§') {
            return -1;
        }
        return Math.round(regular.adv(c) * size);
    }

    @Override
    public int getStringWidth(String text) {
        return (int) Math.ceil(width(text));
    }

    /** Ширина с дробями (для точной вёрстки). */
    public float width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        float w = 0;
        Face f = regular;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                f = style(f, Character.toLowerCase(text.charAt(++i)));
                continue;
            }
            w += f.adv(c) * size;
        }
        return w;
    }

    private Face style(Face cur, char code) {
        if (code == 'l') {
            return bold;
        }
        if (code == 'o') {
            return italic;
        }
        if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) {
            return regular;
        }
        return cur;
    }

    // ------------------------------------------------------------------ вывод

    @Override
    public int drawString(String text, float x, float y, int color, boolean dropShadow) {
        if (text == null) {
            return 0;
        }
        if ((color & 0xFC000000) == 0) {
            color |= 0xFF000000;
        }
        float a = (color >>> 24 & 255) / 255F;
        int base = color & 0xFFFFFF;
        int cur = base;
        Face f = regular;
        boolean under = false, strike = false;
        float pen = x;
        StringBuilder run = new StringBuilder();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableLighting();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                float start = pen;
                pen = flush(run, f, pen, y, cur, a);
                line(start, pen, y, cur, a, under, strike);
                char code = Character.toLowerCase(text.charAt(++i));
                int ci = "0123456789abcdef".indexOf(code);
                if (ci >= 0) {
                    cur = CODES[ci];
                    under = strike = false;
                } else if (code == 'r') {
                    cur = base;
                    under = strike = false;
                } else if (code == 'n') {
                    under = true;
                } else if (code == 'm') {
                    strike = true;
                }
                f = style(f, code);
                continue;
            }
            run.append(c);
        }
        float start = pen;
        pen = flush(run, f, pen, y, cur, a);
        line(start, pen, y, cur, a, under, strike);
        GlStateManager.color(1F, 1F, 1F, 1F);
        return (int) Math.ceil(pen);
    }

    private float flush(StringBuilder run, Face f, float x, float y, int color, float a) {
        if (run.length() == 0) {
            return x;
        }
        f.bind();
        float r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        float k = size / f.scale;
        float base = y + 7.6F - f.ascent * k + (size - 1F) * 4F;
        boolean slant = f == italic && regular != italic && false;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int i = 0; i < run.length(); i++) {
            char c = run.charAt(i);
            float[] gl = f.glyph(c);
            if (gl != null && gl[2] > 0) {
                float x0 = x + gl[4] * k, y0 = base + gl[5] * k;
                float x1 = x0 + gl[2] * k, y1 = y0 + gl[3] * k;
                float u0 = gl[0] / f.w, v0 = gl[1] / f.h, u1 = (gl[0] + gl[2]) / f.w, v1 = (gl[1] + gl[3]) / f.h;
                float sk = slant ? (y1 - y0) * 0.2F : 0;
                buf.pos(x0 - sk, y1, 0).tex(u0, v1).color(r, g, b, a).endVertex();
                buf.pos(x1 - sk, y1, 0).tex(u1, v1).color(r, g, b, a).endVertex();
                buf.pos(x1, y0, 0).tex(u1, v0).color(r, g, b, a).endVertex();
                buf.pos(x0, y0, 0).tex(u0, v0).color(r, g, b, a).endVertex();
            }
            x += (gl == null ? f.adv(' ') : gl[6]) * k;
        }
        t.draw();
        run.setLength(0);
        return x;
    }

    private static void line(float x0, float x1, float y, int color, float a, boolean under, boolean strike) {
        if (!under && !strike || x1 <= x0) {
            return;
        }
        GlStateManager.disableTexture2D();
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, a);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        float yy = under ? y + 8.6F : y + 4.6F;
        b.pos(x0, yy + 0.6F, 0).endVertex();
        b.pos(x1, yy + 0.6F, 0).endVertex();
        b.pos(x1, yy, 0).endVertex();
        b.pos(x0, yy, 0).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();
    }

    /** Строка в масштабе (заголовки, буквица): x, y — верх-лево. */
    public float drawScaled(String text, float x, float y, int color, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1F);
        drawString(text, 0, 0, color, false);
        GlStateManager.popMatrix();
        return x + width(text) * scale;
    }

    // ------------------------------------------------------------------ лицо шрифта

    static final class Face {
        final ResourceLocation tex;
        final Map<Character, float[]> glyphs = new HashMap<>();
        float scale = 4, ascent, w = 1024, h = 1024;
        private boolean loaded;
        private final String name;

        Face(String name) {
            this.name = name;
            this.tex = new ResourceLocation(Tags.MOD_ID, "textures/gui/book/font_" + name + ".png");
        }

        private void load() {
            if (loaded) {
                return;
            }
            loaded = true;
            try (InputStreamReader r = new InputStreamReader(Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation(Tags.MOD_ID, "textures/gui/book/font_" + name + ".json")).getInputStream(),
                    StandardCharsets.UTF_8)) {
                JsonObject o = new JsonParser().parse(r).getAsJsonObject();
                scale = o.get("scale").getAsFloat();
                ascent = o.get("ascent").getAsFloat();
                w = o.get("width").getAsFloat();
                h = o.get("height").getAsFloat();
                for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("glyphs").entrySet()) {
                    JsonArray a = e.getValue().getAsJsonArray();
                    float[] g = new float[7];
                    for (int i = 0; i < 7; i++) {
                        g[i] = a.get(i).getAsFloat();
                    }
                    glyphs.put((char) Integer.parseInt(e.getKey()), g);
                }
            } catch (Exception ex) {
                ru.death.chaosshard.ChaosShard.LOG.warn("Шрифт книги {} не загрузился: {}", name, ex.toString());
            }
        }

        float[] glyph(char c) {
            load();
            float[] g = glyphs.get(c);
            if (g == null && c == ' ') {
                g = glyphs.get(' ');
            }
            return g;
        }

        float adv(char c) {
            float[] g = glyph(c);
            if (g == null) {
                g = glyph('?');
            }
            return g == null ? 4 : g[6] / scale;
        }

        void bind() {
            load();
            Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
    }
}
