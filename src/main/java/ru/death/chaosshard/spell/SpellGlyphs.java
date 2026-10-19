package ru.death.chaosshard.spell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.thread.ThreadBonus;

/**
 * Гнёзда глифов у заклинаний: одно сразу, второе с Нити 3, третье с Нити 5.
 * Хранятся отдельно от перков (сброс перков их не трогает): корень SpellData → "_glyphs" → имя заклинания → int[3], -1 — пусто.
 */
public final class SpellGlyphs implements SpellMods.Hook {

    public static final SpellGlyphs HOOK = new SpellGlyphs();
    public static final int MAX_SLOTS = 3;
    private static final String TAG = "_glyphs";

    /** Сколько гнёзд открыто при такой Нити. */
    public static int slots(int thread) {
        return 1 + (thread >= 3 ? 1 : 0) + (thread >= 5 ? 1 : 0);
    }

    public static int slots(EntityPlayer p) {
        return p.world.isRemote ? slots(ru.death.chaosshard.ChaosShard.proxy.clientThreadLevel()) : slots(ThreadBonus.level(p));
    }

    private static int[] raw(EntityPlayer p, Spell s) {
        NBTTagCompound g = SpellData.root(p).getCompoundTag(TAG);
        int[] a = g.getIntArray(s.name());
        if (a.length != MAX_SLOTS) {
            a = new int[]{-1, -1, -1};
        }
        return a;
    }

    /** Глиф в гнезде или null. */
    public static SpellGlyph get(EntityPlayer p, Spell s, int slot) {
        return slot < 0 || slot >= MAX_SLOTS ? null : SpellGlyph.byMeta(raw(p, s)[slot]);
    }

    /** Есть ли глиф на заклинании (в открытом гнезде). */
    public static boolean has(EntityPlayer p, Spell s, SpellGlyph g) {
        int[] a = raw(p, s);
        int open = slots(p);
        for (int i = 0; i < open; i++) {
            if (a[i] == g.ordinal()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Вложить глиф (meta) в гнездо или вынуть (meta = -1). Глиф берётся из сумки, прежний возвращается в сумку.
     * Возвращает true, если что-то изменилось.
     */
    public static boolean set(EntityPlayerMP p, Spell s, int slot, int meta) {
        if (slot < 0 || slot >= slots(p) || s == Spell.BOOK) {
            return false;
        }
        int[] a = raw(p, s);
        SpellGlyph want = SpellGlyph.byMeta(meta);
        if (want != null) {
            if (!want.fits(s)) {
                p.sendStatusMessage(new TextComponentTranslation("chaosshard.glyph.no_fit"), true);
                return false;
            }
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (i != slot && a[i] == meta) {
                    p.sendStatusMessage(new TextComponentTranslation("chaosshard.glyph.twice"), true);
                    return false;
                }
            }
            if (!p.isCreative() && !take(p, meta)) {
                return false;
            }
        }
        if (a[slot] >= 0 && !p.isCreative()) {
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModItems.SPELL_GLYPH, 1, a[slot]));
        }
        a[slot] = want == null ? -1 : meta;
        NBTTagCompound root = SpellData.root(p);
        NBTTagCompound g = root.getCompoundTag(TAG);
        g.setIntArray(s.name(), a);
        root.setTag(TAG, g);
        SpellData.sync(p);
        if (want != null) {
            ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.GLYPH_SET);
        }
        return true;
    }

    private static boolean take(EntityPlayerMP p, int meta) {
        for (ItemStack st : p.inventory.mainInventory) {
            if (!st.isEmpty() && st.getItem() == ModItems.SPELL_GLYPH && st.getMetadata() == meta) {
                st.shrink(1);
                p.inventoryContainer.detectAndSendChanges();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ действие

    @Override
    public double cost(EntityPlayer p, Spell s) {
        double m = 1;
        if (has(p, s, SpellGlyph.WIDE)) m *= 1.4;
        if (has(p, s, SpellGlyph.THRIFT)) m *= 0.6;
        if (has(p, s, SpellGlyph.HASTE)) m *= 1.3;
        if (has(p, s, SpellGlyph.ECHO)) m *= 1.5;
        if (has(p, s, SpellGlyph.KINDLING)) m *= 1.15;
        return m;
    }

    @Override
    public double power(EntityPlayer p, Spell s) {
        double m = 1;
        if (has(p, s, SpellGlyph.POTENT)) m *= 1.4;
        if (has(p, s, SpellGlyph.THRIFT)) m *= 0.85;
        if (has(p, s, SpellGlyph.BLOOD)) m *= 1.15;
        return m;
    }

    @Override
    public double cooldown(EntityPlayer p, Spell s) {
        double m = 1;
        if (has(p, s, SpellGlyph.POTENT)) m *= 1.5;
        if (has(p, s, SpellGlyph.HASTE)) m *= 0.5;
        return m;
    }

    @Override
    public double twice(EntityPlayer p, Spell s) {
        return has(p, s, SpellGlyph.ECHO) ? 0.35 : 0;
    }

    @Override
    public int area(EntityPlayer p, Spell s) {
        return has(p, s, SpellGlyph.WIDE) ? 1 : 0;
    }

    /** Кровь: здоровье сверх 4 единиц идёт в счёт цены — по 3 энергии за единицу. */
    @Override
    public int extraEnergy(EntityPlayer p, Spell s) {
        return has(p, s, SpellGlyph.BLOOD) ? Math.max(0, (int) (p.getHealth() - 4)) * 3 : 0;
    }

    /** Кровь: половину цены — здоровьем (не ниже 4 единиц здоровья). */
    @Override
    public int payFirst(EntityPlayerMP p, Spell s, int cost) {
        if (!has(p, s, SpellGlyph.BLOOD) || cost <= 0) {
            return 0;
        }
        int want = (cost + 1) / 2;
        int hp = Math.min((int) Math.ceil(want / 3.0), Math.max(0, (int) (p.getHealth() - 4)));
        if (hp <= 0) {
            return 0;
        }
        p.setHealth(p.getHealth() - hp);
        p.world.playSound(null, p.getPosition(), net.minecraft.init.SoundEvents.ENTITY_PLAYER_HURT, net.minecraft.util.SoundCategory.PLAYERS, 0.5F, 0.8F);
        return Math.min(want, hp * 3);
    }

    private SpellGlyphs() {
    }
}
