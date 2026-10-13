package ru.death.chaosshard.structure.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import ru.death.chaosshard.structure.TileOrderBeacon;

/** Золотой луч светоча — тем же способом, что и луч маяка. */
public class OrderBeaconRenderer extends TileEntitySpecialRenderer<TileOrderBeacon> {

    private static final float[] COLOR = {1.0F, 0.92F, 0.55F};

    @Override
    public void render(TileOrderBeacon te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!te.lit || te.getWorld() == null) {
            return;
        }
        GlStateManager.alphaFunc(516, 0.1F);
        bindTexture(TileEntityBeaconRenderer.TEXTURE_BEACON_BEAM);
        GlStateManager.disableFog();
        TileEntityBeaconRenderer.renderBeamSegment(x, y, z, partialTicks, 1.0D, te.getWorld().getTotalWorldTime(), 1, 220, COLOR, 0.18D, 0.24D);
        GlStateManager.enableFog();
    }

    @Override
    public boolean isGlobalRenderer(TileOrderBeacon te) {
        return true;
    }
}
