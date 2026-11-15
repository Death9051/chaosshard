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

    /** Строки списка: «Отголоски», 3 зверя, заголовок «Свита», «Моя свита», 6 типов слуг. */
    static final int ROWS = 12, HEADER_ROW = 4;
    /** Выбор «Моя свита» (индекс выбора = строка − 1). */
    static final int MANAGE = HEADER_ROW;
    private static final int GOLD = 0xFFD27A, OPEN = 0xC9C3D6, DIM = 0x7A6F8C, ACCENT = 0xD9B8FF, SOFT = 0x9A8FB0;

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
        int top = gui.top();
        // список
        for (int row = 0; row < ROWS; row++) {
            int i = row - 1;
            if (row == HEADER_ROW) {
                gui.drawHeader(row, tr("chaosshard.servant.gui.retinue").replace("\u2014", "").replace("-", "").trim());
                continue;
            }
            Object ic;
            String name;
            int col;
            if (i < 0) {
                ic = new ItemStack(ModItems.GRIMOIRE);
                name = tr("chaosshard.beast.intro");
                col = GOLD;
            } else if (i < 3) {
                Beast b = Beast.values()[i];
                int big = ru.death.chaosshard.client.ChaosUi.summonIcon(b.name());
                ic = big >= 0 ? (Object) Integer.valueOf(big) : icon(b);
                boolean known = ClientBeasts.get(b).getBoolean("known");
                String given = ClientBeasts.get(b).getString("name");
                name = known ? (given.isEmpty() ? tr("chaosshard.beast." + b.key()) : given) + " \u00A77" + ClientBeasts.level(b) : tr("chaosshard.beast.unknown");
                col = known ? (ClientBeasts.level(b) >= 5 ? GOLD : OPEN) : DIM;
            } else if (i == MANAGE) {
                Integer big = ru.death.chaosshard.client.ChaosUi.summonIcon(Servants.Kind.HUSK.name());
                ic = big >= 0 ? (Object) big : kindIcon(Servants.Kind.HUSK);
                name = tr("chaosshard.retinue.title");
                col = GOLD;
            } else {
                Servants.Kind k = Servants.Kind.COMMON[i - HEADER_ROW - 1];
                int big = ru.death.chaosshard.client.ChaosUi.summonIcon(k.name());
                ic = big >= 0 ? (Object) Integer.valueOf(big) : kindIcon(k);
                name = tr("chaosshard.servant.kind." + k.key());
                col = k.minThread <= MagicUi.threadLevel() ? OPEN : DIM;
            }
            gui.drawRow(row, ic, name, col, i == sel, mx, my);
        }

        int x = gui.pageX(), y = top + 15, w = gui.pageW();
        if (sel < 0) {
            y = gui.title(tr("chaosshard.beast.intro"), x, y, ACCENT);
            int ty = gui.paragraph(tr("chaosshard.beast.intro.text"), x, y, w, OPEN);
            gui.noteKey("chaosshard.note.beasts", x + 4, ty + 4, gui.pageBottom(), -2.5F);
            return;
        }
        if (sel == MANAGE) {
            drawRetinue(x, y, w, mx, my);
            return;
        }
        if (sel > HEADER_ROW) {
            drawKind(Servants.Kind.COMMON[sel - HEADER_ROW - 1], x, y, w, mx, my);
            return;
        }
        Beast b = Beast.values()[sel];
        NBTTagCompound t = ClientBeasts.get(b);
        String title = tr("chaosshard.beast." + b.key());
        if (!t.getBoolean("known")) {
            y = gui.title(title, x, y, SOFT);
            y = gui.paragraph(tr("chaosshard.beast." + b.key() + ".lore"), x, y, w, 0x8A8098) + 6;
            y = gui.paragraph(tr("chaosshard.beast.unknown.text", tr("chaosshard.ritual.servant_" + b.key())), x, y, w, OPEN) + 6;
            summon(ru.death.chaosshard.ritual.Ritual.summonFor(b.kind), x, y, w, mx, my);
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
        y += 15;

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

    /**
     * Страница типа обычного слуги блоками: описание, «Призыв» (подношения, нужная Нить, кнопка «Сложить в Чашу»),
     * «Сила от Нити» (сила и размер свиты), «Улучшения» (5 строк: название и подношения).
     */
    // ------------------------------------------------------------------ моя свита

    private static java.util.UUID chosen;
    private long asked;
    private final ru.death.chaosshard.client.book.BookButton callBtn = new ru.death.chaosshard.client.book.BookButton(0, 0, 0, "", false),
            stashBtn = new ru.death.chaosshard.client.book.BookButton(0, 0, 0, "", false),
            allBtn = new ru.death.chaosshard.client.book.BookButton(0, 0, 0, "", false);
    /** Строки свиты на экране: x, y, w, h и id. */
    private final List<Object[]> retinueRows = new ArrayList<>();
    private static final int R_FOLLOW = 0, R_GUARD = 1, R_CHEST = 2, R_STASHED = 3, R_FAR = 4;

    /** Попросить у сервера свежий список (не чаще раза в 2 секунды). */
    void askRetinue(boolean now) {
        long t = System.currentTimeMillis();
        if (now || t - asked > 2000) {
            asked = t;
            ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketRetinueAction(-1, null));
        }
    }

    private String retinueStatus(NBTTagCompound t) {
        int st = t.getInteger("state");
        int dist = t.getInteger("dist");
        String where = !t.hasKey("dist") ? "" : dist >= 0 ? " · " + tr("chaosshard.retinue.dist", dist) : " · " + t.getString("world");
        switch (st) {
            case R_STASHED:
                return tr(t.getBoolean("follower") ? "chaosshard.retinue.state.stashed_me" : "chaosshard.retinue.state.stashed_place");
            case R_FAR:
                return tr(dist >= 0 ? "chaosshard.retinue.state.far" : "chaosshard.retinue.state.other", t.getString("world"))
                        + (dist >= 0 ? " · " + tr("chaosshard.retinue.dist", dist) : "");
            case R_GUARD:
                return tr("chaosshard.retinue.state.guard") + where;
            case R_CHEST:
                return tr("chaosshard.retinue.state.chest") + where;
            default:
                return tr("chaosshard.retinue.state.follow") + where;
        }
    }

    private void drawRetinue(int x, int y, int w, int mx, int my) {
        askRetinue(false);
        retinueRows.clear();
        net.minecraft.nbt.NBTTagList list = ClientRetinue.list();
        int ty = gui.title(tr("chaosshard.retinue.title"), x, y, ACCENT);
        String cnt = tr("chaosshard.retinue.count", list.tagCount(), ClientRetinue.limit());
        gui.text(cnt, x + w - gui.font().getStringWidth(cnt), y, list.tagCount() >= ClientRetinue.limit() ? GOLD : SOFT);
        y = gui.paragraph(tr(list.tagCount() == 0 ? "chaosshard.retinue.empty" : "chaosshard.retinue.how"), x, ty, w, SOFT) + 4;
        int bottom = gui.pageBottom() - 30;
        boolean anyActive = false, anyStashed = false;
        NBTTagCompound sel = null;
        int shown = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            java.util.UUID id = t.getUniqueId("id");
            boolean stashed = t.getInteger("state") == R_STASHED;
            anyStashed |= stashed;
            anyActive |= !stashed;
            if (id.equals(chosen)) {
                sel = t;
            }
            if (y + 22 > bottom) {
                continue;
            }
            shown++;
            boolean over = mx >= x - 2 && mx < x + w && my >= y && my < y + 22;
            boolean picked = id.equals(chosen);
            if (picked || over) {
                Gui.drawRect(x - 2, y, x + w, y + 21, picked ? 0x40B070FF : 0x16E8D8FF);
            }
            Servants.Kind k;
            try {
                k = Servants.Kind.valueOf(t.getString("kind"));
            } catch (IllegalArgumentException ex) {
                k = Servants.Kind.HUSK;
            }
            int big = ru.death.chaosshard.client.ChaosUi.summonIcon(k.name());
            if (big >= 0) {
                ru.death.chaosshard.client.ChaosUi.bigIcon(big, x, y + 2, 17, 1F, 1F, 1F, stashed ? 0.45F : 1F);
            }
            String nm = t.getString("name").isEmpty() ? tr("chaosshard.servant.kind." + k.key()) : t.getString("name");
            String hp = t.hasKey("hp") ? "♥ " + Math.round(t.getFloat("hp")) + "/" + Math.round(t.getFloat("max")) : "";
            int hpW = gui.font().getStringWidth(hp);
            gui.text(gui.font().trimStringToWidth(nm, w - 24 - hpW - 4), x + 21, y + 2, stashed ? SOFT : OPEN);
            if (!hp.isEmpty()) {
                float f = t.getFloat("hp") / Math.max(1F, t.getFloat("max"));
                gui.text(hp, x + w - hpW, y + 2, f < 0.35F ? 0xE07A7A : 0x9AD08A);
            }
            gui.text(gui.font().trimStringToWidth(retinueStatus(t), w - 22), x + 21, y + 11, t.getInteger("state") == R_FAR ? GOLD : DIM);
            retinueRows.add(new Object[]{x - 2, y, w + 2, 22, id});
            y += 22;
        }
        if (shown < list.tagCount()) {
            gui.text(tr("chaosshard.retinue.more", list.tagCount() - shown), x, y + 1, DIM);
        }
        if (sel == null) {
            chosen = null;
        }
        // кнопки внизу
        int by = gui.pageBottom() - 12;
        callBtn.visible = stashBtn.visible = false;
        if (sel != null) {
            boolean stashed = sel.getInteger("state") == R_STASHED;
            callBtn.setLabel(tr("chaosshard.retinue.btn.call"));
            callBtn.enabled = !stashed;
            callBtn.x = x;
            callBtn.y = by;
            callBtn.visible = true;
            stashBtn.setLabel(tr(stashed ? "chaosshard.retinue.btn.unstash" : "chaosshard.retinue.btn.stash"));
            stashBtn.enabled = true;
            stashBtn.x = x + callBtn.width + 8;
            stashBtn.y = by;
            stashBtn.visible = true;
            callBtn.drawButton(Minecraft.getMinecraft(), mx, my, 0);
            stashBtn.drawButton(Minecraft.getMinecraft(), mx, my, 0);
            if (callBtn.isMouseOver()) {
                hoverLines = split(tr(stashed ? "chaosshard.retinue.btn.call.stashed" : "chaosshard.retinue.btn.call.desc"));
            }
            if (stashBtn.isMouseOver()) {
                hoverLines = split(tr(stashed ? (sel.getBoolean("follower") ? "chaosshard.retinue.btn.unstash.me" : "chaosshard.retinue.btn.unstash.place")
                        : "chaosshard.retinue.btn.stash.desc"));
            }
        }
        allBtn.visible = anyActive || anyStashed;
        if (allBtn.visible) {
            allBtn.setLabel(tr(anyStashed ? "chaosshard.retinue.btn.unstash_all" : "chaosshard.retinue.btn.stash_all"));
            allBtn.enabled = true;
            allBtn.x = x + w - allBtn.width;
            allBtn.y = sel != null && allBtn.x < stashBtn.x + stashBtn.width + 6 ? by - 14 : by;
            allBtn.drawButton(Minecraft.getMinecraft(), mx, my, 0);
            if (allBtn.isMouseOver()) {
                hoverLines = split(tr(anyStashed ? "chaosshard.retinue.btn.unstash_all.desc" : "chaosshard.retinue.btn.stash_all.desc"));
            }
        }
    }

    /** Клик на странице «Моя свита». true — обработан. */
    boolean clickRetinue(int mx, int my) {
        java.util.UUID act = chosen;
        NBTTagCompound sel = null;
        net.minecraft.nbt.NBTTagList list = ClientRetinue.list();
        boolean anyStashed = false;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            anyStashed |= t.getInteger("state") == R_STASHED;
            if (t.getUniqueId("id").equals(act)) {
                sel = t;
            }
        }
        if (sel != null && callBtn.visible && callBtn.enabled && hit(callBtn, mx, my)) {
            send(0, act);
            return true;
        }
        if (sel != null && stashBtn.visible && hit(stashBtn, mx, my)) {
            send(sel.getInteger("state") == R_STASHED ? 2 : 1, act);
            return true;
        }
        if (allBtn.visible && hit(allBtn, mx, my)) {
            send(anyStashed ? 4 : 3, null);
            return true;
        }
        for (Object[] r : retinueRows) {
            int rx = (Integer) r[0], ry = (Integer) r[1], rw = (Integer) r[2], rh = (Integer) r[3];
            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                java.util.UUID id = (java.util.UUID) r[4];
                chosen = id.equals(chosen) ? null : id;
                Minecraft.getMinecraft().player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.4F, 1.3F);
                return true;
            }
        }
        return false;
    }

    private static boolean hit(net.minecraft.client.gui.GuiButton b, int mx, int my) {
        return mx >= b.x && mx < b.x + b.width && my >= b.y && my < b.y + b.height;
    }

    private void send(int action, java.util.UUID id) {
        asked = System.currentTimeMillis();
        ChaosNetwork.CHANNEL.sendToServer(new ru.death.chaosshard.network.PacketRetinueAction(action, id));
        Minecraft.getMinecraft().player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.0F);
    }

    private void drawKind(Servants.Kind k, int x, int y, int w, int mx, int my) {
        int thread = MagicUi.threadLevel();
        String role = tr(k.gatherer() ? "chaosshard.servant.gui.gatherer" : "chaosshard.servant.gui.fighter");
        int ty = gui.title(tr("chaosshard.servant.kind." + k.key()), x, y, k.minThread <= thread ? ACCENT : SOFT);
        gui.text(role, x + w - gui.font().getStringWidth(role), y, SOFT);
        y = gui.paragraph(tr("chaosshard.servant.kind." + k.key() + ".desc"), x, ty, w, OPEN) + 3;
        // призыв
        y = summon(ru.death.chaosshard.ritual.Ritual.summonFor(k), x, y, w, mx, my) + 3;
        // сила от Нити
        int tier = Math.min(5, thread);
        y = gui.block(tr("chaosshard.servant.gui.block_strength"), null, 0, x, y, w);
        gui.text(tr("chaosshard.servant.gui.strength", thread, 15 * tier, tr("chaosshard.servant.gear." + tier)), x + 2, y, 0xC8B0FF);
        y += 10;
        gui.text(tr("chaosshard.servant.gui.limit", 2 + thread), x + 2, y, 0xB8B0C8);
        y += 13;
        // улучшения
        y = gui.block(tr("chaosshard.servant.gui.block_upgrades"), tr("chaosshard.servant.gui.upgrades_how"), SOFT, x, y, w);
        for (ru.death.chaosshard.servant.ServantUpgrade u : ru.death.chaosshard.servant.ServantUpgrade.of(k)) {
            boolean open = u.minThread <= thread;
            int iconsX = x + w - 3 * 18;
            boolean over = mx >= x && mx < iconsX && my >= y && my < y + 17;
            if (over) {
                net.minecraft.client.gui.Gui.drawRect(x - 2, y, iconsX - 2, y + 17, 0x16E8D8FF);
            }
            gui.text(gui.font().trimStringToWidth(tr(u.key()), iconsX - x - 6), x + 2, y + 4, open ? OPEN : DIM);
            if (over) {
                hoverLines = split("§d" + tr(u.key()) + "\n§7" + tr(u.key() + ".desc") + "\n"
                        + (open ? "§a" : "§c") + tr("chaosshard.magic.need_thread", u.minThread));
            }
            icons(u.offerings(), iconsX, y, mx, my);
            y += 17;
        }
        gui.noteKey("chaosshard.note.kind." + k.key(), x + 4, y + 2, gui.pageBottom() + 8, -2.5F);
    }

    /**
     * Блок «Призыв»: заголовок с нужной Нитью справа, ниже — подношения в ряд (черта под значком — хватает ли)
     * и справа от них кнопка «Сложить в Чашу». Возвращает y под блоком.
     */
    private int summon(ru.death.chaosshard.ritual.Ritual r, int x, int y, int w, int mx, int my) {
        boolean ok = r.minThread <= MagicUi.threadLevel();
        y = gui.block(tr("chaosshard.servant.gui.block_summon"), tr("chaosshard.magic.need_thread", r.minThread), ok ? 0x9AD08A : 0xE07A7A, x, y, w);
        @SuppressWarnings("unchecked")
        List<String>[] hover = new List[1];
        gui.offerRow(r, x + 2, y + 4, mx, my, hover);
        if (hover[0] != null) {
            hoverLines = hover[0];
        }
        gui.setFillTarget(r, x + w, y);
        return y + 28;
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
