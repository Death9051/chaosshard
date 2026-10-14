package ru.death.chaosshard.anchor.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.anchor.ContainerAnchor;
import ru.death.chaosshard.anchor.TileAnchor;

import java.io.IOException;

/** Окно Якоря: вкладки «Якорь» и «Сеть». Рисуется без текстур — плашки и полоски. */
public class GuiAnchor extends GuiContainer {

    /** Последние сведения от сервера. */
    public static NBTTagCompound view = new NBTTagCompound();

    private static final int BG = 0xF0201028;
    private static final int FRAME = 0xFF7A4FB0;
    private static final int TEXT = 0xE0D8F0;
    private static final int DIM = 0x9888B0;
    private static final int ACCENT = 0xC890FF;
    private static final int LIST_ROWS = 9;

    private final ContainerAnchor container;
    private int tab;
    private int scroll;
    private final int[] slotX;

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
        buttonList.add(new GuiButton(0, guiLeft, guiTop - 20, 86, 18, I18n.format("chaosshard.anchor.gui.tab_anchor")));
        buttonList.add(new GuiButton(1, guiLeft + 90, guiTop - 20, 86, 18, I18n.format("chaosshard.anchor.gui.tab_net")));
        setTab(tab);
    }

    private void setTab(int t) {
        tab = t;
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot s = container.inventorySlots.get(i);
            boolean sealHidden = i < TileAnchor.MAX_SEALS && !view.getBoolean("edit") && !s.getHasStack();
            s.xPos = tab == 0 && !sealHidden ? slotX[i] : -10000;
        }
        if (buttonList.size() > 1) {
            buttonList.get(0).enabled = tab != 0;
            buttonList.get(1).enabled = tab != 1;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        setTab(button.id);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && tab == 1) {
            int n = view.getTagList("list", 10).tagCount();
            scroll = Math.max(0, Math.min(Math.max(0, n - LIST_ROWS), scroll + (d > 0 ? -1 : 1)));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        setTab(tab);
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        drawRect(x - 1, y - 1, x + xSize + 1, y + ySize + 1, FRAME);
        drawRect(x, y, x + xSize, y + ySize, BG);
        if (tab != 0) {
            return;
        }
        int open = view.getInteger("slots");
        boolean edit = view.getBoolean("edit");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot s = container.inventorySlots.get(i);
            if (s.xPos < 0) {
                continue;
            }
            boolean locked = i < TileAnchor.MAX_SEALS && (i >= open || !edit);
            drawRect(x + s.xPos - 1, y + s.yPos - 1, x + s.xPos + 17, y + s.yPos + 17, locked ? 0xFF2A2030 : 0xFF4A3A60);
            drawRect(x + s.xPos, y + s.yPos, x + s.xPos + 16, y + s.yPos + 16, locked ? 0xFF15101A : 0xFF100A18);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void bar(int x, int y, int w, double part, int color) {
        drawRect(x, y, x + w, y + 5, 0xFF100A18);
        drawRect(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, part))), y + 5, color);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (tab == 0) {
            drawAnchorTab();
        } else {
            drawNetTab();
        }
    }

    private void drawAnchorTab() {
        NBTTagCompound v = view;
        String owner = v.getString("owner");
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.title", owner), 8, 6, ACCENT);
        if (!v.hasKey("level")) {
            return;
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

    private void drawNetTab() {
        NBTTagCompound v = view;
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.net_title", v.getInteger("size")), 8, 6, ACCENT);
        if (!v.getBoolean("net")) {
            fontRenderer.drawSplitString(I18n.format("chaosshard.anchor.gui.net_none"), 8, 20, 160, TEXT);
            return;
        }
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.energy", fmt(v.getDouble("eLand")), fmt(v.getDouble("eOut"))), 8, 20, TEXT);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.reserve", v.getInteger("reserve")), 8, 31, TEXT);
        fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.garrison", v.getInteger("garrison"), v.getInteger("garrisonMax")), 8, 42, TEXT);
        NBTTagList list = v.getTagList("list", 10);
        int y = 58;
        drawRect(6, y - 3, 170, y - 2, 0xFF4A3A60);
        for (int i = scroll; i < Math.min(list.tagCount(), scroll + LIST_ROWS); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int s = e.getInteger("s");
            String st = I18n.format("chaosshard.anchor.gui.state" + s);
            String line = I18n.format("chaosshard.anchor.gui.row", e.getInteger("x"), e.getInteger("z"), e.getInteger("l"));
            int col = e.getBoolean("here") ? ACCENT : TEXT;
            fontRenderer.drawString(line, 8, y, col);
            fontRenderer.drawString(st, 168 - fontRenderer.getStringWidth(st), y, s == 2 ? 0xFFD060 : s == 1 ? 0x90E090 : DIM);
            y += 11;
        }
        if (list.tagCount() > LIST_ROWS) {
            fontRenderer.drawString(I18n.format("chaosshard.anchor.gui.scroll", scroll + 1,
                    Math.min(list.tagCount(), scroll + LIST_ROWS), list.tagCount()), 8, y + 2, DIM);
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.2f", d).replaceAll("0+$", "");
    }
}
