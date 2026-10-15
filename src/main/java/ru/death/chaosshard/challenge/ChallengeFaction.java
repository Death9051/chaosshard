package ru.death.chaosshard.challenge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityVindicator;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.structure.EntityOrderKnight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Кто приходит на Вызов. Знак стороны — подношение на Алтарь. */
public enum ChallengeFaction {
    /** Орден Света: стража, капитаны, Хранитель. Знак — 4 золотых слитка. */
    ORDER(4),
    /** Мрачная армия Гаррота: солдаты, лучники, щитоносцы, командир. Знак — Знамя Мрачной армии. */
    GARROT(1),
    /** Твари мира: ванильные и Lycanites, вожак стаи. Знак — 8 гнилой плоти. */
    WILD(8),
    /** Сборщики: кибер-зомби (с 3-й волны — громилы), босс — Сборщик. Знак — 4 железных слитка. Награда — детали и импланты. */
    CYBER(4);

    public final int markerCount;

    ChallengeFaction(int markerCount) {
        this.markerCount = markerCount;
    }

    public Item marker() {
        switch (this) {
            case ORDER: return Items.GOLD_INGOT;
            case GARROT: return ModItems.GARROT_BANNER;
            case CYBER: return Items.IRON_INGOT;
            default: return Items.ROTTEN_FLESH;
        }
    }

    public boolean isMarker(ItemStack s) {
        return !s.isEmpty() && s.getItem() == marker();
    }

    public String key() {
        return "chaosshard.challenge.faction." + name().toLowerCase();
    }

    // ------------------------------------------------------------------ бойцы

    /** Обычный боец волны (wave с 1), ещё не в мире. */
    public EntityLiving regular(WorldServer w, int tier, int wave, Random r) {
        switch (this) {
            case ORDER: {
                EntityOrderKnight k = new EntityOrderKnight(w);
                boolean captain = wave >= 3 && r.nextInt(6) == 0;
                k.setup(captain ? EntityOrderKnight.CAPTAIN : EntityOrderKnight.GUARD);
                return k;
            }
            case GARROT:
                return garrot(w, tier, wave, r, false);
            case CYBER:
                return cyber(w, tier, wave, r, false);
            default:
                return wild(w, tier, wave, r);
        }
    }

    /** Босс последней (или каждой пятой) волны. */
    public EntityLiving boss(WorldServer w, int tier, Random r) {
        switch (this) {
            case ORDER: {
                EntityOrderKnight k = new EntityOrderKnight(w);
                k.setup(EntityOrderKnight.KEEPER);
                return k;
            }
            case GARROT:
                return garrot(w, tier, 0, r, true);
            case CYBER:
                return cyber(w, tier, 0, r, true);
            default: {
                EntityLiving m = create(w, pick(r, existing("lycanitesmobs:ettin", "lycanitesmobs:troll", "lycanitesmobs:wendigo")), "minecraft:wither_skeleton");
                double hp = 120 + 60 * tier;
                m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
                m.setHealth((float) hp);
                m.setCustomNameTag(TextFormatting.DARK_GREEN + new TextComponentTranslation("chaosshard.challenge.boss.wild").getUnformattedText());
                return m;
            }
        }
    }

    private static EntityLiving garrot(WorldServer w, int tier, int wave, Random r, boolean boss) {
        if (net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfare") || net.minecraftforge.fml.common.Loader.isModLoaded("ancientwarfarenpc")) {
            try {
                String type = boss ? "faction.leader.elite" : wave >= 3 && r.nextInt(4) == 0 ? "faction.soldier.elite"
                        : wave >= 2 && r.nextInt(3) == 0 ? "faction.archer" : "faction.soldier";
                EntityLiving npc = ru.death.chaosshard.garrot.GarrotRaids.awNpc(w, type);
                if (npc != null) {
                    ru.death.chaosshard.garrot.GarrotRaids.skin(npc, boss ? "commander" : type.equals("faction.archer") ? "archer"
                            : type.endsWith("elite") ? "shieldbearer" : "soldier");
                    if (boss) {
                        double hp = 150 + 50 * tier;
                        npc.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
                        npc.setHealth((float) hp);
                        npc.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.commander").getUnformattedText());
                    }
                    return npc;
                }
            } catch (Throwable t) {
                // нет Ancient Warfare — ванильные переодетые
            }
        }
        EntityLiving s;
        if (boss) {
            EntityWitherSkeleton c = new EntityWitherSkeleton(w);
            c.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            c.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            c.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            double hp = 150 + 50 * tier;
            c.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
            c.setHealth((float) hp);
            c.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.commander").getUnformattedText());
            return c;
        }
        int roll = r.nextInt(6);
        if (wave >= 3 && roll == 0) {
            EntityHusk h = new EntityHusk(w);
            h.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            h.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            h.setChild(false);
            h.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.shield").getUnformattedText());
            s = h;
        } else if (wave >= 2 && roll <= 2) {
            EntitySkeleton k = new EntitySkeleton(w);
            k.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            k.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            k.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.archer").getUnformattedText());
            s = k;
        } else {
            EntityVindicator v = new EntityVindicator(w);
            v.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            v.setCustomNameTag(TextFormatting.DARK_GRAY + new TextComponentTranslation("chaosshard.garrot.soldier").getUnformattedText());
            s = v;
        }
        return s;
    }

    private static EntityLiving wild(WorldServer w, int tier, int wave, Random r) {
        int power = Math.min(5, Math.max(1, tier) + wave / 3);
        List<String> pool = new ArrayList<>();
        pool.add("minecraft:zombie");
        pool.add("minecraft:skeleton");
        pool.add("minecraft:spider");
        pool.addAll(existing("lycanitesmobs:ghoul", "lycanitesmobs:kobold", "lycanitesmobs:trite", "lycanitesmobs:jabberwock"));
        if (power >= 2) {
            pool.add("minecraft:husk");
            pool.add("minecraft:stray");
            pool.add("minecraft:cave_spider");
            pool.addAll(existing("lycanitesmobs:warg", "lycanitesmobs:geonach", "lycanitesmobs:chupacabra", "lycanitesmobs:crusk"));
        }
        if (power >= 3) {
            pool.add("minecraft:witch");
            pool.addAll(existing("lycanitesmobs:troll", "lycanitesmobs:wendigo", "lycanitesmobs:cacodemon"));
        }
        if (power >= 4) {
            pool.add("minecraft:wither_skeleton");
            pool.addAll(existing("lycanitesmobs:ettin", "lycanitesmobs:lurker"));
        }
        return create(w, pool.get(r.nextInt(pool.size())), "minecraft:zombie");
    }

    /** Метка Сборщиков: ступень Вызова (и босс ли), по ней режутся трофейные импланты. */
    public static final String CYBER_TAG = "chaosshard_cyber";

    /** Кибер-зомби Cyberware; с 3-й волны часть — громилы (на ступени 4+ чаще). Без Cyberware — обычные зомби в железе. */
    private static EntityLiving cyber(WorldServer w, int tier, int wave, Random r, boolean boss) {
        EntityLiving m = create(w, "cyberware:cyberzombie", "minecraft:zombie");
        boolean brute = boss || wave >= 3 && r.nextInt(tier >= 4 ? 2 : 3) == 0;
        if (net.minecraftforge.fml.common.Loader.isModLoaded("cyberware")) {
            if (brute) {
                ru.death.chaosshard.compat.body.CyberLoot.brute(m);
            }
        } else {
            m.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            m.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("Tier", Math.max(1, tier));
        t.setBoolean("Boss", boss);
        m.getEntityData().setTag(CYBER_TAG, t);
        if (boss) {
            double hp = 140 + 60 * tier;
            m.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
            m.setHealth((float) hp);
            m.setCustomNameTag(TextFormatting.DARK_AQUA + new TextComponentTranslation("chaosshard.challenge.boss.cyber").getUnformattedText());
        }
        return m;
    }

    static List<String> existing(String... ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (ForgeRegistries.ENTITIES.containsKey(new ResourceLocation(id))) {
                out.add(id);
            }
        }
        return out;
    }

    static String pick(Random r, List<String> l) {
        return l.isEmpty() ? null : l.get(r.nextInt(l.size()));
    }

    static EntityLiving create(WorldServer w, String id, String fallback) {
        Entity e = id == null ? null : EntityList.createEntityByIDFromName(new ResourceLocation(id), w);
        if (!(e instanceof EntityLiving)) {
            e = EntityList.createEntityByIDFromName(new ResourceLocation(fallback), w);
        }
        return (EntityLiving) e;
    }
}
