package ru.death.chaosshard.anchor.client;

import net.minecraft.block.material.MapColor;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.anchor.AnchorSpec;
import ru.death.chaosshard.anchor.AnchorView;
import ru.death.chaosshard.anchor.ContainerAnchor;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.client.ChaosUi;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketAnchorAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static ru.death.chaosshard.client.ChaosUi.ACCENT;
import static ru.death.chaosshard.client.ChaosUi.BAD;
import static ru.death.chaosshard.client.ChaosUi.DIM;
import static ru.death.chaosshard.client.ChaosUi.GOLD;
import static ru.death.chaosshard.client.ChaosUi.GOOD;
import static ru.death.chaosshard.client.ChaosUi.OFF;
import static ru.death.chaosshard.client.ChaosUi.SOFT;
import static ru.death.chaosshard.client.ChaosUi.TEXT;
import static ru.death.chaosshard.client.ChaosUi.in;

/**
 * Окно Якоря: вкладки «Якорь», «Сеть», «Путь» (специализация) и «Склад», в духе Гримуара и Стола Хаоса.
 * <ul>
 * <li>Вкладки со значками; открытая светлая и сливается с окном.</li>
 * <li>«Якорь» разбит на блоки со значками (рост, земля, кровь, набеги, печати); закрытые места печатей — с замком.</li>
 * <li>«Сеть»: в строке Якоря явные кнопки «Взгляд» и «Перенос»; на карте отряды и чужаки различаются формой.</li>
 * <li>Пустые вкладки объясняют, почему пусто; недоступные кнопки пишут причину.</li>
 * </ul>
 */
public class GuiAnchor extends GuiContainer {

    /** Последние сведения от сервера. */
    public static NBTTagCompound view = new NBTTagCompound();

    private static final ResourceLocation BG = new ResourceLocation(Tags.MOD_ID, "textures/gui/anchor_gui.png");

    private static final int X0 = 13, X1 = 202, W = X1 - X0;
    private static final int TAB_X = 2, TAB_STEP = 53;
    private static final int LIST_Y = 54, ROW = 11, LIST_ROWS = 13;
    private static final int BORROW_Y = 184;

    private static final int TAB_ANCHOR = 0, TAB_NET = 1, TAB_PATH = 2, TAB_STORE = 3;

    private final ContainerAnchor container;
    private int tab;
    private int scroll;
    private int picked = -1;
    private boolean map;
    private BlockPos scry;
    private final int[] slotX;
    private DynamicTexture scryTex;
    private long scryShown = Long.MIN_VALUE;

    public GuiAnchor(ContainerAnchor container) {
        super(container);
        this.container = container;
        xSize = 214;
        ySize = 236;
        view = new NBTTagCompound();
        slotX = new int[container.inventorySlots.size()];
        for (int i = 0; i < slotX.length; i++) {
            slotX[i] = container.inventorySlots.get(i).xPos;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        layout();
    }

    /** Видимость мест по вкладке. */
    private void layout() {
        int storage = view.getInteger("storage");
        boolean edit = view.getBoolean("edit");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot s = container.inventorySlots.get(i);
            boolean show;
            if (i < ContainerAnchor.STORAGE_FIRST) {
                show = tab == TAB_ANCHOR;
            } else if (i < ContainerAnchor.SAMPLE_SLOT) {
                show = tab == TAB_STORE && i - ContainerAnchor.STORAGE_FIRST < storage;
            } else if (i == ContainerAnchor.SAMPLE_SLOT) {
                show = tab == TAB_STORE && view.getInteger("spec") == AnchorSpec.HARVEST.ordinal() && view.getBoolean("stage");
            } else {
                show = tab == TAB_ANCHOR || (tab == TAB_STORE && storage > 0);
            }
            s.xPos = show ? slotX[i] : -10000;
        }
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void setScry(BlockPos p) {
        scry = p;
        ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.SCRY, p == null ? -1 : 0, p));
    }

    // ------------------------------------------------------------------ кнопки (свои, без ванильных)

    /** Кнопка «Выбрать» на «Пути»: надпись или причина ("!…"), null — кнопки нет. */
    private String chooseLabel() {
        if (tab != TAB_PATH || view.getInteger("spec") >= 0 || view.getBoolean("alpha")) {
            return null;
        }
        if (view.getInteger("level") < AnchorSpec.CHOOSE_LEVEL) {
            return "!" + I18n.format("chaosshard.anchor.gui.btn.level", AnchorSpec.CHOOSE_LEVEL);
        }
        if (!view.getBoolean("edit")) {
            return "!" + I18n.format("chaosshard.anchor.gui.btn.not_owner");
        }
        if (picked < 0) {
            return "!" + I18n.format("chaosshard.anchor.gui.btn.pick");
        }
        return I18n.format("chaosshard.anchor.gui.choose");
    }

    /** Кнопка «Забрать» опыт поля на «Складе». */
    private String xpLabel() {
        if (tab != TAB_STORE || view.getInteger("storage") <= 0 || view.getInteger("spec") != AnchorSpec.HARVEST.ordinal()) {
            return null;
        }
        if (!view.getBoolean("edit")) {
            return "!" + I18n.format("chaosshard.anchor.gui.btn.not_owner");
        }
        if (view.getInteger("fieldXp") <= 0) {
            return "!" + I18n.format("chaosshard.anchor.gui.btn.no_xp");
        }
        return I18n.format("chaosshard.anchor.gui.take_xp");
    }

    /** Кнопка сверху справа на «Сети»: «Карта»/«Список» или «Назад» из Взгляда. */
    private String netTopLabel() {
        if (tab != TAB_NET || !view.getBoolean("net")) {
            return null;
        }
        if (scry != null) {
            return I18n.format("chaosshard.anchor.gui.back");
        }
        if (!view.getBoolean("watch")) {
            return null;
        }
        return I18n.format(map ? "chaosshard.anchor.gui.list" : "chaosshard.anchor.gui.map");
    }

    private static final int CHOOSE_X = 132, CHOOSE_Y = 212, XP_X = 132, XP_Y = 82, TOP_X = 156, TOP_Y = 2, BTN_W = 70;

    private void drawButton(String label, int x, int y, int w, int mx, int my) {
        if (label == null) {
            return;
        }
        boolean off = label.startsWith("!");
        ChaosUi.button(fontRenderer, x, y, w, off ? label.substring(1) : label, off ? 2 : in(mx, my, x, y, w, 16) ? 1 : 0, OFF);
    }

    // ------------------------------------------------------------------ мышь

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        for (int t = 0; t < 4; t++) {
            if (in(mx, my, TAB_X + t * TAB_STEP, -19, 52, 19)) {
                if (tab != t) {
                    tab = t;
                    scroll = 0;
                    if (tab != TAB_NET && scry != null) {
                        setScry(null);
                    }
                    click();
                    layout();
                }
                return;
            }
        }
        if (button == 0 && clickContent(mx, my)) {
            click();
            layout();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickContent(int mx, int my) {
        String l = chooseLabel();
        if (l != null && !l.startsWith("!") && in(mx, my, CHOOSE_X, CHOOSE_Y, BTN_W, 16)) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.SELECT, picked, null));
            return true;
        }
        l = xpLabel();
        if (l != null && !l.startsWith("!") && in(mx, my, XP_X, XP_Y, BTN_W, 16)) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.TAKE_XP, 0, null));
            return true;
        }
        l = netTopLabel();
        if (l != null && in(mx, my, TOP_X, TOP_Y, 46, 16)) {
            if (scry != null) {
                setScry(null);
            } else {
                map = !map;
            }
            return true;
        }
        if (tab == TAB_PATH && !view.getBoolean("alpha")) {
            if (view.getInteger("borrowSlots") > 0) {
                int slots = Math.min(3, view.getInteger("borrowSlots"));
                for (int row = 0; row < slots; row++) {
                    int y = BORROW_Y + row * 12;
                    int[] b = view.getIntArray("borrow");
                    int cur = row < b.length ? b[row] : -1;
                    int nx = -2;
                    if (in(mx, my, X1 - 22, y - 1, 11, 11)) {
                        nx = prevBorrow(cur);
                    } else if (in(mx, my, X1 - 11, y - 1, 11, 11) || in(mx, my, X0, y - 1, W - 24, 11)) {
                        nx = nextBorrow(cur);
                    }
                    if (nx != -2) {
                        ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.BORROW, (row << 8) | (nx + 1), null));
                        return true;
                    }
                }
            }
            if (view.getInteger("spec") < 0) {
                for (AnchorSpec s : AnchorSpec.values()) {
                    int[] r = specRow(s);
                    if (in(mx, my, r[0], r[1], 94, 12)) {
                        picked = s.ordinal();
                        return true;
                    }
                }
            }
        } else if (tab == TAB_NET && !map && scry == null && view.getBoolean("net")) {
            NBTTagList list = view.getTagList("list", 10);
            int row = (my - LIST_Y) / ROW;
            if (my >= LIST_Y && row >= 0 && row < LIST_ROWS && scroll + row < list.tagCount()) {
                NBTTagCompound e = list.getCompoundTagAt(scroll + row);
                BlockPos p = new BlockPos(e.getInteger("x"), e.getInteger("y"), e.getInteger("z"));
                int y = LIST_Y + row * ROW;
                if (canTravel(e) && in(mx, my, rowIconX(0) - 1, y, 11, ROW)) {
                    ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.TRAVEL, 0, p));
                    return true;
                }
                if (view.getBoolean("watchStage") && in(mx, my, rowIconX(1) - 1, y, 11, ROW)) {
                    setScry(p);
                    return true;
                }
            }
        }
        return false;
    }

    /** Левый край значка-кнопки в строке сети: 0 — Перенос, 1 — Взгляд. */
    private int rowIconX(int which) {
        boolean scrollable = view.getTagList("list", 10).tagCount() > LIST_ROWS;
        return X1 - (scrollable ? 4 : 0) - 12 * (which + 1);
    }

    private boolean canTravel(NBTTagCompound e) {
        return view.getBoolean("path") && !e.getBoolean("here");
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && tab == TAB_NET) {
            int n = view.getTagList("list", 10).tagCount();
            scroll = Math.max(0, Math.min(Math.max(0, n - LIST_ROWS), scroll + (d > 0 ? -1 : 1)));
        }
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = tooltip(mx, my);
        if (tip != null) {
            drawHoveringText(tip, mouseX, mouseY);
        }
    }

    /** Подсказки к значкам-кнопкам и закрытым местам. */
    private List<String> tooltip(int mx, int my) {
        List<String> t = new ArrayList<>();
        if (tab == TAB_NET && !map && scry == null && view.getBoolean("net")) {
            NBTTagList list = view.getTagList("list", 10);
            int row = (my - LIST_Y) / ROW;
            if (my >= LIST_Y && row >= 0 && row < LIST_ROWS && scroll + row < list.tagCount()) {
                NBTTagCompound e = list.getCompoundTagAt(scroll + row);
                int y = LIST_Y + row * ROW;
                if (canTravel(e) && in(mx, my, rowIconX(0) - 1, y, 11, ROW)) {
                    t.add(I18n.format("chaosshard.anchor.gui.tip.travel"));
                } else if (view.getBoolean("watchStage") && in(mx, my, rowIconX(1) - 1, y, 11, ROW)) {
                    t.add(I18n.format("chaosshard.anchor.gui.tip.scry"));
                }
            }
        }
        if (tab == TAB_ANCHOR && mc.player.inventory.getItemStack().isEmpty()) {
            int open = view.getInteger("slots");
            for (int i = 0; i < TileAnchor.MAX_SEALS; i++) {
                int sx = ContainerAnchor.SEAL_X + (i % 6) * 18, sy = ContainerAnchor.SEAL_Y + (i / 6) * 18;
                if (in(mx, my, sx - 1, sy - 1, 18, 18) && !container.inventorySlots.get(i).getHasStack()) {
                    if (i >= open) {
                        t.add(I18n.format("chaosshard.anchor.gui.tip.seal_locked"));
                    } else if (!view.getBoolean("edit")) {
                        t.add(I18n.format("chaosshard.anchor.gui.tip.seal_ro"));
                    }
                }
            }
        }
        return t.isEmpty() ? null : t;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (scryTex != null) {
            scryTex.deleteGlTexture();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        int mx = mouseX - x, my = mouseY - y;
        GlStateManager.color(1, 1, 1, 1);
        for (int t = 0; t < 4; t++) {
            if (t != tab) {
                ChaosUi.smallTab(x + TAB_X + t * TAB_STEP, y, false);
                if (in(mx, my, TAB_X + t * TAB_STEP, -17, 52, 17)) {
                    drawRect(x + TAB_X + t * TAB_STEP + 2, y - 15, x + TAB_X + t * TAB_STEP + 50, y, 0x22FFFFFF);
                }
            }
        }
        mc.getTextureManager().bindTexture(BG);
        GlStateManager.color(1, 1, 1, 1);
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
        ChaosUi.smallTab(x + TAB_X + tab * TAB_STEP, y, true);
        for (int t = 0; t < 4; t++) {
            ChaosUi.bigIcon(ChaosUi.BIG_TAB_ANCHOR + t, x + TAB_X + t * TAB_STEP + 3, y - 16 + (t == tab ? -1 : 1), 16);
        }
        // места
        int open = view.getInteger("slots");
        boolean edit = view.getBoolean("edit");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot s = container.inventorySlots.get(i);
            if (s.xPos < 0) {
                continue;
            }
            int kind = ChaosUi.SLOT_OPEN;
            if (i < ContainerAnchor.STORAGE_FIRST) {
                kind = i >= open ? ChaosUi.SLOT_LOCKED : !edit ? ChaosUi.SLOT_RO : ChaosUi.SLOT_OPEN;
            }
            ChaosUi.slot(x + s.xPos - 1, y + s.yPos - 1, kind);
        }
        if (tab == TAB_NET && scry != null) {
            drawScry(x, y);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        String[] tabs = {"tab_anchor", "tab_net", "tab_path", "tab_store"};
        for (int t = 0; t < 4; t++) {
            boolean on = t == tab;
            boolean hover = !on && in(mx, my, TAB_X + t * TAB_STEP, -17, 52, 17);
            String s = fontRenderer.trimStringToWidth(I18n.format("chaosshard.anchor.gui." + tabs[t]), 30);
            fontRenderer.drawString(s, TAB_X + t * TAB_STEP + 20, on ? -11 : -9, on ? TEXT : hover ? SOFT : DIM);
        }
        switch (tab) {
            case TAB_NET:
                drawNetTab(mx, my);
                break;
            case TAB_PATH:
                drawPathTab(mx, my);
                break;
            case TAB_STORE:
                drawStoreTab(mx, my);
                break;
            default:
                drawAnchorTab();
                break;
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void title(String left, int leftColor, String right, int rightColor) {
        int rw = right == null ? 0 : fontRenderer.getStringWidth(right);
        if (right != null) {
            fontRenderer.drawString(right, X1 - rw, 7, rightColor);
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(left, W - rw - 6), X0, 7, leftColor);
    }

    private String specName(int i) {
        AnchorSpec s = AnchorSpec.of(i);
        return s == null ? "" : I18n.format("chaosshard.anchor.spec." + s.key());
    }

    /** Строка со значком слева. */
    private void line(int icon, String s, int y, int color) {
        if (icon >= 0) {
            ChaosUi.icon(X0, y - 1, icon);
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(s, W - 12), X0 + 12, y, color);
    }

    // ------------------------------------------------------------------ Якорь

    private void drawAnchorTab() {
        NBTTagCompound v = view;
        int spec = v.getInteger("spec");
        title(I18n.format("chaosshard.anchor.gui.title", v.getString("owner")), ACCENT, spec >= 0 ? specName(spec) : null, GOLD);
        if (!v.hasKey("level")) {
            return;
        }
        int lvl = v.getInteger("level"), xp = v.getInteger("xp"), from = v.getInteger("from"), next = v.getInteger("next");
        line(ChaosUi.I_STAR, I18n.format("chaosshard.anchor.gui.level", lvl, v.getInteger("max")), 20, TEXT);
        String xs = next < 0 ? I18n.format("chaosshard.anchor.gui.xp_max", xp) : I18n.format("chaosshard.anchor.gui.xp", xp, next);
        fontRenderer.drawString(xs, X1 - fontRenderer.getStringWidth(xs), 20, DIM);
        ChaosUi.bar(X0, 30, W, next < 0 ? 1 : (double) (xp - from) / Math.max(1, next - from), 0xFF9A5AE0);

        line(ChaosUi.I_LAND, I18n.format("chaosshard.anchor.gui.radius", v.getInteger("radius"), v.getInteger("cap")), 41, TEXT);
        int land = v.getInteger("land");
        boolean captured = v.getBoolean("captured");
        fontRenderer.drawString(captured ? I18n.format("chaosshard.anchor.gui.captured", land)
                : I18n.format("chaosshard.anchor.gui.capturing", land, v.getInteger("capture")), X0 + 12, 51, captured ? GOOD : SOFT);

        int blood = v.getInteger("blood"), cap = Math.max(1, v.getInteger("bloodCap"));
        line(ChaosUi.I_DROP, I18n.format("chaosshard.anchor.gui.blood", blood / 1000.0 < 10 ? String.format("%.1f", blood / 1000.0)
                : String.valueOf(blood / 1000), cap / 1000), 62, TEXT);
        ChaosUi.bar(X0, 72, W, (double) blood / cap, 0xFFB01020);

        int raid = v.getInteger("raid");
        line(ChaosUi.I_SWORD, raid < 0 ? I18n.format("chaosshard.anchor.gui.raid_none")
                : I18n.format("chaosshard.anchor.gui.raid", Math.max(1, (raid + 1199) / 1200)), 83, raid < 0 ? DIM : 0xFFB070);

        int open = v.getInteger("slots");
        boolean edit = v.getBoolean("edit");
        line(ChaosUi.I_SEAL, I18n.format(edit ? "chaosshard.anchor.gui.seals" : "chaosshard.anchor.gui.seals_ro", open, TileAnchor.MAX_SEALS), 94, ACCENT);
        // справа от печатей — пояснение
        int rx = ContainerAnchor.SEAL_X + 6 * 18 + 6;
        List<String> note = new ArrayList<>();
        int hum = v.getInteger("hum");
        if (hum > 0) {
            note.add(I18n.format("chaosshard.anchor.gui.hum", hum));
        }
        note.addAll(fontRenderer.listFormattedStringToWidth(I18n.format(edit ? "chaosshard.anchor.gui.seal_note" : "chaosshard.anchor.gui.seal_note_ro"), X1 - rx));
        for (int i = 0; i < note.size() && i < 4; i++) {
            fontRenderer.drawString(note.get(i), rx, 108 + i * 9, DIM);
        }
    }

    // ------------------------------------------------------------------ Сеть

    private void drawNetTab(int mx, int my) {
        NBTTagCompound v = view;
        title(I18n.format("chaosshard.anchor.gui.net_title", v.getInteger("size")), ACCENT, null, 0);
        if (!v.getBoolean("net")) {
            ChaosUi.empty(fontRenderer, ChaosUi.BIG_TAB_NET, I18n.format("chaosshard.anchor.gui.net_empty"),
                    I18n.format("chaosshard.anchor.gui.net_none"), xSize / 2, 70, 170);
            return;
        }
        drawButton(netTopLabel(), TOP_X, TOP_Y, 46, mx, my);
        if (scry != null) {
            line(ChaosUi.I_EYE, I18n.format("chaosshard.anchor.gui.scry", scry.getX(), scry.getZ()), 20, TEXT);
            return;
        }
        line(ChaosUi.I_BOLT, I18n.format("chaosshard.anchor.gui.energy", fmt(v.getDouble("eLand")), fmt(v.getDouble("eOut"))), 20, TEXT);
        line(ChaosUi.I_STAR, I18n.format("chaosshard.anchor.gui.reserve", v.getInteger("reserve")), 30, TEXT);
        line(ChaosUi.I_SHIELD, I18n.format("chaosshard.anchor.gui.garrison", v.getInteger("garrison"), v.getInteger("garrisonMax")), 40, TEXT);
        if (map) {
            drawNetMap();
            return;
        }
        NBTTagList list = v.getTagList("list", 10);
        ChaosUi.well(X0 - 1, LIST_Y - 1, W + 2, LIST_ROWS * ROW + 2);
        boolean watch = v.getBoolean("watchStage");
        for (int r = 0; r < LIST_ROWS && scroll + r < list.tagCount(); r++) {
            NBTTagCompound e = list.getCompoundTagAt(scroll + r);
            int y = LIST_Y + r * ROW;
            boolean hover = in(mx, my, X0, y, W, ROW);
            drawRect(X0, y, X1, y + ROW, hover ? 0xFF241938 : ((scroll + r) % 2 == 0 ? 0xFF15101F : 0xFF110C1A));
            boolean here = e.getBoolean("here");
            if (here) {
                drawRect(X0, y + 1, X0 + 2, y + ROW - 1, 0xFFECB064);
            }
            int s = e.getInteger("s");
            int sp = e.getInteger("sp");
            int x;
            // кнопки-значки: Перенос и Взгляд
            int bx = rowIconX(0);
            if (canTravel(e)) {
                if (in(mx, my, bx - 1, y, 11, ROW)) {
                    drawRect(bx - 1, y, bx + 10, y + ROW, 0x40FFFFFF);
                }
                ChaosUi.icon(bx, y + 1, ChaosUi.I_PORTAL);
            }
            if (watch) {
                bx = rowIconX(1);
                if (in(mx, my, bx - 1, y, 11, ROW)) {
                    drawRect(bx - 1, y, bx + 10, y + ROW, 0x40FFFFFF);
                }
                ChaosUi.icon(bx, y + 1, ChaosUi.I_EYE);
            }
            x = bx - 4;
            String st = sp >= 0 ? specName(sp) : I18n.format("chaosshard.anchor.gui.state" + s);
            int stw = fontRenderer.getStringWidth(st);
            fontRenderer.drawString(st, x - stw, y + 2, s == 2 ? GOLD : s == 1 ? GOOD : DIM);
            String row = (here ? I18n.format("chaosshard.anchor.gui.here") + " " : "")
                    + I18n.format("chaosshard.anchor.gui.row", e.getInteger("x"), e.getInteger("z"), e.getInteger("l"));
            fontRenderer.drawString(fontRenderer.trimStringToWidth(row, x - stw - X0 - 8), X0 + 4, y + 2, here ? ACCENT : TEXT);
        }
        ChaosUi.scrollbar(X1 - 3, LIST_Y, LIST_ROWS * ROW, scroll, LIST_ROWS, list.tagCount());
        if (v.getBoolean("path") || watch) {
            String hint = I18n.format(v.getBoolean("path") && watch ? "chaosshard.anchor.gui.hint_both2"
                    : v.getBoolean("path") ? "chaosshard.anchor.gui.hint_path2" : "chaosshard.anchor.gui.hint_scry2");
            fontRenderer.drawSplitString(hint, X0, LIST_Y + LIST_ROWS * ROW + 5, W, DIM);
        }
    }

    /** Карта сети (Дозор): Якоря — квадраты, отряды — крестики, чужаки — ромбы. */
    private void drawNetMap() {
        NBTTagList list = view.getTagList("list", 10);
        NBTTagList dots = view.getTagList("dots", 10);
        int ax = X0, ay = LIST_Y, aw = W, ah = 144;
        ChaosUi.well(ax - 1, ay - 1, aw + 2, ah + 2);
        if (list.tagCount() == 0) {
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            minX = Math.min(minX, e.getInteger("x"));
            maxX = Math.max(maxX, e.getInteger("x"));
            minZ = Math.min(minZ, e.getInteger("z"));
            maxZ = Math.max(maxZ, e.getInteger("z"));
        }
        minX -= 64;
        maxX += 64;
        minZ -= 64;
        maxZ += 64;
        double k = Math.min((double) aw / (maxX - minX), (double) ah / (maxZ - minZ));
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int px = ax + (int) ((e.getInteger("x") - minX) * k), pz = ay + (int) ((e.getInteger("z") - minZ) * k);
            int c = e.getBoolean("here") ? 0xFFECB064 : 0xFF9A5AE0;
            drawRect(px - 2, pz - 2, px + 3, pz + 3, c);
        }
        for (int i = 0; i < dots.tagCount(); i++) {
            NBTTagCompound e = dots.getCompoundTagAt(i);
            int px = ax + (int) ((e.getInteger("x") - minX) * k), pz = ay + (int) ((e.getInteger("z") - minZ) * k);
            if (e.getByte("t") == 1) {
                cross(px, pz, 0xFFFF5050);
            } else {
                diamond(px, pz, 0xFFFFE040);
            }
        }
        // легенда: формы, а не только цвет
        int ly = ay + ah + 5, lx = X0;
        drawRect(lx, ly + 2, lx + 5, ly + 7, 0xFF9A5AE0);
        lx += 8;
        String a = I18n.format("chaosshard.anchor.gui.legend_anchor");
        fontRenderer.drawString(a, lx, ly + 1, SOFT);
        lx += fontRenderer.getStringWidth(a) + 10;
        cross(lx + 2, ly + 4, 0xFFFF5050);
        lx += 8;
        String b = I18n.format("chaosshard.anchor.gui.legend_army");
        fontRenderer.drawString(b, lx, ly + 1, SOFT);
        lx += fontRenderer.getStringWidth(b) + 10;
        diamond(lx + 2, ly + 4, 0xFFFFE040);
        lx += 8;
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.legend_foe"), lx, ly + 1, SOFT);
    }

    private static void cross(int x, int y, int c) {
        Gui.drawRect(x - 2, y - 2, x - 1, y - 1, c);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, c);
        Gui.drawRect(x + 2, y - 2, x + 3, y - 1, c);
        Gui.drawRect(x - 2, y + 2, x - 1, y + 3, c);
        Gui.drawRect(x + 2, y + 2, x + 3, y + 3, c);
    }

    private static void diamond(int x, int y, int c) {
        Gui.drawRect(x, y - 2, x + 1, y + 3, c);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, c);
        Gui.drawRect(x - 2, y, x + 3, y + 1, c);
    }

    /** Взгляд Дозора: вид сверху на землю Якоря. */
    private void drawScry(int gx, int gy) {
        NBTTagCompound s = view.getCompoundTag("scry");
        int size = 144, ox = gx + (xSize - size) / 2, oy = gy + 34;
        ChaosUi.well(ox - 1, oy - 1, size + 2, size + 2);
        if (!s.hasKey("c")) {
            return;
        }
        int n = AnchorView.SCRY;
        long stamp = s.getLong("at") ^ ((long) s.getByteArray("c").length << 32) ^ s.hashCode();
        if (scryTex == null) {
            scryTex = new DynamicTexture(n, n);
        }
        if (stamp != scryShown) {
            scryShown = stamp;
            byte[] c = s.getByteArray("c");
            int[] px = scryTex.getTextureData();
            for (int i = 0; i < Math.min(c.length, px.length); i++) {
                int idx = c[i] & 0xFF;
                MapColor mc = idx < MapColor.COLORS.length ? MapColor.COLORS[idx] : null;
                px[i] = 0xFF000000 | (mc == null ? 0x101010 : mc.colorValue);
            }
            scryTex.updateDynamicTexture();
        }
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(scryTex.getGlTextureId());
        drawModalRectWithCustomSizedTexture(ox, oy, 0, 0, size, size, size, size);
        double k = (double) size / n;
        NBTTagList ents = s.getTagList("e", 10);
        for (int i = 0; i < ents.tagCount(); i++) {
            NBTTagCompound e = ents.getCompoundTagAt(i);
            int px = ox + (int) (e.getInteger("x") * k), pz = oy + (int) (e.getInteger("z") * k);
            int t = e.getByte("t");
            int col = t == 0 ? 0xFFFF6060 : t == 1 ? 0xFFFF2020 : t == 2 ? 0xFFFFE040 : 0xFFC890FF;
            drawRect(px - 1, pz - 1, px + 2, pz + 2, col);
        }
        int cx = ox + size / 2, cz = oy + size / 2;
        drawRect(cx - 2, cz - 2, cx + 3, cz + 3, 0xFFE0A0FF);
    }

    // ------------------------------------------------------------------ Путь

    private int[] specRow(AnchorSpec s) {
        int i = s.ordinal();
        boolean net = s.network;
        int idx = net ? i : i - 5;
        return new int[]{net ? X0 : X0 + 100, 31 + idx * 12};
    }

    private void drawPathTab(int mx, int my) {
        NBTTagCompound v = view;
        int spec = v.getInteger("spec");
        int lvl = v.getInteger("level");
        if (v.getBoolean("alpha")) {
            drawAlpha();
            return;
        }
        if (spec >= 0) {
            AnchorSpec s = AnchorSpec.of(spec);
            title(specName(spec), GOLD, I18n.format(s.network ? "chaosshard.anchor.gui.kind_net" : "chaosshard.anchor.gui.kind_local"), DIM);
            int y = drawSpecText(s, X0, 22, W, v.getBoolean("stage"));
            if (s == AnchorSpec.FORGE) {
                line(ChaosUi.I_BOLT, I18n.format("chaosshard.anchor.gui.forge", v.getInteger("forgeRate"), v.getInteger("forgeEnergy")), y + 4, TEXT);
                y += 14;
            }
            List<String> rs = fontRenderer.listFormattedStringToWidth(I18n.format("chaosshard.anchor.gui.reshape"), W);
            for (int i = 0; i < rs.size(); i++) {
                fontRenderer.drawString(rs.get(i), X0, y + 6 + i * 9, DIM);
            }
            drawBorrow(mx, my);
            return;
        }
        boolean can = lvl >= AnchorSpec.CHOOSE_LEVEL;
        title(I18n.format(can ? "chaosshard.anchor.gui.path_choose" : "chaosshard.anchor.gui.path_locked", AnchorSpec.CHOOSE_LEVEL),
                can ? ACCENT : SOFT, null, 0);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.kind_net"), X0 + 3, 20, DIM);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.kind_local"), X0 + 103, 20, DIM);
        NBTTagList counts = v.getTagList("specs", 10);
        for (AnchorSpec s : AnchorSpec.values()) {
            int[] r = specRow(s);
            boolean sel = s.ordinal() == picked;
            boolean hover = in(mx, my, r[0], r[1], 94, 12);
            drawRect(r[0], r[1], r[0] + 94, r[1] + 11, sel ? 0xFF35245A : hover ? 0xFF241938 : 0xFF15101F);
            if (sel) {
                drawRect(r[0], r[1], r[0] + 2, r[1] + 11, 0xFFECB064);
            }
            String name = specName(s.ordinal());
            int n = s.ordinal() < counts.tagCount() ? counts.getCompoundTagAt(s.ordinal()).getInteger("n") : 0;
            fontRenderer.drawString(name, r[0] + 5, r[1] + 2, sel ? GOLD : TEXT);
            if (n > 0) {
                String c = "×" + n;
                fontRenderer.drawString(c, r[0] + 91 - fontRenderer.getStringWidth(c), r[1] + 2, DIM);
            }
        }
        ChaosUi.well(X0 - 1, 94, W + 2, 112);
        if (picked >= 0) {
            drawSpecText(AnchorSpec.of(picked), X0 + 3, 98, W - 6, false);
        } else {
            ChaosUi.empty(fontRenderer, null, null, I18n.format("chaosshard.anchor.gui.path_hint"), xSize / 2, 140, W - 20);
        }
        drawButton(chooseLabel(), CHOOSE_X, CHOOSE_Y, BTN_W, mx, my);
    }

    private void drawAlpha() {
        NBTTagCompound v = view;
        int al = v.getInteger("alphaLvl");
        boolean awake = v.getBoolean("alphaAwake");
        title(I18n.format("chaosshard.anchor.gui.alpha_title", al), GOLD,
                I18n.format(awake ? "chaosshard.anchor.gui.alpha_awake" : "chaosshard.anchor.gui.alpha_asleep"), awake ? GOOD : BAD);
        int xp = v.getInteger("alphaXp"), from = v.getInteger("alphaFrom"), next = v.getInteger("alphaNext");
        String xs = next < 0 ? String.valueOf(xp) : xp + " / " + next;
        fontRenderer.drawString(xs, X1 - fontRenderer.getStringWidth(xs), 19, DIM);
        ChaosUi.bar(X0, 29, W, next < 0 ? 1 : (double) (xp - from) / Math.max(1, next - from), 0xFFE0A0FF);
        int y = 42;
        String[] rows = {"odd", "2", "4", "6", "8", "10"};
        int[] need = {1, 2, 4, 6, 8, 10};
        for (int i = 0; i < rows.length; i++) {
            boolean got = al >= need[i];
            ChaosUi.icon(X0, y - 1, got ? ChaosUi.I_CHECK : ChaosUi.I_LOCK);
            for (String p : fontRenderer.listFormattedStringToWidth(I18n.format("chaosshard.anchor.gui.alpha_" + rows[i]), W - 12)) {
                fontRenderer.drawString(p, X0 + 12, y, got ? TEXT : DIM);
                y += 10;
            }
            y += 2;
        }
        fontRenderer.drawSplitString(I18n.format("chaosshard.anchor.gui.alpha_note"), X0, y + 6, W, DIM);
    }

    /** Заимствования: строки внизу «Пути»; стрелки листают доступные эффекты. */
    private void drawBorrow(int mx, int my) {
        int slots = view.getInteger("borrowSlots");
        if (slots <= 0) {
            return;
        }
        int[] b = view.getIntArray("borrow");
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.borrow2", slots), X0, BORROW_Y - 12, ACCENT);
        for (int i = 0; i < Math.min(slots, 3); i++) {
            int fx = i < b.length ? b[i] : -1;
            String name = fx < 0 ? I18n.format("chaosshard.anchor.gui.borrow_empty")
                    : I18n.format("chaosshard.anchor.fx." + ru.death.chaosshard.anchor.AnchorFx.of(fx).key());
            int y = BORROW_Y + i * 12;
            boolean hover = in(mx, my, X0, y - 1, W, 11);
            drawRect(X0, y - 1, X1, y + 10, hover ? 0xFF241938 : 0xFF15101F);
            fontRenderer.drawString(fontRenderer.trimStringToWidth((i + 1) + ". " + name, W - 30), X0 + 3, y + 1, fx < 0 ? DIM : TEXT);
            boolean hl = in(mx, my, X1 - 22, y - 1, 11, 11), hr = in(mx, my, X1 - 11, y - 1, 11, 11);
            if (hl) {
                drawRect(X1 - 22, y - 1, X1 - 12, y + 10, 0x40FFFFFF);
            }
            if (hr) {
                drawRect(X1 - 11, y - 1, X1 - 1, y + 10, 0x40FFFFFF);
            }
            ChaosUi.icon(X1 - 21, y, ChaosUi.I_LEFT);
            ChaosUi.icon(X1 - 10, y, ChaosUi.I_RIGHT);
        }
    }

    /** Следующий эффект для места заимствования. */
    private int nextBorrow(int cur) {
        return stepBorrow(cur, 1);
    }

    private int prevBorrow(int cur) {
        return stepBorrow(cur, -1);
    }

    /** Ближайший доступный эффект в сторону dir; -1 — «пусто» (по кругу). */
    private int stepBorrow(int cur, int dir) {
        int own = view.getInteger("spec");
        int[] free = view.getIntArray("borrowFree");
        ru.death.chaosshard.anchor.AnchorFx[] all = ru.death.chaosshard.anchor.AnchorFx.values();
        int n = all.length + 1;   // + «пусто»
        int pos = cur + 1;        // 0 — пусто, 1..n-1 — эффекты
        for (int k = 1; k <= n; k++) {
            int p = ((pos + dir * k) % n + n) % n;
            if (p == 0) {
                return -1;
            }
            ru.death.chaosshard.anchor.AnchorFx fx = all[p - 1];
            int sp = fx.spec.ordinal();
            if (sp != own && sp < free.length && free[sp] > 0) {
                return p - 1;
            }
        }
        return -1;
    }

    /** Эффекты специализации, по строкам. Возвращает y после текста. */
    private int drawSpecText(AnchorSpec s, int x, int y, int w, boolean stage) {
        for (int i = 1; i <= 3; i++) {
            String line = "• " + I18n.format("chaosshard.anchor.spec." + s.key() + ".e" + i);
            for (String p : fontRenderer.listFormattedStringToWidth(line, w)) {
                fontRenderer.drawString(p, x, y, TEXT);
                y += 10;
            }
        }
        String st = I18n.format("chaosshard.anchor.gui.stage") + " " + I18n.format("chaosshard.anchor.spec." + s.key() + ".stage");
        for (String p : fontRenderer.listFormattedStringToWidth(st, w)) {
            fontRenderer.drawString(p, x, y, stage ? GOLD : DIM);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ Склад

    private void drawStoreTab(int mx, int my) {
        NBTTagCompound v = view;
        int storage = v.getInteger("storage");
        title(I18n.format("chaosshard.anchor.gui.store_title"), ACCENT, null, 0);
        if (storage <= 0) {
            ChaosUi.empty(fontRenderer, ChaosUi.BIG_TAB_STORE, I18n.format("chaosshard.anchor.gui.store_empty"),
                    I18n.format("chaosshard.anchor.gui.store_none"), xSize / 2, 70, 170);
            return;
        }
        if (v.getInteger("spec") == AnchorSpec.HARVEST.ordinal()) {
            line(ChaosUi.I_XP, I18n.format("chaosshard.anchor.gui.field_xp", v.getInteger("fieldXp")), 86, TEXT);
            drawButton(xpLabel(), XP_X, XP_Y, BTN_W, mx, my);
            if (v.getBoolean("stage")) {
                fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.sample"), ContainerAnchor.SAMPLE_X + 22, ContainerAnchor.SAMPLE_Y + 5, DIM);
            }
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.2f", d).replaceAll("0+$", "");
    }
}
