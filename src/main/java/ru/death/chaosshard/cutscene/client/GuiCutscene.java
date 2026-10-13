package ru.death.chaosshard.cutscene.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.death.chaosshard.cutscene.Cutscene;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketCutsceneSkip;

import java.util.List;

/** Прозрачный экран на время катсцены: забирает ввод, рисует полосы и субтитры. Пробел — пропустить. */
@SideOnly(Side.CLIENT)
public class GuiCutscene extends GuiScreen {

    private int skipHold;

    @Override
    public void initGui() {
        Mouse.setGrabbed(true);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char c, int key) {
        // Esc не закрывает сцену; пропуск — Пробел
        if (key == Keyboard.KEY_SPACE && CutsceneClient.active()) {
            ChaosNetwork.CHANNEL.sendToServer(new PacketCutsceneSkip());
        }
    }

    @Override
    public void updateScreen() {
        if (!CutsceneClient.active()) {
            mc.displayGuiScreen(null);
            return;
        }
        skipHold++;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Cutscene s = CutsceneClient.scene;
        int barH = (int) (Math.max(38, height / 6) * Math.min(1F, CutsceneClient.bars + 0.1F));
        Gui.drawRect(0, 0, width, barH, 0xFF000000);
        Gui.drawRect(0, height - barH, width, height, 0xFF000000);
        if (s == null) {
            return;
        }
        double t = CutsceneClient.ticks + partialTicks;
        for (Cutscene.Line l : s.lines) {
            if (t < l.from || t > l.to) {
                continue;
            }
            float a = (float) Math.min(1.0, Math.min(t - l.from, l.to - t) / 6.0);
            int alpha = Math.max(8, (int) (255 * a)) << 24;
            String who = l.speaker.isEmpty() ? "" : I18n.format(l.speaker);
            List<String> rows = fontRenderer.listFormattedStringToWidth(I18n.format(l.text), Math.min(380, width - 40));
            int y = height - barH + 4;
            if (!who.isEmpty()) {
                drawCenteredString(fontRenderer, who, width / 2, y, alpha | (l.color & 0xFFFFFF));
                y += 11;
            }
            for (String r : rows) {
                drawCenteredString(fontRenderer, r, width / 2, y, alpha | 0xEEEEEE);
                y += 10;
            }
        }
        if (skipHold > 40) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.75F, 0.75F, 1F);
            String hint = I18n.format("chaosshard.cutscene.skip");
            fontRenderer.drawString(hint, (int) ((width - 6) / 0.75F) - fontRenderer.getStringWidth(hint), (int) (6 / 0.75F), 0x80FFFFFF);
            GlStateManager.popMatrix();
        }
    }
}
