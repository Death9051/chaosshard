package ru.death.chaosshard.client.magic;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketSelectSpell;
import ru.death.chaosshard.ritual.Ritual;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellCaster;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно магии Гримуара: вкладки «Заклинания», «Ритуалы», «Слуги».
 * Слева — список (просторные строки, прокрутка колесом), справа — страница выбранного.
 * У ритуалов и призывов — что нужно положить, сколько этого в сумке, и кнопка «Сложить в Чашу».
 */
public class GuiGrimoireMagic extends GuiScreen {

    /** Высота строки списка. */
    static final int ROW = 22;
    private static final int TAB_W = 104, TAB_H = 20;
    /** Как близко должна быть Чаша, чтобы сложить в неё подношения. */
    public static final int BOWL_REACH = 6;

    // цвета
    static final int C_TITLE = 0xE6CCFF, C_TEXT = 0xD2CBE0, C_SOFT = 0x9C90B4, C_DIM = 0x5E566C, C_GOLD = 0xFFD27A,
            C_GOOD = 0x8FD07E, C_BAD = 0xE07A7A, C_VIOLET = 0xB89CFF;

    private static int tab;            // 0 — заклинания, 1 — ритуалы, 2 — слуги (запоминается между открытиями)
    private static int selBeast = -1;  // -1 — страница «Отголоски»
    private static int selSpell;
    private static int selRitual = -1; // -1 — страница «Как провести ритуал»
    private static final int[] scroll = new int[3];

    private final ItemStack grimoire;
    private final List<Spell> spells = new ArrayList<>();
    private int left, top, w, h, listW;
    private GuiButton choose, reset, call, fill;
    private final BeastPage beasts = new BeastPage(this);
    /** Ритуал на открытой странице, подношения которого можно сложить в Чашу (null — нечего). */
    private Ritual fillTarget;
    private List<String> hoverText;

    public GuiGrimoireMagic(ItemStack grimoire) {
        this.grimoire = grimoire;
        for (Spell s : Spell.values()) {
            if (s != Spell.BOOK) {
                spells.add(s);
            }
        }
        if (selSpell >= spells.size()) {
            selSpell = 0;
        }
    }

    /** Для теста клиента: открыть на вкладке, выбрать ритуал (индекс в списке ритуалов) и прокрутить. */
    public static void testState(int t, int ritual, int sc) {
        tab = t;
        selRitual = ritual;
        scroll[t] = sc;
    }

    /** Номер ритуала в списке вкладки «Ритуалы» (-1, если его там нет). */
    public static int shownRitualIndex(Ritual r) {
        return shownRituals().indexOf(r);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        w = Math.max(344, Math.min(width - 40, 440));
        h = Math.max(244, Math.min(height - 56, 300));
        listW = w >= 400 ? 140 : 126;
        left = (width - w) / 2;
        top = (height - h + TAB_H) / 2;
        buttonList.clear();
        choose = new GuiButton(1, left + w - 112, top + h - 30, 100, 20, I18n.format("chaosshard.magic.choose"));
        buttonList.add(choose);
        reset = new GuiButton(2, 0, 0, 96, 20, I18n.format("chaosshard.beast.gui.reset"));
        reset.visible = false;
        buttonList.add(reset);
        call = new GuiButton(3, 0, 0, 60, 20, I18n.format("chaosshard.beast.gui.call"));
        call.visible = false;
        buttonList.add(call);
        fill = new GuiButton(4, left + w - 132, top + h - 30, 120, 20, I18n.format("chaosshard.magic.fill"));
        fill.visible = false;
        buttonList.add(fill);
        updateButton();
    }

    private void updateButton() {
        if (choose == null) {
            return;
        }
        Spell s = spells.get(selSpell);
        choose.visible = tab == 0;
        choose.enabled = MagicUi.unlocked(s);
        boolean current = SpellCaster.magicMode(grimoire) && SpellCaster.selected(grimoire) == s;
        choose.displayString = I18n.format(current ? "chaosshard.magic.chosen" : "chaosshard.magic.choose");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 4 && fillTarget != null) {
            ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketRitualFill(fillTarget));
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                    net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP, 0.8F));
            return;
        }
        if (button.id == 3 && tab == 2 && selBeast >= 0 && selBeast < 3) {
            ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketBeastAction(
                    ru.death.chaosshard.servant.BeastData.Beast.values()[selBeast], 3, 0));
            return;
        }
        if (button.id == 2 && tab == 2 && selBeast >= 0 && selBeast < 3) {
            ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketBeastAction(
                    ru.death.chaosshard.servant.BeastData.Beast.values()[selBeast], 1, 0));
            return;
        }
        if (button.id == 1 && tab == 0) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketSelectSpell(spells.get(selSpell)));
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        beasts.tick();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        if (beasts.editing()) {
            beasts.finishEdit(true);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        super.mouseClickMove(mx, my, button, time);
        beasts.drag(mx);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        beasts.release();
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (tab == 2 && beasts.key(c, key)) {
            return;
        }
        if (key == ru.death.chaosshard.client.MagicKeys.BOOK.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        if (tab == 2 && selBeast >= 0 && selBeast < 3
                && (beasts.clickTitle(ru.death.chaosshard.servant.BeastData.Beast.values()[selBeast], mx, my) || beasts.press(mx, my))) {
            return;
        }
        if (beasts.editing()) {
            beasts.finishEdit(true);
        }
        super.mouseClicked(mx, my, button);
        // вкладки
        for (int i = 0; i < 3; i++) {
            int tx = left + 10 + i * (TAB_W + 4);
            if (mx >= tx && mx < tx + TAB_W && my >= top - TAB_H && my < top) {
                if (tab != i) {
                    mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                            net.minecraft.init.SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
                }
                tab = i;
                updateButton();
                return;
            }
        }
        if (tab == 0 && clickSpell(mx, my)) {
            return;
        }
        // список
        int idx = rowAt(mx, my);
        if (idx >= 0) {
            if (tab == 0 && idx < spells.size()) {
                selSpell = idx;
            } else if (tab == 1 && idx <= shownRituals().size()) {
                selRitual = idx - 1;
            } else if (tab == 2 && idx < BeastPage.ROWS && idx != BeastPage.HEADER_ROW) {
                selBeast = idx - 1;
            } else {
                return;
            }
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                    net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.6F));
            updateButton();
            return;
        }
        if (tab == 2 && selBeast >= 0 && selBeast < 3) {
            beasts.click(ru.death.chaosshard.servant.BeastData.Beast.values()[selBeast], mx, my);
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) {
            scroll[tab] = clampScroll(scroll[tab] + (d > 0 ? -1 : 1), listSize());
        }
    }

    // ------------------------------------------------------------------ список (общий для вкладок)

    int listX() { return left + 10; }
    int listTop() { return top + 12; }
    int visibleRows() { return (h - 44) / ROW; }

    private int listSize() {
        return tab == 0 ? spells.size() : tab == 1 ? shownRituals().size() + 1 : BeastPage.ROWS;
    }

    private int clampScroll(int v, int rows) {
        return Math.max(0, Math.min(Math.max(0, rows - visibleRows()), v));
    }

    /** y строки idx или MIN_VALUE, если она за краем. */
    private int rowY(int idx) {
        int i = idx - scroll[tab];
        return i < 0 || i >= visibleRows() ? Integer.MIN_VALUE : listTop() + i * ROW;
    }

    private int rowAt(int mx, int my) {
        if (mx < listX() || mx >= listX() + listW || my < listTop() || my >= listTop() + visibleRows() * ROW) {
            return -1;
        }
        return scroll[tab] + (my - listTop()) / ROW;
    }

    /** Строка списка: карточка с значком и названием; выбранная — с полосой слева. */
    void drawRow(int idx, ItemStack icon, String name, int color, boolean selected, int mx, int my) {
        int y = rowY(idx);
        if (y == Integer.MIN_VALUE) {
            return;
        }
        int x0 = listX(), x1 = listX() + listW, y1 = y + ROW - 3;
        boolean hover = mx >= x0 && mx < x1 && my >= y && my < y1;
        if (selected) {
            drawGradientRect(x0, y, x1, y1, 0xB04A2A78, 0x902A1648);
            Gui.drawRect(x0, y, x0 + 2, y1, 0xFFD8B4FF);
        } else if (hover) {
            Gui.drawRect(x0, y, x1, y1, 0x30C8A8F0);
        } else {
            Gui.drawRect(x0, y, x1, y1, 0x16FFFFFF);
        }
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(icon, x0 + 5, y + (ROW - 3 - 16) / 2);
        RenderHelper.disableStandardItemLighting();
        fontRenderer.drawString(fontRenderer.trimStringToWidth(name, listW - 32), x0 + 26, y + (ROW - 3 - 8) / 2 + 1,
                selected ? 0xFFF0E0FF : color);
    }

    /** Подзаголовок в списке (не выбирается). */
    void drawHeader(int idx, String text) {
        int y = rowY(idx);
        if (y == Integer.MIN_VALUE) {
            return;
        }
        int cx = listX() + listW / 2, tw = fontRenderer.getStringWidth(text);
        int ly = y + ROW / 2 - 1;
        Gui.drawRect(listX() + 4, ly, cx - tw / 2 - 5, ly + 1, 0x606E5296);
        Gui.drawRect(cx + tw / 2 + 5, ly, listX() + listW - 4, ly + 1, 0x606E5296);
        drawCenteredString(fontRenderer, text, cx, y + ROW / 2 - 5, C_SOFT);
    }

    // ------------------------------------------------------------------ рамки

    /** Окно-книга: тёмный градиент, двойная окантовка, уголки. */
    private void panel(int x, int y, int pw, int ph) {
        Gui.drawRect(x - 2, y - 2, x + pw + 2, y + ph + 2, 0xFF06030A);
        drawGradientRect(x, y, x + pw, y + ph, 0xF5211534, 0xF50D0716);
        int b = 0xFF7A5CA6;
        Gui.drawRect(x, y, x + pw, y + 1, b);
        Gui.drawRect(x, y + ph - 1, x + pw, y + ph, b);
        Gui.drawRect(x, y, x + 1, y + ph, b);
        Gui.drawRect(x + pw - 1, y, x + pw, y + ph, b);
        int in = 0x44C8A8F0;
        Gui.drawRect(x + 3, y + 3, x + pw - 3, y + 4, in);
        Gui.drawRect(x + 3, y + ph - 4, x + pw - 3, y + ph - 3, in);
        Gui.drawRect(x + 3, y + 3, x + 4, y + ph - 3, in);
        Gui.drawRect(x + pw - 4, y + 3, x + pw - 3, y + ph - 3, in);
        corner(x, y);
        corner(x + pw - 1, y);
        corner(x, y + ph - 1);
        corner(x + pw - 1, y + ph - 1);
    }

    /** Золотой ромбик в углу рамки. */
    private static void corner(int cx, int cy) {
        int g = 0xFFD8B878, d = 0xFF8A6A3A;
        Gui.drawRect(cx - 2, cy - 2, cx + 3, cy + 3, d);
        Gui.drawRect(cx - 1, cy - 3, cx + 2, cy + 4, d);
        Gui.drawRect(cx - 3, cy - 1, cx + 4, cy + 2, d);
        Gui.drawRect(cx - 1, cy - 1, cx + 2, cy + 2, g);
    }

    private void tabButton(int i, String label, ItemStack icon, int mx, int my) {
        int tx = left + 10 + i * (TAB_W + 4), ty = top - TAB_H;
        boolean on = i == tab;
        boolean hover = mx >= tx && mx < tx + TAB_W && my >= ty && my < top;
        Gui.drawRect(tx - 1, ty - 1, tx + TAB_W + 1, top, 0xFF06030A);
        if (on) {
            drawGradientRect(tx, ty, tx + TAB_W, top + 1, 0xF5302048, 0xF5211534);
        } else {
            Gui.drawRect(tx, ty, tx + TAB_W, top, hover ? 0xF0201630 : 0xF0140C1E);
        }
        int b = on ? 0xFF7A5CA6 : 0xFF4A3A60;
        Gui.drawRect(tx, ty, tx + TAB_W, ty + 1, b);
        Gui.drawRect(tx, ty, tx + 1, top, b);
        Gui.drawRect(tx + TAB_W - 1, ty, tx + TAB_W, top, b);
        if (on) {
            Gui.drawRect(tx + 1, top - 1, tx + TAB_W - 1, top + 1, 0xF5211534);
        }
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(icon, tx + 6, ty + 2);
        RenderHelper.disableStandardItemLighting();
        fontRenderer.drawString(label, tx + 26, ty + 6, on ? C_TITLE : hover ? C_TEXT : 0x8A8098);
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        hoverText = null;
        fillTarget = null;
        tabButton(0, I18n.format("chaosshard.magic.tab.spells"), new ItemStack(ModItems.CHAOS_ESSENCE), mx, my);
        tabButton(1, I18n.format("chaosshard.magic.tab.rituals"), new ItemStack(ModItems.CHALK), mx, my);
        tabButton(2, I18n.format("chaosshard.magic.tab.servants"), new ItemStack(net.minecraft.init.Items.RABBIT_FOOT), mx, my);
        panel(left, top, w, h);
        // подложка списка
        Gui.drawRect(listX() - 3, top + 8, listX() + listW + 3, top + h - 26, 0x50000000);
        Gui.drawRect(listX() - 3, top + 8, listX() + listW + 3, top + 9, 0x406E5296);
        Gui.drawRect(listX() - 3, top + h - 27, listX() + listW + 3, top + h - 26, 0x406E5296);

        scroll[tab] = clampScroll(scroll[tab], listSize());
        reset.visible = false;
        call.visible = false;
        if (tab == 0) {
            drawSpells(mx, my);
        } else if (tab == 1) {
            drawRituals(mx, my);
        } else {
            beasts.draw(selBeast, mx, my, reset, call);
        }
        // низ списка: Нить и стрелки прокрутки
        fontRenderer.drawString(I18n.format("chaosshard.magic.thread", MagicUi.threadLevel()), listX(), top + h - 19, C_SOFT);
        if (listSize() > visibleRows()) {
            int ax = listX() + listW - 8;
            drawCenteredString(fontRenderer, "▲", ax - 10, top + h - 19, scroll[tab] > 0 ? C_TITLE : 0x4A4058);
            drawCenteredString(fontRenderer, "▼", ax, top + h - 19, scroll[tab] + visibleRows() < listSize() ? C_TITLE : 0x4A4058);
        }
        if (tab == 0) {
            int ex = pageX(), ey = top + h - 28, ew = Math.min(130, pageW() - 124);
            int e = ru.death.chaosshard.client.ClientThreadCache.energy(), m = ru.death.chaosshard.client.ClientThreadCache.maxEnergy();
            fontRenderer.drawString(I18n.format("chaosshard.magic.energy", e, m), ex, ey, C_VIOLET);
            Gui.drawRect(ex, ey + 11, ex + ew, ey + 15, 0xAA1A0A2A);
            drawGradientRect(ex, ey + 11, ex + ew * Math.min(e, m) / Math.max(1, m), ey + 15, 0xFFD0A0FF, 0xFF8A50E0);
        }
        // кнопка «Сложить в Чашу»
        fill.visible = fillTarget != null;
        if (fill.visible) {
            boolean near = nearBowl() != null;
            fill.enabled = near;
            if (mx >= fill.x && mx < fill.x + fill.width && my >= fill.y && my < fill.y + 20) {
                hoverText = split(I18n.format(near ? "chaosshard.magic.fill.desc" : "chaosshard.magic.fill.far", BOWL_REACH));
            }
        }
        super.drawScreen(mx, my, pt);
        if (tab == 2) {
            beasts.tooltip(mx, my);
        }
        if (hoverText != null) {
            drawHoveringText(hoverText, mx, my);
        }
    }

    /** Ближайшая Чаша в BOWL_REACH блоках от игрока (по загруженному миру клиента) или null. */
    private BlockPos nearBowl() {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        BlockPos p = new BlockPos(mc.player);
        for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-BOWL_REACH, -3, -BOWL_REACH), p.add(BOWL_REACH, 3, BOWL_REACH))) {
            if (mc.world.getBlockState(q).getBlock() == ModBlocks.RITUAL_BOWL) {
                return q.toImmutable();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ для страницы слуг

    int left() { return left; }
    int top() { return top; }
    int pageX() { return listX() + listW + 16; }
    int pageW() { return left + w - 14 - pageX(); }
    int pageBottom() { return top + h - 36; }
    int listW() { return listW; }
    net.minecraft.client.gui.FontRenderer font() { return fontRenderer; }
    net.minecraft.client.renderer.RenderItem items() { return itemRender; }
    void text(String t, int x, int y, int c) { drawString(fontRenderer, t, x, y, c); }
    int paragraph(String t, int x, int y, int w, int c) { return para(t, x, y, w, c); }
    void hover(java.util.List<String> lines, int x, int y) { drawHoveringText(lines, x, y); }
    /** Кнопка «Сложить в Чашу» для ритуала r в точке (x, y); x правого края кнопки. */
    void setFillTarget(Ritual r, int right, int y) {
        fillTarget = r;
        fill.x = right - fill.width;
        fill.y = y;
    }
    int fillWidth() { return fill.width; }
    int title(String t, int x, int y, int color) { return pageTitle(t, x, y, color); }
    /** Подношения ритуала в ряд; возвращает x после ряда. */
    int offerRow(Ritual r, int x, int y, int mx, int my, List<String>[] hover) { return drawOfferRow(r, x, y, mx, my, hover); }

    /** Заголовок страницы с тонкой чертой под ним. Возвращает y для текста. */
    private int pageTitle(String t, int x, int y, int color) {
        drawString(fontRenderer, "§l" + t, x, y, color);
        Gui.drawRect(x, y + 12, x + pageW(), y + 13, 0x606E5296);
        Gui.drawRect(x, y + 12, x + Math.min(pageW(), fontRenderer.getStringWidth("§l" + t) + 6), y + 13, 0xC0B48CE0);
        return y + 19;
    }

    /** Подзаголовок раздела. */
    private int section(String t, int x, int y) {
        drawString(fontRenderer, t, x, y, C_SOFT);
        return y + 12;
    }

    // ------------------------------------------------------------------ заклинания

    private void drawSpells(int mx, int my) {
        Spell current = SpellCaster.magicMode(grimoire) ? SpellCaster.selected(grimoire) : null;
        for (int i = 0; i < spells.size(); i++) {
            Spell s = spells.get(i);
            boolean open = MagicUi.unlocked(s);
            drawRow(i, MagicUi.icon(s), I18n.format(s.key()), !open ? C_DIM : s == current ? C_GOLD : C_TEXT, i == selSpell, mx, my);
        }

        Spell s = spells.get(selSpell);
        int x = pageX(), y = top + 12, pw = pageW();
        boolean open = MagicUi.unlocked(s);
        y = pageTitle(I18n.format(s.key()), x, y, open ? C_TITLE : 0x8A8098);
        y = para(I18n.format(s.key() + ".desc"), x, y, pw, C_TEXT) + 6;
        boolean enough = ru.death.chaosshard.client.ClientThreadCache.energy() >= SpellCaster.cost(mc.player, s);
        String req = I18n.format("chaosshard.magic.need_thread", s.minThread);
        drawString(fontRenderer, req, x, y, open ? C_GOOD : C_BAD);
        int rx = x + fontRenderer.getStringWidth(req) + 10;
        String costS = I18n.format("chaosshard.magic.cost", SpellCaster.cost(mc.player, s));
        drawString(fontRenderer, costS, rx, y, enough ? C_VIOLET : C_BAD);
        drawString(fontRenderer, I18n.format("chaosshard.magic.cooldown", cooldown(SpellCaster.cooldown(mc.player, s))),
                rx + fontRenderer.getStringWidth(costS) + 10, y, 0xB0A8C0);
        y += 16;
        // формы
        formRects.clear();
        String[] forms = ru.death.chaosshard.spell.SpellPerks.forms(s);
        if (forms.length > 1) {
            drawString(fontRenderer, I18n.format("chaosshard.magic.forms"), x, y + 4, C_SOFT);
            int fx = x + fontRenderer.getStringWidth(I18n.format("chaosshard.magic.forms")) + 8;
            int cur = ru.death.chaosshard.spell.SpellData.form(mc.player, s);
            for (int f = 0; f < forms.length; f++) {
                String fn = I18n.format(ru.death.chaosshard.spell.SpellPerks.formKey(s, f));
                int fw = fontRenderer.getStringWidth(fn) + 12;
                boolean fo = ru.death.chaosshard.spell.SpellData.formOpen(mc.player, s, f);
                boolean over = mx >= fx && mx < fx + fw && my >= y && my < y + 15;
                int bg = f == cur ? 0xD04A2A78 : over && fo ? 0x90402A60 : fo ? 0x80241830 : 0x60201A28;
                Gui.drawRect(fx, y, fx + fw, y + 15, bg);
                if (f == cur) {
                    Gui.drawRect(fx, y + 14, fx + fw, y + 15, 0xFFD8B4FF);
                }
                drawString(fontRenderer, fn, fx + 6, y + 4, f == cur ? 0xFFF0E0FF : fo ? C_TEXT : C_DIM);
                formRects.add(new int[]{fx, y, fw, 15, f});
                fx += fw + 4;
            }
            y += 22;
        }
        // перки
        perkRects.clear();
        ru.death.chaosshard.spell.SpellPerks.Perk[] perks = ru.death.chaosshard.spell.SpellPerks.perks(s);
        int points = ru.death.chaosshard.spell.SpellData.points(mc.player);
        int free = ru.death.chaosshard.spell.SpellData.free(mc.player, s);
        drawString(fontRenderer, I18n.format("chaosshard.magic.perks", free, points), x, y, free > 0 ? C_GOLD : C_SOFT);
        y += 14;
        String hoverPerk = null;
        for (int k = 0; k < perks.length; k++) {
            ru.death.chaosshard.spell.SpellPerks.Perk pk = perks[k];
            int r = ru.death.chaosshard.spell.SpellData.rank(mc.player, s, pk.id);
            boolean can = r < pk.max && free > 0 && open;
            boolean over = mx >= x && mx < x + pw && my >= y - 1 && my < y + 15;
            if (over) {
                Gui.drawRect(x - 2, y - 2, x + pw, y + 15, 0x28C8A8F0);
            }
            Gui.drawRect(x, y, x + 13, y + 13, can ? 0xFF7A48B8 : 0xFF2A2236);
            drawCenteredString(fontRenderer, r >= pk.max ? "✔" : "+", x + 7, y + 3, can ? 0xFFFFFF : 0x6E6680);
            perkRects.add(new int[]{x, y, 13, 13, k});
            String name = I18n.format(pk.key(s));
            drawString(fontRenderer, name, x + 19, y + 3, r > 0 ? 0xF0D8FF : C_TEXT);
            int px = x + 23 + fontRenderer.getStringWidth(name);
            for (int q = 0; q < pk.max; q++) {
                Gui.drawRect(px + q * 8, y + 4, px + q * 8 + 6, y + 10, q < r ? 0xFFC890FF : 0xFF3A3048);
            }
            if (over) {
                hoverPerk = I18n.format(pk.key(s) + ".desc");
            }
            y += 17;
        }
        y += 4;
        if (hoverPerk != null) {
            para(hoverPerk, x, y, pw, 0xC0B8D0);
        } else {
            para("§o" + I18n.format(s.key() + ".lore"), x, y, pw, 0x8A8098);
        }
    }

    private final List<int[]> formRects = new ArrayList<>();
    private final List<int[]> perkRects = new ArrayList<>();

    /** Клик по форме или перку на странице заклинания. */
    private boolean clickSpell(int mx, int my) {
        Spell s = spells.get(selSpell);
        for (int[] r : formRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                if (ru.death.chaosshard.spell.SpellData.formOpen(mc.player, s, r[4])) {
                    ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketSpellAction(
                            ru.death.chaosshard.network.PacketSpellAction.FORM, s, r[4]));
                }
                return true;
            }
        }
        for (int[] r : perkRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketSpellAction(
                        ru.death.chaosshard.network.PacketSpellAction.BUY, s, r[4]));
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                        net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.2F));
                return true;
            }
        }
        return false;
    }

    private static String cooldown(int ticks) {
        if (ticks < 20) {
            return I18n.format("chaosshard.magic.cooldown.none");
        }
        int sec = ticks / 20;
        return sec >= 60 ? I18n.format("chaosshard.magic.min", sec / 60) : I18n.format("chaosshard.magic.sec", sec);
    }

    // ------------------------------------------------------------------ ритуалы

    /** В списке ритуалов — без призывов: они на вкладке «Слуги». */
    static List<Ritual> shownRituals() {
        List<Ritual> out = new ArrayList<>();
        for (Ritual r : Ritual.values()) {
            if (!r.isSummon() && r.available()) {
                out.add(r);
            }
        }
        return out;
    }

    private void drawRituals(int mx, int my) {
        Ritual[] all = shownRituals().toArray(new Ritual[0]);
        if (selRitual >= all.length) {
            selRitual = -1;
        }
        drawRow(0, new ItemStack(ModItems.CHALK), I18n.format("chaosshard.magic.howto"), C_GOLD, selRitual < 0, mx, my);
        for (int i = 0; i < all.length; i++) {
            boolean open = all[i].minThread <= MagicUi.threadLevel();
            drawRow(i + 1, ritualIcon(all[i]), I18n.format(all[i].key()), open ? C_TEXT : C_DIM, i == selRitual, mx, my);
        }

        int x = pageX(), y = top + 12, pw = pageW();
        if (selRitual < 0) {
            y = pageTitle(I18n.format("chaosshard.magic.howto"), x, y, C_TITLE);
            int textW = pw - 76;
            int ty = para(I18n.format("chaosshard.magic.howto.text"), x, y, textW, C_TEXT);
            para(I18n.format("chaosshard.magic.howto.summons"), x, ty + 8, textW, C_SOFT);
            drawCircle(x + pw - 64, y + 2);
            return;
        }
        Ritual r = all[selRitual];
        boolean open = r.minThread <= MagicUi.threadLevel();
        y = pageTitle(I18n.format(r.key()), x, y, open ? C_TITLE : 0x8A8098);
        y = para(I18n.format(r.key() + ".desc"), x, y, pw, C_TEXT) + 8;
        y = section(I18n.format(r.isAltar() ? "chaosshard.magic.offerings.altar" : "chaosshard.magic.offerings"), x, y);
        @SuppressWarnings("unchecked")
        List<String>[] hover = new List[1];
        y = offerList(r, x, y, pw, mx, my, hover) + 6;
        drawString(fontRenderer, I18n.format("chaosshard.magic.need_thread", r.minThread), x, y, open ? C_GOOD : C_BAD);
        String dur = r.duration <= 0 ? I18n.format("chaosshard.magic.instant") : cooldown(r.duration);
        String ds = I18n.format("chaosshard.magic.duration", dur);
        drawString(fontRenderer, ds, x + pw - fontRenderer.getStringWidth(ds), y, 0xB0A8C0);
        y += 13;
        if (r.isSphere()) {
            y = para(I18n.format("chaosshard.magic.sphere"), x, y, pw, 0xC890FF);
        }
        if (r.isAltar()) {
            para(I18n.format("chaosshard.magic.altar"), x, y, pw, C_GOLD);
        } else {
            setFillTarget(r, left + w - 12, top + h - 30);
        }
        if (hover[0] != null) {
            hoverText = hover[0];
        }
    }

    /** Подношения столбиком. Для каждой строки: значок, «N× название», справа — «есть M» (зелёным/красным). */
    private int offerList(Ritual r, int x, int y, int pw, int mx, int my, List<String>[] hover) {
        List<ItemStack> stacks = r.offeringStacks();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            int have = countInBag(r, i);
            boolean ok = have >= s.getCount() || mc.player.isCreative();
            Gui.drawRect(x, y, x + pw, y + 18, 0x14FFFFFF);
            Gui.drawRect(x, y, x + 1, y + 18, ok ? 0xC08FD07E : 0xC0E07A7A);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(s, x + 3, y + 1);
            RenderHelper.disableStandardItemLighting();
            String name = s.getCount() + "× " + s.getDisplayName();
            String hs = I18n.format("chaosshard.magic.have", have);
            int hw = fontRenderer.getStringWidth(hs);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(name, pw - hw - 32), x + 24, y + 5, C_TEXT);
            fontRenderer.drawString(hs, x + pw - hw - 4, y + 5, ok ? C_GOOD : C_BAD);
            if (mx >= x && mx < x + pw && my >= y && my < y + 18) {
                hover[0] = s.getTooltip(mc.player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
            }
            y += 20;
        }
        return y;
    }

    /** Подношения в ряд (страницы призыва): значок с числом, под ним черта — зелёная, если хватает. Возвращает x после ряда. */
    private int drawOfferRow(Ritual r, int x, int y, int mx, int my, List<String>[] hover) {
        List<ItemStack> stacks = r.offeringStacks();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            int have = countInBag(r, i);
            boolean ok = have >= s.getCount() || mc.player.isCreative();
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(s, x, y);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, s, x, y, null);
            RenderHelper.disableStandardItemLighting();
            Gui.drawRect(x, y + 17, x + 16, y + 19, ok ? 0xFF8FD07E : 0xFFE07A7A);
            if (mx >= x && mx < x + 16 && my >= y && my < y + 19) {
                List<String> tip = new ArrayList<>(s.getTooltip(mc.player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL));
                tip.add((ok ? "§a" : "§c") + I18n.format("chaosshard.magic.have", have));
                hover[0] = tip;
            }
            x += 20;
        }
        return x;
    }

    /** Сколько в сумке того, что подходит как i-е подношение. */
    private int countInBag(Ritual r, int i) {
        int n = 0;
        for (ItemStack s : mc.player.inventory.mainInventory) {
            if (r.offeringMatches(i, s)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : mc.player.inventory.offHandInventory) {
            if (r.offeringMatches(i, s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ItemStack ritualIcon(Ritual r) {
        switch (r) {
            case GROWTH: return new ItemStack(net.minecraft.init.Items.WHEAT);
            case SKY: return new ItemStack(net.minecraft.init.Items.WATER_BUCKET);
            case WARD: return new ItemStack(net.minecraft.init.Items.SHIELD);
            case STORM: return new ItemStack(net.minecraft.init.Items.BLAZE_ROD);
            case SUMMON: return new ItemStack(net.minecraft.init.Items.ROTTEN_FLESH);
            case CORRUPTION: return new ItemStack(ModBlocks.CHAOS_ANCHOR);
            case DUSK: return new ItemStack(net.minecraft.init.Items.CLOCK);
            case BREED: return new ItemStack(net.minecraft.init.Items.WHEAT);
            case PULL: return new ItemStack(net.minecraft.init.Items.ENDER_PEARL);
            case SIGHT: return new ItemStack(net.minecraft.init.Items.ENDER_EYE);
            case RECALL: return new ItemStack(net.minecraft.init.Items.GOLDEN_APPLE);
            case BLOOD_MOON: return new ItemStack(net.minecraft.init.Items.REDSTONE);
            case GREAT_SURGE: return new ItemStack(ModItems.WORLD_SHARD);
            case CHALLENGE: return new ItemStack(ModItems.CHALLENGE_SPHERE);
            case SERVANT_HUSK: return new ItemStack(net.minecraft.init.Items.ROTTEN_FLESH);
            case SERVANT_BONE: return new ItemStack(net.minecraft.init.Items.SKULL, 1, 1);
            case SERVANT_EXECUTIONER: return new ItemStack(net.minecraft.init.Items.IRON_AXE);
            case SERVANT_HARE: return new ItemStack(net.minecraft.init.Items.RABBIT_FOOT);
            case SERVANT_CAT: return new ItemStack(net.minecraft.init.Items.FISH);
            case SERVANT_TIGER: return new ItemStack(net.minecraft.init.Items.BLAZE_ROD);
            default: return new ItemStack(ModItems.CHAOS_ESSENCE);
        }
    }

    /** Схема круга 5×5: Чаша в центре, 8 знаков мела. */
    private void drawCircle(int x, int y) {
        int c = 12;
        Gui.drawRect(x - 3, y - 3, x + c * 5 + 3, y + c * 5 + 3, 0x40000000);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int px = x + (dx + 2) * c, py = y + (dz + 2) * c;
                boolean ring = (Math.abs(dx) == 2 || dx == 0) && (Math.abs(dz) == 2 || dz == 0) && !(dx == 0 && dz == 0);
                int col = dx == 0 && dz == 0 ? 0xFFE6DCF0 : ring ? 0xFFB070FF : 0x20FFFFFF;
                Gui.drawRect(px + 1, py + 1, px + c - 1, py + c - 1, col);
            }
        }
        drawCenteredString(fontRenderer, I18n.format("chaosshard.magic.legend"), x + c * 5 / 2, y + c * 5 + 6, 0x8A8098);
    }

    private int para(String text, int x, int y, int pw, int color) {
        for (String line : fontRenderer.listFormattedStringToWidth(text, pw)) {
            drawString(fontRenderer, line, x, y, color);
            y += 11;
        }
        return y;
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\n")) {
            out.addAll(fontRenderer.listFormattedStringToWidth(part, 220));
        }
        return out;
    }
}
