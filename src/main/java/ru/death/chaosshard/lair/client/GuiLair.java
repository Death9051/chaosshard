package ru.death.chaosshard.lair.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.client.ChaosUi;
import ru.death.chaosshard.lair.ContainerLair;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketLairAction;

import java.util.ArrayList;
import java.util.List;

import static ru.death.chaosshard.client.ChaosUi.*;

/**
 * Окно Сердца Логова: ступени роста (Ложе → Башня → Цитадель), что логово даёт сейчас, что нужно для роста
 * (земля, Нить, плата — с отметками «есть/нет») и две кнопки: «Возрождаться здесь» и «Вырастить».
 * У недоступной кнопки причина видна в строках требований и в подсказке.
 */
public class GuiLair extends GuiContainer {

    private static final ResourceLocation BG = new ResourceLocation(Tags.MOD_ID, "textures/gui/anchor_gui.png");
    /** Что прислал сервер (ContainerLair → PacketLairView). */
    public static NBTTagCompound view = new NBTTagCompound();

    private static final int NODE_Y = 22, NOW_Y = 72, NOW_H = 60, GROW_Y = 152, GROW_H = 50, BTN_Y = 210, BTN_W = 92;
    private static final int[] NODE_X = {35, 96, 157};

    private List<String> hover;

    public GuiLair(ContainerLair c) {
        super(c);
        xSize = 214;
        ySize = 236;
        view = new NBTTagCompound();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        hover = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hover != null) {
            drawHoveringText(hover, mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(BG);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    private static String tr(String k, Object... a) {
        return I18n.format(k, a);
    }

    private static ItemStack levelIcon(int lv) {
        return lv == 1 ? new ItemStack(Items.BED, 1, 10) : lv == 2 ? new ItemStack(Blocks.PURPUR_PILLAR) : new ItemStack(Items.NETHER_STAR);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        String title = tr("tile.chaosshard.lair_core.name");
        fontRenderer.drawString(title, (xSize - fontRenderer.getStringWidth(title)) / 2, 7, TEXT);
        if (!view.hasKey("level")) {
            String w = tr("chaosshard.lair.gui.wait");
            fontRenderer.drawString(w, (xSize - fontRenderer.getStringWidth(w)) / 2, 100, DIM);
            return;
        }
        int lv = view.getInteger("level");
        drawSteps(lv, mx, my);
        drawNow(lv);
        drawGrow(lv, mx, my);
        drawButtons(lv, mx, my);
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Ступени роста с линиями между ними. */
    private void drawSteps(int lv, int mx, int my) {
        for (int i = 0; i < 2; i++) {
            int x0 = NODE_X[i] + 22, x1 = NODE_X[i + 1];
            drawRect(x0, NODE_Y + 10, x1, NODE_Y + 12, i + 2 <= lv ? 0xFFB070FF : 0xFF3A3150);
        }
        for (int i = 0; i < 3; i++) {
            int n = i + 1, x = NODE_X[i];
            int border = n == lv ? 0xFFECB064 : n < lv ? 0xFFB070FF : 0xFF3A3150;
            drawRect(x, NODE_Y, x + 22, NODE_Y + 22, border);
            well(x + 1, NODE_Y + 1, 20, 20);
            GlStateManager.color(1, 1, 1, 1);
            item(levelIcon(n), x + 3, NODE_Y + 3);
            if (n > lv) {
                drawRect(x + 1, NODE_Y + 1, x + 21, NODE_Y + 21, 0xA0100C18);
            }
            String name = tr("chaosshard.lair.gui.step_" + n);
            int c = n == lv ? GOLD : n < lv ? ACCENT : OFF;
            fontRenderer.drawString(name, x + 11 - fontRenderer.getStringWidth(name) / 2, NODE_Y + 26, c);
            if (in(mx, my, x, NODE_Y, 22, 22)) {
                hover = new ArrayList<>();
                hover.add(name + (n == lv ? " §7" + tr("chaosshard.lair.gui.current") : ""));
                hover.add("§7" + tr("chaosshard.lair.gui.step_" + n + ".desc"));
            }
        }
    }

    private void line(int icon, String text, int y, int color, float alpha) {
        icon(16, y - 1, icon, alpha);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(text, 172), 28, y, color);
    }

    /** Что логово даёт сейчас. */
    private void drawNow(int lv) {
        fontRenderer.drawString(tr("chaosshard.lair.gui.now"), 14, NOW_Y - 11, SOFT);
        well(12, NOW_Y, 190, NOW_H);
        int y = NOW_Y + 4;
        line(I_LAND, tr("chaosshard.lair.gui.radius", view.getInteger("radius")), y, TEXT, 1F);
        y += 11;
        int power = view.getInteger("power"), base = view.getInteger("base");
        String pw = tr("chaosshard.lair.gui.power", power);
        if (power > base && base > 0) {
            pw += " §7" + tr("chaosshard.lair.gui.power_thread", Math.round(100.0 * (power - base) / base));
        }
        line(I_BOLT, pw, y, TEXT, 1F);
        y += 11;
        line(I_DROP, tr(lv >= 3 ? "chaosshard.lair.gui.rest_2" : "chaosshard.lair.gui.rest_1"), y, TEXT, 1F);
        y += 11;
        boolean cit = lv >= 3;
        line(cit ? I_SHIELD : I_LOCK, tr(cit ? "chaosshard.lair.gui.citadel_guard" : "chaosshard.lair.gui.citadel_guard_locked"), y,
                cit ? TEXT : OFF, cit ? 1F : 0.6F);
        y += 11;
        line(cit ? I_STAR : I_LOCK, tr(cit ? "chaosshard.lair.gui.citadel_mend" : "chaosshard.lair.gui.citadel_mend_locked"), y,
                cit ? TEXT : OFF, cit ? 1F : 0.6F);
    }

    /** Что нужно для следующей ступени. */
    private void drawGrow(int lv, int mx, int my) {
        if (!view.hasKey("next")) {
            fontRenderer.drawString(tr("chaosshard.lair.gui.grow_max_title"), 14, GROW_Y - 11, SOFT);
            well(12, GROW_Y, 190, GROW_H);
            empty(fontRenderer, null, null, tr("chaosshard.lair.max"), 107, GROW_Y + 18, 170);
            return;
        }
        int next = view.getInteger("next");
        fontRenderer.drawString(tr("chaosshard.lair.gui.grow", tr("chaosshard.lair.gui.step_" + next)), 14, GROW_Y - 11, SOFT);
        well(12, GROW_Y, 190, GROW_H);
        int y = GROW_Y + 4;
        boolean land = next < 3 ? view.getBoolean("land") : view.getBoolean("pillar");
        String landText = next < 3 ? tr("chaosshard.lair.gui.need_land") : tr("chaosshard.lair.gui.need_pillar", view.getInteger("pillarLevel"));
        line(land ? I_CHECK : I_LOCK, landText, y, land ? GOOD : BAD, 1F);
        y += 11;
        int nit = view.getInteger("nit"), need = view.getInteger("nitNeed");
        boolean nitOk = nit >= need || view.getBoolean("creative");
        line(nitOk ? I_CHECK : I_LOCK, tr("chaosshard.lair.gui.need_thread", need, nit), y, nitOk ? GOOD : BAD, 1F);
        y += 13;
        NBTTagList cost = view.getTagList("cost", 10);
        for (int i = 0; i < cost.tagCount(); i++) {
            NBTTagCompound t = cost.getCompoundTagAt(i);
            ItemStack st = new ItemStack(t);
            int x = 18 + i * 62;
            item(st, x, y);
            int have = t.getInteger("have"), n = t.getInteger("need");
            boolean ok = have >= n || view.getBoolean("creative");
            String c = Math.min(have, 999) + "/" + n;
            fontRenderer.drawString(c, x + 19, y + 4, ok ? GOOD : BAD);
            if (in(mx, my, x, y, 16, 16)) {
                hover = new ArrayList<>();
                hover.add(st.getDisplayName());
                hover.add("§7" + tr("chaosshard.lair.gui.have", have, n));
            }
        }
    }

    /** Первая причина, по которой расти нельзя (null — можно). */
    private String blocker() {
        if (!view.hasKey("next")) {
            return tr("chaosshard.lair.max");
        }
        int next = view.getInteger("next");
        if (!view.getBoolean("land")) {
            return tr("chaosshard.lair.need_land");
        }
        if (next >= 3 && !view.getBoolean("pillar")) {
            return tr("chaosshard.lair.need_pillar");
        }
        if (view.getBoolean("creative")) {
            return null;
        }
        if (view.getInteger("nit") < view.getInteger("nitNeed")) {
            return tr("chaosshard.lair.thin", view.getInteger("nitNeed"));
        }
        NBTTagList cost = view.getTagList("cost", 10);
        for (int i = 0; i < cost.tagCount(); i++) {
            if (cost.getCompoundTagAt(i).getInteger("have") < cost.getCompoundTagAt(i).getInteger("need")) {
                return tr("chaosshard.lair.cost_" + next);
            }
        }
        return null;
    }

    private void drawButtons(int lv, int mx, int my) {
        boolean spawn = view.getBoolean("spawn");
        boolean overS = in(mx, my, 12, BTN_Y, BTN_W, 16);
        button(fontRenderer, 12, BTN_Y, BTN_W, tr(spawn ? "chaosshard.lair.gui.spawn_here" : "chaosshard.lair.gui.spawn"),
                spawn ? 2 : overS ? 1 : 0, GOOD);
        if (spawn) {
            icon(14, BTN_Y + 4, I_CHECK);
        }
        if (overS) {
            hover = new ArrayList<>();
            hover.add(tr(spawn ? "chaosshard.lair.gui.spawn_here.desc" : "chaosshard.lair.gui.spawn.desc"));
        }
        String why = blocker();
        boolean overG = in(mx, my, 110, BTN_Y, BTN_W, 16);
        String label = view.hasKey("next") ? tr("chaosshard.lair.gui.grow_btn", tr("chaosshard.lair.gui.step_" + view.getInteger("next")))
                : tr("chaosshard.lair.gui.grow_done");
        button(fontRenderer, 110, BTN_Y, BTN_W, label, why != null ? 2 : overG ? 1 : 0, OFF);
        if (overG) {
            hover = new ArrayList<>();
            if (why != null) {
                hover.add("§c" + why);
            } else {
                hover.add(tr("chaosshard.lair.gui.grow_btn.desc"));
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || !view.hasKey("level")) {
            return;
        }
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        if (in(mx, my, 12, BTN_Y, BTN_W, 16) && !view.getBoolean("spawn")) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketLairAction(PacketLairAction.SPAWN));
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.5F, 1F);
        } else if (in(mx, my, 110, BTN_Y, BTN_W, 16) && blocker() == null) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketLairAction(PacketLairAction.GROW));
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.5F, 1F);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
