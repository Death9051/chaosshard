package ru.death.chaosshard.lair.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import ru.death.chaosshard.client.ChaosUi;
import ru.death.chaosshard.lair.ContainerLair;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketLairAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно Сердца Логова в духе голограммы (HUD): тёмное стекло со скошенными углами, тонкие неоновые линии,
 * скобки по углам, сетка и бегущая полоса развёртки. Всё рисуется кодом, без текстуры.
 * <p>
 * Сверху ступени роста (Ложе → Башня → Цитадель), ниже — что логово даёт сейчас (метка слева, значение справа),
 * потом что нужно для роста (отметки «есть / нет», плата в ячейках) и две кнопки. У недоступной кнопки
 * причина видна в строках требований и в подсказке.
 */
public class GuiLair extends GuiContainer {

    /** Что прислал сервер (ContainerLair → PacketLairView). */
    public static NBTTagCompound view = new NBTTagCompound();

    // палитра голограммы: фиолетовый неон Хаоса и холодный циан для данных
    private static final int GLASS = 0xF207050F, GLASS_HEAD = 0xF20C0819, GRID = 0x0EC77DFF;
    private static final int NEON = 0xFFC77DFF, NEON_MID = 0x99C77DFF, NEON_LOW = 0x40C77DFF;
    private static final int CYAN = 0xFF7DEBFF, CYAN_LOW = 0x337DEBFF;
    private static final int TXT = 0xEDE7FF, LABEL = 0xA89CC8, MUTED = 0x6A6088, OK = 0x7DF0B4, BAD = 0xFF7088, WARM = 0xFFC870;

    private static final int W = 214, H = 290, CUT = 7;
    private static final int NODE_Y = 30, NODE = 24, BAT_Y = 84, NOW_Y = 124, ROW = 11, NOW_ROWS = 6, GROW_Y = 216, BTN_Y = 268, BTN_W = 94,
            BTN_H = 16;
    private static final int[] NODE_X = {30, 95, 160};

    private List<String> hover;
    private float time;

    public GuiLair(ContainerLair c) {
        super(c);
        xSize = W;
        ySize = H;
        view = new NBTTagCompound();
    }

    private static String tr(String k, Object... a) {
        return I18n.format(k, a);
    }

    private static String up(String s) {
        return s.toUpperCase(new Locale("ru"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        time = (mc.world == null ? 0 : mc.world.getTotalWorldTime()) + partialTicks;
        hover = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hover != null) {
            drawHoveringText(hover, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ примитивы голограммы

    /** Заливка со скошенными углами. */
    private void chamferFill(int x, int y, int w, int h, int cut, int color) {
        for (int i = 0; i < cut; i++) {
            drawRect(x + cut - i, y + i, x + w - cut + i, y + i + 1, color);
            drawRect(x + cut - i, y + h - 1 - i, x + w - cut + i, y + h - i, color);
        }
        drawRect(x, y + cut, x + w, y + h - cut, color);
    }

    /** Рамка 1px со скошенными углами. */
    private void chamferLine(int x, int y, int w, int h, int cut, int color) {
        drawRect(x + cut, y, x + w - cut, y + 1, color);
        drawRect(x + cut, y + h - 1, x + w - cut, y + h, color);
        drawRect(x, y + cut, x + 1, y + h - cut, color);
        drawRect(x + w - 1, y + cut, x + w, y + h - cut, color);
        for (int i = 1; i < cut; i++) {
            drawRect(x + cut - i, y + i, x + cut - i + 1, y + i + 1, color);
            drawRect(x + w - cut + i - 1, y + i, x + w - cut + i, y + i + 1, color);
            drawRect(x + cut - i, y + h - 1 - i, x + cut - i + 1, y + h - i, color);
            drawRect(x + w - cut + i - 1, y + h - 1 - i, x + w - cut + i, y + h - i, color);
        }
    }

    /** Угловые скобки вокруг области. */
    private void brackets(int x, int y, int w, int h, int len, int color) {
        drawRect(x, y, x + len, y + 1, color);
        drawRect(x, y, x + 1, y + len, color);
        drawRect(x + w - len, y, x + w, y + 1, color);
        drawRect(x + w - 1, y, x + w, y + len, color);
        drawRect(x, y + h - 1, x + len, y + h, color);
        drawRect(x, y + h - len, x + 1, y + h, color);
        drawRect(x + w - len, y + h - 1, x + w, y + h, color);
        drawRect(x + w - 1, y + h - len, x + w, y + h, color);
    }

    /** Заголовок раздела: шеврон, подпись капителью и линия до края с засечкой. */
    private void section(String title, int y) {
        section(title, null, 0, y);
    }

    /** То же, со значением справа (линия идёт между ними). */
    private void section(String title, String right, int rightColor, int y) {
        int x = 12;
        drawRect(x, y + 2, x + 1, y + 5, CYAN);
        drawRect(x + 1, y + 3, x + 2, y + 4, CYAN);
        String t = up(title);
        fontRenderer.drawString(t, x + 5, y, CYAN & 0xFFFFFF);
        int lx = x + 9 + fontRenderer.getStringWidth(t);
        int rx = W - 12;
        if (right != null) {
            int rw = fontRenderer.getStringWidth(right);
            fontRenderer.drawString(right, rx - rw, y, rightColor);
            rx -= rw + 4;
        } else {
            drawRect(W - 16, y + 3, W - 12, y + 6, NEON_MID);
        }
        drawRect(lx, y + 4, rx, y + 5, NEON_LOW);
    }

    /** Коротко: 1234567 → «1,23M», 45000 → «45k». */
    private static String num(int v) {
        if (v >= 1000000) {
            return String.format(Locale.ROOT, "%.2fM", v / 1000000.0);
        }
        if (v >= 10000) {
            return (v / 1000) + "k";
        }
        return String.valueOf(v);
    }

    /** Батарея: сегментная шкала, запас и откуда приходит энергия. */
    private void drawBattery(int mx, int my) {
        int e = view.getInteger("energy"), cap = Math.max(1, view.getInteger("capacity"));
        section(tr("chaosshard.lair.hud.battery"), num(e) + " / " + num(cap) + " FE", TXT, BAT_Y - 12);
        int x0 = 12, segs = 38, sw = 4, gap = 1, y = BAT_Y;
        drawRect(x0 - 1, y - 1, x0 + segs * (sw + gap), y + 10, 0x60140C24);
        double part = Math.min(1.0, (double) e / cap);
        int full = (int) Math.floor(part * segs);
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.2F);
        for (int i = 0; i < segs; i++) {
            int sx = x0 + i * (sw + gap);
            int c;
            if (i < full) {
                // от фиолетового к голубому по мере заполнения
                float k = (float) i / segs;
                int r = (int) (0xC7 + (0x7D - 0xC7) * k), g = (int) (0x7D + (0xEB - 0x7D) * k), b = 0xFF;
                c = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else if (i == full && part > 0 && part < 1) {
                c = ((int) (0x50 + 0x80 * pulse) << 24) | 0x7DEBFF;
            } else {
                c = 0xFF1C1430;
            }
            drawRect(sx, y, sx + sw, y + 9, c);
            if (i < full) {
                drawRect(sx, y, sx + sw, y + 1, 0x60FFFFFF);
            }
        }
        int in = view.getInteger("inflow");
        String left = tr("chaosshard.lair.hud.inflow", in > 0 ? "+" + num(in) : "0");
        fontRenderer.drawString(left, 14, y + 13, in > 0 ? OK : MUTED);
        String right = tr("chaosshard.lair.hud.sources", view.getInteger("forges"), view.getInteger("generators"));
        fontRenderer.drawString(right, W - 14 - fontRenderer.getStringWidth(right), y + 13, LABEL);
        if (ChaosUi.in(mx, my, x0, y - 1, segs * (sw + gap), 24)) {
            hover = new ArrayList<>();
            hover.add(tr("chaosshard.lair.hud.battery") + ": " + e + " / " + cap + " FE");
            for (String l : fontRenderer.listFormattedStringToWidth(tr("chaosshard.lair.hud.battery.desc"), 200)) {
                hover.add("\u00A77" + l);
            }
        }
    }

    // ------------------------------------------------------------------ фон

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        // мягкое свечение вокруг стекла
        chamferLine(x - 2, y - 2, W + 4, H + 4, CUT + 1, 0x18C77DFF);
        chamferLine(x - 1, y - 1, W + 2, H + 2, CUT, 0x30C77DFF);
        chamferFill(x, y, W, H, CUT, GLASS);
        // шапка
        drawRect(x + CUT, y + 1, x + W - CUT, y + 22, GLASS_HEAD);
        // сетка 8px
        for (int gx = x + 8; gx < x + W - 4; gx += 8) {
            drawRect(gx, y + 23, gx + 1, y + H - 4, GRID);
        }
        for (int gy = y + 24; gy < y + H - 4; gy += 8) {
            drawRect(x + 4, gy, x + W - 4, gy + 1, GRID);
        }
        // бегущая полоса развёртки
        int scan = (int) (time * 0.9F) % (H + 30) - 15;
        if (scan > 22 && scan < H - 4) {
            drawRect(x + 2, y + scan, x + W - 2, y + scan + 1, 0x22C77DFF);
            drawRect(x + 2, y + scan - 3, x + W - 2, y + scan, 0x0AC77DFF);
        }
        chamferLine(x, y, W, H, CUT, NEON_MID);
        brackets(x + 3, y + 3, W - 6, H - 6, 9, CYAN);
        // черта под шапкой с вырезом посередине
        drawRect(x + 10, y + 22, x + W / 2 - 14, y + 23, NEON_MID);
        drawRect(x + W / 2 + 14, y + 22, x + W - 10, y + 23, NEON_MID);
        drawRect(x + W / 2 - 10, y + 21, x + W / 2 + 10, y + 22, NEON);
        GlStateManager.color(1, 1, 1, 1);
    }

    // ------------------------------------------------------------------ содержимое

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        String title = up(tr("tile.chaosshard.lair_core.name"));
        fontRenderer.drawString(title, 13, 8, TXT);
        if (!view.hasKey("level")) {
            boolean on = ((int) (time / 8)) % 2 == 0;
            String w = tr("chaosshard.lair.gui.wait");
            fontRenderer.drawString(w, (W - fontRenderer.getStringWidth(w)) / 2, 110, on ? LABEL : MUTED);
            return;
        }
        int lv = view.getInteger("level");
        // метка уровня справа в шапке с мигающим огоньком
        String lvl = tr("chaosshard.lair.hud.level", lv);
        int lw = fontRenderer.getStringWidth(lvl);
        fontRenderer.drawString(lvl, W - 13 - lw, 8, CYAN & 0xFFFFFF);
        if (((int) (time / 10)) % 2 == 0) {
            drawRect(W - 19 - lw, 10, W - 16 - lw, 13, 0xFF7DF0B4);
        }
        drawSteps(lv, mx, my);
        drawBattery(mx, my);
        drawNow(lv);
        drawGrow(mx, my);
        drawButtons(mx, my);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static ItemStack levelIcon(int lv) {
        return lv == 1 ? new ItemStack(Items.BED, 1, 10) : lv == 2 ? new ItemStack(Blocks.PURPUR_PILLAR) : new ItemStack(Items.NETHER_STAR);
    }

    /** Ступени роста: узлы со скошенными углами, пунктир между ними, у текущей — пульс. */
    private void drawSteps(int lv, int mx, int my) {
        for (int i = 0; i < 2; i++) {
            int x0 = NODE_X[i] + NODE + 3, x1 = NODE_X[i + 1] - 3, yy = NODE_Y + NODE / 2;
            boolean done = i + 2 <= lv;
            for (int x = x0; x < x1; x += 4) {
                drawRect(x, yy, Math.min(x + 2, x1), yy + 1, done ? NEON : 0x553A3150);
            }
            if (done) {
                int run = x0 + (int) (time * 0.6F) % Math.max(1, x1 - x0);
                drawRect(run, yy - 1, run + 2, yy + 2, CYAN);
            }
        }
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.15F);
        for (int i = 0; i < 3; i++) {
            int n = i + 1, x = NODE_X[i];
            boolean cur = n == lv, done = n < lv;
            if (cur) {
                int a = (int) (0x30 + 0x40 * pulse);
                chamferLine(x - 2, NODE_Y - 2, NODE + 4, NODE + 4, 5, (a << 24) | 0xFFC870);
            }
            chamferFill(x, NODE_Y, NODE, NODE, 4, cur ? 0xE0221430 : 0xD0100A1C);
            chamferLine(x, NODE_Y, NODE, NODE, 4, cur ? 0xFFFFC870 : done ? NEON : 0xFF3A3150);
            GlStateManager.color(1, 1, 1, 1);
            ChaosUi.item(levelIcon(n), x + 4, NODE_Y + 4);
            if (n > lv) {
                chamferFill(x + 1, NODE_Y + 1, NODE - 2, NODE - 2, 3, 0xB0080610);
            }
            String name = up(tr("chaosshard.lair.gui.step_" + n));
            int c = cur ? WARM : done ? (NEON & 0xFFFFFF) : MUTED;
            fontRenderer.drawString(name, x + NODE / 2 - fontRenderer.getStringWidth(name) / 2, NODE_Y + NODE + 5, c);
            if (ChaosUi.in(mx, my, x, NODE_Y, NODE, NODE)) {
                hover = new ArrayList<>();
                hover.add(tr("chaosshard.lair.gui.step_" + n) + (cur ? " §7" + tr("chaosshard.lair.gui.current") : ""));
                hover.add("§7" + tr("chaosshard.lair.gui.step_" + n + ".desc"));
            }
        }
    }

    /** Строка данных: метка слева, точки-направляющие, значение справа. */
    private void dataRow(int y, String label, String value, int valueColor, boolean locked) {
        int x0 = 16, x1 = W - 16;
        if (locked) {
            ChaosUi.icon(x0 - 1, y - 1, ChaosUi.I_LOCK, 0.6F);
            x0 += 10;
        }
        fontRenderer.drawString(label, x0, y, locked ? MUTED : LABEL);
        int vw = fontRenderer.getStringWidth(value);
        fontRenderer.drawString(value, x1 - vw, y, valueColor);
        int dotFrom = x0 + fontRenderer.getStringWidth(label) + 4, dotTo = x1 - vw - 4;
        for (int x = dotFrom; x < dotTo; x += 3) {
            drawRect(x, y + 6, x + 1, y + 7, locked ? 0x30FFFFFF : 0x45C77DFF);
        }
    }

    private void drawNow(int lv) {
        section(tr("chaosshard.lair.hud.now"), NOW_Y - 12);
        chamferFill(10, NOW_Y - 2, W - 20, ROW * NOW_ROWS + 6, 3, 0x60140C24);
        chamferLine(10, NOW_Y - 2, W - 20, ROW * NOW_ROWS + 6, 3, NEON_LOW);
        int y = NOW_Y + 2;
        dataRow(y, tr("chaosshard.lair.hud.radius"), tr("chaosshard.lair.hud.blocks", view.getInteger("radius")), CYAN & 0xFFFFFF, false);
        y += ROW;
        int power = view.getInteger("power"), base = view.getInteger("base");
        String pw = tr("chaosshard.lair.hud.fe", power);
        if (power > base && base > 0) {
            pw = "+" + Math.round(100.0 * (power - base) / base) + "%  " + pw;
        }
        dataRow(y, tr("chaosshard.lair.hud.power"), pw, CYAN & 0xFFFFFF, false);
        y += ROW;
        dataRow(y, tr("chaosshard.lair.hud.output"), tr("chaosshard.lair.hud.fe", num(view.getInteger("output"))), CYAN & 0xFFFFFF, false);
        y += ROW;
        dataRow(y, tr("chaosshard.lair.hud.rest"), tr(lv >= 3 ? "chaosshard.lair.hud.regen_2" : "chaosshard.lair.hud.regen_1"), CYAN & 0xFFFFFF, false);
        y += ROW;
        boolean cit = lv >= 3;
        dataRow(y, tr("chaosshard.lair.hud.nospawn"), tr(cit ? "chaosshard.lair.hud.on" : "chaosshard.lair.hud.citadel_only"), cit ? OK : MUTED, !cit);
        y += ROW;
        dataRow(y, tr("chaosshard.lair.hud.mend"), tr(cit ? "chaosshard.lair.hud.on" : "chaosshard.lair.hud.citadel_only"), cit ? OK : MUTED, !cit);
    }

    /** Галочка или крестик в квадратной ячейке. */
    private void tick(int x, int y, boolean ok) {
        drawRect(x, y, x + 9, y + 9, ok ? 0x407DF0B4 : 0x40FF7088);
        chamferLine(x, y, 9, 9, 1, ok ? 0xFF7DF0B4 : 0xFFFF7088);
        if (ok) {
            drawRect(x + 2, y + 4, x + 3, y + 6, 0xFF7DF0B4);
            drawRect(x + 3, y + 5, x + 4, y + 7, 0xFF7DF0B4);
            drawRect(x + 4, y + 4, x + 5, y + 6, 0xFF7DF0B4);
            drawRect(x + 5, y + 3, x + 6, y + 5, 0xFF7DF0B4);
            drawRect(x + 6, y + 2, x + 7, y + 4, 0xFF7DF0B4);
        } else {
            for (int i = 0; i < 5; i++) {
                drawRect(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFFFF7088);
                drawRect(x + 6 - i, y + 2 + i, x + 7 - i, y + 3 + i, 0xFFFF7088);
            }
        }
    }

    private void drawGrow(int mx, int my) {
        if (!view.hasKey("next")) {
            section(tr("chaosshard.lair.hud.grow_max"), GROW_Y - 12);
            chamferLine(10, GROW_Y - 2, W - 20, 42, 3, NEON_LOW);
            String s = tr("chaosshard.lair.max");
            List<String> lines = fontRenderer.listFormattedStringToWidth(s, W - 40);
            for (int i = 0; i < lines.size(); i++) {
                fontRenderer.drawString(lines.get(i), (W - fontRenderer.getStringWidth(lines.get(i))) / 2, GROW_Y + 10 + i * 10, WARM);
            }
            return;
        }
        int next = view.getInteger("next");
        section(tr("chaosshard.lair.hud.grow", tr("chaosshard.lair.gui.step_" + next)), GROW_Y - 12);
        int y = GROW_Y;
        boolean land = next < 3 ? view.getBoolean("land") : view.getBoolean("pillar");
        tick(14, y - 1, land);
        fontRenderer.drawString(next < 3 ? tr("chaosshard.lair.gui.need_land") : tr("chaosshard.lair.gui.need_pillar", view.getInteger("pillarLevel")),
                28, y, land ? TXT : BAD);
        y += ROW;
        int nit = view.getInteger("nit"), need = view.getInteger("nitNeed");
        boolean nitOk = nit >= need || view.getBoolean("creative");
        tick(14, y - 1, nitOk);
        fontRenderer.drawString(tr("chaosshard.lair.gui.need_thread", need, nit), 28, y, nitOk ? TXT : BAD);
        y += ROW + 3;
        NBTTagList cost = view.getTagList("cost", 10);
        for (int i = 0; i < cost.tagCount(); i++) {
            NBTTagCompound t = cost.getCompoundTagAt(i);
            ItemStack st = new ItemStack(t);
            int cx = 12 + i * 64, cw = 60;
            int have = t.getInteger("have"), n = t.getInteger("need");
            boolean ok = have >= n || view.getBoolean("creative");
            chamferFill(cx, y, cw, 20, 3, ok ? 0x30164030 : 0x30401018);
            chamferLine(cx, y, cw, 20, 3, ok ? 0x907DF0B4 : 0x90FF7088);
            GlStateManager.color(1, 1, 1, 1);
            ChaosUi.item(st, cx + 3, y + 2);
            String c = Math.min(have, 999) + "/" + n;
            fontRenderer.drawString(c, cx + cw - 4 - fontRenderer.getStringWidth(c), y + 6, ok ? OK : BAD);
            if (ChaosUi.in(mx, my, cx, y, cw, 20)) {
                hover = new ArrayList<>();
                hover.add(st.getDisplayName());
                hover.add("§7" + tr("chaosshard.lair.gui.have", have, n));
            }
        }
    }

    /** Первая причина, по которой расти нельзя (null — можно). */
    private String blocker() {
        if (!view.hasKey("next")) {
            return tr("chaosshard.lair.max");
        }
        int next = view.getInteger("next");
        if (!view.getBoolean("land")) {
            return tr("chaosshard.lair.need_land");
        }
        if (next >= 3 && !view.getBoolean("pillar")) {
            return tr("chaosshard.lair.need_pillar");
        }
        if (view.getBoolean("creative")) {
            return null;
        }
        if (view.getInteger("nit") < view.getInteger("nitNeed")) {
            return tr("chaosshard.lair.thin", view.getInteger("nitNeed"));
        }
        NBTTagList cost = view.getTagList("cost", 10);
        for (int i = 0; i < cost.tagCount(); i++) {
            if (cost.getCompoundTagAt(i).getInteger("have") < cost.getCompoundTagAt(i).getInteger("need")) {
                return tr("chaosshard.lair.cost_" + next);
            }
        }
        return null;
    }

    /** Кнопка голограммы: скошенные углы, заливка под мышью, недоступная — пунктиром. */
    private void hudButton(int x, int y, String label, int state, int accent) {
        if (state == 2) {
            for (int i = x + 3; i < x + BTN_W - 3; i += 4) {
                drawRect(i, y, i + 2, y + 1, 0x60FFFFFF & accent);
                drawRect(i, y + BTN_H - 1, i + 2, y + BTN_H, 0x60FFFFFF & accent);
            }
            drawRect(x, y + 3, x + 1, y + BTN_H - 3, 0x60FFFFFF & accent);
            drawRect(x + BTN_W - 1, y + 3, x + BTN_W, y + BTN_H - 3, 0x60FFFFFF & accent);
        } else {
            chamferFill(x, y, BTN_W, BTN_H, 3, state == 1 ? (0x50000000 | (accent & 0xFFFFFF)) : 0x28000000 | (accent & 0xFFFFFF));
            chamferLine(x, y, BTN_W, BTN_H, 3, accent);
            if (state == 1) {
                drawRect(x + 4, y + BTN_H - 2, x + BTN_W - 4, y + BTN_H - 1, 0xFFFFFFFF);
            }
        }
        String t = fontRenderer.trimStringToWidth(label, BTN_W - 8);
        int col = state == 2 ? MUTED : state == 1 ? 0xFFFFFF : TXT;
        fontRenderer.drawString(t, x + (BTN_W - fontRenderer.getStringWidth(t)) / 2, y + 4, col);
    }

    private void drawButtons(int mx, int my) {
        boolean spawn = view.getBoolean("spawn");
        int sx = 10, gx = W - 10 - BTN_W;
        boolean overS = ChaosUi.in(mx, my, sx, BTN_Y, BTN_W, BTN_H);
        if (spawn) {
            chamferFill(sx, BTN_Y, BTN_W, BTN_H, 3, 0x30164030);
            chamferLine(sx, BTN_Y, BTN_W, BTN_H, 3, 0xFF7DF0B4);
            tick(sx + 4, BTN_Y + 3, true);
            String t = fontRenderer.trimStringToWidth(tr("chaosshard.lair.gui.spawn_here"), BTN_W - 18);
            fontRenderer.drawString(t, sx + 16, BTN_Y + 4, OK);
        } else {
            hudButton(sx, BTN_Y, tr("chaosshard.lair.gui.spawn"), overS ? 1 : 0, 0xFF7DEBFF);
        }
        if (overS) {
            hover = new ArrayList<>();
            hover.add(tr(spawn ? "chaosshard.lair.gui.spawn_here.desc" : "chaosshard.lair.gui.spawn.desc"));
        }
        String why = blocker();
        boolean overG = ChaosUi.in(mx, my, gx, BTN_Y, BTN_W, BTN_H);
        String label = view.hasKey("next") ? tr("chaosshard.lair.gui.grow_btn", tr("chaosshard.lair.gui.step_" + view.getInteger("next")))
                : tr("chaosshard.lair.gui.grow_done");
        hudButton(gx, BTN_Y, label, why != null ? 2 : overG ? 1 : 0, 0xFFFFC870);
        if (overG) {
            hover = new ArrayList<>();
            hover.add(why != null ? "§c" + why : tr("chaosshard.lair.gui.grow_btn.desc"));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || !view.hasKey("level")) {
            return;
        }
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        if (ChaosUi.in(mx, my, 10, BTN_Y, BTN_W, BTN_H) && !view.getBoolean("spawn")) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketLairAction(PacketLairAction.SPAWN));
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.4F);
        } else if (ChaosUi.in(mx, my, W - 10 - BTN_W, BTN_Y, BTN_W, BTN_H) && blocker() == null) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketLairAction(PacketLairAction.GROW));
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.4F);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
