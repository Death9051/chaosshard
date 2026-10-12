package ru.death.chaosshard.client.magic;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.spell.Spell;

/** Общее для окон магии: значки заклинаний, цвета, рисование кольцевых секторов. */
public final class MagicUi {

    public static final int SILVER = 0xFFC9C3D6;
    public static final int VIOLET = 0xFFB070FF;
    public static final int DIM = 0xFF6E6680;
    public static final int BG = 0xF0120C18;

    public static int threadLevel() {
        return ChaosShard.proxy.clientThreadLevel();
    }

    public static boolean unlocked(Spell s) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        return s.minThread <= threadLevel() || (mc.player != null && mc.player.isCreative());
    }

    /** Значок заклинания — предмет, который его напоминает. */
    public static ItemStack icon(Spell s) {
        switch (s) {
            case SPROUT: return new ItemStack(net.minecraft.init.Blocks.SAPLING);
            case BRIDGE: return new ItemStack(ModBlocks.CHAOS_STONE);
            case SPRING: return new ItemStack(net.minecraft.init.Items.WATER_BUCKET);
            case RIFT: return new ItemStack(net.minecraft.init.Items.IRON_PICKAXE);
            case STARSIGHT: return new ItemStack(net.minecraft.init.Items.ENDER_EYE);
            case RECALL: return new ItemStack(ModItems.BREACH_BOOK);
            case DASH: return new ItemStack(net.minecraft.init.Items.FEATHER);
            case BOLT: return new ItemStack(ModItems.VEIL_SHARD);
            case FORM: return new ItemStack(ModItems.CHAOS_ESSENCE);
            default: return new ItemStack(ModItems.GRIMOIRE);
        }
    }

    /** Кольцевой сектор (углы в радианах, 0 — вверх, по часовой). */
    public static void sector(double cx, double cy, double r0, double r1, double a0, double a1, int argb) {
        float a = (argb >>> 24) / 255F, r = ((argb >> 16) & 255) / 255F, g = ((argb >> 8) & 255) / 255F, b = (argb & 255) / 255F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int steps = Math.max(4, (int) ((a1 - a0) * 24));
        for (int i = 0; i <= steps; i++) {
            double ang = a0 + (a1 - a0) * i / steps;
            double sx = Math.sin(ang), sy = -Math.cos(ang);
            buf.pos(cx + sx * r1, cy + sy * r1, 0).color(r, g, b, a).endVertex();
            buf.pos(cx + sx * r0, cy + sy * r0, 0).color(r, g, b, a).endVertex();
        }
        t.draw();
        GlStateManager.enableTexture2D();
    }

    /** Рамка-окантовка «серебро» вокруг прямоугольника. */
    public static void frame(int x, int y, int w, int h) {
        net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + h, BG);
        net.minecraft.client.gui.Gui.drawRect(x, y, x + w, y + 1, SILVER);
        net.minecraft.client.gui.Gui.drawRect(x, y + h - 1, x + w, y + h, SILVER);
        net.minecraft.client.gui.Gui.drawRect(x, y, x + 1, y + h, SILVER);
        net.minecraft.client.gui.Gui.drawRect(x + w - 1, y, x + w, y + h, SILVER);
        net.minecraft.client.gui.Gui.drawRect(x + 2, y + 2, x + w - 2, y + 3, 0x664A3A60);
        net.minecraft.client.gui.Gui.drawRect(x + 2, y + h - 3, x + w - 2, y + h - 2, 0x664A3A60);
    }

    private MagicUi() {
    }
}
