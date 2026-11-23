package ru.death.chaosshard.breach.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import ru.death.chaosshard.breach.BlockRitualSpire;
import ru.death.chaosshard.breach.BreachRitual;
import ru.death.chaosshard.breach.ContainerBreachAltar;
import ru.death.chaosshard.client.ChaosUi;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketAltarStart;
import ru.death.chaosshard.ritual.Ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно Алтаря Прорыва: слева 12 мест для подношений, справа — какой ритуал из них сложится, целы ли шпили,
 * и кнопка «Начать» (нужен Гримуар в сумке). Внизу сумка.
 */
public class GuiBreachAltar extends GuiContainer {

    private static final int W = 190, H = 204, CUT = 6;
    private static final int RX = 98, RW = 80, BTN_Y = 86;
    private static final int BG = 0xF0120D1A, HEAD = 0xF01A1326, LINE = 0xFF6E4FA0, LINE_LOW = 0x606E4FA0;

    private final ContainerBreachAltar container;

    public GuiBreachAltar(ContainerBreachAltar container) {
        super(container);
        this.container = container;
        xSize = W;
        ySize = H;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        List<String> tip = buttonTip(mouseX - guiLeft, mouseY - guiTop);
        if (tip != null) {
            drawHoveringText(tip, mouseX, mouseY);
        }
    }

    private void chamfer(int x, int y, int w, int h, int cut, int color) {
        for (int i = 0; i < cut; i++) {
            drawRect(x + cut - i, y + i, x + w - cut + i, y + i + 1, color);
            drawRect(x + cut - i, y + h - 1 - i, x + w - cut + i, y + h - i, color);
        }
        drawRect(x, y + cut, x + w, y + h - cut, color);
    }

    private void frame(int x, int y, int w, int h, int cut, int color) {
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

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        GlStateManager.color(1, 1, 1, 1);
        frame(x - 1, y - 1, W + 2, H + 2, CUT + 1, 0x306E4FA0);
        chamfer(x, y, W, H, CUT, BG);
        drawRect(x + CUT, y + 1, x + W - CUT, y + 19, HEAD);
        frame(x, y, W, H, CUT, LINE);
        drawRect(x + 8, y + 19, x + W - 8, y + 20, LINE_LOW);
        for (int i = 0; i < TileSlots.COUNT; i++) {
            ChaosUi.slot(x + ContainerBreachAltar.SX - 1 + (i % 4) * 18, y + ContainerBreachAltar.SY - 1 + (i / 4) * 18, ChaosUi.SLOT_OPEN);
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                int sy = r < 3 ? ContainerBreachAltar.PY + r * 18 : ContainerBreachAltar.PY + 58;
                ChaosUi.slot(x + ContainerBreachAltar.PX - 1 + c * 18, y + sy - 1, ChaosUi.SLOT_OPEN);
            }
        }
        ChaosUi.well(x + RX - 2, y + ContainerBreachAltar.SY - 2, RW + 4, 50);
        int mx = mouseX - x, my = mouseY - y;
        boolean can = canStart() == null;
        ChaosUi.button(fontRenderer, x + RX - 2, y + BTN_Y, RW + 4, I18n.format("chaosshard.altar.gui.start"),
                !can ? 2 : ChaosUi.in(mx, my, RX - 2, BTN_Y, RW + 4, 16) ? 1 : 0, ChaosUi.OFF);
    }

    /** Подношения в местах Алтаря (брошенные сверху окно не видит). */
    private List<ItemStack> offered() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < TileSlots.COUNT; i++) {
            ItemStack s = container.getSlot(i).getStack();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private Ritual match(List<ItemStack> st) {
        if (st.isEmpty()) {
            return null;
        }
        if (ru.death.chaosshard.challenge.ChallengeSpec.parse(st) != null) {
            return Ritual.CHALLENGE;
        }
        return Ritual.findAltar(st);
    }

    private int spires() {
        int whole = 0;
        for (BlockPos s : BreachRitual.pillars(container.tile.getPos())) {
            if (BlockRitualSpire.isWhole(mc.world, s)) {
                whole++;
            }
        }
        return whole;
    }

    private boolean hasGrimoire() {
        if (mc.player.isCreative()) {
            return true;
        }
        for (ItemStack s : mc.player.inventory.mainInventory) {
            if (s.getItem() == ModItems.GRIMOIRE) {
                return true;
            }
        }
        return mc.player.getHeldItemOffhand().getItem() == ModItems.GRIMOIRE;
    }

    /** Почему нельзя начать (ключ перевода) или null. */
    private String canStart() {
        if (match(offered()) == null) {
            return offered().isEmpty() ? "chaosshard.altar.gui.why.empty" : "chaosshard.altar.gui.why.unknown";
        }
        if (spires() < 4) {
            return "chaosshard.altar.gui.why.spires";
        }
        if (!hasGrimoire()) {
            return "chaosshard.altar.gui.why.grimoire";
        }
        return null;
    }

    private List<String> buttonTip(int mx, int my) {
        if (!ChaosUi.in(mx, my, RX - 2, BTN_Y, RW + 4, 16)) {
            return null;
        }
        String why = canStart();
        List<String> out = new ArrayList<>();
        out.addAll(fontRenderer.listFormattedStringToWidth(why == null ? I18n.format("chaosshard.altar.gui.start.desc")
                : "§c" + I18n.format(why), 180));
        return out;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("tile.chaosshard.breach_altar.name"), 10, 6, ChaosUi.TEXT);
        fontRenderer.drawString(I18n.format("chaosshard.altar.gui.offerings"), ContainerBreachAltar.SX, 23, ChaosUi.SOFT);
        fontRenderer.drawString(I18n.format("container.inventory"), ContainerBreachAltar.PX, ContainerBreachAltar.PY - 11, ChaosUi.SOFT);
        int sp = spires();
        String s = I18n.format("chaosshard.altar.gui.spires", sp);
        fontRenderer.drawString(s, W - 10 - fontRenderer.getStringWidth(s), 6, sp >= 4 ? ChaosUi.GOOD : ChaosUi.BAD);
        // что сложится
        List<ItemStack> st = offered();
        Ritual r = match(st);
        int y = ContainerBreachAltar.SY + 2;
        String head = I18n.format("chaosshard.altar.gui.ritual");
        fontRenderer.drawString(head, RX, y, ChaosUi.DIM);
        y += 11;
        String body;
        int color;
        if (r != null) {
            body = I18n.format(r.key());
            color = ChaosUi.ACCENT;
        } else if (st.isEmpty()) {
            body = I18n.format("chaosshard.altar.gui.none");
            color = ChaosUi.OFF;
        } else {
            body = I18n.format("chaosshard.altar.gui.unknown");
            color = ChaosUi.BAD;
        }
        for (String l : fontRenderer.listFormattedStringToWidth(body, RW)) {
            if (y > ContainerBreachAltar.SY + 40) {
                break;
            }
            fontRenderer.drawString(l, RX, y, color);
            y += 10;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && ChaosUi.in(mouseX - guiLeft, mouseY - guiTop, RX - 2, BTN_Y, RW + 4, 16) && canStart() == null) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketAltarStart(container.tile.getPos()));
            mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.0F);
            mc.displayGuiScreen(null);
        }
    }

    /** Сколько мест на Алтаре. */
    private static final class TileSlots {
        static final int COUNT = ru.death.chaosshard.breach.TileBreachAltar.SLOTS;
    }
}
