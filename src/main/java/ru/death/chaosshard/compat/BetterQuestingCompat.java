package ru.death.chaosshard.compat;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.client.gui.themes.gui_args.GArgsNone;
import betterquesting.api2.client.gui.themes.presets.PresetGUIs;
import betterquesting.client.gui2.GuiHome;
import betterquesting.client.themes.ThemeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Открытие книги квестов Better Questing — так же, как это делает клавиша самого BQ.
 * Класс загружается только если Better Questing установлен.
 */
@SideOnly(Side.CLIENT)
public final class BetterQuestingCompat {

    public static void openQuestBook() {
        Minecraft mc = Minecraft.getMinecraft();
        if (BQ_Settings.useBookmark && GuiHome.bookmark != null) {
            mc.displayGuiScreen(GuiHome.bookmark);
        } else {
            mc.displayGuiScreen(ThemeRegistry.INSTANCE.getGui(PresetGUIs.HOME, GArgsNone.NONE));
        }
    }

    private BetterQuestingCompat() {
    }
}
