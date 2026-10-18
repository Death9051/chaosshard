package ru.death.chaosshard.spell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketSpellData;
import ru.death.chaosshard.thread.ThreadCapability;
import ru.death.chaosshard.thread.ThreadManager;

/**
 * Прокачка заклинаний игрока: ранги перков и выбранная форма каждого заклинания.
 * Хранится в «переживающих смерть» данных игрока; клиент получает копию пакетом.
 * Очков у заклинания столько, сколько уровней Нити (без учёта истончения святой землёй).
 */
public final class SpellData {

    public static final String TAG = "chaosshard_spells";
    private static final String FORM = "form";

    /** Копия на клиенте. */
    public static NBTTagCompound client = new NBTTagCompound();

    static NBTTagCompound root(EntityPlayer p) {
        if (p.world.isRemote) {
            return client;
        }
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(TAG)) {
            persisted.setTag(TAG, new NBTTagCompound());
        }
        return persisted.getCompoundTag(TAG);
    }

    private static NBTTagCompound of(EntityPlayer p, Spell s) {
        NBTTagCompound r = root(p);
        if (!r.hasKey(s.name())) {
            if (p.world.isRemote) {
                return new NBTTagCompound();
            }
            r.setTag(s.name(), new NBTTagCompound());
        }
        return r.getCompoundTag(s.name());
    }

    public static int rank(EntityPlayer p, Spell s, String perk) {
        return of(p, s).getInteger(perk);
    }

    public static boolean has(EntityPlayer p, Spell s, String perk) {
        return rank(p, s, perk) > 0;
    }

    public static int spent(EntityPlayer p, Spell s) {
        int n = 0;
        for (SpellPerks.Perk k : SpellPerks.perks(s)) {
            if (rank(p, s, k.id) > 0) {
                n += k.cost();
            }
        }
        return n;
    }

    /** Что показать рядом с именем заклинания: место (Зов Нити), иначе ветку или «без ветки». */
    public static String variantKey(EntityPlayer p, Spell s) {
        if (SpellPerks.forms(s).length > 1) {
            return SpellPerks.formKey(s, form(p, s));
        }
        int b = branch(p, s);
        return b > 0 ? SpellPerks.branchKey(s, b - 1) : "chaosshard.magic.branch.none";
    }

    /** Выбранная ветка: 0 — нет, 1 — A, 2 — B. */
    public static int branch(EntityPlayer p, Spell s) {
        return has(p, s, "a1") ? 1 : has(p, s, "b1") ? 2 : 0;
    }

    /** Сколько ступеней взято в выбранной ветке (0–3). */
    public static int tier(EntityPlayer p, Spell s) {
        int b = branch(p, s);
        if (b == 0) {
            return 0;
        }
        String c = b == 1 ? "a" : "b";
        return has(p, s, c + "3") ? 3 : has(p, s, c + "2") ? 2 : 1;
    }

    /** Ветка A не ниже ступени t. */
    public static boolean a(EntityPlayer p, Spell s, int t) {
        return branch(p, s) == 1 && tier(p, s) >= t;
    }

    /** Ветка B не ниже ступени t. */
    public static boolean b(EntityPlayer p, Spell s, int t) {
        return branch(p, s) == 2 && tier(p, s) >= t;
    }

    /** Очков на заклинание: уровень Нити. */
    public static int points(EntityPlayer p) {
        if (p.world.isRemote) {
            return ThreadManager.levelFor(ru.death.chaosshard.client.ClientThreadCache.points());
        }
        ru.death.chaosshard.thread.IThread t = ThreadCapability.get(p);
        return t == null ? 0 : ThreadManager.levelFor(t.getPoints());
    }

    public static int free(EntityPlayer p, Spell s) {
        return Math.max(0, points(p) - spent(p, s));
    }

    public static boolean formOpen(EntityPlayer p, Spell s, int form) {
        if (form <= 0) {
            return true;
        }
        if (form >= SpellPerks.forms(s).length) {
            return false;
        }
        SpellPerks.Perk opener = SpellPerks.opener(s, form);
        return opener == null || has(p, s, opener.id);
    }

    public static int form(EntityPlayer p, Spell s) {
        int f = of(p, s).getInteger(FORM);
        return formOpen(p, s, f) ? f : 0;
    }

    /** Следующая открытая форма по кругу. */
    public static int nextForm(EntityPlayer p, Spell s) {
        int n = SpellPerks.forms(s).length;
        int cur = form(p, s);
        for (int i = 1; i <= n; i++) {
            int f = (cur + i) % n;
            if (formOpen(p, s, f)) {
                return f;
            }
        }
        return cur;
    }

    // ------------------------------------------------------------------ изменения (сервер)

    public static boolean buy(EntityPlayerMP p, Spell s, int perkIndex) {
        SpellPerks.Perk[] all = SpellPerks.perks(s);
        if (perkIndex < 0 || perkIndex >= all.length) {
            return false;
        }
        SpellPerks.Perk k = all[perkIndex];
        NBTTagCompound t = of(p, s);
        int r = t.getInteger(k.id);
        if (r >= k.max) {
            return false;
        }
        int br = branch(p, s);
        if (br != 0 && br != k.branch + 1) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.spell.branch_closed"), true);
            return false;
        }
        if (k.tier > 1 && tier(p, s) < k.tier - 1) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.spell.tier_first"), true);
            return false;
        }
        if (free(p, s) < k.cost()) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.spell.no_points"), true);
            return false;
        }
        t.setInteger(k.id, r + 1);
        if (k.form > 0 && r == 0) {
            t.setInteger(FORM, k.form);
        }
        sync(p);
        p.sendStatusMessage(new TextComponentTranslation("chaosshard.spell.perk_taken",
                new TextComponentTranslation(k.key(s)), k.tier, 3), true);
        ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.SPELL_PERK);
        if (k.tier == 1) {
            // выбор ветки — бывшая веха «другая форма»
            ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.SPELL_FORM);
        }
        return true;
    }

    public static boolean setForm(EntityPlayerMP p, Spell s, int form) {
        if (!formOpen(p, s, form)) {
            return false;
        }
        of(p, s).setInteger(FORM, form);
        sync(p);
        p.sendStatusMessage(new TextComponentTranslation("chaosshard.spell.form_set", new TextComponentTranslation(s.key()),
                new TextComponentTranslation(SpellPerks.formKey(s, form))), true);
        if (form > 0) {
            ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.SPELL_FORM);
        }
        return true;
    }

    /** Для автотеста и отладки: у заклинания только ветка branch (1 — A, 2 — B, 0 — без ветки) до ступени tier. */
    public static void debugSet(EntityPlayerMP p, Spell s, int branch, int tier) {
        NBTTagCompound t = of(p, s);
        for (SpellPerks.Perk k : SpellPerks.perks(s)) {
            t.removeTag(k.id);
        }
        for (int i = 1; i <= tier && branch > 0; i++) {
            t.setInteger((branch == 1 ? "a" : "b") + i, 1);
        }
        sync(p);
    }

    /** Сбросить все перки (ритуал «Чистые страницы»). */
    public static void reset(EntityPlayerMP p) {
        NBTTagCompound r = root(p);
        for (Spell s : Spell.values()) {
            r.removeTag(s.name());
        }
        sync(p);
    }

    public static void sync(EntityPlayerMP p) {
        ChaosNetwork.CHANNEL.sendTo(new PacketSpellData(root(p).copy()), p);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) event.player);
        }
    }
}
