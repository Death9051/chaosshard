package ru.death.chaosshard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.spell.SpellCaster;

/**
 * Шкала энергии Хаоса — вертикальная полоска справа от хотбара.
 * Видна, когда Гримуар в руке в режиме Магии или энергия ещё не восстановилась.
 */
public class EnergyHud {

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !ClientThreadCache.energyKnown() || mc.player.isSpectator()) {
            return;
        }
        int e = ClientThreadCache.energy(), m = ClientThreadCache.maxEnergy();
        ItemStack g = MagicKeys.heldGrimoire(mc.player);
        boolean magic = !g.isEmpty() && SpellCaster.magicMode(g);
        float heat = ru.death.chaosshard.spell.SpellHeat.heat(mc.player);
        if (!magic && e >= m && heat <= 0) {
            return;
        }
        ScaledResolution sr = event.getResolution();
        int x = sr.getScaledWidth() / 2 + 96, y = sr.getScaledHeight() - 22, h = 20;
        Gui.drawRect(x - 1, y - 1, x + 5, y + h + 1, 0xFFC9C3D6);
        Gui.drawRect(x, y, x + 4, y + h, 0xFF1A0A2A);
        int fill = h * Math.min(e, m) / Math.max(1, m);
        Gui.drawRect(x, y + h - fill, x + 4, y + h, 0xFF9B4DFF);
        Gui.drawRect(x, y + h - fill, x + 1, y + h, 0xFFD9B8FF);
        mc.fontRenderer.drawStringWithShadow(String.valueOf(e), x + 14, y + 6, 0xC9A0FF);
        // разогрев: тонкая полоска рядом; на максимуме мигает
        if (heat > 0 || magic) {
            int hx = x + 7;
            Gui.drawRect(hx - 1, y - 1, hx + 3, y + h + 1, 0xFF6E5A88);
            Gui.drawRect(hx, y, hx + 2, y + h, 0xFF1A0A2A);
            int hf = (int) (h * heat / ru.death.chaosshard.spell.SpellHeat.MAX);
            boolean surge = heat >= ru.death.chaosshard.spell.SpellHeat.MAX;
            int col = surge ? (System.currentTimeMillis() / 250 % 2 == 0 ? 0xFFFFE090 : 0xFFFF7040)
                    : ru.death.chaosshard.spell.SpellHeat.locked(mc.player) > 0 ? 0xFF8A7A9A : 0xFFFF9A30;
            Gui.drawRect(hx, y + h - hf, hx + 2, y + h, col);
        }
    }
}
