package ru.death.chaosshard.client.magic;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import ru.death.chaosshard.client.book.BookArt;
import ru.death.chaosshard.client.book.BookButton;
import ru.death.chaosshard.client.book.BookFont;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.init.ModSounds;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketSelectSpell;
import ru.death.chaosshard.ritual.Ritual;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellCaster;

import java.util.ArrayList;
import java.util.List;

/**
 * Гримуар — раскрытая книга (замечание №26): переплёт, тёмный пергамент, закладки-ленты «Заклинания», «Ритуалы», «Слуги».
 * Слева — оглавление (с номерами страниц и ленточкой у выбранного), справа — страница: заголовок, текст,
 * рукописные пометки на полях. Смена страницы — перелистывание с шелестом.
 * Вёрстка — в «пикселях книги» (440×272); на маленьком экране книга целиком уменьшается.
 */
public class GuiGrimoireMagic extends GuiScreen {

    static final int ROW = 19;
    /** Как близко должна быть Чаша, чтобы сложить в неё подношения. */
    public static final int BOWL_REACH = 6;
    private static final int TAB_X = 30, TAB_STEP = 34, TAB_W = 28;

    private static int tab;
    private static int selBeast = -1;
    private static int selSpell;
    private static int selRitual = -1;
    private static final int[] scroll = new int[3];

    private final ItemStack grimoire;
    private final List<Spell> spells = new ArrayList<>();
    private final BeastPage beasts = new BeastPage(this);
    private BookFont font;
    private float k = 1;
    private int left, top;
    private BookButton choose, reset, call, fill;
    private Ritual fillTarget;
    private List<String> hoverText;
    // перелистывание
    private int pageKey = Integer.MIN_VALUE;
    private long flipAt;
    private boolean flipForward = true;

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
        testState(t, ritual, sc, -1);
    }

    /** То же, плюс выбранная строка вкладки «Слуги» (-1 — «Отголоски»). */
    public static void testState(int t, int ritual, int sc, int beast) {
        tab = t;
        selBeast = beast;
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
        font = BookFont.body();
        fontRenderer = font;
        k = Math.min(1F, Math.min((width - 12) / (float) BookArt.W, (height - 30) / (float) (BookArt.H + 24)));
        left = (int) ((width / k - BookArt.W) / 2);
        top = (int) ((height / k - BookArt.H + 20) / 2);
        buttonList.clear();
        choose = new BookButton(1, 0, 0, I18n.format("chaosshard.magic.choose"), true);
        buttonList.add(choose);
        reset = new BookButton(2, 0, 0, I18n.format("chaosshard.beast.gui.reset"), false);
        reset.visible = false;
        buttonList.add(reset);
        call = new BookButton(3, 0, 0, I18n.format("chaosshard.beast.gui.call"), false);
        call.visible = false;
        buttonList.add(call);
        fill = new BookButton(4, 0, 0, I18n.format("chaosshard.magic.fill"), true);
        fill.visible = false;
        buttonList.add(fill);
        updateButton();
        if (pageKey == Integer.MIN_VALUE) {
            rustle();
        }
    }

    private void updateButton() {
        if (choose == null) {
            return;
        }
        Spell s = spells.get(selSpell);
        choose.visible = tab == 0;
        choose.enabled = MagicUi.unlocked(s);
        boolean current = SpellCaster.magicMode(grimoire) && SpellCaster.selected(grimoire) == s;
        choose.setLabel(I18n.format(current ? "chaosshard.magic.chosen" : "chaosshard.magic.choose"));
        choose.x = pageX() + pageW() - choose.width;
        choose.y = top + BookArt.PB - 42;
    }

    private void rustle() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.BOOK_PAGE, 0.9F + mc.world.rand.nextFloat() * 0.2F));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 4 && fillTarget != null) {
            ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketRitualFill(fillTarget));
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP, 0.8F));
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

    private int lx(int mx) {
        return (int) (mx / k);
    }

    private int ly(int my) {
        return (int) (my / k);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        super.mouseClickMove(lx(mx), ly(my), button, time);
        beasts.drag(lx(mx));
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(lx(mx), ly(my), state);
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
    protected void mouseClicked(int rawX, int rawY, int button) throws java.io.IOException {
        int mx = lx(rawX), my = ly(rawY);
        if (tab == 2 && selBeast >= 0 && selBeast < 3
                && (beasts.clickTitle(ru.death.chaosshard.servant.BeastData.Beast.values()[selBeast], mx, my) || beasts.press(mx, my))) {
            return;
        }
        if (beasts.editing()) {
            beasts.finishEdit(true);
        }
        super.mouseClicked(mx, my, button);
        for (int i = 0; i < 3; i++) {
            int tx = left + TAB_X + i * TAB_STEP;
            if (mx >= tx && mx < tx + TAB_W && my >= top - 24 && my < top + 4) {
                tab = i;
                updateButton();
                return;
            }
        }
        // стрелки прокрутки
        int ay = top + BookArt.PB - 17;
        if (my >= ay - 2 && my < ay + 10 && listSize() > visibleRows()) {
            int ax = left + BookArt.MID - 30;
            if (mx >= ax - 2 && mx < ax + 9) {
                scroll[tab] = clampScroll(scroll[tab] - 1, listSize());
                return;
            }
            if (mx >= ax + 11 && mx < ax + 22) {
                scroll[tab] = clampScroll(scroll[tab] + 1, listSize());
                return;
            }
        }
        if (tab == 0 && clickSpell(mx, my, button)) {
            return;
        }
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

    // ------------------------------------------------------------------ оглавление (левая страница)

    int listX() { return left + 26; }
    int listX1() { return left + BookArt.MID - 14; }
    int listTop() { return top + 48; }
    int visibleRows() { return (BookArt.PB - 48 - 24) / ROW; }

    private int listSize() {
        return tab == 0 ? spells.size() : tab == 1 ? shownRituals().size() + 1 : BeastPage.ROWS;
    }

    private int clampScroll(int v, int rows) {
        return Math.max(0, Math.min(Math.max(0, rows - visibleRows()), v));
    }

    private int rowY(int idx) {
        int i = idx - scroll[tab];
        return i < 0 || i >= visibleRows() ? Integer.MIN_VALUE : listTop() + i * ROW;
    }

    private int rowAt(int mx, int my) {
        if (mx < listX() - 4 || mx >= listX1() || my < listTop() || my >= listTop() + visibleRows() * ROW) {
            return -1;
        }
        return scroll[tab] + (my - listTop()) / ROW;
    }

    /** Строка оглавления: значок, название, отточие, номер страницы; у выбранной — ленточка на полях. */
    void drawRow(int idx, ItemStack icon, String name, int color, boolean selected, int mx, int my) {
        int y = rowY(idx);
        if (y == Integer.MIN_VALUE) {
            return;
        }
        int x0 = listX(), x1 = listX1();
        boolean hover = mx >= x0 - 4 && mx < x1 && my >= y && my < y + ROW;
        if (hover && !selected) {
            Gui.drawRect(x0 - 3, y, x1 + 2, y + ROW - 1, 0x16E8D8FF);
        }
        if (selected) {
            Gui.drawRect(x0 - 3, y, x1 + 2, y + ROW - 1, 0x22ECB064);
            BookArt.marker(x0 - 15, y + 2);
        }
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x0, y + 1.5F, 0);
        GlStateManager.scale(0.95F, 0.95F, 1F);
        itemRender.renderItemAndEffectIntoGUI(icon, 0, 0);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        String num = String.valueOf(3 + idx * 2);
        int nw = font.getStringWidth(num);
        String shown = font.trimStringToWidth(name, x1 - x0 - 24 - nw - 8);
        int c = selected ? BookArt.ACCENT : color;
        font.drawString(shown, x0 + 20, y + 4, c, false);
        BookArt.leader(x0 + 20 + font.getStringWidth(shown) + 2, x1 - nw - 2, y + 12);
        font.drawScaled(num, x1 - nw * 0.85F, y + 5, BookArt.SOFT, 0.85F);
    }

    /** Подзаголовок в оглавлении (не выбирается). */
    void drawHeader(int idx, String text) {
        int y = rowY(idx);
        if (y == Integer.MIN_VALUE) {
            return;
        }
        int cx = (listX() + listX1()) / 2;
        float tw = font.width("§o" + text);
        Gui.drawRect(listX(), y + ROW / 2, (int) (cx - tw / 2 - 6), y + ROW / 2 + 1, 0x608F78A8);
        Gui.drawRect((int) (cx + tw / 2 + 6), y + ROW / 2, listX1(), y + ROW / 2 + 1, 0x608F78A8);
        font.drawString("§o" + text, cx - tw / 2, y + 5, BookArt.SOFT, false);
    }

    /** Заголовок с увеличенной цветной первой буквой и разделителем под ним. Возвращает y для текста. */
    private int heading(String t, int x, int y, int w, int color) {
        if (t.isEmpty()) {
            return y;
        }
        String first = t.substring(0, 1), rest = t.substring(1);
        float fx = font.drawScaled("§l" + first, x, y - 3, BookArt.RED, 1.55F);
        font.drawScaled("§l" + rest, fx + 0.5F, y + 0.5F, color, 1.2F);
        BookArt.divider(x, y + 17, w);
        return y + 26;
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    public void drawScreen(int rawX, int rawY, float pt) {
        drawDefaultBackground();
        int mx = lx(rawX), my = ly(rawY);
        hoverText = null;
        fillTarget = null;
        GlStateManager.pushMatrix();
        GlStateManager.scale(k, k, 1F);
        // закладки торчат из-под переплёта
        String[] names = {I18n.format("chaosshard.magic.tab.spells"), I18n.format("chaosshard.magic.tab.rituals"),
                I18n.format("chaosshard.magic.tab.servants")};
        ItemStack[] icons = {new ItemStack(ModItems.CHAOS_ESSENCE), new ItemStack(ModItems.CHALK), new ItemStack(net.minecraft.init.Items.RABBIT_FOOT)};
        for (int i = 0; i < 3; i++) {
            int tx = left + TAB_X + i * TAB_STEP;
            boolean hover = mx >= tx && mx < tx + TAB_W && my >= top - 24 && my < top + 4;
            BookArt.ribbon(i, tx, top + 6, i == tab, hover);
            if (hover) {
                hoverText = java.util.Collections.singletonList(names[i]);
            }
        }
        BookArt.book(left, top);
        for (int i = 0; i < 3; i++) {
            int tx = left + TAB_X + i * TAB_STEP;
            int len = i == tab ? 30 : 23;
            if (mx >= tx && mx < tx + TAB_W && my >= top - 24 && my < top + 4 && i != tab) {
                len = 26;
            }
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx + 6, top + 6 - len + 3, 0);
            itemRender.renderItemAndEffectIntoGUI(icons[i], 0, 0);
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
        }
        // левая страница: раздел и оглавление
        String section = tab == 0 ? I18n.format("chaosshard.magic.book.spells") : tab == 1
                ? I18n.format("chaosshard.magic.book.rituals") : I18n.format("chaosshard.magic.book.servants");
        heading(section, listX(), top + 19, listX1() - listX(), BookArt.INK);
        scroll[tab] = clampScroll(scroll[tab], listSize());
        reset.visible = false;
        call.visible = false;
        int key;
        if (tab == 0) {
            drawSpells(mx, my);
            key = selSpell;
        } else if (tab == 1) {
            drawRituals(mx, my);
            key = 1000 + selRitual;
        } else {
            beasts.draw(selBeast, mx, my, reset, call);
            key = 2000 + selBeast;
        }
        // низ левой страницы
        int fy = top + BookArt.PB - 17;
        font.drawString(I18n.format("chaosshard.magic.thread", MagicUi.threadLevel()), listX(), fy, BookArt.SOFT, false);
        if (listSize() > visibleRows()) {
            int ax = left + BookArt.MID - 30;
            font.drawString("▲", ax, fy, scroll[tab] > 0 ? BookArt.ACCENT : BookArt.DIM, false);
            font.drawString("▼", ax + 13, fy, scroll[tab] + visibleRows() < listSize() ? BookArt.ACCENT : BookArt.DIM, false);
        }
        if (tab == 0) {
            int e = ru.death.chaosshard.client.ClientThreadCache.energy(), m = ru.death.chaosshard.client.ClientThreadCache.maxEnergy();
            int ex = listX() + 52, ew = left + BookArt.MID - 40 - ex;
            font.drawScaled(I18n.format("chaosshard.magic.energy", e, m), ex, fy - 3, BookArt.VIOLET, 0.8F);
            Gui.drawRect(ex, fy + 7, ex + ew, fy + 9, 0xFF1A0E26);
            drawGradientRect(ex, fy + 7, ex + ew * Math.min(e, m) / Math.max(1, m), fy + 9, 0xFFD0A0FF, 0xFF8A50E0);
            // разогрев
            float heat = ru.death.chaosshard.spell.SpellHeat.heat(mc.player);
            boolean surge = heat >= ru.death.chaosshard.spell.SpellHeat.MAX;
            long lock = ru.death.chaosshard.spell.SpellHeat.locked(mc.player);
            Gui.drawRect(ex, fy + 11, ex + ew, fy + 13, 0xFF1A0E26);
            int hw = (int) (ew * heat / ru.death.chaosshard.spell.SpellHeat.MAX);
            int hot = surge ? (System.currentTimeMillis() / 250 % 2 == 0 ? 0xFFFFE090 : 0xFFFF7040) : lock > 0 ? 0xFF8A7A9A : 0xFFFF9A30;
            Gui.drawRect(ex, fy + 11, ex + hw, fy + 13, hot);
            if (mx >= ex && mx < ex + ew && my >= fy + 5 && my < fy + 15) {
                hoverText = split(I18n.format(surge ? "chaosshard.heat.tip.surge" : lock > 0 ? "chaosshard.heat.tip.lock" : "chaosshard.heat.tip",
                        (int) heat, (int) ru.death.chaosshard.spell.SpellHeat.MAX, (lock + 19) / 20));
            }
        }
        int lastRow = listTop() + Math.min(visibleRows(), listSize()) * ROW;
        noteKey("chaosshard.note.section." + tab, listX() + 6, lastRow + 4, fy - 4, -2.5F);
        // перелистывание
        if (key != pageKey) {
            if (pageKey != Integer.MIN_VALUE) {
                flipForward = key > pageKey;
                flipAt = System.currentTimeMillis();
                rustle();
            }
            pageKey = key;
        }
        float p = (System.currentTimeMillis() - flipAt) / 260F;
        if (p < 1) {
            BookArt.flip(left, top, p, flipForward);
        }
        fill.visible = fillTarget != null;
        if (fill.visible) {
            boolean near = nearBowl() != null;
            fill.enabled = near;
            fill.hint = I18n.format(near ? "chaosshard.magic.fill.hint" : "chaosshard.magic.fill.far_short", BOWL_REACH);
            fill.fit();
            if (fill.x + fill.width > left + BookArt.PR - 12) {
                fill.x = left + BookArt.PR - 12 - fill.width;
            }
            if (mx >= fill.x && mx < fill.x + fill.width && my >= fill.y && my < fill.y + fill.height) {
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
        GlStateManager.popMatrix();
    }

    /** Рукописная пометка: Bad Script, слегка наискосок; рисуется, только если влезает между yMin и yMax. */
    void note(String text, int x, int yMin, int yMax, float angle) {
        BookFont s = BookFont.script();
        String[] lines = text.replace("\\n", "\n").split("\n");
        int h = lines.length * 11;
        if (yMax - yMin < h) {
            return;
        }
        int y = yMax - h;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate(angle, 0, 0, 1);
        for (int i = 0; i < lines.length; i++) {
            s.drawString(lines[i], 0, i * 11, 0xD8D8B66E, false);
        }
        GlStateManager.popMatrix();
    }

    /** Пометка по ключу перевода (если ключ есть). */
    void noteKey(String key, int x, int yMin, int yMax, float angle) {
        if (I18n.hasKey(key)) {
            note(I18n.format(key), x, yMin, yMax, angle);
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
    int pageX() { return left + BookArt.MID + 14; }
    int pageW() { return BookArt.PR - 12 - BookArt.MID - 14; }
    int pageBottom() { return top + BookArt.PB - 22; }
    net.minecraft.client.gui.FontRenderer font() { return font; }
    net.minecraft.client.renderer.RenderItem items() { return itemRender; }
    void text(String t, int x, int y, int c) { font.drawString(t, x, y, c, false); }
    int paragraph(String t, int x, int y, int w, int c) { return para(t, x, y, w, c); }
    void hover(java.util.List<String> lines, int x, int y) { drawHoveringText(lines, x, y); }

    /** Кнопка «Сложить в Чашу» для ритуала r: правый край кнопки — right, верх — y. */
    void setFillTarget(Ritual r, int right, int y) {
        fillTarget = r;
        fill.hint = I18n.format("chaosshard.magic.fill.hint");
        fill.fit();
        fill.x = right - fill.width;
        fill.y = y;
    }
    int title(String t, int x, int y, int color) { return heading(t, x, y + 4, pageW(), color); }
    /** Подношения ритуала в ряд; возвращает x после ряда. */
    int offerRow(Ritual r, int x, int y, int mx, int my, List<String>[] hover) { return drawOfferRow(r, x, y, mx, my, hover); }

    private int section(String t, int x, int y) {
        font.drawString("§o" + t, x, y, BookArt.VIOLET, false);
        return y + 13;
    }

    // ------------------------------------------------------------------ заклинания

    private void drawSpells(int mx, int my) {
        Spell current = SpellCaster.magicMode(grimoire) ? SpellCaster.selected(grimoire) : null;
        for (int i = 0; i < spells.size(); i++) {
            Spell s = spells.get(i);
            boolean open = MagicUi.unlocked(s);
            drawRow(i, MagicUi.icon(s), I18n.format(s.key()), !open ? BookArt.DIM : s == current ? BookArt.VIOLET : BookArt.INK, i == selSpell, mx, my);
        }
        updateButton();
        Spell s = spells.get(selSpell);
        int x = pageX(), pw = pageW();
        boolean open = MagicUi.unlocked(s);
        int y = heading(I18n.format(s.key()), x, top + 19, pw, open ? BookArt.INK : BookArt.SOFT);
        y = para(I18n.format(s.key() + ".desc"), x, y, pw, BookArt.INK) + 5;
        boolean enough = ru.death.chaosshard.client.ClientThreadCache.energy() >= SpellCaster.cost(mc.player, s);
        String req = I18n.format("chaosshard.magic.need_thread", s.minThread);
        font.drawString(req, x, y, open ? BookArt.GOOD : BookArt.BAD, false);
        String costS = I18n.format("chaosshard.magic.cost", SpellCaster.cost(mc.player, s));
        font.drawString(costS, x + pw - font.getStringWidth(costS), y, enough ? BookArt.VIOLET : BookArt.BAD, false);
        y += 11;
        font.drawString(I18n.format("chaosshard.magic.cooldown", cooldown(SpellCaster.cooldown(mc.player, s))), x, y, BookArt.SOFT, false);
        y += 14;
        y = drawGlyphs(s, x, y, pw, mx, my);
        // формы
        formRects.clear();
        String[] forms = ru.death.chaosshard.spell.SpellPerks.forms(s);
        if (forms.length > 1) {
            String fl = I18n.format("chaosshard.magic.forms");
            font.drawString("§o" + fl, x, y + 2, BookArt.VIOLET, false);
            int fx = x + font.getStringWidth("§o" + fl) + 6;
            int cur = ru.death.chaosshard.spell.SpellData.form(mc.player, s);
            for (int f = 0; f < forms.length; f++) {
                String fn = I18n.format(ru.death.chaosshard.spell.SpellPerks.formKey(s, f));
                int fw = font.getStringWidth(fn) + 6;
                if (fx + fw > x + pw) {
                    fx = x + 10;
                    y += 13;
                }
                boolean fo = ru.death.chaosshard.spell.SpellData.formOpen(mc.player, s, f);
                boolean over = mx >= fx && mx < fx + fw && my >= y && my < y + 12;
                if (f == cur) {
                    Gui.drawRect(fx, y + 11, fx + fw, y + 12, 0xFFECB064);
                } else if (over && fo) {
                    Gui.drawRect(fx, y + 11, fx + fw, y + 12, 0x80ECB064);
                }
                font.drawString(fn, fx + 3, y + 2, f == cur ? BookArt.ACCENT : fo ? BookArt.INK : BookArt.DIM, false);
                formRects.add(new int[]{fx, y, fw, 12, f});
                fx += fw + 4;
            }
            y += 17;
        }
        // перки
        perkRects.clear();
        ru.death.chaosshard.spell.SpellPerks.Perk[] perks = ru.death.chaosshard.spell.SpellPerks.perks(s);
        int points = ru.death.chaosshard.spell.SpellData.points(mc.player);
        int free = ru.death.chaosshard.spell.SpellData.free(mc.player, s);
        y = section(I18n.format("chaosshard.magic.perks", free, points), x, y);
        String hoverPerk = null;
        for (int q = 0; q < perks.length; q++) {
            ru.death.chaosshard.spell.SpellPerks.Perk pk = perks[q];
            int r = ru.death.chaosshard.spell.SpellData.rank(mc.player, s, pk.id);
            boolean can = r < pk.max && free > 0 && open;
            boolean over = mx >= x && mx < x + pw && my >= y - 1 && my < y + 12;
            if (over) {
                Gui.drawRect(x - 2, y - 1, x + pw, y + 12, 0x18E8D8FF);
            }
            int box = r >= pk.max ? 0xFFECB064 : can ? 0xFFB070FF : 0xFF3A3048;
            Gui.drawRect(x, y + 1, x + 9, y + 10, box);
            Gui.drawRect(x + 1, y + 2, x + 8, y + 9, 0xFF160D20);
            font.drawString(r >= pk.max ? "✔" : "+", x + 1.5F, y + 1, r >= pk.max ? BookArt.ACCENT : can ? 0xFFFFFF : BookArt.DIM, false);
            perkRects.add(new int[]{x, y, 10, 11, q});
            String name = I18n.format(pk.key(s));
            font.drawString(name, x + 14, y + 1, r > 0 ? BookArt.ACCENT : BookArt.INK, false);
            int px = x + 18 + font.getStringWidth(name);
            for (int z = 0; z < pk.max; z++) {
                Gui.drawRect(px + z * 7, y + 4, px + z * 7 + 5, y + 8, z < r ? 0xFFC890FF : 0xFF3A3048);
            }
            if (over) {
                hoverPerk = I18n.format(pk.key(s) + ".desc");
            }
            y += 13;
        }
        y += 3;
        int bottom = choose.y - 4;
        if (hoverPerk != null) {
            para(hoverPerk, x, y, pw, BookArt.SOFT);
        } else {
            int ly = para("§o" + I18n.format(s.key() + ".lore"), x, y, pw, BookArt.SOFT);
            noteKey("chaosshard.note.spell." + s.name().toLowerCase(), x + 2, ly + 2, bottom, -2F);
        }
    }

    private final List<int[]> formRects = new ArrayList<>();
    private final List<int[]> perkRects = new ArrayList<>();

    /** Клик по форме или перку на странице заклинания. */
    private boolean clickSpell(int mx, int my, int button) {
        Spell s = spells.get(selSpell);
        if (clickGlyph(s, mx, my, button)) {
            return true;
        }
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
                mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(
                        net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.2F));
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ глифы

    private final List<int[]> glyphRects = new ArrayList<>();
    private final List<int[]> pickRects = new ArrayList<>();
    private int pickSlot = -1;
    private Spell pickSpell;

    /** Гнёзда глифов заклинания и, если открыт, выбор глифа из сумки. Возвращает y под рядом. */
    private int drawGlyphs(Spell s, int x, int y, int pw, int mx, int my) {
        glyphRects.clear();
        pickRects.clear();
        if (pickSpell != s) {
            pickSlot = -1;
        }
        String label = "\u00A7o" + I18n.format("chaosshard.glyph.slots");
        font.drawString(label, x, y + 5, BookArt.VIOLET, false);
        int gx = x + font.getStringWidth(label) + 6;
        int open = ru.death.chaosshard.spell.SpellGlyphs.slots(mc.player);
        for (int i = 0; i < ru.death.chaosshard.spell.SpellGlyphs.MAX_SLOTS; i++) {
            int bx = gx + i * 22;
            boolean on = i < open;
            boolean over = on && mx >= bx && mx < bx + 18 && my >= y && my < y + 18;
            int border = !on ? 0xFF2E2438 : pickSlot == i ? 0xFFECB064 : over ? 0xFFC496FF : 0xFF6E5A88;
            Gui.drawRect(bx, y, bx + 18, y + 18, border);
            Gui.drawRect(bx + 1, y + 1, bx + 17, y + 17, 0xFF140C1C);
            ru.death.chaosshard.spell.SpellGlyph g = on ? ru.death.chaosshard.spell.SpellGlyphs.get(mc.player, s, i) : null;
            if (g != null) {
                RenderHelper.enableGUIStandardItemLighting();
                itemRender.renderItemAndEffectIntoGUI(ru.death.chaosshard.spell.ItemSpellGlyph.of(g), bx + 1, y + 1);
                RenderHelper.disableStandardItemLighting();
            } else if (!on) {
                font.drawScaled(String.valueOf(i == 1 ? 3 : 5), bx + 6.5F, y + 5, BookArt.DIM, 0.85F);
            }
            if (over) {
                hoverText = g != null ? split("\u00A7d" + I18n.format(g.key()) + "\n\u00A77" + I18n.format(g.key() + ".desc") + "\n\u00A78" + I18n.format("chaosshard.glyph.slot.filled"))
                        : split(I18n.format("chaosshard.glyph.slot.empty"));
            } else if (!on && mx >= bx && mx < bx + 18 && my >= y && my < y + 18) {
                hoverText = split(I18n.format("chaosshard.glyph.slot.locked", i == 1 ? 3 : 5));
            }
            glyphRects.add(new int[]{bx, y, 18, 18, i, on ? 1 : 0});
        }
        y += 22;
        if (pickSlot >= 0) {
            List<ru.death.chaosshard.spell.SpellGlyph> have = new ArrayList<>();
            for (ru.death.chaosshard.spell.SpellGlyph g : ru.death.chaosshard.spell.SpellGlyph.values()) {
                if (g.fits(s) && glyphCount(g) > 0 && !onSpell(s, g)) {
                    have.add(g);
                }
            }
            Gui.drawRect(x - 2, y - 2, x + pw + 2, y + 21, 0xF0100818);
            Gui.drawRect(x - 2, y - 2, x + pw + 2, y - 1, 0xFF6E5A88);
            if (have.isEmpty()) {
                font.drawString("\u00A7o" + I18n.format("chaosshard.glyph.none"), x + 2, y + 4, BookArt.SOFT, false);
            }
            for (int i = 0; i < have.size(); i++) {
                int bx = x + i * 20;
                ru.death.chaosshard.spell.SpellGlyph g = have.get(i);
                boolean over = mx >= bx && mx < bx + 18 && my >= y && my < y + 18;
                if (over) {
                    Gui.drawRect(bx, y, bx + 18, y + 18, 0x40ECB064);
                    hoverText = split("\u00A7d" + I18n.format(g.key()) + "\n\u00A77" + I18n.format(g.key() + ".desc"));
                }
                RenderHelper.enableGUIStandardItemLighting();
                ItemStack st = ru.death.chaosshard.spell.ItemSpellGlyph.of(g);
                st.setCount(glyphCount(g));
                itemRender.renderItemAndEffectIntoGUI(st, bx + 1, y + 1);
                itemRender.renderItemOverlayIntoGUI(mc.fontRenderer, st, bx + 1, y + 1, null);
                RenderHelper.disableStandardItemLighting();
                pickRects.add(new int[]{bx, y, 18, 18, g.ordinal()});
            }
            y += 24;
        }
        return y;
    }

    private boolean onSpell(Spell s, ru.death.chaosshard.spell.SpellGlyph g) {
        for (int i = 0; i < ru.death.chaosshard.spell.SpellGlyphs.MAX_SLOTS; i++) {
            if (ru.death.chaosshard.spell.SpellGlyphs.get(mc.player, s, i) == g) {
                return true;
            }
        }
        return false;
    }

    private int glyphCount(ru.death.chaosshard.spell.SpellGlyph g) {
        if (mc.player.isCreative()) {
            return 1;
        }
        int n = 0;
        for (ItemStack st : mc.player.inventory.mainInventory) {
            if (!st.isEmpty() && st.getItem() == ModItems.SPELL_GLYPH && st.getMetadata() == g.ordinal()) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** ЛКМ по гнезду — выбрать глиф из сумки, ПКМ — вынуть; клик по глифу в выборе — вложить. */
    private boolean clickGlyph(Spell s, int mx, int my, int button) {
        for (int[] r : pickRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketSpellAction(
                        ru.death.chaosshard.network.PacketSpellAction.GLYPH, s, pickSlot * 16 + r[4] + 1));
                mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.4F));
                pickSlot = -1;
                return true;
            }
        }
        for (int[] r : glyphRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                if (r[5] == 0) {
                    return true;
                }
                if (button == 1) {
                    if (ru.death.chaosshard.spell.SpellGlyphs.get(mc.player, s, r[4]) != null) {
                        ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketSpellAction(
                                ru.death.chaosshard.network.PacketSpellAction.GLYPH, s, r[4] * 16));
                        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP, 1.0F));
                    }
                    pickSlot = -1;
                } else {
                    pickSlot = pickSlot == r[4] ? -1 : r[4];
                    pickSpell = s;
                }
                return true;
            }
        }
        if (pickSlot >= 0) {
            pickSlot = -1;
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
        drawRow(0, new ItemStack(ModItems.CHALK), I18n.format("chaosshard.magic.howto"), BookArt.ACCENT, selRitual < 0, mx, my);
        for (int i = 0; i < all.length; i++) {
            boolean open = all[i].minThread <= MagicUi.threadLevel();
            drawRow(i + 1, ritualIcon(all[i]), I18n.format(all[i].key()), open ? BookArt.INK : BookArt.DIM, i == selRitual, mx, my);
        }
        int x = pageX(), pw = pageW();
        int bottom = top + BookArt.PB - 20;
        if (selRitual < 0) {
            int y = heading(I18n.format("chaosshard.magic.howto"), x, top + 19, pw, BookArt.INK);
            int ty = para(I18n.format("chaosshard.magic.howto.text"), x, y, pw - 70, BookArt.INK);
            drawCircle(x + pw - 62, y + 2);
            ty = para(I18n.format("chaosshard.magic.howto.summons"), x, Math.max(ty + 6, y + 82), pw, BookArt.SOFT);
            noteKey("chaosshard.note.howto", x + 4, ty + 4, bottom, -2.5F);
            return;
        }
        Ritual r = all[selRitual];
        boolean open = r.minThread <= MagicUi.threadLevel();
        int y = heading(I18n.format(r.key()), x, top + 19, pw, open ? BookArt.INK : BookArt.SOFT);
        y = para(I18n.format(r.key() + ".desc"), x, y, pw, BookArt.INK) + 6;
        y = section(I18n.format(r.isAltar() ? "chaosshard.magic.offerings.altar" : "chaosshard.magic.offerings"), x, y);
        @SuppressWarnings("unchecked")
        List<String>[] hover = new List[1];
        y = offerList(r, x, y, pw, mx, my, hover) + 3;
        font.drawString(I18n.format("chaosshard.magic.need_thread", r.minThread), x, y, open ? BookArt.GOOD : BookArt.BAD, false);
        String dur = r.duration <= 0 ? I18n.format("chaosshard.magic.instant") : cooldown(r.duration);
        String ds = I18n.format("chaosshard.magic.duration", dur);
        font.drawString(ds, x + pw - font.getStringWidth(ds), y, BookArt.SOFT, false);
        y += 13;
        if (r.isSphere()) {
            y = para("§o" + I18n.format("chaosshard.magic.sphere"), x, y, pw, BookArt.VIOLET);
        }
        int noteBottom = bottom;
        if (r.isAltar()) {
            y = para(I18n.format("chaosshard.magic.altar"), x, y, pw, BookArt.ACCENT);
        } else {
            setFillTarget(r, x + pw, top + BookArt.PB - 42);
            noteBottom = fill.y - 3;
        }
        noteKey("chaosshard.note.ritual." + r.name().toLowerCase(), x + 4, y + 3, noteBottom, -2.5F);
        if (hover[0] != null) {
            hoverText = hover[0];
        }
    }

    /** Подношения столбиком: значок, «N× название», справа — «есть M» (зелёным/красным). */
    private int offerList(Ritual r, int x, int y, int pw, int mx, int my, List<String>[] hover) {
        List<ItemStack> stacks = r.offeringStacks();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            int have = countInBag(r, i);
            boolean ok = have >= s.getCount() || mc.player.isCreative();
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(s, x, y);
            RenderHelper.disableStandardItemLighting();
            String hs = I18n.format("chaosshard.magic.have", have);
            int hw = font.getStringWidth(hs);
            String name = font.trimStringToWidth(s.getCount() + "× " + s.getDisplayName(), pw - hw - 26);
            font.drawString(name, x + 20, y + 4, BookArt.INK, false);
            font.drawString(hs, x + pw - hw, y + 4, ok ? BookArt.GOOD : BookArt.BAD, false);
            Gui.drawRect(x + 20, y + 16, x + pw, y + 17, 0x407A6894);
            if (mx >= x && mx < x + pw && my >= y && my < y + 17) {
                hover[0] = s.getTooltip(mc.player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
            }
            y += 19;
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
            itemRender.renderItemOverlayIntoGUI(mc.fontRenderer, s, x, y, null);
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
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int px = x + (dx + 2) * c, py = y + (dz + 2) * c;
                boolean ring = (Math.abs(dx) == 2 || dx == 0) && (Math.abs(dz) == 2 || dz == 0) && !(dx == 0 && dz == 0);
                int col = dx == 0 && dz == 0 ? 0xFFECB064 : ring ? 0xFFB070FF : 0x18FFFFFF;
                Gui.drawRect(px + 1, py + 1, px + c - 1, py + c - 1, col);
            }
        }
        float lw = font.width(I18n.format("chaosshard.magic.legend")) * 0.8F;
        font.drawScaled(I18n.format("chaosshard.magic.legend"), x + c * 5 / 2F - lw / 2, y + c * 5 + 4, BookArt.SOFT, 0.8F);
    }

    private int para(String text, int x, int y, int pw, int color) {
        String style = text.startsWith("§o") ? "§o" : "";
        for (String line : font.listFormattedStringToWidth(text, pw)) {
            font.drawString(line.startsWith("§") ? line : style + line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.replace("\\n", "\n").split("\n")) {
            out.addAll(font.listFormattedStringToWidth(part, 220));
        }
        return out;
    }
}
