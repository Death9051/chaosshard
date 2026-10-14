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

    private static NBTTagCompound root(EntityPlayer p) {
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
            n += rank(p, s, k.id);
        }
        return n;
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
        if (free(p, s) <= 0) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.spell.no_points"), true);
            return false;
        }
        t.setInteger(k.id, r + 1);
        if (k.form > 0 && r == 0) {
            t.setInteger(FORM, k.form);
        }
        sync(p);
        p.sendStatusMessage(new TextComponentTranslation("chaosshard.spell.perk_taken",
                new TextComponentTranslation(k.key(s)), r + 1, k.max), true);
        ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.SPELL_PERK);
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
