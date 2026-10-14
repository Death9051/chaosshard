package ru.death.chaosshard.act5.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/** Титры финала: строки медленно плывут вверх. Пробел — быстрее, Esc — закрыть. */
public class GuiCredits extends GuiScreen {

    private final List<String> lines = new ArrayList<>();
    private float scroll;
    private int ticks;

    @Override
    public void initGui() {
        if (lines.isEmpty()) {
            for (int i = 0; i < 200; i++) {
                String k = "chaosshard.credits." + i;
                if (!I18n.hasKey(k)) {
                    break;
                }
                lines.add(I18n.format(k));
            }
        }
    }

    @Override
    public void updateScreen() {
        ticks++;
        scroll += Keyboard.isKeyDown(Keyboard.KEY_SPACE) ? 3.0F : 0.5F;
        if (scroll > lines.size() * 14 + height + 40) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF08040C);
        float s = scroll + (Keyboard.isKeyDown(Keyboard.KEY_SPACE) ? 3.0F : 0.5F) * partialTicks;
        // сдвиг дробный, через матрицу: строки едут плавно, по настоящим пикселям экрана, а не рывками по пикселю интерфейса
        float y0 = height + 20 - s;
        int whole = (int) Math.floor(y0);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0, y0 - whole, 0);
        for (int i = 0; i < lines.size(); i++) {
            int y = whole + i * 14;
            if (y < -14 || y > height + 14) {
                continue;
            }
            String l = lines.get(i);
            int col = l.startsWith("§") ? 0xFFFFFF : 0xC8B8E0;
            drawCenteredString(fontRenderer, l, width / 2, y, col);
        }
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
