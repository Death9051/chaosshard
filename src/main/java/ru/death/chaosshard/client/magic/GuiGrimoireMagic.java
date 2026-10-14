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
    /** Строк списка ритуалов в окне (дальше — прокрутка колесом) и первая видимая строка. */
    private static final int RIT_ROWS = 13;
    private static int ritScroll;

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
        top = (height - H + 16) / 2;   // вкладки над окном (16 px) тоже должны влезть в экран
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
        if (tab == 0 && clickSpell(mx, my)) {
            return;
        }
        // список
        if (mx >= left + 6 && mx < left + LIST_W && my >= top + 8) {
            int row = (my - top - 8) / ROW;
            if (tab == 0 && row >= 0 && row < spells.size()) {
                selSpell = row;
            } else if (tab == 1 && row >= 0 && row < RIT_ROWS && ritScroll + row <= shownRituals().size()) {
                selRitual = ritScroll + row - 1;
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
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0 && tab == 1) {
            ritScroll = clampScroll(ritScroll + (d > 0 ? -1 : 1), shownRituals().size() + 1);
        }
    }

    private static int clampScroll(int v, int rows) {
        return Math.max(0, Math.min(Math.max(0, rows - RIT_ROWS), v));
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
        y += 13;
        y = para(I18n.format(s.key() + ".desc"), x, y, w, 0xC9C3D6);
        y += 4;
        boolean enough = ru.death.chaosshard.client.ClientThreadCache.energy() >= SpellCaster.cost(mc.player, s);
        String req = I18n.format("chaosshard.magic.need_thread", s.minThread);
        drawString(fontRenderer, req, x, y, open ? 0x9AD08A : 0xD07070);
        int rx = x + fontRenderer.getStringWidth(req) + 8;
        String costS = I18n.format("chaosshard.magic.cost", SpellCaster.cost(mc.player, s));
        drawString(fontRenderer, costS, rx, y, enough ? 0xB89CFF : 0xD07070);
        drawString(fontRenderer, I18n.format("chaosshard.magic.cooldown", cooldown(SpellCaster.cooldown(mc.player, s))),
                rx + fontRenderer.getStringWidth(costS) + 8, y, 0xB0A8C0);
        y += 13;
        // формы
        formRects.clear();
        String[] forms = ru.death.chaosshard.spell.SpellPerks.forms(s);
        if (forms.length > 1) {
            drawString(fontRenderer, I18n.format("chaosshard.magic.forms"), x, y + 3, 0x9A8FB0);
            int fx = x + fontRenderer.getStringWidth(I18n.format("chaosshard.magic.forms")) + 6;
            int cur = ru.death.chaosshard.spell.SpellData.form(mc.player, s);
            for (int f = 0; f < forms.length; f++) {
                String fn = I18n.format(ru.death.chaosshard.spell.SpellPerks.formKey(s, f));
                int fw = fontRenderer.getStringWidth(fn) + 8;
                boolean fo = ru.death.chaosshard.spell.SpellData.formOpen(mc.player, s, f);
                int bg = f == cur ? 0xC04A2A70 : fo ? 0x80241830 : 0x60201A28;
                Gui.drawRect(fx, y, fx + fw, y + 13, bg);
                drawString(fontRenderer, fn, fx + 4, y + 3, f == cur ? 0xE8C8FF : fo ? 0xC9C3D6 : 0x5E566C);
                formRects.add(new int[]{fx, y, fw, 13, f});
                fx += fw + 3;
            }
            y += 17;
        }
        // перки
        perkRects.clear();
        ru.death.chaosshard.spell.SpellPerks.Perk[] perks = ru.death.chaosshard.spell.SpellPerks.perks(s);
        int points = ru.death.chaosshard.spell.SpellData.points(mc.player);
        int free = ru.death.chaosshard.spell.SpellData.free(mc.player, s);
        drawString(fontRenderer, I18n.format("chaosshard.magic.perks", free, points), x, y, free > 0 ? 0xFFD27A : 0x9A8FB0);
        y += 12;
        String hoverPerk = null;
        for (int k = 0; k < perks.length; k++) {
            ru.death.chaosshard.spell.SpellPerks.Perk pk = perks[k];
            int r = ru.death.chaosshard.spell.SpellData.rank(mc.player, s, pk.id);
            boolean can = r < pk.max && free > 0 && open;
            Gui.drawRect(x, y, x + 12, y + 12, can ? 0xFF6A3AA0 : 0xFF2A2236);
            drawCenteredString(fontRenderer, r >= pk.max ? "*" : "+", x + 6, y + 2, can ? 0xFFFFFF : 0x6E6680);
            perkRects.add(new int[]{x, y, 12, 12, k});
            String name = I18n.format(pk.key(s));
            drawString(fontRenderer, name, x + 16, y + 2, r > 0 ? 0xE8C8FF : 0xC9C3D6);
            int px = x + 18 + fontRenderer.getStringWidth(name);
            for (int q = 0; q < pk.max; q++) {
                Gui.drawRect(px + q * 7, y + 4, px + q * 7 + 5, y + 9, q < r ? 0xFFB070FF : 0xFF3A3048);
            }
            if (mx >= x && mx < x + w && my >= y && my < y + 12) {
                hoverPerk = I18n.format(pk.key(s) + ".desc");
            }
            y += 14;
        }
        y += 2;
        if (hoverPerk != null) {
            para(hoverPerk, x, y, w, 0xB0A8C0);
        } else {
            para(I18n.format(s.key() + ".lore"), x, y, w, 0x8A8098);
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
        ritScroll = clampScroll(ritScroll, all.length + 1);
        RenderHelper.enableGUIStandardItemLighting();
        int last = Math.min(all.length, ritScroll + RIT_ROWS - 1);
        for (int i = ritScroll - 1; i < last; i++) {
            int y = top + 8 + (i + 1 - ritScroll) * ROW;
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
        // стрелки прокрутки, если список длиннее окна
        if (all.length + 1 > RIT_ROWS) {
            drawCenteredString(fontRenderer, "\u25B2", left + LIST_W - 16, top + H - 14, ritScroll > 0 ? 0xD9B8FF : 0x4A4058);
            drawCenteredString(fontRenderer, "\u25BC", left + LIST_W - 6, top + H - 14,
                    ritScroll + RIT_ROWS < all.length + 1 ? 0xD9B8FF : 0x4A4058);
        }

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
        if (r.isAltar()) {
            y += 11;
            drawString(fontRenderer, I18n.format("chaosshard.magic.altar"), x, y, 0xFFD27A);
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
