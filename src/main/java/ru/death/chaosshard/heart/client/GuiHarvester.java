package ru.death.chaosshard.heart.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import ru.death.chaosshard.heart.ContainerHarvester;

/** Окно Жнеца на ванильной текстуре сундука (2 ряда). */
public class GuiHarvester extends GuiContainer {

    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");

    public GuiHarvester(ContainerHarvester container) {
        super(container);
        xSize = 176;
        ySize = 150;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(0.85F, 0.7F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(CHEST);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, 2 * 18 + 17);
        drawTexturedModalRect(guiLeft, guiTop + 2 * 18 + 17, 0, 126, xSize, 96);
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("tile.chaosshard.heart_harvester.name"), 8, 6, 0x404040);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }
}
