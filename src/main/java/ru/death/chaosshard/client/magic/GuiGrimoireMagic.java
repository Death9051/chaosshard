package ru.death.chaosshard.client.magic;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
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
 * Окно магии Гримуара: вкладки «Заклинания» и «Ритуалы».
 * Слева — список, справа — страница выбранного: описание, цена, условия; у ритуалов — подношения и схема круга.
 */
public class GuiGrimoireMagic extends GuiScreen {

    private static final int W = 344, H = 244, LIST_W = 112, ROW = 16;
    private static int tab;          // 0 — заклинания, 1 — ритуалы, 2 — слуги (запоминается между открытиями)
    private static int selBeast = -1;  // -1 — страница «Отголоски»
    private static int selSpell;
    private static int selRitual = -1;  // -1 — страница «Как провести ритуал»

    private final ItemStack grimoire;
    private final List<Spell> spells = new ArrayList<>();
    private int left, top;
    private GuiButton choose, reset, call;
    private final BeastPage beasts = new BeastPage(this);

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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        left = (width - W) / 2;
        top = (height - H) / 2;
        buttonList.clear();
        choose = new GuiButton(1, left + W - 96, top + H - 26, 86, 20, I18n.format("chaosshard.magic.choose"));
        buttonList.add(choose);
        reset = new GuiButton(2, 0, 0, 96, 20, I18n.format("chaosshard.beast.gui.reset"));
        reset.visible = false;
        buttonList.add(reset);
        call = new GuiButton(3, 0, 0, 60, 20, I18n.format("chaosshard.beast.gui.call"));
        call.visible = false;
        buttonList.add(call);
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
            int tx = left + 6 + i * 84;
            if (mx >= tx && mx < tx + 80 && my >= top - 16 && my < top) {
                tab = i;
                updateButton();
                return;
            }
        }
        // список
        if (mx >= left + 6 && mx < left + LIST_W && my >= top + 8) {
            int row = (my - top - 8) / ROW;
            if (tab == 0 && row >= 0 && row < spells.size()) {
                selSpell = row;
            } else if (tab == 1 && row >= 0 && row <= shownRituals().size()) {
                selRitual = row - 1;
            } else if (tab == 2 && row >= 0 && row <= BeastPage.ROWS - 1 && row != BeastPage.HEADER_ROW) {
                selBeast = row - 1;
            }
            updateButton();
            return;
        }
        if (tab == 2 && selBeast >= 0 && selBeast < 3) {
            beasts.click(ru.death.chaosshard.servant.BeastData.Beast.values()[selBeast], mx, my);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        // вкладки
        String[] tabs = {I18n.format("chaosshard.magic.tab.spells"), I18n.format("chaosshard.magic.tab.rituals"),
                I18n.format("chaosshard.magic.tab.servants")};
        for (int i = 0; i < 3; i++) {
            int tx = left + 6 + i * 84;
            MagicUi.frame(tx, top - 16, 80, 18);
            if (i == tab) {
                Gui.drawRect(tx + 1, top - 1, tx + 79, top + 2, MagicUi.BG);
            }
            drawCenteredString(fontRenderer, tabs[i], tx + 40, top - 11, i == tab ? 0xD9B8FF : 0x8A8098);
        }
        MagicUi.frame(left, top, W, H);
        Gui.drawRect(left + LIST_W + 4, top + 6, left + LIST_W + 5, top + H - 6, 0xFF4A3A60);

        reset.visible = false;
        call.visible = false;
        if (tab == 0) {
            drawSpells(mx, my);
        } else if (tab == 1) {
            drawRituals(mx, my);
        } else {
            beasts.draw(selBeast, mx, my, reset, call);
        }
        // уровень Нити внизу списка
        drawString(fontRenderer, I18n.format("chaosshard.magic.thread", MagicUi.threadLevel()), left + 8, top + H - 14, 0x9A8FB0);
        if (tab == 0) {
            int ex = left + LIST_W + 12, ey = top + H - 22, ew = 100;
            int e = ru.death.chaosshard.client.ClientThreadCache.energy(), m = ru.death.chaosshard.client.ClientThreadCache.maxEnergy();
            Gui.drawRect(ex, ey + 10, ex + ew, ey + 13, 0xAA1A0A2A);
            Gui.drawRect(ex, ey + 10, ex + ew * Math.min(e, m) / Math.max(1, m), ey + 13, 0xFFB070FF);
            drawString(fontRenderer, I18n.format("chaosshard.magic.energy", e, m), ex, ey, 0xB89CFF);
        }
        super.drawScreen(mx, my, pt);
        if (tab == 2) {
            beasts.tooltip(mx, my);
        }
    }

    // ------------------------------------------------------------------ для страницы слуг

    int left() { return left; }
    int top() { return top; }
    int pageW() { return W; }
    int pageH() { return H; }
    int listW() { return LIST_W; }
    int row() { return ROW; }
    net.minecraft.client.gui.FontRenderer font() { return fontRenderer; }
    net.minecraft.client.renderer.RenderItem items() { return itemRender; }
    void text(String t, int x, int y, int c) { drawString(fontRenderer, t, x, y, c); }
    int paragraph(String t, int x, int y, int w, int c) { return para(t, x, y, w, c); }
    void hover(java.util.List<String> lines, int x, int y) { drawHoveringText(lines, x, y); }

    // ------------------------------------------------------------------ заклинания

    private void drawSpells(int mx, int my) {
        Spell current = SpellCaster.magicMode(grimoire) ? SpellCaster.selected(grimoire) : null;
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < spells.size(); i++) {
            Spell s = spells.get(i);
            int y = top + 8 + i * ROW;
            boolean open = MagicUi.unlocked(s);
            if (i == selSpell) {
                Gui.drawRect(left + 5, y - 1, left + LIST_W, y + ROW - 2, 0x804A2A70);
            }
            itemRender.renderItemAndEffectIntoGUI(MagicUi.icon(s), left + 8, y);
            String name = I18n.format(s.key());
            int col = !open ? 0x5E566C : s == current ? 0xE8C8FF : 0xC9C3D6;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(name, LIST_W - 32), left + 28, y + 4, col);
        }
        RenderHelper.disableStandardItemLighting();

        Spell s = spells.get(selSpell);
        int x = left + LIST_W + 12, y = top + 10, w = W - LIST_W - 22;
        boolean open = MagicUi.unlocked(s);
        drawString(fontRenderer, "§l" + I18n.format(s.key()), x, y, open ? 0xD9B8FF : 0x8A8098);
        y += 14;
        y = para(I18n.format(s.key() + ".desc"), x, y, w, 0xC9C3D6);
        y += 6;
        y = para(I18n.format(s.key() + ".lore"), x, y, w, 0x8A8098);
        y += 8;
        drawString(fontRenderer, I18n.format("chaosshard.magic.need_thread", s.minThread), x, y,
                open ? 0x9AD08A : 0xD07070);
        y += 11;
        boolean enough = ru.death.chaosshard.client.ClientThreadCache.energy() >= SpellCaster.cost(mc.player, s);
        drawString(fontRenderer, I18n.format("chaosshard.magic.cost", SpellCaster.cost(mc.player, s)), x, y, enough ? 0xB89CFF : 0xD07070);
        y += 11;
        drawString(fontRenderer, I18n.format("chaosshard.magic.cooldown", cooldown(s.cooldown)), x, y, 0xB0A8C0);
        y += 11;
        drawString(fontRenderer, I18n.format(s.onBlock ? "chaosshard.magic.target.block" : "chaosshard.magic.target.air"), x, y, 0xB0A8C0);
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
            if (!r.isSummon()) {
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
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = -1; i < all.length; i++) {
            int y = top + 8 + (i + 1) * ROW;
            if (i == selRitual) {
                Gui.drawRect(left + 5, y - 1, left + LIST_W, y + ROW - 2, 0x804A2A70);
            }
            ItemStack icon = i < 0 ? new ItemStack(ModItems.CHALK) : ritualIcon(all[i]);
            itemRender.renderItemAndEffectIntoGUI(icon, left + 8, y);
            String name = i < 0 ? I18n.format("chaosshard.magic.howto") : I18n.format(all[i].key());
            boolean open = i < 0 || all[i].minThread <= MagicUi.threadLevel();
            fontRenderer.drawString(fontRenderer.trimStringToWidth(name, LIST_W - 32), left + 28, y + 4, open ? 0xC9C3D6 : 0x5E566C);
        }
        RenderHelper.disableStandardItemLighting();

        int x = left + LIST_W + 12, y = top + 10, w = W - LIST_W - 22;
        if (selRitual < 0) {
            drawString(fontRenderer, "§l" + I18n.format("chaosshard.magic.howto"), x, y, 0xD9B8FF);
            y += 14;
            y = para(I18n.format("chaosshard.magic.howto.text"), x, y, w - 70, 0xC9C3D6);
            para(I18n.format("chaosshard.magic.howto.summons"), x, y + 6, w - 70, 0x9A8FB0);
            drawCircle(left + W - 70, top + 26);
            return;
        }
        Ritual r = all[selRitual];
        boolean open = r.minThread <= MagicUi.threadLevel();
        drawString(fontRenderer, "§l" + I18n.format(r.key()), x, y, open ? 0xD9B8FF : 0x8A8098);
        y += 14;
        y = para(I18n.format(r.key() + ".desc"), x, y, w, 0xC9C3D6);
        y += 8;
        drawString(fontRenderer, I18n.format("chaosshard.magic.offerings"), x, y, 0x9A8FB0);
        y += 11;
        List<ItemStack> offer = r.offeringStacks();
        RenderHelper.enableGUIStandardItemLighting();
        int ix = x;
        ItemStack hover = ItemStack.EMPTY;
        for (ItemStack s : offer) {
            itemRender.renderItemAndEffectIntoGUI(s, ix, y);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, s, ix, y, null);
            if (mx >= ix && mx < ix + 16 && my >= y && my < y + 16) {
                hover = s;
            }
            ix += 20;
        }
        RenderHelper.disableStandardItemLighting();
        y += 22;
        drawString(fontRenderer, I18n.format("chaosshard.magic.need_thread", r.minThread), x, y, open ? 0x9AD08A : 0xD07070);
        y += 11;
        String dur = r.duration <= 0 ? I18n.format("chaosshard.magic.instant") : cooldown(r.duration);
        drawString(fontRenderer, I18n.format("chaosshard.magic.duration", dur), x, y, 0xB0A8C0);
        if (r.isSphere()) {
            y += 11;
            drawString(fontRenderer, I18n.format("chaosshard.magic.sphere"), x, y, 0xC890FF);
        }
        if (!hover.isEmpty()) {
            renderToolTip(hover, mx, my);
        }
    }

    private static ItemStack ritualIcon(Ritual r) {
        switch (r) {
            case GROWTH: return new ItemStack(net.minecraft.init.Items.WHEAT);
            case SKY: return new ItemStack(net.minecraft.init.Items.WATER_BUCKET);
            case WARD: return new ItemStack(net.minecraft.init.Items.SHIELD);
            case STORM: return new ItemStack(net.minecraft.init.Items.BLAZE_ROD);
            case SUMMON: return new ItemStack(net.minecraft.init.Items.ROTTEN_FLESH);
            case CORRUPTION: return new ItemStack(ModBlocks.CHAOS_ANCHOR);
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
                int col = dx == 0 && dz == 0 ? 0xFFC9C3D6 : ring ? 0xFFB070FF : 0x30FFFFFF;
                Gui.drawRect(px + 1, py + 1, px + c - 1, py + c - 1, col);
            }
        }
        drawCenteredString(fontRenderer, I18n.format("chaosshard.magic.legend"), x + c * 5 / 2, y + c * 5 + 4, 0x8A8098);
    }

    private int para(String text, int x, int y, int w, int color) {
        for (String line : fontRenderer.listFormattedStringToWidth(text, w)) {
            drawString(fontRenderer, line, x, y, color);
            y += 10;
        }
        return y;
    }
}
