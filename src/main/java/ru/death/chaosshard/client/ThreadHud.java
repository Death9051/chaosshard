package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.thread.ThreadManager;

/**
 * Шкала Нити в левом верхнем углу.
 * Видна, пока игрок приседает, и 5 секунд после любого изменения Нити.
 */
public class ThreadHud {

    private static final int SHOW_TICKS = 100;

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT || !ChaosConfig.thread.showHud) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || !ClientThreadCache.known() || mc.gameSettings.showDebugInfo) {
            return;
        }
        boolean recent = mc.world.getTotalWorldTime() - ClientThreadCache.changedAt() < SHOW_TICKS;
        if (!mc.player.isSneaking() && !recent) {
            return;
        }

        int points = ClientThreadCache.points();
        int level = ThreadManager.levelFor(points);
        int next = ThreadManager.nextThreshold(level);
        int prev = level == 0 ? 0 : ChaosConfig.thread.pointsPerLevel[level - 1];

        String title = next < 0
                ? I18n.format("chaosshard.hud.thread.max", roman(level))
                : I18n.format("chaosshard.hud.thread", roman(level), points, next);

        FontRenderer font = mc.fontRenderer;
        int x = 6, y = 6, width = 90;
        font.drawStringWithShadow(title, x, y, 0xC9A0FF);

        float fill = next < 0 ? 1.0F : (float) (points - prev) / Math.max(1, next - prev);
        int barY = y + font.FONT_HEIGHT + 2;
        Gui.drawRect(x, barY, x + width, barY + 3, 0xAA1A0A2A);
        Gui.drawRect(x, barY, x + Math.round(width * Math.min(1.0F, Math.max(0.0F, fill))), barY + 3, 0xFF9B4DFF);
    }

    private static String roman(int n) {
        String[] r = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
