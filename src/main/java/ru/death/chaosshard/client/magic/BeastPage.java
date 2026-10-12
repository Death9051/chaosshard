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
import ru.death.chaosshard.servant.Servants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Вкладка «Слуги» окна магии: отголоски старых знакомств — Заяц, Кошка, Тигр. */
class BeastPage {

    /** Строки списка: «Отголоски», 3 зверя, заголовок «Свита», 6 типов слуг. */
    static final int ROWS = 11, HEADER_ROW = 4;
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

    void draw(int sel, int mx, int my, GuiButton reset, GuiButton call) {
        hoverLines = null;
        perkCells.clear();
        int left = gui.left(), top = gui.top(), listW = gui.listW();
        // список
        RenderHelper.enableGUIStandardItemLighting();
        for (int row = 0; row < ROWS; row++) {
            int i = row - 1;
            int y = top + 8 + row * gui.row();
            if (row == HEADER_ROW) {
                gui.font().drawString("§o" + tr("chaosshard.servant.gui.retinue"), left + 10, y + 4, SOFT);
                continue;
            }
            if (i == sel) {
                Gui.drawRect(left + 5, y - 1, left + listW, y + row() - 2, 0x804A2A70);
            }
            ItemStack ic;
            String name;
            int col;
            if (i < 0) {
                ic = new ItemStack(ModItems.GRIMOIRE);
                name = tr("chaosshard.beast.intro");
                col = OPEN;
            } else if (i < 3) {
                Beast b = Beast.values()[i];
                ic = icon(b);
                boolean known = ClientBeasts.get(b).getBoolean("known");
                String given = ClientBeasts.get(b).getString("name");
                name = known ? (given.isEmpty() ? tr("chaosshard.beast." + b.key()) : given) + " §7" + ClientBeasts.level(b) : tr("chaosshard.beast.unknown");
                col = known ? (ClientBeasts.level(b) >= 5 ? GOLD : OPEN) : DIM;
            } else {
                Servants.Kind k = Servants.Kind.COMMON[i - HEADER_ROW];
                ic = kindIcon(k);
                name = tr("chaosshard.servant.kind." + k.key());
                col = k.minThread <= MagicUi.threadLevel() ? OPEN : DIM;
            }
            gui.items().renderItemAndEffectIntoGUI(ic, left + 8, y);
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
        if (sel >= HEADER_ROW) {
            drawKind(Servants.Kind.COMMON[sel - HEADER_ROW], x, y, w, mx, my);
            return;
        }
        Beast b = Beast.values()[sel];
        NBTTagCompound t = ClientBeasts.get(b);
        String title = tr("chaosshard.beast." + b.key());
        if (!t.getBoolean("known")) {
            gui.text("§l" + title, x, y, SOFT);
            y += 14;
            y = gui.paragraph(tr("chaosshard.beast." + b.key() + ".lore"), x, y, w, 0x8A8098) + 6;
            y = gui.paragraph(tr("chaosshard.beast.unknown.text", tr("chaosshard.ritual.servant_" + b.key())), x, y, w, OPEN) + 4;
            ru.death.chaosshard.ritual.Ritual r = ru.death.chaosshard.ritual.Ritual.summonFor(b.kind);
            int ix = icons(r.offeringStacks(), x, y, mx, my);
            gui.text(tr("chaosshard.magic.need_thread", r.minThread), ix + 4, y + 4, r.minThread <= MagicUi.threadLevel() ? 0x9AD08A : 0xD07070);
            return;
        }
        int lvl = ClientBeasts.level(b);
        String given = t.getString("name");
        titleBox[0] = x; titleBox[1] = y - 2; titleBox[2] = w - 40; titleBox[3] = 12;
        if (editing == b && field != null) {
            field.x = x;
            field.y = y - 2;
            field.drawTextBox();
        } else {
            String shown = given.isEmpty() ? title : given;
            gui.text("§l" + shown, x, y, lvl >= 5 ? GOLD : ACCENT);
            if (!given.isEmpty()) {
                gui.text("§8" + title, x + gui.font().getStringWidth("§l" + shown) + 4, y, SOFT);
            }
            if (mx >= x && mx < x + w - 40 && my >= y - 2 && my < y + 10) {
                hoverLines = split(tr("chaosshard.beast.gui.rename"));
            }
        }
        String ls = tr("chaosshard.beast.gui.level", lvl);
        gui.text(ls, x + w - gui.font().getStringWidth(ls), y, lvl >= 5 ? GOLD : ACCENT);
        y += 13;

        // модель: крутится сама, зажатой кнопкой мыши — вручную
        int bx = x, by = y, bw = 64, bh = 70;
        modelBox[0] = bx; modelBox[1] = by; modelBox[2] = bw; modelBox[3] = bh;
        Gui.drawRect(bx, by, bx + bw, by + bh, 0x60180C28);
        Gui.drawRect(bx, by + bh - 1, bx + bw, by + bh, 0xFF4A3A60);
        EntityChaosBeast m = model(b, lvl);
        if (m != null) {
            int scale = b == Beast.TIGER ? 17 : b == Beast.CAT ? 30 : 32;
            int fx = bx + bw / 2, fy = by + bh - 8;
            GlStateManager.color(1, 1, 1, 1);
            if (!dragging) {
                yaw += 0.6F;
            }
            drawModel(fx, fy, scale, yaw, m);
        }
        if (!dragging && mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
            hoverLines = split(tr("chaosshard.beast." + b.key() + ".lore") + "\n§8" + tr("chaosshard.beast.gui.rotate"));
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
        int bxCur = sx;
        if (t.getLong("ret") == 0) {
            call.visible = true;
            call.x = sx;
            call.y = sy;
            call.width = gui.font().getStringWidth(call.displayString) + 14;
            bxCur = sx + call.width + 4;
            if (mx >= call.x && mx < call.x + call.width && my >= call.y && my < call.y + 20) {
                hoverLines = split(tr("chaosshard.beast.gui.call.desc"));
            }
        }
        if (spent > 0) {
            reset.visible = true;
            reset.x = bxCur;
            reset.y = sy;
            reset.width = Math.min(sx + sw - bxCur, 110);
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

    // ------------------------------------------------------------------ имя

    private final int[] titleBox = new int[4];
    private Beast editing;
    private net.minecraft.client.gui.GuiTextField field;

    boolean editing() {
        return editing != null;
    }

    /** Клик по имени — начать ввод. */
    boolean clickTitle(Beast b, int mx, int my) {
        if (!ClientBeasts.get(b).getBoolean("known")) {
            return false;
        }
        if (editing != null && field != null) {
            field.mouseClicked(mx, my, 0);
            if (mx >= field.x && mx < field.x + field.width && my >= field.y && my < field.y + field.height) {
                return true;
            }
            finishEdit(true);
            return false;
        }
        if (mx >= titleBox[0] && mx < titleBox[0] + titleBox[2] && my >= titleBox[1] && my < titleBox[1] + titleBox[3]) {
            editing = b;
            field = new net.minecraft.client.gui.GuiTextField(0, gui.font(), titleBox[0], titleBox[1], 120, 12);
            field.setMaxStringLength(ru.death.chaosshard.servant.Beasts.NAME_MAX);
            String given = ClientBeasts.get(b).getString("name");
            field.setText(given.isEmpty() ? tr("chaosshard.beast." + b.key()) : given);
            field.setFocused(true);
            field.setCursorPositionEnd();
            field.setSelectionPos(0);
            return true;
        }
        return false;
    }

    /** Клавиша во время ввода. true — съедена. */
    boolean key(char c, int code) {
        if (editing == null || field == null) {
            return false;
        }
        if (code == org.lwjgl.input.Keyboard.KEY_RETURN || code == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
            finishEdit(true);
        } else if (code == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            finishEdit(false);
        } else {
            field.textboxKeyTyped(c, code);
        }
        return true;
    }

    void finishEdit(boolean save) {
        if (editing != null && field != null && save) {
            String text = field.getText().trim();
            if (text.equals(tr("chaosshard.beast." + editing.key()))) {
                text = "";
            }
            ChaosNetwork.CHANNEL.sendToServer(new PacketBeastAction(editing, text));
        }
        editing = null;
        field = null;
    }

    void tick() {
        if (field != null) {
            field.updateCursorCounter();
        }
    }

    // ------------------------------------------------------------------ вращение модели

    private final int[] modelBox = new int[4];
    private static float yaw = 30F;
    private boolean dragging;
    private int dragX;

    /** Нажата кнопка мыши: начать вращение, если курсор на модели. true — нажатие забрано. */
    boolean press(int mx, int my) {
        if (mx >= modelBox[0] && mx < modelBox[0] + modelBox[2] && my >= modelBox[1] && my < modelBox[1] + modelBox[3]) {
            dragging = true;
            dragX = mx;
            return true;
        }
        return false;
    }

    void drag(int mx) {
        if (dragging) {
            yaw += (mx - dragX) * 2.5F;
            dragX = mx;
        }
    }

    void release() {
        dragging = false;
    }

    /** Как GuiInventory.drawEntityOnScreen, но с полным поворотом вокруг оси. */
    private static void drawModel(int x, int y, int scale, float yawDeg, net.minecraft.entity.EntityLivingBase e) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, 50.0F);
        GlStateManager.scale((float) -scale, (float) scale, (float) scale);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-12.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(yawDeg, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        float by = e.renderYawOffset, ry = e.rotationYaw, rp = e.rotationPitch, ph = e.prevRotationYawHead, hy = e.rotationYawHead;
        e.renderYawOffset = 0;
        e.rotationYaw = 0;
        e.rotationPitch = 0;
        e.rotationYawHead = 0;
        e.prevRotationYawHead = 0;
        net.minecraft.client.renderer.entity.RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        rm.setPlayerViewY(180.0F);
        rm.setRenderShadow(false);
        rm.renderEntity(e, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
        rm.setRenderShadow(true);
        e.renderYawOffset = by;
        e.rotationYaw = ry;
        e.rotationPitch = rp;
        e.prevRotationYawHead = ph;
        e.rotationYawHead = hy;
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
    }

    private int row() {
        return gui.row();
    }

    static ItemStack kindIcon(Servants.Kind k) {
        switch (k) {
            case HUSK: return new ItemStack(Items.ROTTEN_FLESH);
            case BONE: return new ItemStack(Items.SKULL, 1, 1);
            case EXECUTIONER: return new ItemStack(Items.IRON_AXE);
            case REAPER: return new ItemStack(Items.IRON_HOE);
            case LUMBERJACK: return new ItemStack(Items.STONE_AXE);
            default: return new ItemStack(Items.IRON_PICKAXE);
        }
    }

    /** Страница типа обычного слуги: роль, призыв, сила от Нити, 5 улучшений. */
    private void drawKind(Servants.Kind k, int x, int y, int w, int mx, int my) {
        int thread = MagicUi.threadLevel();
        gui.text("§l" + tr("chaosshard.servant.kind." + k.key()), x, y, k.minThread <= thread ? ACCENT : SOFT);
        String role = tr(k.gatherer() ? "chaosshard.servant.gui.gatherer" : "chaosshard.servant.gui.fighter");
        gui.text(role, x + w - gui.font().getStringWidth(role), y, SOFT);
        y += 13;
        y = gui.paragraph(tr("chaosshard.servant.kind." + k.key() + ".desc"), x, y, w, OPEN) + 4;
        // призыв
        ru.death.chaosshard.ritual.Ritual r = ru.death.chaosshard.ritual.Ritual.summonFor(k);
        gui.text(tr("chaosshard.servant.gui.summon"), x, y + 4, SOFT);
        int ix = x + gui.font().getStringWidth(tr("chaosshard.servant.gui.summon")) + 4;
        ix = icons(r.offeringStacks(), ix, y, mx, my);
        gui.text(tr("chaosshard.magic.need_thread", r.minThread), ix + 4, y + 4, r.minThread <= thread ? 0x9AD08A : 0xD07070);
        y += 20;
        int tier = Math.min(5, thread);
        gui.text(tr("chaosshard.servant.gui.strength", thread, 15 * tier, tr("chaosshard.servant.gear." + tier)), x, y, 0xB89CFF);
        y += 11;
        gui.text(tr("chaosshard.servant.gui.limit", 2 + thread), x, y, 0xB0A8C0);
        y += 13;
        gui.text(tr("chaosshard.servant.gui.upgrades"), x, y, SOFT);
        y += 11;
        for (ru.death.chaosshard.servant.ServantUpgrade u : ru.death.chaosshard.servant.ServantUpgrade.of(k)) {
            boolean open = u.minThread <= thread;
            int iconsX = x + w - 3 * 18;
            gui.text(gui.font().trimStringToWidth(tr(u.key()), iconsX - x - 4), x, y + 4, open ? OPEN : DIM);
            if (mx >= x && mx < iconsX && my >= y && my < y + 17) {
                hoverLines = split("§d" + tr(u.key()) + "\n§7" + tr(u.key() + ".desc") + "\n"
                        + (open ? "§a" : "§c") + tr("chaosshard.magic.need_thread", u.minThread));
            }
            icons(u.offerings(), iconsX, y, mx, my);
            y += 18;
        }
    }

    /** Нарисовать стопки в ряд; наведённая — в подсказку. Возвращает x после последней. */
    private int icons(List<ItemStack> stacks, int x, int y, int mx, int my) {
        RenderHelper.enableGUIStandardItemLighting();
        for (ItemStack s : stacks) {
            gui.items().renderItemAndEffectIntoGUI(s, x, y);
            gui.items().renderItemOverlayIntoGUI(gui.font(), s, x, y, null);
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                hoverLines = s.getTooltip(Minecraft.getMinecraft().player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
            }
            x += 18;
        }
        RenderHelper.disableStandardItemLighting();
        return x;
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
