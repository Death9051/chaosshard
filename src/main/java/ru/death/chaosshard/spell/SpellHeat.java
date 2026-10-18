package ru.death.chaosshard.spell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.Tags;

/**
 * Разогрев (замечание №27): каждое заклинание греет шкалу Хаоса. На максимуме следующее выходит в мощной модификации
 * (сильнее, шире, почти даром и с короткой перезарядкой), после чего шкала падает до половины и полминуты не растёт.
 * Без заклинаний шкала остывает. Ничего плохого разогрев не делает.
 * Хранится в корне SpellData ("_heat", "_heat_lock", "_heat_last"), на клиент — вместе с перками.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class SpellHeat implements SpellMods.Hook {

    public static final SpellHeat HOOK = new SpellHeat();
    public static final float MAX = 100;
    /** Сколько шкала не растёт после мощного заклинания (тиков). */
    public static final int LOCK = 600;
    /** Через сколько тиков без заклинаний шкала начинает остывать. */
    public static final int IDLE = 100;

    public static float heat(EntityPlayer p) {
        return SpellData.root(p).getFloat("_heat");
    }

    /** Шкала на максимуме — следующее заклинание мощное. */
    public static boolean surge(EntityPlayer p) {
        return heat(p) >= MAX;
    }

    /** Сколько тиков ещё шкала не растёт (0 — растёт). */
    public static long locked(EntityPlayer p) {
        return Math.max(0, SpellData.root(p).getLong("_heat_lock") - p.world.getTotalWorldTime());
    }

    /** Сколько добавляет заклинание такой цены. */
    public static float gain(EntityPlayer p, Spell s, int energy) {
        float g = Math.max(8, Math.min(25, 8 + energy * 0.5F));
        if (SpellGlyphs.has(p, s, SpellGlyph.KINDLING)) {
            g *= 2;
        }
        return g;
    }

    /** Для автотеста и отладки. */
    public static void set(EntityPlayerMP p, float heat, long lockTicks) {
        NBTTagCompound r = SpellData.root(p);
        r.setFloat("_heat", Math.max(0, Math.min(MAX, heat)));
        r.setLong("_heat_lock", lockTicks > 0 ? p.world.getTotalWorldTime() + lockTicks : 0);
        r.setLong("_heat_last", p.world.getTotalWorldTime());
        SpellData.sync(p);
    }

    // ------------------------------------------------------------------ мощная модификация

    @Override
    public double power(EntityPlayer p, Spell s) {
        return surge(p) ? 1.75 : 1;
    }

    @Override
    public int area(EntityPlayer p, Spell s) {
        return surge(p) ? 2 : 0;
    }

    @Override
    public double cost(EntityPlayer p, Spell s) {
        return surge(p) ? 0.01 : 1;
    }

    @Override
    public double cooldown(EntityPlayer p, Spell s) {
        return surge(p) ? 0.25 : 1;
    }

    /** После удачного заклинания: мощное — сбросить до половины и заморозить; обычное — нагреть. */
    @Override
    public void after(EntityPlayerMP p, Spell s, int energy) {
        NBTTagCompound r = SpellData.root(p);
        long now = p.world.getTotalWorldTime();
        float h = r.getFloat("_heat");
        if (h >= MAX) {
            r.setFloat("_heat", MAX / 2);
            r.setLong("_heat_lock", now + LOCK);
            WorldServer ws = (WorldServer) p.world;
            ws.spawnParticle(EnumParticleTypes.FLAME, p.posX, p.posY + 1, p.posZ, 40, 0.6, 0.8, 0.6, 0.06);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, p.posX, p.posY + 1, p.posZ, 60, 0.8, 1.0, 0.8, 0.1);
            p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8F, 0.6F);
            p.world.playSound(null, p.getPosition(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0F, 0.7F);
            p.sendStatusMessage(new TextComponentTranslation("chaosshard.heat.surge"), true);
        } else if (now >= r.getLong("_heat_lock")) {
            float nh = Math.min(MAX, h + gain(p, s, energy));
            r.setFloat("_heat", nh);
            if (nh >= MAX) {
                p.world.playSound(null, p.getPosition(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.6F, 1.4F);
                p.sendStatusMessage(new TextComponentTranslation("chaosshard.heat.ready"), true);
            }
        }
        r.setLong("_heat_last", now);
        SpellData.sync(p);
    }

    /** Остывание: после паузы без заклинаний (5 с, накалённая — 15 с) — по 4 в секунду; во время заморозки — нет. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote || e.player.ticksExisted % 20 != 0) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        NBTTagCompound r = SpellData.root(p);
        float h = r.getFloat("_heat");
        long now = p.world.getTotalWorldTime();
        // накалённая шкала держится дольше: мощное заклинание можно приберечь
        if (h <= 0 || now < r.getLong("_heat_lock") || now - r.getLong("_heat_last") < (h >= MAX ? IDLE * 3 : IDLE)) {
            return;
        }
        r.setFloat("_heat", Math.max(0, h - 4));
        SpellData.sync(p);
    }

    private SpellHeat() {
    }
}
