package ru.death.chaosshard.servant;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.servant.Servants.Kind;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.ArrayList;
import java.util.List;

/**
 * Улучшения обычных слуг: по 5 на тип, у одного слуги не больше 3. Ритуал в Чаше — улучшаются только слуги этого
 * типа, которые стоят в круге (до 8 блоков от Чаши). Улучшение живёт в самом слуге: погиб — пропало.
 */
public enum ServantUpgrade {
    // Скорлупа
    HUSK_FLESH(Kind.HUSK, 0, 1, "minecraft:rotten_flesh", 4, "minecraft:leather", 0, 4),
    HUSK_BITE(Kind.HUSK, 0, 1, "minecraft:rotten_flesh", 4, "minecraft:spider_eye", 0, 2),
    HUSK_BLAST(Kind.HUSK, 1, 1, "minecraft:rotten_flesh", 4, "minecraft:gunpowder", 0, 4),
    HUSK_BAIT(Kind.HUSK, 1, 1, "minecraft:rotten_flesh", 4, "minecraft:porkchop", 0, 4),
    HUSK_SAND(Kind.HUSK, 1, 1, "minecraft:rotten_flesh", 4, "minecraft:sand", 0, 8),
    // Костяной страж
    BONE_SHIELD(Kind.BONE, 2, 2, "minecraft:bone", 4, "minecraft:shield", 0, 1),
    BONE_WITHER(Kind.BONE, 2, 2, "minecraft:bone", 4, "minecraft:soul_sand", 0, 4),
    BONE_FLAME(Kind.BONE, 2, 2, "minecraft:bone", 4, "minecraft:blaze_powder", 0, 2),
    BONE_FORT(Kind.BONE, 2, 2, "minecraft:bone", 4, "minecraft:iron_ingot", 0, 4),
    BONE_SWEEP(Kind.BONE, 3, 2, "minecraft:bone", 4, "minecraft:flint", 0, 4),
    // Палач
    EXEC_HEAVY(Kind.EXECUTIONER, 3, 3, "minecraft:emerald", 2, "minecraft:iron_block", 0, 1),
    EXEC_EXECUTE(Kind.EXECUTIONER, 3, 3, "minecraft:emerald", 2, "minecraft:skull", 0, 1),
    EXEC_THIRST(Kind.EXECUTIONER, 3, 3, "minecraft:emerald", 2, "minecraft:fermented_spider_eye", 0, 2),
    EXEC_UNSTOP(Kind.EXECUTIONER, 3, 3, "minecraft:emerald", 2, "minecraft:sugar", 0, 8),
    EXEC_CRY(Kind.EXECUTIONER, 4, 3, "minecraft:emerald", 2, "minecraft:gold_ingot", 0, 4),
    // Жнец
    REAPER_WIDE(Kind.REAPER, 1, 1, "minecraft:wheat", 4, "minecraft:iron_hoe", 0, 1),
    REAPER_MEAL(Kind.REAPER, 1, 1, "minecraft:wheat", 4, "minecraft:dye", 15, 16),
    REAPER_BOUNTY(Kind.REAPER, 2, 1, "minecraft:wheat", 4, "minecraft:golden_carrot", 0, 2),
    REAPER_PLOW(Kind.REAPER, 2, 1, "minecraft:wheat", 4, "minecraft:water_bucket", 0, 1),
    REAPER_PICKUP(Kind.REAPER, 2, 1, "minecraft:wheat", 4, "minecraft:hopper", 0, 1),
    // Лесоруб
    WOOD_SHARP(Kind.LUMBERJACK, 1, 1, "minecraft:sapling", 4, "minecraft:iron_axe", 0, 1),
    WOOD_FORESTER(Kind.LUMBERJACK, 1, 1, "minecraft:sapling", 4, "minecraft:dye", 15, 16),
    WOOD_WIDE(Kind.LUMBERJACK, 1, 1, "minecraft:sapling", 4, "minecraft:string", 0, 8),
    WOOD_LEAVES(Kind.LUMBERJACK, 2, 1, "minecraft:sapling", 4, "minecraft:shears", 0, 1),
    WOOD_CHARCOAL(Kind.LUMBERJACK, 2, 1, "minecraft:sapling", 4, "minecraft:coal", 1, 8),
    // Рудокоп
    MINE_PICK(Kind.MINER, 2, 2, "minecraft:coal", 4, "minecraft:iron_pickaxe", 0, 1),
    MINE_SENSE(Kind.MINER, 2, 2, "minecraft:coal", 4, "minecraft:redstone", 0, 16),
    MINE_WIDE(Kind.MINER, 3, 2, "minecraft:coal", 4, "minecraft:tnt", 0, 2),
    MINE_TORCH(Kind.MINER, 2, 2, "minecraft:coal", 4, "minecraft:glowstone_dust", 0, 8),
    MINE_SMELT(Kind.MINER, 3, 2, "minecraft:coal", 4, "minecraft:furnace", 0, 1);

    public static final String TAG = "chaosshard_upg";
    public static final int MAX = 3;
    public static final int RADIUS = 8;

    public final Kind kind;
    public final int minThread, essence;
    private final String sigil, item;
    private final int sigilCount, itemMeta, itemCount;

    ServantUpgrade(Kind kind, int minThread, int essence, String sigil, int sigilCount, String item, int itemMeta, int itemCount) {
        this.kind = kind;
        this.minThread = minThread;
        this.essence = essence;
        this.sigil = sigil;
        this.sigilCount = sigilCount;
        this.item = item;
        this.itemMeta = itemMeta;
        this.itemCount = itemCount;
    }

    public String key() {
        return "chaosshard.upgrade." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Номер улучшения внутри своего типа (0–4). */
    public int index() {
        int i = 0;
        for (ServantUpgrade u : values()) {
            if (u == this) {
                return i;
            }
            if (u.kind == kind) {
                i++;
            }
        }
        return i;
    }

    public static List<ServantUpgrade> of(Kind k) {
        List<ServantUpgrade> out = new ArrayList<>();
        for (ServantUpgrade u : values()) {
            if (u.kind == k) {
                out.add(u);
            }
        }
        return out;
    }

    private static Item item(String id) {
        Item i = Item.getByNameOrId(id);
        return i == null ? Items.AIR : i;
    }

    public List<ItemStack> offerings() {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(item("chaosshard:chaos_essence"), essence));
        // саженцы — любые (мета не важна), показываем дубовый
        out.add(new ItemStack(item(sigil), sigilCount, 0));
        out.add(new ItemStack(item(item), itemCount, itemMeta));
        return out;
    }

    private boolean anyMeta(String id) {
        return id.equals("minecraft:sapling");
    }

    /** Совпадает ли содержимое Чаши с подношениями (точно, порядок не важен). */
    public boolean matches(List<ItemStack> contents) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : contents) {
            if (!s.isEmpty()) {
                left.add(s.copy());
            }
        }
        Object[][] need = {{item("chaosshard:chaos_essence"), 0, essence, false},
                {item(sigil), 0, sigilCount, anyMeta(sigil)}, {item(item), itemMeta, itemCount, false}};
        for (Object[] n : need) {
            int count = (Integer) n[2];
            for (ItemStack s : left) {
                if (count > 0 && s.getItem() == n[0] && ((Boolean) n[3] || s.getMetadata() == (Integer) n[1])) {
                    int take = Math.min(count, s.getCount());
                    s.shrink(take);
                    count -= take;
                }
            }
            if (count > 0) {
                return false;
            }
        }
        for (ItemStack s : left) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static ServantUpgrade find(List<ItemStack> contents) {
        for (ServantUpgrade u : values()) {
            if (u.matches(contents)) {
                return u;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ у слуги

    public static boolean has(EntityLiving m, ServantUpgrade u) {
        return (m.getEntityData().getInteger(TAG) & (1 << u.ordinal())) != 0;
    }

    public static int count(EntityLiving m) {
        return Integer.bitCount(m.getEntityData().getInteger(TAG));
    }

    /** Провести ритуал улучшения у Чаши. true — подношения потрачены. */
    public static boolean perform(EntityPlayerMP p, ServantUpgrade u, BlockPos bowl) {
        if (ThreadBonus.level(p) < u.minThread && !p.isCreative()) {
            p.sendMessage(Breach.grimoire("chaosshard.ritual.thin", u.minThread));
            return false;
        }
        List<EntityLiving> all = p.world.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(bowl).grow(RADIUS),
                x -> x.isEntityAlive() && Servants.isServantOf(x, p.getUniqueID()) && Servants.kindOf(x) == u.kind);
        List<EntityLiving> ok = new ArrayList<>();
        boolean full = false;
        for (EntityLiving m : all) {
            if (has(m, u)) {
                continue;
            }
            if (count(m) >= MAX) {
                full = true;
                continue;
            }
            ok.add(m);
        }
        TextComponentTranslation kindName = new TextComponentTranslation("chaosshard.servant.kind." + u.kind.key());
        if (ok.isEmpty()) {
            p.sendMessage(Breach.grimoire(all.isEmpty() ? "chaosshard.upgrade.none" : full ? "chaosshard.upgrade.full" : "chaosshard.upgrade.already", kindName));
            return false;
        }
        WorldServer ws = (WorldServer) p.world;
        for (EntityLiving m : ok) {
            m.getEntityData().setInteger(TAG, m.getEntityData().getInteger(TAG) | (1 << u.ordinal()));
            ServantGear.refresh(m, p);
            m.heal(m.getMaxHealth());
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, m.posX, m.posY + 1, m.posZ, 40, 0.3, 0.6, 0.3, 0.1);
            ws.spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE, m.posX, m.posY + 1.5, m.posZ, 40, 0.3, 0.6, 0.3, 0.5);
            if (count(m) >= MAX) {
                Milestones.grant(p, "servant_upgrade3");
            }
        }
        ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, bowl.getX() + 0.5, bowl.getY() + 1, bowl.getZ() + 0.5, 60, 1.5, 0.5, 1.5, 0.03);
        p.world.playSound(null, bowl, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0F, 0.7F);
        p.world.playSound(null, bowl, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.BLOCKS, 0.8F, 1.1F);
        p.sendMessage(Breach.grimoire("chaosshard.upgrade.done", new TextComponentTranslation(u.key()), ok.size(), kindName));
        Milestones.grant(p, "servant_upgrade");
        return true;
    }
}
