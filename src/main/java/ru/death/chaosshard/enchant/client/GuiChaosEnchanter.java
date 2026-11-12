package ru.death.chaosshard.enchant.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.enchant.ContainerChaosEnchanter;
import ru.death.chaosshard.enchant.EnchantKnowledge;
import ru.death.chaosshard.enchant.EnchanterLogic;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketEnchanterAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Окно Стола Хаоса: вкладки «Зачарование» и «Изучение». */
public class GuiChaosEnchanter extends GuiContainer {

    private static final int BG = 0xF0201028;
    private static final int FRAME = 0xFF7A4FB0;
    private static final int TEXT = 0xE0D8F0;
    private static final int DIM = 0x8878A0;
    private static final int ACCENT = 0xC890FF;
    private static final int GOOD = 0x90E0A0;
    private static final int BAD = 0xFF7070;

    private static final int LIST_X = 32, LIST_Y = 18, LIST_W = 158, ROW = 11, ROWS = 8;
    private static final int BTN_TAB0 = 0, BTN_TAB1 = 1, BTN_MINUS = 10, BTN_PLUS = 11, BTN_ACT = 12;

    private final ContainerChaosEnchanter container;
    private int tab;
    private int scroll;
    private Enchantment picked;
    private int level;
    private float shelves;
    private int powerTick;

    /** Строка списка. */
    private static final class Row {
        final Enchantment e;
        final boolean usable;
        final boolean fromItem;
        final String right;
        final int rightColor;

        Row(Enchantment e, boolean usable, boolean fromItem, String right, int rightColor) {
            this.e = e;
            this.usable = usable;
            this.fromItem = fromItem;
            this.right = right;
            this.rightColor = rightColor;
        }
    }

    private List<Row> rows = new ArrayList<>();

    public GuiChaosEnchanter(ContainerChaosEnchanter container) {
        super(container);
        this.container = container;
        xSize = 196;
        ySize = 226;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new GuiButton(BTN_TAB0, guiLeft, guiTop - 20, 80, 18, I18n.format("chaosshard.enchanter.tab.enchant")));
        buttonList.add(new GuiButton(BTN_TAB1, guiLeft + 82, guiTop - 20, 80, 18, I18n.format("chaosshard.enchanter.tab.research")));
        buttonList.add(new GuiButton(BTN_MINUS, guiLeft + 32, guiTop + 121, 14, 16, "-"));
        buttonList.add(new GuiButton(BTN_PLUS, guiLeft + 76, guiTop + 121, 14, 16, "+"));
        buttonList.add(new GuiButton(BTN_ACT, guiLeft + 104, guiTop + 121, 86, 16, ""));
        shelves = EnchanterLogic.shelfPower(mc.world, container.tile.getPos());
    }

    private GuiButton button(int id) {
        for (GuiButton b : buttonList) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    private float power() {
        return EnchanterLogic.tablePower(shelves);
    }

    private static String name(Enchantment e) {
        return I18n.format(e.getName());
    }

    private static String time(int sec) {
        return sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60;
    }

    // ---- списки ----

    private void rebuild() {
        List<Row> out = new ArrayList<>();
        ItemStack stack = container.item();
        Set<String> known = EnchantKnowledge.client;
        Comparator<Enchantment> byName = Comparator.comparing(GuiChaosEnchanter::name);
        if (tab == 0) {
            if (!stack.isEmpty()) {
                List<Enchantment> list = new ArrayList<>();
                for (Enchantment e : EnchanterLogic.all()) {
                    if (known.contains(EnchanterLogic.id(e)) && EnchanterLogic.fits(e, stack)) {
                        list.add(e);
                    }
                }
                list.sort(byName);
                for (Enchantment e : list) {
                    int cur = EnchanterLogic.currentLevel(stack, e);
                    int max = EnchanterLogic.maxLevelAt(e, power());
                    Enchantment bad = EnchanterLogic.conflict(stack, e);
                    if (bad != null) {
                        out.add(new Row(e, false, false, I18n.format("chaosshard.enchanter.conflict"), DIM));
                    } else if (cur >= EnchanterLogic.maxLevel(e)) {
                        out.add(new Row(e, false, false, EnchanterLogic.roman(cur) + " ✔", GOOD));
                    } else if (max <= cur) {
                        out.add(new Row(e, false, false, I18n.format("chaosshard.enchanter.need_power",
                                EnchanterLogic.powerFor(e, cur + 1)), DIM));
                    } else {
                        String r = (cur > 0 ? EnchanterLogic.roman(cur) + "→" : "") + EnchanterLogic.roman(max);
                        out.add(new Row(e, true, false, r, ACCENT));
                    }
                }
            }
        } else {
            String busy = researchId();
            Map<Enchantment, Integer> onItem = EnchanterLogic.on(stack);
            List<Enchantment> fromItem = new ArrayList<>(), rest = new ArrayList<>();
            for (Enchantment e : EnchanterLogic.all()) {
                if (known.contains(EnchanterLogic.id(e))) {
                    continue;
                }
                if (onItem.containsKey(e)) {
                    fromItem.add(e);
                } else if (EnchanterLogic.researchable(e)) {
                    rest.add(e);
                }
            }
            fromItem.sort(byName);
            rest.sort(byName);
            for (Enchantment e : fromItem) {
                out.add(new Row(e, true, true, I18n.format("chaosshard.enchanter.from_item"), GOOD));
            }
            Enchantment running = EnchanterLogic.byId(busy);
            if (running != null && !onItem.containsKey(running)) {
                rest.remove(running);
                out.add(new Row(running, true, false, time(container.secondsLeft), ACCENT));
            }
            for (Enchantment e : rest) {
                int lap = EnchanterLogic.researchLapis(e);
                int sec = EnchanterLogic.researchTicks(e, shelves) / 20;
                out.add(new Row(e, busy.isEmpty(), false, lap + " ◆ " + time(sec),
                        container.lapis() >= lap || mc.player.capabilities.isCreativeMode ? TEXT : BAD));
            }
        }
        rows = out;
        scroll = Math.max(0, Math.min(scroll, rows.size() - ROWS));
        if (picked != null && rows.stream().noneMatch(r -> r.e == picked)) {
            picked = null;
        }
    }

    private String researchId() {
        if (container.researchNum < 0) {
            return "";
        }
        Enchantment e = Enchantment.getEnchantmentByID(container.researchNum);
        return e == null ? "" : EnchanterLogic.id(e);
    }

    private Row row(Enchantment e) {
        for (Row r : rows) {
            if (r.e == e) {
                return r;
            }
        }
        return null;
    }

    // ---- кнопки ----

    private void layout() {
        for (GuiButton b : buttonList) {
            if (b.id == BTN_TAB0 || b.id == BTN_TAB1) {
                b.enabled = b.id != tab;
            }
        }
        GuiButton minus = button(BTN_MINUS), plus = button(BTN_PLUS), act = button(BTN_ACT);
        ItemStack stack = container.item();
        Row r = picked == null ? null : row(picked);
        boolean creative = mc.player.capabilities.isCreativeMode;
        if (tab == 0) {
            int cur = picked == null ? 0 : EnchanterLogic.currentLevel(stack, picked);
            int max = picked == null ? 0 : EnchanterLogic.maxLevelAt(picked, power());
            boolean on = r != null && r.usable;
            minus.visible = plus.visible = on;
            minus.enabled = on && level > cur + 1;
            plus.enabled = on && level < max;
            act.visible = true;
            act.displayString = I18n.format("chaosshard.enchanter.do_enchant");
            if (on) {
                int xp = EnchanterLogic.xpCost(picked, level, cur, EnchanterLogic.others(stack, picked));
                int lap = EnchanterLogic.lapisCost(level, cur);
                act.enabled = creative || mc.player.experienceLevel >= xp && container.lapis() >= lap;
            } else {
                act.enabled = false;
            }
        } else {
            minus.visible = plus.visible = false;
            act.visible = true;
            String busy = researchId();
            if (r != null && r.fromItem) {
                act.displayString = I18n.format("chaosshard.enchanter.do_learn_item");
                act.enabled = true;
            } else if (r != null && !busy.isEmpty() && EnchanterLogic.id(picked).equals(busy)) {
                act.displayString = I18n.format("chaosshard.enchanter.do_cancel");
                act.enabled = !container.researchDone;
            } else if (!busy.isEmpty()) {
                act.displayString = I18n.format("chaosshard.enchanter.busy");
                act.enabled = false;
            } else {
                act.displayString = I18n.format("chaosshard.enchanter.do_research");
                act.enabled = r != null && (creative || container.lapis() >= EnchanterLogic.researchLapis(picked));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id == BTN_TAB0 || b.id == BTN_TAB1) {
            tab = b.id;
            picked = null;
            scroll = 0;
        } else if (b.id == BTN_MINUS) {
            level--;
        } else if (b.id == BTN_PLUS) {
            level++;
        } else if (b.id == BTN_ACT && picked != null) {
            String id = EnchanterLogic.id(picked);
            if (tab == 0) {
                send(PacketEnchanterAction.ENCHANT, id, level);
            } else {
                Row r = row(picked);
                if (r != null && r.fromItem) {
                    send(PacketEnchanterAction.LEARN_ITEM, id, 0);
                } else if (id.equals(researchId())) {
                    send(PacketEnchanterAction.CANCEL, id, 0);
                } else {
                    send(PacketEnchanterAction.RESEARCH, id, 0);
                }
            }
        }
    }

    private static void send(int action, String id, int level) {
        ChaosNetwork.CHANNEL.sendToServer(new PacketEnchanterAction(action, id, level));
    }

    private void pick(Enchantment e) {
        picked = e;
        if (tab == 0 && e != null) {
            ItemStack stack = container.item();
            int cur = EnchanterLogic.currentLevel(stack, e);
            level = Math.max(cur + 1, EnchanterLogic.maxLevelAt(e, power()));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + ROWS * ROW) {
            int i = scroll + (my - LIST_Y) / ROW;
            if (i < rows.size()) {
                pick(rows.get(i).e);
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            scroll = Math.max(0, Math.min(Math.max(0, rows.size() - ROWS), scroll + (d > 0 ? -1 : 1)));
        }
    }

    // ---- рисование ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (++powerTick % 20 == 0) {
            shelves = EnchanterLogic.shelfPower(mc.world, container.tile.getPos());
        }
        rebuild();
        if (picked != null && tab == 0) {
            int cur = EnchanterLogic.currentLevel(container.item(), picked);
            int max = EnchanterLogic.maxLevelAt(picked, power());
            level = Math.max(cur + 1, Math.min(level, Math.max(cur + 1, max)));
        }
        layout();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + ROWS * ROW) {
            int i = scroll + (my - LIST_Y) / ROW;
            if (i < rows.size()) {
                drawHoveringText(stats(rows.get(i).e), mouseX, mouseY);
            }
        } else if (mx >= 4 && mx < 30 && my >= 68 && my < 92) {
            List<String> t = new ArrayList<>();
            t.add(I18n.format("chaosshard.enchanter.power.tip1", fmt(shelves)));
            t.add(TextFormatting.GRAY + I18n.format("chaosshard.enchanter.power.tip2", fmt(power())));
            drawHoveringText(t, mouseX, mouseY);
        }
    }

    private static String fmt(float f) {
        return Math.abs(f - Math.round(f)) < 0.05 ? String.valueOf(Math.round(f)) : String.format("%.1f", f);
    }

    /**
     * Описание зачарования. Ключи по общему для модов образцу {@code enchantment.<мод>.<имя>.desc} (так их пишут
     * Apotheosis, Cyclic, CoFH, EvilCraft, Astral и другие, а для ванильных и прочих — наш перевод), затем
     * {@code <ключ названия>.desc}.
     */
    public static String description(Enchantment e) {
        if (e.getRegistryName() != null) {
            String k = "enchantment." + e.getRegistryName().getNamespace() + "." + e.getRegistryName().getPath() + ".desc";
            if (I18n.hasKey(k)) {
                return I18n.format(k);
            }
        }
        String k = e.getName() + ".desc";
        return I18n.hasKey(k) ? I18n.format(k) : null;
    }

    /** Характеристики зачарования для подсказки. */
    private List<String> stats(Enchantment e) {
        List<String> t = new ArrayList<>();
        t.add((e.isCurse() ? TextFormatting.RED : TextFormatting.LIGHT_PURPLE) + name(e));
        String desc = description(e);
        if (desc != null) {
            for (String line : fontRenderer.listFormattedStringToWidth(desc, 220)) {
                t.add(TextFormatting.GRAY + line);
            }
        }
        t.add(I18n.format("chaosshard.enchanter.stat.rarity",
                I18n.format("chaosshard.enchanter.rarity." + e.getRarity().name().toLowerCase(java.util.Locale.ROOT))));
        int max = EnchanterLogic.maxLevel(e);
        t.add(I18n.format("chaosshard.enchanter.stat.max", EnchanterLogic.roman(max)));
        StringBuilder sb = new StringBuilder();
        for (int k = 1; k <= Math.min(max, 10); k++) {
            int need = EnchanterLogic.powerFor(e, k);
            sb.append(need <= power() ? TextFormatting.GREEN : TextFormatting.DARK_GRAY)
                    .append(EnchanterLogic.roman(k)).append(' ').append(need).append(TextFormatting.RESET);
            if (k < Math.min(max, 10)) {
                sb.append("  ");
            }
        }
        t.add(I18n.format("chaosshard.enchanter.stat.power"));
        t.add(sb.toString());
        if (e.isTreasureEnchantment()) {
            t.add(TextFormatting.GOLD + I18n.format("chaosshard.enchanter.stat.treasure"));
        }
        if (tab == 1 && !EnchantKnowledge.client.contains(EnchanterLogic.id(e))) {
            t.add(TextFormatting.GRAY + I18n.format("chaosshard.enchanter.stat.research",
                    EnchanterLogic.researchLapis(e), time(EnchanterLogic.researchTicks(e, shelves) / 20)));
        }
        return t;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        drawRect(x - 1, y - 1, x + xSize + 1, y + ySize + 1, FRAME);
        drawRect(x, y, x + xSize, y + ySize, BG);
        for (Slot s : container.inventorySlots) {
            drawRect(x + s.xPos - 1, y + s.yPos - 1, x + s.xPos + 17, y + s.yPos + 17, 0xFF4A3A60);
            drawRect(x + s.xPos, y + s.yPos, x + s.xPos + 16, y + s.yPos + 16, 0xFF100A18);
        }
        drawRect(x + LIST_X - 1, y + LIST_Y - 1, x + LIST_X + LIST_W + 1, y + LIST_Y + ROWS * ROW + 1, 0xFF4A3A60);
        drawRect(x + LIST_X, y + LIST_Y, x + LIST_X + LIST_W, y + LIST_Y + ROWS * ROW, 0xFF120C1A);
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("tile.chaosshard.chaos_enchanter.name"), 6, 6, TEXT);
        // сила
        fontRenderer.drawString(I18n.format("chaosshard.enchanter.power"), 4, 70, DIM);
        fontRenderer.drawString(fmt(power()), 4, 80, ACCENT);
        // список
        if (rows.isEmpty()) {
            String hint = tab == 0
                    ? I18n.format(container.item().isEmpty() ? "chaosshard.enchanter.hint.put" : "chaosshard.enchanter.hint.none")
                    : I18n.format("chaosshard.enchanter.hint.all_known");
            fontRenderer.drawSplitString(hint, LIST_X + 4, LIST_Y + 4, LIST_W - 8, DIM);
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int ry = LIST_Y + i * ROW;
            if (r.e == picked) {
                drawRect(LIST_X, ry, LIST_X + LIST_W, ry + ROW, 0xFF3A2858);
            }
            int rw = fontRenderer.getStringWidth(r.right);
            String n = fontRenderer.trimStringToWidth(name(r.e), LIST_W - rw - 10);
            fontRenderer.drawString(n, LIST_X + 3, ry + 2, r.usable ? (r.e.isCurse() ? BAD : TEXT) : DIM);
            fontRenderer.drawString(r.right, LIST_X + LIST_W - rw - 3, ry + 2, r.rightColor);
        }
        if (rows.size() > ROWS) {
            int h = ROWS * ROW, bar = Math.max(8, h * ROWS / rows.size());
            int by = LIST_Y + (h - bar) * scroll / Math.max(1, rows.size() - ROWS);
            drawRect(LIST_X + LIST_W - 2, by, LIST_X + LIST_W, by + bar, 0xFF7A4FB0);
        }
        // низ
        ItemStack stack = container.item();
        if (tab == 0) {
            if (picked != null && row(picked) != null && row(picked).usable) {
                int cur = EnchanterLogic.currentLevel(stack, picked);
                fontRenderer.drawString(EnchanterLogic.roman(level), 61 - fontRenderer.getStringWidth(EnchanterLogic.roman(level)) / 2, 125, ACCENT);
                int xp = EnchanterLogic.xpCost(picked, level, cur, EnchanterLogic.others(stack, picked));
                int lap = EnchanterLogic.lapisCost(level, cur);
                boolean creative = mc.player.capabilities.isCreativeMode;
                String a = I18n.format("chaosshard.enchanter.cost_xp", xp);
                String b = I18n.format("chaosshard.enchanter.cost_lapis", lap);
                fontRenderer.drawString(fontRenderer.trimStringToWidth(name(picked), 70), LIST_X, 110, TEXT);
                int ax = 104;
                fontRenderer.drawString(a, ax, 110, creative || mc.player.experienceLevel >= xp ? GOOD : BAD);
                fontRenderer.drawString(b, ax + fontRenderer.getStringWidth(a) + 6, 110, creative || container.lapis() >= lap ? GOOD : BAD);
            }
        } else {
            Enchantment running = EnchanterLogic.byId(researchId());
            if (running != null) {
                String s = container.researchDone ? I18n.format("chaosshard.enchanter.research_done", name(running))
                        : I18n.format("chaosshard.enchanter.researching", name(running));
                fontRenderer.drawString(fontRenderer.trimStringToWidth(s, 158), LIST_X, 109, ACCENT);
                int w = 68;
                drawRect(LIST_X, 125, LIST_X + w, 130, 0xFF100A18);
                drawRect(LIST_X, 125, LIST_X + w * Math.max(0, Math.min(1000, container.partPermille)) / 1000, 130, 0xFFC890FF);
            } else if (picked != null) {
                fontRenderer.drawString(fontRenderer.trimStringToWidth(name(picked), 158), LIST_X, 109, TEXT);
            }
        }
    }
}
