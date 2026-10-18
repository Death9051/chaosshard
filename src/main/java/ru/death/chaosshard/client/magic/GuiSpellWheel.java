package ru.death.chaosshard.client.magic;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import ru.death.chaosshard.client.MagicKeys;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketSelectSpell;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellCaster;

import java.util.ArrayList;
import java.util.List;

/**
 * Колесо заклинаний: держишь V — колесо открыто, наводишь мышь на сектор, отпускаешь V (или щёлкаешь) — выбрано.
 * Закрытые страницы серые, с нужным уровнем Нити.
 */
public class GuiSpellWheel extends GuiScreen {

    private final ItemStack grimoire;
    private final List<Spell> spells = new ArrayList<>();
    private int hovered = -1;
    private int age;

    public GuiSpellWheel(ItemStack grimoire) {
        this.grimoire = grimoire;
        for (Spell s : Spell.values()) {
            if (s != Spell.BOOK) {
                spells.add(s);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        age++;
        int code = MagicKeys.WHEEL.getKeyCode();
        boolean down = code > 0 ? Keyboard.isKeyDown(code) : true;
        if (!down && age > 2) {
            choose();
        }
    }

    private void choose() {
        if (hovered >= 0) {
            Spell s = spells.get(hovered);
            if (MagicUi.unlocked(s)) {
                ChaosNetwork.CHANNEL.sendToServer(new PacketSelectSpell(s));
            }
        }
        mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1 && hovered >= 0) {
            // ПКМ по сектору — следующая форма заклинания
            Spell s = spells.get(hovered);
            if (MagicUi.unlocked(s)) {
                int next = ru.death.chaosshard.spell.SpellData.nextForm(mc.player, s);
                ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketSpellAction(
                        ru.death.chaosshard.network.PacketSpellAction.FORM, s, next));
            }
            return;
        }
        choose();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int cx = width / 2, cy = height / 2;
        double r0 = 34, r1 = 92;
        int n = spells.size();
        double dx = mouseX - cx, dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        hovered = -1;
        if (dist > 20) {
            double ang = Math.atan2(dx, -dy);
            if (ang < 0) {
                ang += Math.PI * 2;
            }
            hovered = (int) ((ang + Math.PI / n) / (Math.PI * 2 / n)) % n;
        }
        Spell current = SpellCaster.selected(grimoire);
        boolean magic = SpellCaster.magicMode(grimoire);
        double seg = Math.PI * 2 / n;
        for (int i = 0; i < n; i++) {
            Spell s = spells.get(i);
            double a0 = i * seg - seg / 2 + 0.03, a1 = i * seg + seg / 2 - 0.03;
            boolean open = MagicUi.unlocked(s);
            int col = !open ? 0xB0201A28 : i == hovered ? 0xE07A3FB8 : (magic && s == current) ? 0xD04A2A70 : 0xC0241830;
            MagicUi.sector(cx, cy, r0, r1 + (i == hovered ? 6 : 0), a0, a1, col);
            MagicUi.sector(cx, cy, r1 + (i == hovered ? 6 : 0), r1 + (i == hovered ? 8 : 2), a0, a1,
                    open ? 0xFFC9C3D6 : 0xFF4A4458);
        }
        MagicUi.sector(cx, cy, 0, r0 - 4, 0, Math.PI * 2, 0xD0100A16);
        MagicUi.sector(cx, cy, r0 - 4, r0 - 2, 0, Math.PI * 2, 0xFFC9C3D6);

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < n; i++) {
            Spell s = spells.get(i);
            double ang = i * seg;
            double r = (r0 + r1) / 2 + (i == hovered ? 3 : 0);
            int ix = (int) (cx + Math.sin(ang) * r) - 8, iy = (int) (cy - Math.cos(ang) * r) - 8;
            if (!MagicUi.unlocked(s)) {
                GlStateManager.color(0.4F, 0.4F, 0.4F);
            }
            itemRender.renderItemAndEffectIntoGUI(MagicUi.icon(s), ix, iy);
        }
        RenderHelper.disableStandardItemLighting();

        // Центр — имя наведённого или текущего заклинания
        Spell show = hovered >= 0 ? spells.get(hovered) : (magic ? current : null);
        if (show != null) {
            String name = I18n.format(show.key());
            boolean open = MagicUi.unlocked(show);
            drawCenteredString(fontRenderer, name, cx, cy - 14, open ? 0xD9B8FF : 0x7A7288);
            if (open) {
                drawCenteredString(fontRenderer, I18n.format(ru.death.chaosshard.spell.SpellData.variantKey(mc.player, show)), cx, cy - 4, 0xC890FF);
            }
            String sub = open ? I18n.format(show.key() + ".desc") : I18n.format("chaosshard.magic.need_thread", show.minThread);
            if (open) {
                boolean enough = ru.death.chaosshard.client.ClientThreadCache.energy() >= ru.death.chaosshard.spell.SpellCaster.cost(mc.player, show);
                drawCenteredString(fontRenderer, I18n.format("chaosshard.magic.cost", ru.death.chaosshard.spell.SpellCaster.cost(mc.player, show)), cx, cy + 7, enough ? 0xB89CFF : 0xD07070);
                if (ru.death.chaosshard.spell.SpellPerks.forms(show).length > 1) {
                    drawCenteredString(fontRenderer, I18n.format("chaosshard.magic.wheel_form"), cx, cy + (int) r1 + 4, 0x8A8098);
                }
            }
            List<String> lines = fontRenderer.listFormattedStringToWidth(sub, 220);
            int y = cy + (int) r1 + 16;
            for (String l : lines) {
                drawCenteredString(fontRenderer, l, cx, y, open ? 0xC9C3D6 : 0x8A8098);
                y += 10;
            }
        } else {
            drawCenteredString(fontRenderer, I18n.format("chaosshard.magic.wheel"), cx, cy - 4, 0xC9C3D6);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
