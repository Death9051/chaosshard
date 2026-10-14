package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.spell.ChaosEnergy;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellMods;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tools.ToolCore;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.TinkerUtil;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Уровни материалов Хаоса на инструментах Tinkers и их модификаторы заклинаний.
 * У инструмента свой опыт для каждого материала Хаоса в нём (уровни 1–5): убийства, заклинания,
 * на захваченной земле — вдвое, плюс сфера «Закалка». Модификаторы работают, пока инструмент в основной руке,
 * а Гримуар — во второй.
 */
public final class ChaosMaterials implements SpellMods.Hook {

    public static final String CHAOS = "chaosshard_chaos", DISTORTED = "chaosshard_distorted",
            CRYSTAL = "chaosshard_crystal", BLOOD = "chaosshard_bloodplatinum", STAR = "chaosshard_starlight";
    public static final String[] ALL = {CHAOS, DISTORTED, CRYSTAL, BLOOD, STAR};
    public static final int[] XP = {0, 100, 300, 700, 1500};
    private static final String TAG_XP = "chaosshard_matxp";
    public static final String TAG_STORE = "chaosshard_crystal_store";

    // ------------------------------------------------------------------ материалы и опыт

    public static boolean isTool(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof ToolCore;
    }

    /** Материалы Хаоса в инструменте (без повторов). */
    public static Set<String> materials(ItemStack s) {
        Set<String> out = new LinkedHashSet<>();
        if (!isTool(s)) {
            return out;
        }
        for (Material m : TinkerUtil.getMaterialsFromTagList(TagUtil.getBaseMaterialsTagList(s))) {
            for (String id : ALL) {
                if (id.equals(m.getIdentifier())) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    public static int xp(ItemStack s, String mat) {
        return s.hasTagCompound() ? s.getTagCompound().getCompoundTag(TAG_XP).getInteger(mat) : 0;
    }

    public static int levelOf(int xp) {
        int l = 1;
        for (int i = 1; i < XP.length; i++) {
            if (xp >= XP[i]) {
                l = i + 1;
            }
        }
        return l;
    }

    /** Уровень материала в инструменте; 0 — материала нет. */
    public static int level(ItemStack s, String mat) {
        return materials(s).contains(mat) ? levelOf(xp(s, mat)) : 0;
    }

    /** Добавить опыт всем материалам Хаоса инструмента. */
    public static void addXp(ItemStack s, int amount, EntityPlayer p) {
        Set<String> mats = materials(s);
        if (mats.isEmpty() || amount <= 0) {
            return;
        }
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound t = s.getTagCompound().getCompoundTag(TAG_XP);
        for (String m : mats) {
            int before = levelOf(t.getInteger(m));
            int now = Math.min(XP[XP.length - 1], t.getInteger(m) + amount);
            t.setInteger(m, now);
            int after = levelOf(now);
            if (after > before && p instanceof EntityPlayerMP) {
                p.sendMessage(Breach.grimoire("chaosshard.material.levelup",
                        new TextComponentTranslation("material." + m + ".name"), after));
                if (after >= 5) {
                    ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) p, ru.death.chaosshard.quest.Milestones.MATERIAL_MAX);
                }
            }
        }
        s.getTagCompound().setTag(TAG_XP, t);
    }

    private static boolean captured(EntityPlayer p) {
        return ru.death.chaosshard.anchor.TileAnchor.energyMultiplier(p.world, p.getPosition()) > 1.0;
    }

    /** Инструмент в основной руке, Гримуар во второй — иначе модификаторов нет. */
    private static ItemStack armed(EntityPlayer p) {
        ItemStack main = p.getHeldItemMainhand();
        return isTool(main) && p.getHeldItemOffhand().getItem() == ModItems.GRIMOIRE ? main : ItemStack.EMPTY;
    }

    private static int lv(EntityPlayer p, String mat) {
        ItemStack t = armed(p);
        return t.isEmpty() ? 0 : level(t, mat);
    }

    public static int store(ItemStack s) {
        return s.hasTagCompound() ? s.getTagCompound().getInteger(TAG_STORE) : 0;
    }

    public static int storeCap(ItemStack s) {
        return 20 * level(s, CRYSTAL);
    }

    public static void setStore(ItemStack s, int v) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        s.getTagCompound().setInteger(TAG_STORE, Math.max(0, Math.min(v, storeCap(s))));
    }

    // ------------------------------------------------------------------ модификаторы заклинаний

    private static String attuned(EntityPlayer p) {
        return Loader.isModLoaded("astralsorcery") ? ru.death.chaosshard.compat.astral.AstralOverflow.attuned(p) : null;
    }

    /** Сила привкуса созвездия: 1 — своё созвездие, 0.5 — всё сразу через Связь с Хаосом, 0 — нет. */
    private static double star(EntityPlayer p, String constellation) {
        if (lv(p, STAR) <= 0) {
            return 0;
        }
        String a = attuned(p);
        return a == null ? 0 : a.equals("chaos") ? 0.5 : a.equals(constellation) ? 1.0 : 0;
    }

    @Override
    public int maxEnergy(EntityPlayer p) {
        return 10 * lv(p, CHAOS);
    }

    @Override
    public double twice(EntityPlayer p, Spell s) {
        return 0.05 * lv(p, DISTORTED);
    }

    @Override
    public double cost(EntityPlayer p, Spell s) {
        double m = 1.0;
        if (s == Spell.DASH) {
            m *= 1 - 0.06 * lv(p, STAR) * star(p, "vicio");
        }
        return m;
    }

    @Override
    public double power(EntityPlayer p, Spell s) {
        double m = 1 + 0.05 * lv(p, STAR) * star(p, "discidia");
        int blood = lv(p, BLOOD);
        if (blood >= 5 && ChaosEnergy.get(p) < ru.death.chaosshard.spell.SpellCaster.cost(p, s)) {
            m *= 1.25;
        }
        return m;
    }

    @Override
    public double cooldown(EntityPlayer p, Spell s) {
        return 1.0;
    }

    @Override
    public int extraEnergy(EntityPlayer p, Spell s) {
        ItemStack t = armed(p);
        int n = t.isEmpty() ? 0 : store(t);
        int blood = lv(p, BLOOD);
        if (blood > 0) {
            n += (int) Math.max(0, (p.getHealth() - 2) * perHp(blood));
        }
        return n;
    }

    /** Энергии за единицу здоровья: 1 сердце (2 ед.) — 10 энергии, на 5-м уровне — 15. */
    private static double perHp(int blood) {
        return blood >= 5 ? 7.5 : 5.0;
    }

    @Override
    public int payFirst(EntityPlayerMP p, Spell s, int cost) {
        ItemStack t = armed(p);
        if (t.isEmpty() || level(t, CRYSTAL) <= 0) {
            return 0;
        }
        int got = Math.min(cost, store(t));
        setStore(t, store(t) - got);
        return got;
    }

    @Override
    public int payExtra(EntityPlayerMP p, Spell s, int need) {
        int blood = lv(p, BLOOD);
        if (blood <= 0 || need <= 0) {
            return 0;
        }
        double hp = Math.ceil(need / perHp(blood));
        hp = Math.min(hp, p.getHealth() - 1);
        if (hp <= 0) {
            return 0;
        }
        p.setHealth((float) (p.getHealth() - hp));
        p.world.playSound(null, p.getPosition(), net.minecraft.init.SoundEvents.ENTITY_PLAYER_HURT, net.minecraft.util.SoundCategory.PLAYERS, 0.7F, 0.7F);
        ((net.minecraft.world.WorldServer) p.world).spawnParticle(net.minecraft.util.EnumParticleTypes.REDSTONE,
                p.posX, p.posY + 1, p.posZ, 20, 0.3, 0.5, 0.3, 0.0);
        return (int) Math.ceil(hp * perHp(blood));
    }

    @Override
    public boolean freeCooldown(EntityPlayer p, Spell s, int fromStore, int cost) {
        return lv(p, CRYSTAL) >= 5 && cost > 0 && fromStore >= cost;
    }

    @Override
    public void after(EntityPlayerMP p, Spell s, int energy) {
        ItemStack t = armed(p);
        if (t.isEmpty()) {
            return;
        }
        addXp(t, energy * (captured(p) ? 2 : 1), p);
        double aev = lv(p, STAR) * star(p, "aevitas");
        if (aev > 0) {
            p.heal((float) (energy / 10.0 * aev / 5.0 * 2));
        }
        double arm = lv(p, STAR) * star(p, "armara");
        if (arm > 0) {
            p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 200, Math.max(0, (int) Math.round(arm / 2) - 1)));
        }
    }

    /** Эворсио: Разлом шире на 1 (с 3-го уровня Света звезды). */
    @Override
    public int area(EntityPlayer p, Spell s) {
        return s == Spell.RIFT && lv(p, STAR) >= 3 && star(p, "evorsio") > 0 ? 1 : 0;
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase dead = event.getEntityLiving();
        if (dead.world.isRemote || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)
                || ru.death.chaosshard.anchor.AnchorSpecs.isAnchorHit(event.getSource())) {
            return;   // убил Якорь, а не оружие в руке
        }
        EntityPlayerMP p = (EntityPlayerMP) event.getSource().getTrueSource();
        ItemStack main = p.getHeldItemMainhand();
        if (!isTool(main)) {
            return;
        }
        int amount = Math.max(1, (int) (dead.getMaxHealth() / 2));
        addXp(main, amount * (captured(p) ? 2 : 1), p);
    }

    /** Сфера «Закалка»: опыт материалам инструмента во второй руке. */
    public static boolean temper(EntityPlayerMP p) {
        ItemStack tool = isTool(p.getHeldItemOffhand()) ? p.getHeldItemOffhand() : p.getHeldItemMainhand();
        if (materials(tool).isEmpty()) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.material.temper.none"), true);
            return false;
        }
        addXp(tool, 300, p);
        p.sendStatusMessage(Breach.grimoire("chaosshard.material.temper.done"), true);
        return true;
    }

}
