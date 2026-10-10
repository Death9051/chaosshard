package ru.death.chaosshard.heart.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import ru.death.chaosshard.client.ClientThreadCache;
import ru.death.chaosshard.heart.ContainerHeart;
import ru.death.chaosshard.heart.HeartTiers;
import ru.death.chaosshard.heart.TileHeart;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketHeartAction;
import ru.death.chaosshard.thread.ThreadManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Окно Сердца Острова: ступень, таймер нароста, выбор руды, гнёзда модулей и пробуждение.
 * Фон рисуется кодом — без текстуры.
 */
public class GuiHeart extends GuiContainer {

    private static final int BG = 0xF0140A1C;
    private static final int PANEL = 0xFF2A1238;
    private static final int EDGE = 0xFF7A3AB0;
    private static final int TEXT = 0xE8D8FF;
    private static final int DIM = 0x9080A8;

    private static final int ORE_X = 8, ORE_Y = 64, ORE_COLS = 7, ORE_ROWS = 3;
    private static final int COST_Y = 126;

    private final ContainerHeart container;
    private final TileHeart heart;
    private GuiButton upgradeButton;

    // Для кликов и подсказок, считается при отрисовке
    private final List<IBlockState> shownOres = new ArrayList<>();
    private final List<HeartTiers.Cost> shownCosts = new ArrayList<>();

    public GuiHeart(ContainerHeart container) {
        super(container);
        this.container = container;
        this.heart = container.heart;
        this.xSize = ContainerHeart.GUI_W;
        this.ySize = ContainerHeart.GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        upgradeButton = new GuiButton(0, guiLeft + 112, guiTop + COST_Y - 2, 58, 20, I18n.format("chaosshard.heart.gui.upgrade"));
        buttonList.add(upgradeButton);
    }

    private int tier() {
        return container.tier > 0 ? container.tier : heart.getTier();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        drawOwnTooltips(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        drawRect(x - 1, y - 1, x + xSize + 1, y + ySize + 1, EDGE);
        drawRect(x, y, x + xSize, y + ySize, BG);
        // Панели
        drawRect(x + 4, y + 16, x + 144, y + 54, PANEL);
        drawRect(x + 4, y + ORE_Y - 4, x + 144, y + ORE_Y + ORE_ROWS * 18, PANEL);
        drawRect(x + 4, y + COST_Y - 4, x + 172, y + COST_Y + 20, PANEL);
        // Гнёзда модулей
        for (int i = 0; i < TileHeart.MODULE_SLOTS; i++) {
            int sx = x + 151, sy = y + 19 + i * 20;
            boolean open = i < tier();
            drawRect(sx - 1, sy - 1, sx + 19, sy + 19, open ? EDGE : 0xFF302838);
            drawRect(sx, sy, sx + 18, sy + 18, open ? 0xFF1A0E24 : 0xFF141018);
        }
        // Слоты инвентаря
        for (Slot s : container.inventorySlots) {
            if (s.slotNumber >= TileHeart.MODULE_SLOTS) {
                drawRect(x + s.xPos - 1, y + s.yPos - 1, x + s.xPos + 17, y + s.yPos + 17, 0xFF3A2A48);
                drawRect(x + s.xPos, y + s.yPos, x + s.xPos + 16, y + s.yPos + 16, 0xFF1A1222);
            }
        }
        // Полоса роста
        int barX = x + 46, barY = y + 22, barW = 92;
        drawRect(barX, barY, barX + barW, barY + 6, 0xFF120818);
        int interval = Math.max(1, container.interval);
        int fill = (int) (barW * Math.min(1.0, container.timer / (double) interval));
        drawRect(barX, barY, barX + fill, barY + 6, 0xFFB050FF);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int tier = tier();
        fontRenderer.drawString(I18n.format("chaosshard.heart.gui.title", tier, HeartTiers.maxTier()), 6, 5, TEXT);
        fontRenderer.drawString(I18n.format("chaosshard.heart.gui.modules"), 146, 5, DIM);

        // Сердце крупно
        GlStateManager.pushMatrix();
        GlStateManager.translate(10, 19, 0);
        GlStateManager.scale(2, 2, 1);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(new ItemStack(ModBlocks.ISLAND_HEART), 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();

        int secs = Math.max(0, (container.interval - container.timer) / 20);
        fontRenderer.drawString(I18n.format("chaosshard.heart.gui.next", secs), 46, 31, DIM);
        fontRenderer.drawString(I18n.format("chaosshard.heart.gui.growths", container.growths, container.maxGrowths), 46, 42, TEXT);

        // Выбор руды
        fontRenderer.drawString(I18n.format("chaosshard.heart.gui.ores"), 8, ORE_Y - 12 + 2, TEXT);
        shownOres.clear();
        RenderHelper.enableGUIStandardItemLighting();
        for (HeartTiers.Growth g : HeartTiers.pool(tier)) {
            if (shownOres.size() >= ORE_COLS * ORE_ROWS || shownOres.contains(g.state)) {
                continue;
            }
            int i = shownOres.size();
            shownOres.add(g.state);
            int ox = ORE_X + (i % ORE_COLS) * 18, oy = ORE_Y + (i / ORE_COLS) * 18;
            itemRender.renderItemAndEffectIntoGUI(stackOf(g.state), ox, oy);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        for (int i = 0; i < shownOres.size(); i++) {
            if (!heart.isEnabled(shownOres.get(i))) {
                int ox = ORE_X + (i % ORE_COLS) * 18, oy = ORE_Y + (i / ORE_COLS) * 18;
                drawRect(ox, oy, ox + 16, oy + 16, 0xC0100010);
                fontRenderer.drawString("x", ox + 5, oy + 4, 0xFF5050);
            }
        }
        GlStateManager.enableDepth();

        // Пробуждение
        shownCosts.clear();
        boolean ok = true;
        if (tier >= HeartTiers.maxTier()) {
            fontRenderer.drawString(I18n.format("chaosshard.heart.gui.max"), 8, COST_Y + 4, TEXT);
            ok = false;
        } else {
            int target = tier + 1;
            int need = HeartTiers.threadFor(target);
            int have = ThreadManager.levelFor(ClientThreadCache.points());
            boolean threadOk = have >= need || mc.player.isCreative();
            ok = threadOk;
            fontRenderer.drawString(I18n.format("chaosshard.heart.gui.thread", need), 112, COST_Y - 12, threadOk ? 0x80FF80 : 0xFF6060);
            int cx = 8;
            RenderHelper.enableGUIStandardItemLighting();
            for (HeartTiers.Cost c : HeartTiers.costFor(target)) {
                shownCosts.add(c);
                ItemStack icon = iconOf(c);
                itemRender.renderItemAndEffectIntoGUI(icon, cx, COST_Y);
                cx += 25;
            }
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            cx = 8;
            for (HeartTiers.Cost c : shownCosts) {
                int count = HeartTiers.count(mc.player, c);
                boolean enough = count >= c.count || mc.player.isCreative();
                ok &= enough;
                String txt = Math.min(count, 999) + "/" + c.count;
                GlStateManager.pushMatrix();
                GlStateManager.translate(cx + 1, COST_Y + 17, 0);
                GlStateManager.scale(0.5, 0.5, 1);
                fontRenderer.drawString(txt, 0, 0, enough ? 0x80FF80 : 0xFF6060);
                GlStateManager.popMatrix();
                cx += 25;
            }
            GlStateManager.enableDepth();
        }
        upgradeButton.enabled = ok;
        upgradeButton.visible = tier < HeartTiers.maxTier();
        fontRenderer.drawString(I18n.format("container.inventory"), 8, ContainerHeart.INV_Y - 10, DIM);
    }

    private void drawOwnTooltips(int mouseX, int mouseY) {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        for (int i = 0; i < shownOres.size(); i++) {
            int ox = ORE_X + (i % ORE_COLS) * 18, oy = ORE_Y + (i / ORE_COLS) * 18;
            if (mx >= ox && mx < ox + 16 && my >= oy && my < oy + 16) {
                IBlockState s = shownOres.get(i);
                List<String> tip = new ArrayList<>();
                tip.add(stackOf(s).getDisplayName());
                tip.add(I18n.format(heart.isEnabled(s) ? "chaosshard.heart.gui.on" : "chaosshard.heart.gui.off"));
                drawHoveringText(tip, mouseX, mouseY);
                return;
            }
        }
        int cx = 8;
        for (HeartTiers.Cost c : shownCosts) {
            if (mx >= cx && mx < cx + 16 && my >= COST_Y && my < COST_Y + 16) {
                drawHoveringText(c.displayName() + " ×" + c.count, mouseX, mouseY);
                return;
            }
            cx += 25;
        }
        for (int i = 0; i < TileHeart.MODULE_SLOTS; i++) {
            int sx = 151, sy = 19 + i * 20;
            if (i >= tier() && mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                drawHoveringText(I18n.format("chaosshard.heart.gui.slot_locked", i + 1), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        for (int i = 0; i < shownOres.size(); i++) {
            int ox = ORE_X + (i % ORE_COLS) * 18, oy = ORE_Y + (i / ORE_COLS) * 18;
            if (mx >= ox && mx < ox + 16 && my >= oy && my < oy + 16) {
                ChaosNetwork.CHANNEL.sendToServer(new PacketHeartAction(heart.getPos(), PacketHeartAction.TOGGLE,
                        TileHeart.key(shownOres.get(i))));
                mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.4F, 1.4F);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == upgradeButton) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketHeartAction(heart.getPos(), PacketHeartAction.UPGRADE, ""));
        }
    }

    private static ItemStack stackOf(IBlockState state) {
        Item item = Item.getItemFromBlock(state.getBlock());
        return new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
    }

    private static ItemStack iconOf(HeartTiers.Cost c) {
        if (c.ore != null) {
            List<ItemStack> ores = OreDictionary.getOres(c.ore);
            if (!ores.isEmpty()) {
                ItemStack s = ores.get(0).copy();
                if (s.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    s.setItemDamage(0);
                }
                return s;
            }
            return ItemStack.EMPTY;
        }
        return c.stack;
    }
}
