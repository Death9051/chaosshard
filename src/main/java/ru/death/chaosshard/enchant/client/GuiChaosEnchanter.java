package ru.death.chaosshard.enchant.client;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.Tags;
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

/**
 * Окно Стола Хаоса в духе Гримуара: серебряная рамка, тёмный фиолет, золото для выбранного.
 * <ul>
 * <li>Вкладки сверху: активная сливается с окном, у неактивной — тёмный фон.</li>
 * <li>Слева место предмета и лазурита, под ними шкала силы полок (золотая отметка — 15 обычных полок).</li>
 * <li>Список: полоска слева — редкость, справа — что можно сделать (уровни, замок с нужной силой, цена изучения).</li>
 * <li>Панель выбранного: название, описание, уровень стрелками, цена и кнопка. Если кнопка недоступна,
 * на ней написано почему («Мало опыта», «Стол занят»…), а не просто серый цвет.</li>
 * </ul>
 * Текстуры: chaos_enchanter.png (фон) и chaos_enchanter_widgets.png (кнопки, вкладки, значки),
 * рисует docs/sprites/stol_haosa_gui.py.
 */
public class GuiChaosEnchanter extends GuiContainer {

    private static final ResourceLocation BG_TEX = new ResourceLocation(Tags.MOD_ID, "textures/gui/chaos_enchanter.png");
    private static final ResourceLocation WD_TEX = new ResourceLocation(Tags.MOD_ID, "textures/gui/chaos_enchanter_widgets.png");

    // цвета текста (контраст к фону ≥ 4.5:1)
    private static final int TEXT = 0xECE6F8;
    private static final int SOFT = 0xB9AED2;
    private static final int DIM = 0x8C80A8;
    private static final int ACCENT = 0xD7A6FF;
    private static final int GOLD = 0xECB064;
    private static final int GOOD = 0x8FE3A6;
    private static final int BAD = 0xFF8080;
    private static final int OFF = 0x6E6680;

    // разметка (совпадает с текстурой)
    private static final int LIST_X = 45, LIST_Y = 21, LIST_W = 160, ROW = 12, ROWS = 7;
    private static final int DET_X = 45, DET_Y = 109, DET_W = 160;
    private static final int BTN_X = 141, BTN_Y = 140, BTN_W = 62, BTN_H = 16;
    private static final int GAUGE_X = 18, GAUGE_Y = 79, GAUGE_H = 56;
    private static final int TAB_W = 92;
    /** Шкала полна при силе стола 90 (30 силы полок); отметка на половине — 15 обычных полок. */
    private static final float GAUGE_FULL = 90F;

    private final ContainerChaosEnchanter container;
    private int tab;
    private int scroll;
    private Enchantment picked;
    private int level;
    private float shelves;
    private int powerTick;

    private enum Kind { READY, MAXED, LOCKED, CONFLICT, FROM_ITEM, RUNNING, RESEARCH }

    /** Строка списка. */
    private static final class Row {
        final Enchantment e;
        final Kind kind;

        Row(Enchantment e, Kind kind) {
            this.e = e;
            this.kind = kind;
        }
    }

    private List<Row> rows = new ArrayList<>();

    public GuiChaosEnchanter(ContainerChaosEnchanter container) {
        super(container);
        this.container = container;
        xSize = 214;
        ySize = 250;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        shelves = EnchanterLogic.shelfPower(mc.world, container.tile.getPos());
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

    private boolean creative() {
        return mc.player.capabilities.isCreativeMode;
    }

    // ------------------------------------------------------------------ списки

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
                List<Row> ready = new ArrayList<>(), rest = new ArrayList<>();
                for (Enchantment e : list) {
                    int cur = EnchanterLogic.currentLevel(stack, e);
                    if (EnchanterLogic.conflict(stack, e) != null) {
                        rest.add(new Row(e, Kind.CONFLICT));
                    } else if (cur >= EnchanterLogic.maxLevel(e)) {
                        rest.add(new Row(e, Kind.MAXED));
                    } else if (EnchanterLogic.maxLevelAt(e, power()) <= cur) {
                        rest.add(new Row(e, Kind.LOCKED));
                    } else {
                        ready.add(new Row(e, Kind.READY));
                    }
                }
                out.addAll(ready);   // доступное — сверху
                out.addAll(rest);
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
            Enchantment running = EnchanterLogic.byId(busy);
            if (running != null && !onItem.containsKey(running)) {
                rest.remove(running);
                out.add(new Row(running, Kind.RUNNING));
            }
            for (Enchantment e : fromItem) {
                out.add(new Row(e, Kind.FROM_ITEM));
            }
            for (Enchantment e : rest) {
                out.add(new Row(e, Kind.RESEARCH));
            }
        }
        rows = out;
        scroll = Math.max(0, Math.min(scroll, rows.size() - ROWS));
        if (picked != null && row(picked) == null) {
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

    // ------------------------------------------------------------------ действие кнопки

    /** Надпись на кнопке и можно ли нажать. Недоступная кнопка говорит, почему. */
    private String actionLabel() {
        Row r = picked == null ? null : row(picked);
        if (r == null) {
            return null;
        }
        ItemStack stack = container.item();
        if (tab == 0) {
            switch (r.kind) {
                case CONFLICT:
                    return "!" + I18n.format("chaosshard.enchanter.btn.conflict");
                case MAXED:
                    return "!" + I18n.format("chaosshard.enchanter.btn.maxed");
                case LOCKED:
                    return "!" + I18n.format("chaosshard.enchanter.btn.locked");
                default:
                    break;
            }
            int cur = EnchanterLogic.currentLevel(stack, picked);
            int xp = EnchanterLogic.xpCost(picked, level, cur, EnchanterLogic.others(stack, picked));
            int lap = EnchanterLogic.lapisCost(level, cur);
            if (!creative() && mc.player.experienceLevel < xp) {
                return "!" + I18n.format("chaosshard.enchanter.btn.no_xp");
            }
            if (!creative() && container.lapis() < lap) {
                return "!" + I18n.format("chaosshard.enchanter.btn.no_lapis");
            }
            return I18n.format("chaosshard.enchanter.do_enchant");
        }
        String busy = researchId();
        if (r.kind == Kind.FROM_ITEM) {
            return I18n.format("chaosshard.enchanter.do_learn_item");
        }
        if (r.kind == Kind.RUNNING) {
            return container.researchDone ? "!" + I18n.format("chaosshard.enchanter.btn.done") : I18n.format("chaosshard.enchanter.do_cancel");
        }
        if (!busy.isEmpty()) {
            return "!" + I18n.format("chaosshard.enchanter.busy");
        }
        if (!creative() && container.lapis() < EnchanterLogic.researchLapis(picked)) {
            return "!" + I18n.format("chaosshard.enchanter.btn.no_lapis");
        }
        return I18n.format("chaosshard.enchanter.do_research");
    }

    private void act() {
        String label = actionLabel();
        if (label == null || label.startsWith("!")) {
            return;
        }
        String id = EnchanterLogic.id(picked);
        Row r = row(picked);
        if (tab == 0) {
            send(PacketEnchanterAction.ENCHANT, id, level);
        } else if (r != null && r.kind == Kind.FROM_ITEM) {
            send(PacketEnchanterAction.LEARN_ITEM, id, 0);
        } else if (r != null && r.kind == Kind.RUNNING) {
            send(PacketEnchanterAction.CANCEL, id, 0);
        } else {
            send(PacketEnchanterAction.RESEARCH, id, 0);
        }
        click();
    }

    private static void send(int action, String id, int level) {
        ChaosNetwork.CHANNEL.sendToServer(new PacketEnchanterAction(action, id, level));
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void pick(Enchantment e) {
        picked = e;
        if (tab == 0 && e != null) {
            int cur = EnchanterLogic.currentLevel(container.item(), e);
            level = Math.max(cur + 1, EnchanterLogic.maxLevelAt(e, power()));
        }
    }

    private int curLevel() {
        return picked == null ? 0 : EnchanterLogic.currentLevel(container.item(), picked);
    }

    private int maxLevel() {
        return picked == null ? 0 : EnchanterLogic.maxLevelAt(picked, power());
    }

    private boolean levelArrows() {
        Row r = picked == null ? null : row(picked);
        return tab == 0 && r != null && r.kind == Kind.READY;
    }

    // ------------------------------------------------------------------ мышь

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        for (int t = 0; t < 2; t++) {
            if (in(mx, my, 6 + t * (TAB_W + 2), -19, TAB_W, 19)) {
                if (tab != t) {
                    tab = t;
                    picked = null;
                    scroll = 0;
                    click();
                }
                return;
            }
        }
        if (button == 0) {
            if (in(mx, my, LIST_X, LIST_Y, LIST_W, ROWS * ROW)) {
                int i = scroll + (my - LIST_Y) / ROW;
                if (i < rows.size()) {
                    pick(rows.get(i).e);
                    click();
                }
                return;
            }
            if (picked != null && in(mx, my, BTN_X, BTN_Y, BTN_W, BTN_H)) {
                act();
                return;
            }
            if (levelArrows()) {
                if (in(mx, my, 160, 110, 11, 11) && level > curLevel() + 1) {
                    level--;
                    click();
                    return;
                }
                if (in(mx, my, 194, 110, 11, 11) && level < maxLevel()) {
                    level++;
                    click();
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            scroll = Math.max(0, Math.min(Math.max(0, rows.size() - ROWS), scroll + (d > 0 ? -1 : 1)));
        }
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (++powerTick % 20 == 0) {
            shelves = EnchanterLogic.shelfPower(mc.world, container.tile.getPos());
        }
        rebuild();
        if (picked != null && tab == 0) {
            int cur = curLevel();
            level = Math.max(cur + 1, Math.min(level, Math.max(cur + 1, maxLevel())));
        }
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        if (in(mx, my, LIST_X, LIST_Y, LIST_W, ROWS * ROW)) {
            int i = scroll + (my - LIST_Y) / ROW;
            if (i < rows.size()) {
                drawHoveringText(stats(rows.get(i).e), mouseX, mouseY);
            }
        } else if (in(mx, my, 12, 76, 22, 82)) {
            List<String> t = new ArrayList<>();
            t.add(TextFormatting.LIGHT_PURPLE + I18n.format("chaosshard.enchanter.power.tip1", fmt(shelves)));
            t.add(TextFormatting.GRAY + I18n.format("chaosshard.enchanter.power.tip2", fmt(power())));
            t.add(TextFormatting.GOLD + I18n.format("chaosshard.enchanter.power.tip3"));
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

    private static String rarityKey(Enchantment e) {
        return e.getRarity().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Цвет полоски редкости: обычное — серое, необычное — зелёное, редкое — синее, очень редкое — фиолетовое. */
    private static int rarityColor(Enchantment e) {
        if (e.isCurse()) {
            return 0xFFE05A5A;
        }
        if (e.isTreasureEnchantment()) {
            return 0xFFECB064;
        }
        switch (e.getRarity()) {
            case COMMON:
                return 0xFF9A96A8;
            case UNCOMMON:
                return 0xFF6FD08C;
            case RARE:
                return 0xFF6FA8FF;
            default:
                return 0xFFC77DFF;
        }
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
        t.add(I18n.format("chaosshard.enchanter.stat.rarity", I18n.format("chaosshard.enchanter.rarity." + rarityKey(e))));
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

    // ------------------------------------------------------------------ фон

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        int mx = mouseX - x, my = mouseY - y;
        GlStateManager.color(1, 1, 1, 1);
        // неактивная вкладка — под окном, активная — поверх, сливается с ним
        mc.getTextureManager().bindTexture(WD_TEX);
        int other = 1 - tab;
        drawTexturedModalRect(x + 6 + other * (TAB_W + 2), y - 17, 0, 80, TAB_W, 20);
        if (in(mx, my, 6 + other * (TAB_W + 2), -17, TAB_W, 17)) {
            drawRect(x + 8 + other * (TAB_W + 2), y - 15, x + 4 + other * (TAB_W + 2) + TAB_W, y, 0x22FFFFFF);
        }
        mc.getTextureManager().bindTexture(BG_TEX);
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
        mc.getTextureManager().bindTexture(WD_TEX);
        drawTexturedModalRect(x + 6 + tab * (TAB_W + 2), y - 20, 0, 54, TAB_W, 24);
        // призраки пустых мест
        if (container.item().isEmpty()) {
            drawTexturedModalRect(x + ContainerChaosEnchanter.ITEM_X, y + ContainerChaosEnchanter.ITEM_Y, 0, 160, 16, 16);
        }
        if (container.lapis() == 0) {
            drawTexturedModalRect(x + ContainerChaosEnchanter.LAPIS_X, y + ContainerChaosEnchanter.LAPIS_Y, 16, 160, 16, 16);
        }
        // шкала силы
        int h = Math.round(GAUGE_H * Math.min(1F, power() / GAUGE_FULL));
        if (h > 0) {
            drawTexturedModalRect(x + GAUGE_X, y + GAUGE_Y + GAUGE_H - h, 100, 54 + GAUGE_H - h, 10, h);
            int shine = (int) ((mc.world.getTotalWorldTime() + partialTicks) * 0.7F) % (h + 8);
            if (shine < h) {
                drawRect(x + GAUGE_X + 1, y + GAUGE_Y + GAUGE_H - 1 - shine, x + GAUGE_X + 9, y + GAUGE_Y + GAUGE_H - shine, 0x66FFFFFF);
            }
        }
        // значки вкладок
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(new ItemStack(Items.ENCHANTED_BOOK), x + 10, y - 16 + (tab == 0 ? -1 : 1));
        itemRender.renderItemAndEffectIntoGUI(new ItemStack(Items.WRITABLE_BOOK), x + 10 + TAB_W + 2, y - 16 + (tab == 1 ? -1 : 1));
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    // ------------------------------------------------------------------ передний план

    private void icon(int x, int y, int idx) {
        mc.getTextureManager().bindTexture(WD_TEX);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        drawTexturedModalRect(x, y, idx * 10, 140, 9, 9);
    }

    private static final int I_LAPIS = 0, I_XP = 1, I_CLOCK = 2, I_LOCK = 3, I_CHECK = 4, I_LEFT = 5, I_RIGHT = 6, I_STAR = 7;

    /** Текст справа налево от x1 со значком; возвращает левый край. */
    private int rightText(int x1, int y, String s, int color, int iconIdx) {
        int w = fontRenderer.getStringWidth(s);
        fontRenderer.drawString(s, x1 - w, y, color);
        if (iconIdx >= 0) {
            icon(x1 - w - 11, y - 1, iconIdx);
            return x1 - w - 11;
        }
        return x1 - w;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        String title = I18n.format("tile.chaosshard.chaos_enchanter.name");
        fontRenderer.drawString(title, (xSize - fontRenderer.getStringWidth(title)) / 2, 7, TEXT);
        // вкладки
        String[] tabs = {I18n.format("chaosshard.enchanter.tab.enchant"), I18n.format("chaosshard.enchanter.tab.research")};
        for (int t = 0; t < 2; t++) {
            boolean on = t == tab;
            boolean hover = !on && in(mx, my, 6 + t * (TAB_W + 2), -17, TAB_W, 17);
            fontRenderer.drawString(tabs[t], 6 + t * (TAB_W + 2) + 24, on ? -11 : -9, on ? TEXT : (hover ? SOFT : DIM));
        }
        // сила
        String pw = fmt(power());
        String lbl = I18n.format("chaosshard.enchanter.power");
        fontRenderer.drawString(lbl, 23 - fontRenderer.getStringWidth(lbl) / 2, 139, DIM);
        fontRenderer.drawString(pw, 23 - fontRenderer.getStringWidth(pw) / 2, 149, ACCENT);
        drawList(mx, my);
        drawDetail(mx, my);
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawList(int mx, int my) {
        if (rows.isEmpty()) {
            String hint = tab == 0
                    ? I18n.format(container.item().isEmpty() ? "chaosshard.enchanter.hint.put" : "chaosshard.enchanter.hint.none")
                    : I18n.format("chaosshard.enchanter.hint.all_known");
            List<String> lines = fontRenderer.listFormattedStringToWidth(hint, LIST_W - 20);
            int y0 = LIST_Y + (ROWS * ROW - lines.size() * 10) / 2;
            for (int i = 0; i < lines.size(); i++) {
                String s = lines.get(i);
                fontRenderer.drawString(s, LIST_X + (LIST_W - fontRenderer.getStringWidth(s)) / 2, y0 + i * 10, DIM);
            }
            return;
        }
        boolean scrollable = rows.size() > ROWS;
        int right = LIST_X + LIST_W - (scrollable ? 6 : 3);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int ry = LIST_Y + i * ROW;
            boolean sel = r.e == picked;
            boolean hover = in(mx, my, LIST_X, ry, LIST_W, ROW);
            int bg = sel ? 0xFF35245A : hover ? 0xFF241938 : ((scroll + i) % 2 == 0 ? 0xFF15101F : 0xFF110C1A);
            drawRect(LIST_X, ry, LIST_X + LIST_W, ry + ROW, bg);
            if (sel) {
                drawRect(LIST_X, ry, LIST_X + LIST_W, ry + 1, 0xFF6A55A0);
                drawRect(LIST_X, ry + ROW - 1, LIST_X + LIST_W, ry + ROW, 0xFF6A55A0);
            }
            drawRect(LIST_X, ry + 1, LIST_X + 2, ry + ROW - 1, rarityColor(r.e));
            if (sel) {
                drawRect(LIST_X + 3, ry + 5, LIST_X + 5, ry + 7, 0xFFECB064);
            }
            boolean active = r.kind == Kind.READY || r.kind == Kind.FROM_ITEM || r.kind == Kind.RUNNING
                    || (r.kind == Kind.RESEARCH && researchId().isEmpty());
            int nameColor = r.e.isCurse() ? 0xFF9090 : (active ? TEXT : DIM);
            int left;
            ItemStack stack = container.item();
            switch (r.kind) {
                case READY: {
                    int cur = EnchanterLogic.currentLevel(stack, r.e);
                    int max = EnchanterLogic.maxLevelAt(r.e, power());
                    String s = (cur > 0 ? EnchanterLogic.roman(cur) + " › " : "")
                            + (max > 1 && cur == 0 ? "I–" : "") + EnchanterLogic.roman(max);
                    left = rightText(right, ry + 2, s, ACCENT, -1);
                    break;
                }
                case MAXED:
                    left = rightText(right, ry + 2, EnchanterLogic.roman(EnchanterLogic.currentLevel(stack, r.e)), GOOD, I_CHECK);
                    break;
                case LOCKED:
                    left = rightText(right, ry + 2, String.valueOf(EnchanterLogic.powerFor(r.e, EnchanterLogic.currentLevel(stack, r.e) + 1)), DIM, I_LOCK);
                    break;
                case CONFLICT:
                    left = rightText(right, ry + 2, I18n.format("chaosshard.enchanter.conflict"), 0xC88A9A, -1);
                    break;
                case FROM_ITEM:
                    left = rightText(right, ry + 2, I18n.format("chaosshard.enchanter.from_item"), GOOD, I_STAR);
                    break;
                case RUNNING:
                    left = rightText(right, ry + 2, time(container.secondsLeft), GOLD, I_CLOCK);
                    break;
                default: {
                    // в строке — только цена; время изучения — в панели ниже и в подсказке (меньше шума в списке)
                    int lap = EnchanterLogic.researchLapis(r.e);
                    left = rightText(right, ry + 2, String.valueOf(lap), creative() || container.lapis() >= lap ? SOFT : BAD, I_LAPIS);
                    break;
                }
            }
            String n = fontRenderer.trimStringToWidth(name(r.e), left - (LIST_X + 8) - 4);
            fontRenderer.drawString(n, LIST_X + 8, ry + 2, nameColor);
        }
        if (scrollable) {
            int h = ROWS * ROW;
            int bar = Math.max(10, h * ROWS / rows.size());
            int by = LIST_Y + (h - bar) * scroll / Math.max(1, rows.size() - ROWS);
            drawRect(LIST_X + LIST_W - 3, LIST_Y, LIST_X + LIST_W, LIST_Y + h, 0xFF0B0812);
            drawRect(LIST_X + LIST_W - 3, by, LIST_X + LIST_W, by + bar, 0xFFAAAEBC);
            drawRect(LIST_X + LIST_W - 3, by, LIST_X + LIST_W - 2, by + bar, 0xFFE1E4EE);
        }
    }

    private void drawDetail(int mx, int my) {
        Row r = picked == null ? null : row(picked);
        if (r == null) {
            String s = I18n.format(rows.isEmpty() ? "chaosshard.enchanter.hint.empty" : "chaosshard.enchanter.hint.pick");
            List<String> lines = fontRenderer.listFormattedStringToWidth(s, DET_W - 16);
            for (int i = 0; i < lines.size() && i < 3; i++) {
                String l = lines.get(i);
                fontRenderer.drawString(l, DET_X + (DET_W - fontRenderer.getStringWidth(l)) / 2, DET_Y + 14 + i * 10 - lines.size() * 4, DIM);
            }
            return;
        }
        ItemStack stack = container.item();
        // название и уровень
        int nameRight = DET_X + DET_W - 4;
        if (levelArrows()) {
            int cur = curLevel(), max = maxLevel();
            boolean canDown = level > cur + 1, canUp = level < max;
            String lv = EnchanterLogic.roman(level);
            if (in(mx, my, 160, 110, 11, 11) && canDown) {
                drawRect(160, 110, 171, 121, 0x33FFFFFF);
            }
            if (in(mx, my, 194, 110, 11, 11) && canUp) {
                drawRect(194, 110, 205, 121, 0x33FFFFFF);
            }
            GlStateManager.color(1, 1, 1, canDown ? 1F : 0.3F);
            mc.getTextureManager().bindTexture(WD_TEX);
            GlStateManager.enableBlend();
            drawTexturedModalRect(161, 111, I_LEFT * 10, 140, 9, 9);
            GlStateManager.color(1, 1, 1, canUp ? 1F : 0.3F);
            drawTexturedModalRect(195, 111, I_RIGHT * 10, 140, 9, 9);
            GlStateManager.color(1, 1, 1, 1);
            fontRenderer.drawString(lv, 183 - fontRenderer.getStringWidth(lv) / 2, 112, GOLD);
            nameRight = 158;
        } else {
            String rar = I18n.format("chaosshard.enchanter.rarity." + rarityKey(picked));
            nameRight = rightText(DET_X + DET_W - 4, 112, rar, rarityColor(picked) & 0xFFFFFF, -1) - 4;
        }
        drawRect(DET_X + 2, 111, DET_X + 4, 120, rarityColor(picked));
        fontRenderer.drawString(fontRenderer.trimStringToWidth(name(picked), nameRight - (DET_X + 7)), DET_X + 7, 112,
                picked.isCurse() ? 0xFF9090 : TEXT);
        // описание
        String desc = description(picked);
        if (desc != null) {
            List<String> lines = fontRenderer.listFormattedStringToWidth(desc, DET_W - 10);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                String l = lines.get(i);
                if (i == 1 && lines.size() > 2) {
                    l = fontRenderer.trimStringToWidth(l, DET_W - 18) + "…";
                }
                fontRenderer.drawString(l, DET_X + 4, 123 + i * 9, SOFT);
            }
        }
        // цена / ход изучения
        int cy = 145;
        if (tab == 0 && r.kind == Kind.READY) {
            int cur = curLevel();
            int xp = EnchanterLogic.xpCost(picked, level, cur, EnchanterLogic.others(stack, picked));
            int lap = EnchanterLogic.lapisCost(level, cur);
            boolean okXp = creative() || mc.player.experienceLevel >= xp, okLap = creative() || container.lapis() >= lap;
            icon(DET_X + 4, cy - 1, I_XP);
            String a = I18n.format("chaosshard.enchanter.cost_xp", xp);
            fontRenderer.drawString(a, DET_X + 15, cy, okXp ? GOOD : BAD);
            int lx = DET_X + 15 + fontRenderer.getStringWidth(a) + 6;
            icon(lx, cy - 1, I_LAPIS);
            fontRenderer.drawString(String.valueOf(lap), lx + 11, cy, okLap ? GOOD : BAD);
        } else if (tab == 1) {
            if (r.kind == Kind.RUNNING) {
                int w = 70;
                drawRect(DET_X + 4, cy + 1, DET_X + 4 + w, cy + 7, 0xFF0B0812);
                int fill = w * Math.max(0, Math.min(1000, container.partPermille)) / 1000;
                mc.getTextureManager().bindTexture(WD_TEX);
                GlStateManager.color(1, 1, 1, 1);
                drawTexturedModalRect(DET_X + 4, cy + 1, 112, 54, fill, 5);
                fontRenderer.drawString(time(container.secondsLeft), DET_X + 8 + w, cy, GOLD);
            } else if (r.kind == Kind.FROM_ITEM) {
                icon(DET_X + 4, cy - 1, I_STAR);
                fontRenderer.drawString(I18n.format("chaosshard.enchanter.free"), DET_X + 15, cy, GOOD);
            } else {
                int lap = EnchanterLogic.researchLapis(picked);
                boolean ok = creative() || container.lapis() >= lap;
                icon(DET_X + 4, cy - 1, I_LAPIS);
                String a = String.valueOf(lap);
                fontRenderer.drawString(a, DET_X + 15, cy, ok ? GOOD : BAD);
                int cx = DET_X + 15 + fontRenderer.getStringWidth(a) + 6;
                icon(cx, cy - 1, I_CLOCK);
                fontRenderer.drawString(time(EnchanterLogic.researchTicks(picked, shelves) / 20), cx + 11, cy, SOFT);
            }
        }
        // кнопка
        String label = actionLabel();
        if (label != null) {
            boolean off = label.startsWith("!");
            String text = off ? label.substring(1) : label;
            boolean hover = !off && in(mx, my, BTN_X, BTN_Y, BTN_W, BTN_H);
            mc.getTextureManager().bindTexture(WD_TEX);
            GlStateManager.color(1, 1, 1, 1);
            int v = off ? 32 : (hover ? 16 : 0);
            drawTexturedModalRect(BTN_X, BTN_Y, 0, v, BTN_W / 2, BTN_H);
            drawTexturedModalRect(BTN_X + BTN_W / 2, BTN_Y, 100 - BTN_W / 2, v, BTN_W / 2, BTN_H);
            text = fontRenderer.trimStringToWidth(text, BTN_W - 6);
            fontRenderer.drawString(text, BTN_X + (BTN_W - fontRenderer.getStringWidth(text)) / 2, BTN_Y + 4,
                    off ? (label.contains(I18n.format("chaosshard.enchanter.btn.no_xp")) || label.contains(I18n.format("chaosshard.enchanter.btn.no_lapis")) ? 0xD08080 : OFF)
                            : (hover ? 0xFFFFFF : TEXT));
        }
    }
}
