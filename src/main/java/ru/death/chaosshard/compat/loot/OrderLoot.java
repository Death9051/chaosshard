package ru.death.chaosshard.compat.loot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.hero.Lightbearer;
import ru.death.chaosshard.structure.EntityOrderKnight;

import java.util.ArrayList;
import java.util.List;

/**
 * Добыча с Ордена и войска Гаррота — предметы Reliquary и Bountiful Baubles (если они есть в сборке).
 * Рыцари носят святую воду, капитаны Гаррота — то, что Reliquary берёт с иссохших скелетов,
 * Светочи оставляют по реликвии и безделушке, постройки Ордена прячут их в сундуках.
 * Всё ищется по имени: без мода просто ничего не выпадает.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class OrderLoot {

    /** Реликвия Reliquary и безделушка Bountiful Baubles каждого Светоча. */
    static final String[][] HERO = {
            {"xreliquary:mercy_cross", "bountifulbaubles:shieldcobalt"},
            {"xreliquary:angelheart_vial", "bountifulbaubles:amuletcross"},
            {"xreliquary:salamander_eye", "bountifulbaubles:trinketobsidianskull"},
            {"xreliquary:fortune_coin", "bountifulbaubles:trinketluckyhorseshoe"},
            {"xreliquary:hero_medallion", "bountifulbaubles:crowngold"}};

    /** Сундуки Ордена: имя → вес. Монастырь — святыни, остальные постройки — походное. */
    static final Object[][] MONASTERY = {
            {"xreliquary:glowing_water", 30}, {"xreliquary:holy_hand_grenade", 10}, {"xreliquary:angelheart_vial", 3},
            {"bountifulbaubles:amuletcross", 3}, {"bountifulbaubles:trinketapple", 6}, {"bountifulbaubles:trinketvitamins", 6}};
    static final Object[][] OUTPOST = {
            {"xreliquary:glowing_water", 20}, {"xreliquary:angelic_feather", 3}, {"bountifulbaubles:ringiron", 8},
            {"bountifulbaubles:trinketluckyhorseshoe", 3}, {"bountifulbaubles:shieldcobalt", 3},
            {"bountifulbaubles:trinketmagiclenses", 4}, {"bountifulbaubles:potionrecall", 6}};

    static Item item(String id) {
        return Item.getByNameOrId(id);
    }

    static void drop(Entity at, ItemStack s, List<EntityItem> drops) {
        if (!s.isEmpty()) {
            drops.add(new EntityItem(at.world, at.posX, at.posY + 0.5, at.posZ, s));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!(e.world instanceof WorldServer) || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)) {
            return;
        }
        java.util.Random r = e.world.rand;
        if (e instanceof EntityOrderKnight) {
            boolean captain = ((EntityOrderKnight) e).variant() != EntityOrderKnight.GUARD;
            Item water = item("xreliquary:glowing_water");
            if (water != null && r.nextFloat() < (captain ? 0.25F : 0.06F) * (1 + event.getLootingLevel() * 0.5F)) {
                drop(e, new ItemStack(water), event.getDrops());
            }
            Item grenade = item("xreliquary:holy_hand_grenade");
            if (captain && grenade != null && r.nextFloat() < 0.08F) {
                drop(e, new ItemStack(grenade, 1 + r.nextInt(2)), event.getDrops());
            }
        } else if (e.getTags().contains(ru.death.chaosshard.garrot.GarrotRaids.TAG_CAPTAIN)) {
            // Reliquary сам решает, что падает с иссохшего скелета (его конфиг и шансы)
            roll((WorldServer) e.world, new ResourceLocation("xreliquary", "inject/entities/wither_skeleton"), e,
                    (EntityPlayerMP) event.getSource().getTrueSource(), event);
        }
    }

    static void roll(WorldServer ws, ResourceLocation table, EntityLivingBase e, EntityPlayerMP p, LivingDropsEvent event) {
        LootTable t = ws.getLootTableManager().getLootTableFromLocation(table);
        if (t == null || t == LootTable.EMPTY_LOOT_TABLE) {
            return;
        }
        LootContext ctx = new LootContext.Builder(ws).withLootedEntity(e).withPlayer(p).withDamageSource(event.getSource())
                .withLuck(p.getLuck()).build();
        for (ItemStack s : t.generateLootForPools(ws.rand, ctx)) {
            drop(e, s, event.getDrops());
        }
    }

    /** Реликвия и безделушка павшего Светоча — рядом с трофеем. */
    public static void heroRelics(WorldServer ws, Lightbearer h, BlockPos at) {
        for (String id : HERO[h.ordinal()]) {
            Item it = item(id);
            if (it != null) {
                EntityItem ei = new EntityItem(ws, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, new ItemStack(it));
                ei.setEntityInvulnerable(true);
                ei.setNoDespawn();
                ws.spawnEntity(ei);
            }
        }
    }

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        ResourceLocation n = event.getName();
        if (!Tags.MOD_ID.equals(n.getNamespace()) || !n.getPath().startsWith("structures/")) {
            return;
        }
        LootPool pool = pool(n.getPath().equals("structures/monastery") ? MONASTERY : OUTPOST);
        if (pool != null) {
            event.getTable().addPool(pool);
        }
    }

    /** Один бросок на сундук; пустышка — чтобы находки были редкими. Null — ни одного мода нет. */
    static LootPool pool(Object[][] table) {
        List<LootEntry> entries = new ArrayList<>();
        for (Object[] row : table) {
            Item it = item((String) row[0]);
            if (it != null) {
                entries.add(new LootEntryItem(it, (Integer) row[1], 0, new LootFunction[0], new LootCondition[0], (String) row[0]));
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        entries.add(new LootEntryEmpty(60, 0, new LootCondition[0], "chaosshard:nothing"));
        return new LootPool(entries.toArray(new LootEntry[0]), new LootCondition[0], new RandomValueRange(1, 2),
                new RandomValueRange(0, 0), "chaosshard_relics");
    }

    private OrderLoot() {
    }
}
