package ru.death.chaosshard.client.book;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/** Кнопка в книге: сургучная печать с подписью или строка чернилами с ромбиком. */
public class BookButton extends GuiButton {

    private final boolean seal;
    /** Вторая строка под подписью печати (мелко). */
    public String hint = "";

    public BookButton(int id, int x, int y, String label, boolean seal) {
        super(id, x, y, 10, seal ? 26 : 12, label);
        this.seal = seal;
        fit();
    }

    /** Ширина по подписи. */
    public void fit() {
        BookFont f = BookFont.body();
        width = seal ? 32 + (int) Math.max(f.width(displayString), f.width(hint) * 0.8F) + 4 : 10 + f.getStringWidth(displayString);
    }

    public void setLabel(String label) {
        displayString = label;
        fit();
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my, float partial) {
        if (!visible) {
            return;
        }
        hovered = mx >= x && my >= y && mx < x + width && my < y + height;
        BookFont f = BookFont.body();
        int col = !enabled ? BookArt.DIM : hovered ? 0xFFD89A : BookArt.ACCENT;
        if (seal) {
            BookArt.seal(x, y, !enabled ? 2 : hovered ? 1 : 0);
            if (hint.isEmpty()) {
                f.drawString(displayString, x + 32, y + 8, col, false);
            } else {
                f.drawString(displayString, x + 32, y + 3, col, false);
                f.drawScaled(hint, x + 32, y + 15, enabled ? BookArt.SOFT : BookArt.DIM, 0.8F);
            }
        } else {
            Gui.drawRect(x + 1, y + 4, x + 5, y + 8, col | 0xFF000000);
            f.drawString(displayString, x + 9, y + 1, col, false);
            if (hovered && enabled) {
                Gui.drawRect(x + 9, y + 11, x + width, y + 12, (col & 0xFFFFFF) | 0xA0000000);
            }
        }
    }
}
