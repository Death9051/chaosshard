package ru.death.chaosshard.anchor.client;

import net.minecraft.block.material.MapColor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.anchor.AnchorSpec;
import ru.death.chaosshard.anchor.AnchorView;
import ru.death.chaosshard.anchor.ContainerAnchor;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketAnchorAction;

import java.io.IOException;
import java.util.List;

/** Окно Якоря: вкладки «Якорь», «Сеть», «Путь» (специализация) и «Склад». Рисуется без текстур. */
public class GuiAnchor extends GuiContainer {

    /** Последние сведения от сервера. */
    public static NBTTagCompound view = new NBTTagCompound();

    private static final int BG = 0xF0201028;
    private static final int FRAME = 0xFF7A4FB0;
    private static final int TEXT = 0xE0D8F0;
    private static final int DIM = 0x9888B0;
    private static final int ACCENT = 0xC890FF;
    private static final int LIST_ROWS = 12;
    private static final int LIST_Y = 58;

    private static final int TAB_ANCHOR = 0, TAB_NET = 1, TAB_PATH = 2, TAB_STORE = 3;
    private static final int BTN_CHOOSE = 10, BTN_MAP = 11, BTN_BACK = 12, BTN_XP = 13;

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
        xSize = 176;
        ySize = 222;
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
        String[] tabs = {"tab_anchor", "tab_net", "tab_path", "tab_store"};
        for (int i = 0; i < tabs.length; i++) {
            buttonList.add(new GuiButton(i, guiLeft + i * 44, guiTop - 20, 43, 18, I18n.format("chaosshard.anchor.gui." + tabs[i])));
        }
        buttonList.add(new GuiButton(BTN_CHOOSE, guiLeft + 98, guiTop + 196, 70, 18, I18n.format("chaosshard.anchor.gui.choose")));
        buttonList.add(new GuiButton(BTN_MAP, guiLeft + 120, guiTop + 2, 52, 14, I18n.format("chaosshard.anchor.gui.map")));
        buttonList.add(new GuiButton(BTN_BACK, guiLeft + 120, guiTop + 2, 52, 14, I18n.format("chaosshard.anchor.gui.back")));
        buttonList.add(new GuiButton(BTN_XP, guiLeft + 110, guiTop + 74, 58, 16, I18n.format("chaosshard.anchor.gui.take_xp")));
        layout();
    }

    private GuiButton button(int id) {
        for (GuiButton b : buttonList) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    /** Видимость мест и кнопок по вкладке. */
    private void layout() {
        int storage = view.getInteger("storage");
        boolean edit = view.getBoolean("edit");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot s = container.inventorySlots.get(i);
            boolean show;
            if (i < ContainerAnchor.STORAGE_FIRST) {
                show = tab == TAB_ANCHOR && (edit || s.getHasStack());
            } else if (i < ContainerAnchor.SAMPLE_SLOT) {
                show = tab == TAB_STORE && i - ContainerAnchor.STORAGE_FIRST < storage;
            } else if (i == ContainerAnchor.SAMPLE_SLOT) {
                show = tab == TAB_STORE && view.getInteger("spec") == AnchorSpec.HARVEST.ordinal() && view.getBoolean("stage");
            } else {
                show = tab == TAB_ANCHOR || tab == TAB_STORE;
            }
            s.xPos = show ? slotX[i] : -10000;
        }
        for (GuiButton b : buttonList) {
            if (b.id < 4) {
                b.enabled = b.id != tab;
            }
        }
        GuiButton choose = button(BTN_CHOOSE);
        if (choose != null) {
            choose.visible = tab == TAB_PATH && view.getInteger("spec") < 0;
            choose.enabled = edit && picked >= 0 && view.getInteger("level") >= AnchorSpec.CHOOSE_LEVEL;
        }
        GuiButton mapB = button(BTN_MAP);
        if (mapB != null) {
            mapB.visible = tab == TAB_NET && view.getBoolean("watch") && scry == null;
            mapB.displayString = I18n.format(map ? "chaosshard.anchor.gui.list" : "chaosshard.anchor.gui.map");
        }
        GuiButton back = button(BTN_BACK);
        if (back != null) {
            back.visible = tab == TAB_NET && scry != null;
        }
        GuiButton xp = button(BTN_XP);
        if (xp != null) {
            xp.visible = tab == TAB_STORE && view.getInteger("spec") == AnchorSpec.HARVEST.ordinal();
            xp.enabled = edit && view.getInteger("fieldXp") > 0;
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        if (b.id < 4) {
            tab = b.id;
            if (tab != TAB_NET && scry != null) {
                setScry(null);
            }
        } else if (b.id == BTN_CHOOSE && picked >= 0) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.SELECT, picked, null));
        } else if (b.id == BTN_MAP) {
            map = !map;
        } else if (b.id == BTN_BACK) {
            setScry(null);
        } else if (b.id == BTN_XP) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.TAKE_XP, 0, null));
        }
        layout();
    }

    private void setScry(BlockPos p) {
        scry = p;
        ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.SCRY, p == null ? -1 : 0, p));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        if (tab == TAB_PATH) {
            for (AnchorSpec s : AnchorSpec.values()) {
                int[] r = specRow(s);
                if (mx >= r[0] && mx < r[0] + 78 && my >= r[1] - 1 && my < r[1] + 10) {
                    picked = s.ordinal();
                }
            }
            layout();
        } else if (tab == TAB_NET && !map && scry == null) {
            NBTTagList list = view.getTagList("list", 10);
            int row = (my - LIST_Y) / 11;
            if (my >= LIST_Y && row >= 0 && row < LIST_ROWS && scroll + row < list.tagCount()) {
                NBTTagCompound e = list.getCompoundTagAt(scroll + row);
                BlockPos p = new BlockPos(e.getInteger("x"), e.getInteger("y"), e.getInteger("z"));
                if (mx >= 150 && view.getBoolean("path") && !e.getBoolean("here")) {
                    ChaosNetwork.CHANNEL.sendToServer(new PacketAnchorAction(PacketAnchorAction.TRAVEL, 0, p));
                } else if (mx < 120 && view.getBoolean("watchStage")) {
                    setScry(p);
                    layout();
                }
            }
        }
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
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
        drawRect(x - 1, y - 1, x + xSize + 1, y + ySize + 1, FRAME);
        drawRect(x, y, x + xSize, y + ySize, BG);
        int open = view.getInteger("slots");
        boolean edit = view.getBoolean("edit");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot s = container.inventorySlots.get(i);
            if (s.xPos < 0) {
                continue;
            }
            boolean locked = i < ContainerAnchor.STORAGE_FIRST && (i >= open || !edit);
            drawRect(x + s.xPos - 1, y + s.yPos - 1, x + s.xPos + 17, y + s.yPos + 17, locked ? 0xFF2A2030 : 0xFF4A3A60);
            drawRect(x + s.xPos, y + s.yPos, x + s.xPos + 16, y + s.yPos + 16, locked ? 0xFF15101A : 0xFF100A18);
        }
        if (tab == TAB_NET && scry != null) {
            drawScry(x, y);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void bar(int x, int y, int w, double part, int color) {
        drawRect(x, y, x + w, y + 5, 0xFF100A18);
        drawRect(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, part))), y + 5, color);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        switch (tab) {
            case TAB_NET:
                drawNetTab();
                break;
            case TAB_PATH:
                drawPathTab(mouseX - guiLeft, mouseY - guiTop);
                break;
            case TAB_STORE:
                drawStoreTab();
                break;
            default:
                drawAnchorTab();
                break;
        }
    }

    private String specName(int i) {
        AnchorSpec s = AnchorSpec.of(i);
        return s == null ? "" : I18n.format("chaosshard.anchor.spec." + s.key());
    }

    private void drawAnchorTab() {
        NBTTagCompound v = view;
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.title", v.getString("owner")), 8, 6, ACCENT);
        if (!v.hasKey("level")) {
            return;
        }
        int spec = v.getInteger("spec");
        if (spec >= 0) {
            String s = specName(spec);
            fontRenderer.drawString(s, 168 - fontRenderer.getStringWidth(s), 6, 0xFFD060);
        }
        int lvl = v.getInteger("level"), xp = v.getInteger("xp"), from = v.getInteger("from"), next = v.getInteger("next");
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.level", lvl, v.getInteger("max")), 8, 20, TEXT);
        String xs = next < 0 ? I18n.format("chaosshard.anchor.gui.xp_max", xp) : I18n.format("chaosshard.anchor.gui.xp", xp, next);
        fontRenderer.drawString(xs, 168 - fontRenderer.getStringWidth(xs), 20, DIM);
        bar(8, 30, 160, next < 0 ? 1 : (double) (xp - from) / Math.max(1, next - from), 0xFF9A5AE0);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.radius", v.getInteger("radius"), v.getInteger("cap")), 8, 39, TEXT);
        int land = v.getInteger("land");
        fontRenderer.drawString(v.getBoolean("captured") ? I18n.format("chaosshard.anchor.gui.captured", land)
                : I18n.format("chaosshard.anchor.gui.capturing", land, v.getInteger("capture")), 8, 50, TEXT);
        int blood = v.getInteger("blood"), cap = Math.max(1, v.getInteger("bloodCap"));
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.blood", blood / 1000.0 < 10 ? String.format("%.1f", blood / 1000.0)
                : String.valueOf(blood / 1000), cap / 1000), 8, 61, TEXT);
        bar(8, 71, 160, (double) blood / cap, 0xFFB01020);
        int raid = v.getInteger("raid");
        fontRenderer.drawString(raid < 0 ? I18n.format("chaosshard.anchor.gui.raid_none")
                : I18n.format("chaosshard.anchor.gui.raid", Math.max(1, (raid + 1199) / 1200)), 8, 78, DIM);
        int open = v.getInteger("slots");
        fontRenderer.drawString(I18n.format(v.getBoolean("edit") ? "chaosshard.anchor.gui.seals" : "chaosshard.anchor.gui.seals_ro",
                open, TileAnchor.MAX_SEALS), 8, 88, ACCENT);
        int hum = v.getInteger("hum");
        if (hum > 0) {
            String h = I18n.format("chaosshard.anchor.gui.hum", hum);
            fontRenderer.drawString(h, 168 - fontRenderer.getStringWidth(h), 102, DIM);
        }
        fontRenderer.drawString(I18n.format("container.inventory"), 8, ContainerAnchor.INV_Y - 10, DIM);
    }

    // ------------------------------------------------------------------ Сеть

    private void drawNetTab() {
        NBTTagCompound v = view;
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.net_title", v.getInteger("size")), 8, 6, ACCENT);
        if (!v.getBoolean("net")) {
            fontRenderer.drawSplitString(I18n.format("chaosshard.anchor.gui.net_none"), 8, 20, 160, TEXT);
            return;
        }
        if (scry != null) {
            fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.scry", scry.getX(), scry.getZ()), 8, 20, TEXT);
            return;
        }
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.energy", fmt(v.getDouble("eLand")), fmt(v.getDouble("eOut"))), 8, 20, TEXT);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.reserve", v.getInteger("reserve")), 8, 31, TEXT);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.garrison", v.getInteger("garrison"), v.getInteger("garrisonMax")), 8, 42, TEXT);
        if (map) {
            drawNetMap();
            return;
        }
        NBTTagList list = v.getTagList("list", 10);
        int y = LIST_Y;
        drawRect(6, y - 3, 170, y - 2, 0xFF4A3A60);
        boolean path = v.getBoolean("path");
        for (int i = scroll; i < Math.min(list.tagCount(), scroll + LIST_ROWS); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int s = e.getInteger("s");
            String line = I18n.format("chaosshard.anchor.gui.row", e.getInteger("x"), e.getInteger("z"), e.getInteger("l"));
            int sp = e.getInteger("sp");
            String st = sp >= 0 ? specName(sp) : I18n.format("chaosshard.anchor.gui.state" + s);
            int col = e.getBoolean("here") ? ACCENT : TEXT;
            fontRenderer.drawString(line, 8, y, col);
            fontRenderer.drawString(st, 146 - fontRenderer.getStringWidth(st), y, s == 2 ? 0xFFD060 : s == 1 ? 0x90E090 : DIM);
            if (path && !e.getBoolean("here")) {
                fontRenderer.drawString("»", 158, y, ACCENT);
            }
            y += 11;
        }
        if (list.tagCount() > LIST_ROWS) {
            fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.scroll", scroll + 1,
                    Math.min(list.tagCount(), scroll + LIST_ROWS), list.tagCount()), 8, y + 2, DIM);
        }
        if (path || v.getBoolean("watchStage")) {
            fontRenderer.drawSplitString(I18n.format(path && v.getBoolean("watchStage") ? "chaosshard.anchor.gui.hint_both"
                    : path ? "chaosshard.anchor.gui.hint_path" : "chaosshard.anchor.gui.hint_scry"), 8, 200, 160, DIM);
        }
    }

    /** Карта сети (Дозор): Якоря и отряды. */
    private void drawNetMap() {
        NBTTagList list = view.getTagList("list", 10);
        NBTTagList dots = view.getTagList("dots", 10);
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
        int ax = 8, ay = 56, aw = 160, ah = 150;
        double k = Math.min((double) aw / (maxX - minX), (double) ah / (maxZ - minZ));
        drawRect(ax, ay, ax + aw, ay + ah, 0xFF120C1A);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int px = ax + (int) ((e.getInteger("x") - minX) * k), pz = ay + (int) ((e.getInteger("z") - minZ) * k);
            int c = e.getBoolean("here") ? 0xFFE0A0FF : 0xFF9A5AE0;
            drawRect(px - 2, pz - 2, px + 3, pz + 3, c);
        }
        for (int i = 0; i < dots.tagCount(); i++) {
            NBTTagCompound e = dots.getCompoundTagAt(i);
            int px = ax + (int) ((e.getInteger("x") - minX) * k), pz = ay + (int) ((e.getInteger("z") - minZ) * k);
            drawRect(px - 1, pz - 1, px + 2, pz + 2, e.getByte("t") == 1 ? 0xFFFF4040 : 0xFFFFE040);
        }
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.map_legend"), 8, ay + ah + 3, DIM);
    }

    /** Взгляд Дозора: вид сверху на землю Якоря. */
    private void drawScry(int gx, int gy) {
        NBTTagCompound s = view.getCompoundTag("scry");
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
        int size = 144, ox = gx + 16, oy = gy + 34;
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
        return new int[]{net ? 8 : 92, 30 + idx * 12};
    }

    private void drawPathTab(int mx, int my) {
        NBTTagCompound v = view;
        int spec = v.getInteger("spec");
        int lvl = v.getInteger("level");
        if (spec >= 0) {
            AnchorSpec s = AnchorSpec.of(spec);
            fontRenderer.drawString(specName(spec), 8, 6, 0xFFD060);
            String kind = I18n.format(s.network ? "chaosshard.anchor.gui.kind_net" : "chaosshard.anchor.gui.kind_local");
            fontRenderer.drawString(kind, 168 - fontRenderer.getStringWidth(kind), 6, DIM);
            int y = drawSpecText(s, 22, v.getBoolean("stage"));
            if (s == AnchorSpec.FORGE) {
                fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.forge", v.getInteger("forgeRate"), v.getInteger("forgeEnergy")), 8, y + 4, TEXT);
                y += 14;
            }
            fontRenderer.drawSplitString(I18n.format("chaosshard.anchor.gui.reshape"), 8, Math.max(y + 8, 186), 160, DIM);
            return;
        }
        fontRenderer.drawString(I18n.format(lvl >= AnchorSpec.CHOOSE_LEVEL ? "chaosshard.anchor.gui.path_choose" : "chaosshard.anchor.gui.path_locked",
                AnchorSpec.CHOOSE_LEVEL), 8, 6, ACCENT);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.kind_net"), 8, 18, DIM);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.kind_local"), 92, 18, DIM);
        NBTTagList counts = v.getTagList("specs", 10);
        for (AnchorSpec s : AnchorSpec.values()) {
            int[] r = specRow(s);
            boolean hover = mx >= r[0] && mx < r[0] + 78 && my >= r[1] - 1 && my < r[1] + 10;
            int col = s.ordinal() == picked ? 0xFFD060 : hover ? ACCENT : TEXT;
            String name = specName(s.ordinal());
            int n = s.ordinal() < counts.tagCount() ? counts.getCompoundTagAt(s.ordinal()).getInteger("n") : 0;
            fontRenderer.drawString(name + (n > 0 ? " §7×" + n : ""), r[0], r[1], col);
        }
        if (picked >= 0) {
            drawSpecText(AnchorSpec.of(picked), 96, false);
        }
    }

    /** Эффекты специализации, по строкам. Возвращает y после текста. */
    private int drawSpecText(AnchorSpec s, int y, boolean stage) {
        for (int i = 1; i <= 3; i++) {
            String line = "• " + I18n.format("chaosshard.anchor.spec." + s.key() + ".e" + i);
            List<String> parts = fontRenderer.listFormattedStringToWidth(line, 160);
            for (String p : parts) {
                fontRenderer.drawString(p, 8, y, TEXT);
                y += 10;
            }
        }
        String st = I18n.format("chaosshard.anchor.gui.stage") + " " + I18n.format("chaosshard.anchor.spec." + s.key() + ".stage");
        for (String p : fontRenderer.listFormattedStringToWidth(st, 160)) {
            fontRenderer.drawString(p, 8, y, stage ? 0xFFD060 : DIM);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ Склад

    private void drawStoreTab() {
        NBTTagCompound v = view;
        int storage = v.getInteger("storage");
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.store_title"), 8, 6, ACCENT);
        if (storage <= 0) {
            fontRenderer.drawSplitString(I18n.format("chaosshard.anchor.gui.store_none"), 8, 20, 160, TEXT);
            return;
        }
        if (v.getInteger("spec") == AnchorSpec.HARVEST.ordinal()) {
            fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.field_xp", v.getInteger("fieldXp")), 8, 78, TEXT);
            if (v.getBoolean("stage")) {
                fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.sample"), 28, 108, DIM);
            }
        }
        fontRenderer.drawString(I18n.format("container.inventory"), 8, ContainerAnchor.INV_Y - 10, DIM);
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.2f", d).replaceAll("0+$", "");
    }
}
