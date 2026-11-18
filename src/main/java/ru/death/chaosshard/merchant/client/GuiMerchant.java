package ru.death.chaosshard.merchant.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.client.ChaosUi;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketMerchantAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно торговца: слева товары (постоянные и сменные на сегодня), справа счёт и скупка добычи Lycanites.
 * Тёмное стекло со скошенными углами и латунные линии — в одном духе с окном логова, только теплее.
 */
public class GuiMerchant extends GuiScreen {

    private static NBTTagCompound view = new NBTTagCompound();

    private static final int GLASS = 0xF20B0907, GLASS_HEAD = 0xF2171009, GRID = 0x0CD9A441;
    private static final int BRASS = 0xFFD9A441, BRASS_MID = 0x99D9A441, BRASS_LOW = 0x40D9A441;
    private static final int TXT = 0xF0E6D2, LABEL = 0xB8A88A, MUTED = 0x6E6252, OK = 0x8FE3A6, BAD = 0xFF8C80, GOLD = 0xFFD66B;

    private static final int W = 300, H = 264, CUT = 7, ROW = 16;
    private static final int LX = 10, LW = 170, RX = 188, RW = 102, TOP = 34;
    private static final int LOT_ROWS = 7;

    private int left, top, lotScroll;
    private List<String> hover;
    private float time;

    /** Пакет пришёл: обновить открытое окно или открыть новое. */
    public static void show(NBTTagCompound t) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiMerchant) {
            view = t;
        } else if (t.getBoolean("open")) {
            view = t;
            mc.displayGuiScreen(new GuiMerchant());
        }
    }

    @Override
    public void initGui() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    private static String tr(String k, Object... a) {
        return I18n.format(k, a);
    }

    private static String up(String s) {
        return s.toUpperCase(new Locale("ru"));
    }

    private static String money(long v) {
        return String.format(Locale.ROOT, "%,d", v).replace(',', ' ');
    }

    // ------------------------------------------------------------------ примитивы

    private void chamferFill(int x, int y, int w, int h, int cut, int color) {
        for (int i = 0; i < cut; i++) {
            drawRect(x + cut - i, y + i, x + w - cut + i, y + i + 1, color);
            drawRect(x + cut - i, y + h - 1 - i, x + w - cut + i, y + h - i, color);
        }
        drawRect(x, y + cut, x + w, y + h - cut, color);
    }

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

    private void section(String title, int x, int w, int y) {
        drawRect(x, y + 2, x + 1, y + 5, GOLD | 0xFF000000);
        drawRect(x + 1, y + 3, x + 2, y + 4, GOLD | 0xFF000000);
        String t = up(title);
        fontRenderer.drawString(t, x + 5, y, GOLD);
        drawRect(x + 9 + fontRenderer.getStringWidth(t), y + 4, x + w, y + 5, BRASS_LOW);
    }

    private static ItemStack coinIcon;

    /** Значок монеты (золотая монета Ancient Warfare, если она есть в сборке). */
    private void coin(int x, int y) {
        if (coinIcon == null) {
            Item it = Item.getByNameOrId("ancientwarfare:coin");
            if (it == null) {
                it = Item.getByNameOrId("ancientwarfarenpc:coin");
            }
            coinIcon = it == null ? ItemStack.EMPTY : new ItemStack(it);
            if (!coinIcon.isEmpty()) {
                coinIcon.setTagInfo("metal", new net.minecraft.nbt.NBTTagString("gold"));
            }
        }
        if (coinIcon.isEmpty()) {
            drawRect(x + 1, y, x + 7, y + 8, 0xFFD9A441);
            drawRect(x, y + 1, x + 8, y + 7, 0xFFD9A441);
            drawRect(x + 2, y + 2, x + 6, y + 6, 0xFFFFD66B);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5F, 0.5F, 1F);
        ChaosUi.item(coinIcon, 0, 0);
        GlStateManager.popMatrix();
    }

    /** Цена с монетой, выровненная по правому краю. Возвращает ширину. */
    private int price(long v, int right, int y, int color) {
        String s = money(v);
        int w = fontRenderer.getStringWidth(s);
        fontRenderer.drawString(s, right - w, y, color);
        coin(right - w - 10, y);
        return w + 10;
    }

    private void button(int x, int y, int w, String label, int state) {
        if (state == 2) {
            for (int i = x + 3; i < x + w - 3; i += 4) {
                drawRect(i, y, i + 2, y + 1, 0x60D9A441);
                drawRect(i, y + 15, i + 2, y + 16, 0x60D9A441);
            }
            drawRect(x, y + 3, x + 1, y + 13, 0x60D9A441);
            drawRect(x + w - 1, y + 3, x + w, y + 13, 0x60D9A441);
        } else {
            chamferFill(x, y, w, 16, 3, state == 1 ? 0x60D9A441 : 0x28D9A441);
            chamferLine(x, y, w, 16, 3, BRASS);
        }
        String t = fontRenderer.trimStringToWidth(label, w - 8);
        fontRenderer.drawString(t, x + (w - fontRenderer.getStringWidth(t)) / 2, y + 4, state == 2 ? MUTED : state == 1 ? 0xFFFFFF : TXT);
    }

    // ------------------------------------------------------------------ рисование

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        time = (mc.world == null ? 0 : mc.world.getTotalWorldTime()) + partialTicks;
        hover = null;
        GlStateManager.pushMatrix();
        GlStateManager.translate(left, top, 0);
        int mx = mouseX - left, my = mouseY - top;
        drawFrame();
        if (!view.hasKey("offers")) {
            String w = tr("chaosshard.lair.gui.wait");
            fontRenderer.drawString(w, (W - fontRenderer.getStringWidth(w)) / 2, H / 2, LABEL);
        } else {
            drawHeader();
            drawOffers(mx, my);
            drawAccount(mx, my);
            drawLots(mx, my);
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hover != null) {
            drawHoveringText(hover, mouseX, mouseY);
        }
    }

    private void drawFrame() {
        chamferLine(-2, -2, W + 4, H + 4, CUT + 1, 0x18D9A441);
        chamferLine(-1, -1, W + 2, H + 2, CUT, 0x30D9A441);
        chamferFill(0, 0, W, H, CUT, GLASS);
        drawRect(CUT, 1, W - CUT, 22, GLASS_HEAD);
        for (int gx = 8; gx < W - 4; gx += 8) {
            drawRect(gx, 23, gx + 1, H - 4, GRID);
        }
        for (int gy = 24; gy < H - 4; gy += 8) {
            drawRect(4, gy, W - 4, gy + 1, GRID);
        }
        chamferLine(0, 0, W, H, CUT, BRASS_MID);
        drawRect(10, 22, W - 10, 23, BRASS_MID);
        drawRect(RX - 5, 28, RX - 4, H - 10, BRASS_LOW);
    }

    private void drawHeader() {
        fontRenderer.drawString(up(tr("entity.chaosshard.merchant.name")), 13, 8, TXT);
        long leave = view.getLong("leave");
        String s = tr("chaosshard.merchant.gui.leave", Math.max(1, (int) Math.ceil(leave / 1200.0)));
        fontRenderer.drawString(s, W - 13 - fontRenderer.getStringWidth(s), 8, LABEL);
    }

    private List<NBTTagCompound> list(String key) {
        List<NBTTagCompound> out = new ArrayList<>();
        NBTTagList l = view.getTagList(key, 10);
        for (int i = 0; i < l.tagCount(); i++) {
            out.add(l.getCompoundTagAt(i));
        }
        return out;
    }

    /** Строки товаров: (номер в списке сервера, y). Разделитель «на сегодня» перед первым сменным. */
    private int offerY(List<NBTTagCompound> offers, int idx) {
        int y = TOP + 12;
        for (int i = 0; i < idx; i++) {
            y += ROW;
            if (!offers.get(i).getBoolean("daily") && offers.get(i + 1).getBoolean("daily")) {
                y += 14;
            }
        }
        return y;
    }

    private void drawOffers(int mx, int my) {
        List<NBTTagCompound> offers = list("offers");
        long bal = view.getLong("balance");
        section(tr("chaosshard.merchant.gui.goods"), LX, LW, TOP);
        for (int i = 0; i < offers.size(); i++) {
            NBTTagCompound o = offers.get(i);
            if (o.getBoolean("daily") && (i == 0 || !offers.get(i - 1).getBoolean("daily"))) {
                section(tr("chaosshard.merchant.gui.today"), LX, LW, offerY(offers, i) - 12);
            }
            int y = offerY(offers, i);
            ItemStack st = new ItemStack(o);
            int p = o.getInteger("price");
            boolean can = bal >= p;
            boolean over = ChaosUi.in(mx, my, LX, y, LW, ROW);
            if (over) {
                chamferFill(LX, y, LW, ROW, 2, can ? 0x40D9A441 : 0x30FF8C80);
            } else if (i % 2 == 0) {
                drawRect(LX, y, LX + LW, y + ROW, 0x18D9A441);
            }
            GlStateManager.color(1, 1, 1, 1);
            ChaosUi.item(st, LX + 1, y);
            String name = st.getDisplayName() + (st.getCount() > 1 ? " ×" + st.getCount() : "");
            int pw = fontRenderer.getStringWidth(money(p)) + 14;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(name, LW - 22 - pw), LX + 20, y + 4, can ? TXT : MUTED);
            price(p, LX + LW - 3, y + 4, can ? GOLD : BAD);
            if (over) {
                hover = new ArrayList<>(st.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL));
                hover.add("");
                hover.add((can ? "§6" : "§c") + tr("chaosshard.merchant.gui.price", money(p)));
                hover.add("§7" + (can ? tr("chaosshard.merchant.gui.buy_hint") : tr("chaosshard.merchant.gui.poor", money(p - bal))));
            }
        }
    }

    private void drawAccount(int mx, int my) {
        section(tr("chaosshard.merchant.gui.account"), RX, RW, TOP);
        long bal = view.getLong("balance");
        chamferFill(RX, TOP + 12, RW, 22, 3, 0x40140E08);
        chamferLine(RX, TOP + 12, RW, 22, 3, BRASS_LOW);
        String b = money(bal);
        GlStateManager.pushMatrix();
        GlStateManager.translate(RX + RW - 6, TOP + 17, 0);
        GlStateManager.scale(1.5F, 1.5F, 1F);
        int bw = fontRenderer.getStringWidth(b);
        fontRenderer.drawString(b, -bw, 0, GOLD);
        GlStateManager.popMatrix();
        coin(RX + RW - 6 - (int) (bw * 1.5F) - 12, TOP + 19);
        if (ChaosUi.in(mx, my, RX, TOP + 12, RW, 22)) {
            int[] v = view.getIntArray("values");
            hover = new ArrayList<>();
            hover.add(tr("chaosshard.merchant.gui.account") + ": " + money(bal));
            hover.add("§7" + tr("chaosshard.merchant.gui.rates", v.length > 0 ? v[0] : 1, v.length > 1 ? v[1] : 10, v.length > 2 ? v[2] : 100,
                    v.length > 3 ? v[3] : 250));
        }
        long coins = view.getLong("coins");
        int y = TOP + 40;
        String c = coins > 0 ? tr("chaosshard.merchant.gui.pocket", money(coins)) : tr("chaosshard.merchant.gui.no_coins");
        for (String l : fontRenderer.listFormattedStringToWidth(c, RW)) {
            fontRenderer.drawString(l, RX, y, coins > 0 ? LABEL : MUTED);
            y += 10;
        }
        boolean over = ChaosUi.in(mx, my, RX, TOP + 62, RW, 16);
        button(RX, TOP + 62, RW, tr("chaosshard.merchant.gui.deposit"), coins <= 0 ? 2 : over ? 1 : 0);
        if (over) {
            hover = new ArrayList<>();
            hover.add(coins > 0 ? tr("chaosshard.merchant.gui.deposit.desc") : "§7" + tr("chaosshard.merchant.gui.no_coins.desc"));
        }
    }

    private static final int LOTS_Y = TOP + 96;

    private void drawLots(int mx, int my) {
        section(tr("chaosshard.merchant.gui.buys"), RX, RW, LOTS_Y - 12);
        List<NBTTagCompound> lots = list("lots");
        if (lots.isEmpty()) {
            List<String> ls = fontRenderer.listFormattedStringToWidth(tr("chaosshard.merchant.gui.nothing"), RW - 4);
            int y = LOTS_Y + 6;
            for (String l : ls) {
                fontRenderer.drawString(l, RX + (RW - fontRenderer.getStringWidth(l)) / 2, y, MUTED);
                y += 10;
            }
            return;
        }
        lotScroll = Math.max(0, Math.min(lotScroll, lots.size() - LOT_ROWS));
        for (int r = 0; r < Math.min(LOT_ROWS, lots.size()); r++) {
            NBTTagCompound lt = lots.get(r + lotScroll);
            int y = LOTS_Y + r * ROW;
            ItemStack st = new ItemStack(lt);
            boolean over = ChaosUi.in(mx, my, RX, y, RW - 4, ROW);
            if (over) {
                chamferFill(RX, y, RW - 4, ROW, 2, 0x40D9A441);
            } else if (r % 2 == 0) {
                drawRect(RX, y, RX + RW - 4, y + ROW, 0x18D9A441);
            }
            GlStateManager.color(1, 1, 1, 1);
            ChaosUi.item(st, RX + 1, y);
            fontRenderer.drawString("×" + lt.getInteger("n"), RX + 19, y + 4, lt.getBoolean("part") ? LABEL : TXT);
            price(lt.getLong("total"), RX + RW - 6, y + 4, OK);
            if (over) {
                hover = new ArrayList<>();
                hover.add(st.getDisplayName() + " ×" + lt.getInteger("n"));
                hover.add("§a" + tr("chaosshard.merchant.gui.sell_for", money(lt.getLong("total"))));
                hover.add("§7" + tr("chaosshard.merchant.gui.sell_hint"));
                if (lt.getBoolean("part")) {
                    hover.add("§e" + tr("chaosshard.merchant.gui.part"));
                }
            }
        }
        ChaosUi.scrollbar(RX + RW - 3, LOTS_Y, LOT_ROWS * ROW, lotScroll, LOT_ROWS, lots.size());
        long all = 0;
        for (NBTTagCompound lt : lots) {
            if (!lt.getBoolean("part")) {
                all += lt.getLong("total");
            }
        }
        int by = H - 26;
        boolean over = ChaosUi.in(mx, my, RX, by, RW, 16);
        button(RX, by, RW, tr("chaosshard.merchant.gui.sell_all"), all <= 0 ? 2 : over ? 1 : 0);
        if (over) {
            hover = new ArrayList<>();
            hover.add(all > 0 ? tr("chaosshard.merchant.gui.sell_all.desc", money(all)) : "§7" + tr("chaosshard.merchant.gui.only_parts"));
        }
    }

    // ------------------------------------------------------------------ ввод

    private void send(int action, int arg, String key) {
        ChaosNetwork.CHANNEL.sendToServer(new PacketMerchantAction(view.getInteger("entity"), action, arg, key));
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.2F);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || !view.hasKey("offers")) {
            return;
        }
        int mx = mouseX - left, my = mouseY - top;
        List<NBTTagCompound> offers = list("offers");
        for (int i = 0; i < offers.size(); i++) {
            if (ChaosUi.in(mx, my, LX, offerY(offers, i), LW, ROW)) {
                if (view.getLong("balance") >= offers.get(i).getInteger("price")) {
                    int times = isShiftKeyDown() ? 5 : 1;
                    send(PacketMerchantAction.BUY, (times << 8) | i, "");
                }
                return;
            }
        }
        if (ChaosUi.in(mx, my, RX, TOP + 62, RW, 16) && view.getLong("coins") > 0) {
            send(PacketMerchantAction.DEPOSIT, 0, "");
            return;
        }
        List<NBTTagCompound> lots = list("lots");
        for (int r = 0; r < Math.min(LOT_ROWS, lots.size() - lotScroll); r++) {
            if (ChaosUi.in(mx, my, RX, LOTS_Y + r * ROW, RW - 4, ROW)) {
                send(PacketMerchantAction.SELL, 0, lots.get(r + lotScroll).getString("key"));
                return;
            }
        }
        if (!lots.isEmpty() && ChaosUi.in(mx, my, RX, H - 26, RW, 16)) {
            send(PacketMerchantAction.SELL_ALL, 0, "");
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            lotScroll += d > 0 ? -1 : 1;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // окно сразу видит монеты и добычу, которые игрок подобрал, пока оно открыто
        if (mc.player != null && mc.player.ticksExisted % 40 == 0 && view.hasKey("entity")) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketMerchantAction(view.getInteger("entity"), PacketMerchantAction.REFRESH, 0, ""));
        }
        net.minecraft.entity.Entity e = mc.world == null ? null : mc.world.getEntityByID(view.getInteger("entity"));
        if (view.hasKey("entity") && (e == null || !e.isEntityAlive() || mc.player.getDistanceSq(e) > 10 * 10)) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        view = new NBTTagCompound();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
