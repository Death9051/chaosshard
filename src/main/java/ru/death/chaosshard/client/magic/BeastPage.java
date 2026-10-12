package ru.death.chaosshard.client.magic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import ru.death.chaosshard.client.ClientBeasts;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketBeastAction;
import ru.death.chaosshard.servant.BeastData;
import ru.death.chaosshard.servant.BeastData.Beast;
import ru.death.chaosshard.servant.EntityChaosBeast;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Вкладка «Слуги» окна магии: отголоски старых знакомств — Заяц, Кошка, Тигр. */
class BeastPage {

    private static final int GOLD = 0xFFD27A, OPEN = 0xC9C3D6, DIM = 0x5E566C, ACCENT = 0xD9B8FF, SOFT = 0x9A8FB0;

    private final GuiGrimoireMagic gui;
    private final Map<Beast, EntityChaosBeast> models = new EnumMap<>(Beast.class);
    /** Что подсказать при наведении (заполняется во время отрисовки). */
    private List<String> hoverLines;
    /** Ячейки перков на экране: x, y, w, h, индекс. */
    private final List<int[]> perkCells = new ArrayList<>();

    BeastPage(GuiGrimoireMagic gui) {
        this.gui = gui;
    }

    static ItemStack icon(Beast b) {
        switch (b) {
            case HARE: return new ItemStack(Items.RABBIT_FOOT);
            case CAT: return new ItemStack(Items.FISH);
            default: return new ItemStack(Items.BLAZE_ROD);
        }
    }

    void draw(int sel, int mx, int my, GuiButton reset) {
        hoverLines = null;
        perkCells.clear();
        int left = gui.left(), top = gui.top(), row = gui.row(), listW = gui.listW();
        // список
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = -1; i < 3; i++) {
            int y = top + 8 + (i + 1) * row;
            if (i == sel) {
                Gui.drawRect(left + 5, y - 1, left + listW, y + row - 2, 0x804A2A70);
            }
            ItemStack ic = i < 0 ? new ItemStack(ModItems.GRIMOIRE) : icon(Beast.values()[i]);
            gui.items().renderItemAndEffectIntoGUI(ic, left + 8, y);
            String name;
            int col;
            if (i < 0) {
                name = tr("chaosshard.beast.intro");
                col = OPEN;
            } else {
                Beast b = Beast.values()[i];
                boolean known = ClientBeasts.get(b).getBoolean("known");
                name = known ? tr("chaosshard.beast." + b.key()) + " §7" + ClientBeasts.level(b) : tr("chaosshard.beast.unknown");
                col = known ? (ClientBeasts.level(b) >= 5 ? GOLD : OPEN) : DIM;
            }
            gui.font().drawString(gui.font().trimStringToWidth(name, listW - 32), left + 28, y + 4, col);
        }
        RenderHelper.disableStandardItemLighting();

        int x = left + listW + 12, y = top + 10, w = gui.pageW() - listW - 22;
        if (sel < 0) {
            gui.text("§l" + tr("chaosshard.beast.intro"), x, y, ACCENT);
            y += 14;
            gui.paragraph(tr("chaosshard.beast.intro.text"), x, y, w, OPEN);
            return;
        }
        Beast b = Beast.values()[sel];
        NBTTagCompound t = ClientBeasts.get(b);
        String title = tr("chaosshard.beast." + b.key());
        if (!t.getBoolean("known")) {
            gui.text("§l" + title, x, y, SOFT);
            y += 14;
            y = gui.paragraph(tr("chaosshard.beast." + b.key() + ".lore"), x, y, w, 0x8A8098) + 6;
            gui.paragraph(tr("chaosshard.beast.unknown.text", tr("chaosshard.ritual.servant_" + b.key())), x, y, w, OPEN);
            return;
        }
        int lvl = ClientBeasts.level(b);
        gui.text("§l" + title, x, y, lvl >= 5 ? GOLD : ACCENT);
        String ls = tr("chaosshard.beast.gui.level", lvl);
        gui.text(ls, x + w - gui.font().getStringWidth(ls), y, lvl >= 5 ? GOLD : ACCENT);
        y += 13;

        // модель: смотрит за курсором
        int bx = x, by = y, bw = 64, bh = 70;
        Gui.drawRect(bx, by, bx + bw, by + bh, 0x60180C28);
        Gui.drawRect(bx, by + bh - 1, bx + bw, by + bh, 0xFF4A3A60);
        EntityChaosBeast m = model(b, lvl);
        if (m != null) {
            int scale = b == Beast.TIGER ? 17 : b == Beast.CAT ? 30 : 32;
            int fx = bx + bw / 2, fy = by + bh - 8;
            GlStateManager.color(1, 1, 1, 1);
            GuiInventory.drawEntityOnScreen(fx, fy, scale, fx - mx, fy - 30 - my, m);
        }
        if (mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
            hoverLines = split(tr("chaosshard.beast." + b.key() + ".lore"));
        }

        // опыт, состояние, очки
        int sx = bx + bw + 8, sw = w - bw - 8, sy = by;
        int xp = t.getInteger("xp");
        String xpText;
        float frac;
        if (lvl >= 5) {
            xpText = tr("chaosshard.beast.gui.ascended");
            frac = 1;
        } else if (lvl == 4) {
            xpText = xp >= BeastData.XP[4] ? tr("chaosshard.beast.gui.ready") : tr("chaosshard.beast.gui.xp", xp, BeastData.XP[4]);
            frac = Math.min(1F, (xp - BeastData.XP[3]) / (float) (BeastData.XP[4] - BeastData.XP[3]));
        } else {
            xpText = tr("chaosshard.beast.gui.xp", xp, BeastData.XP[lvl]);
            frac = (xp - BeastData.XP[lvl - 1]) / (float) (BeastData.XP[lvl] - BeastData.XP[lvl - 1]);
        }
        gui.text(xpText, sx, sy, 0xB89CFF);
        Gui.drawRect(sx, sy + 10, sx + sw, sy + 13, 0xAA1A0A2A);
        Gui.drawRect(sx, sy + 10, sx + (int) (sw * Math.max(0, frac)), sy + 13, lvl >= 5 ? 0xFFFFD27A : 0xFFB070FF);
        if (mx >= sx && mx < sx + sw && my >= sy && my < sy + 14) {
            hoverLines = split(tr("chaosshard.beast." + b.key() + ".xp"));
        }
        sy += 18;
        gui.text(status(t), sx, sy, 0xB0A8C0);
        sy += 11;
        int points = ClientBeasts.points(b);
        gui.text(tr("chaosshard.beast.gui.points", points), sx, sy, points > 0 ? 0x9AD08A : SOFT);
        sy += 11;
        int spent = Integer.bitCount(t.getInteger("perks"));
        gui.text(tr("chaosshard.beast.gui.cost", 2 * (spent + 1)), sx, sy, SOFT);
        sy += 12;
        if (spent > 0) {
            reset.visible = true;
            reset.x = sx;
            reset.y = sy;
            reset.width = Math.min(sw, 110);
            if (mx >= reset.x && mx < reset.x + reset.width && my >= reset.y && my < reset.y + 20) {
                hoverLines = split(tr("chaosshard.beast.gui.reset.desc"));
            }
        }

        // уровни
        y = by + bh + 5;
        gui.text(tr("chaosshard.beast.gui.levels"), x, y, SOFT);
        y += 11;
        for (int l = 1; l <= 5; l++) {
            String k = "chaosshard.beast." + b.key() + ".lvl." + l;
            int c = l <= lvl ? (l == 5 ? GOLD : OPEN) : DIM;
            gui.text(gui.font().trimStringToWidth(l + " · " + tr(k), w), x, y, c);
            if (mx >= x && mx < x + w && my >= y - 1 && my < y + 9) {
                hoverLines = split((l <= lvl ? "§d" : "§8") + tr(k) + "\n§7" + tr(k + ".desc"));
            }
            y += 10;
        }

        // перки: две ветки по четыре
        y += 4;
        int colW = (w - 6) / 2;
        for (int br = 0; br < 2; br++) {
            gui.text(tr("chaosshard.beast." + b.key() + ".branch." + br), x + br * (colW + 6), y, SOFT);
        }
        y += 11;
        for (int i = 0; i < BeastData.PERKS; i++) {
            int col = i / 4, r = i % 4;
            int cx = x + col * (colW + 6), cy = y + r * 13;
            boolean owned = ClientBeasts.has(b, i);
            boolean can = !owned && points > 0;
            boolean over = mx >= cx && mx < cx + colW && my >= cy - 1 && my < cy + 11;
            if (over) {
                Gui.drawRect(cx - 1, cy - 2, cx + colW, cy + 11, 0x504A2A70);
            }
            int box = owned ? 0xFFFFD27A : can ? 0xFFB070FF : 0xFF3A3048;
            Gui.drawRect(cx, cy + 1, cx + 7, cy + 8, box);
            if (!owned) {
                Gui.drawRect(cx + 1, cy + 2, cx + 6, cy + 7, 0xFF120A1C);
            }
            String pk = "chaosshard.beast." + b.key() + ".perk." + i;
            gui.text(gui.font().trimStringToWidth(tr(pk), colW - 11), cx + 10, cy, owned ? GOLD : can ? OPEN : 0x7A7088);
            perkCells.add(new int[]{cx, cy - 1, colW, 12, i});
            if (over) {
                String foot = owned ? tr("chaosshard.beast.gui.owned")
                        : can ? tr("chaosshard.beast.gui.buy", 2 * (spent + 1)) : tr("chaosshard.beast.gui.no_points");
                hoverLines = split((owned ? "§6" : "§d") + tr(pk) + "\n§7" + tr(pk + ".desc") + "\n" + foot);
            }
        }
    }

    void click(Beast b, int mx, int my) {
        if (!ClientBeasts.get(b).getBoolean("known")) {
            return;
        }
        for (int[] c : perkCells) {
            if (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]) {
                if (!ClientBeasts.has(b, c[4]) && ClientBeasts.points(b) > 0) {
                    ChaosNetwork.CHANNEL.sendToServer(new PacketBeastAction(b, 0, c[4]));
                    Minecraft.getMinecraft().player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.2F);
                }
                return;
            }
        }
    }

    void tooltip(int mx, int my) {
        if (hoverLines != null) {
            gui.hover(hoverLines, mx, my);
        }
    }

    private static String tr(String key, Object... args) {
        return I18n.format(key, args).replace("\\n", "\n");
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\n")) {
            out.addAll(gui.font().listFormattedStringToWidth(part, 200));
        }
        return out;
    }

    private static String status(NBTTagCompound t) {
        long ret = t.getLong("ret");
        if (ret > 0) {
            long left = Math.max(0, ret - Minecraft.getMinecraft().world.getTotalWorldTime()) / 20;
            return tr("chaosshard.beast.gui.thread", left / 60, String.format("%02d", left % 60));
        }
        return tr(t.getBoolean("out") ? "chaosshard.beast.gui.out" : "chaosshard.beast.gui.away");
    }

    private EntityChaosBeast model(Beast b, int lvl) {
        EntityChaosBeast m = models.get(b);
        if (m == null || m.world != Minecraft.getMinecraft().world) {
            switch (b) {
                case HARE: m = new EntityChaosBeast.Hare(Minecraft.getMinecraft().world); break;
                case CAT: m = new EntityChaosBeast.Cat(Minecraft.getMinecraft().world); break;
                default: m = new EntityChaosBeast.Tiger(Minecraft.getMinecraft().world); break;
            }
            models.put(b, m);
        }
        m.setLevel(lvl);
        return m;
    }
}
