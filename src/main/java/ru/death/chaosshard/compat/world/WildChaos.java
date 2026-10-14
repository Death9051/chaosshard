package ru.death.chaosshard.compat.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.Corruption;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.garrot.GarrotRaids;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.structure.EntityOrderKnight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lycanite's Mobs в мире Осколка.
 * — Обитатели Скверны: на захваченной земле селятся демоны. Осколка и его слуг не трогают, Орден и войско Гаррота — бьют.
 * — Свет Ордена: капитан Ордена, вступив в бой, зовёт стихийных стражей света.
 * Без Lycanite's Mobs ничего не происходит.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class WildChaos {

    public static final String TAG_DEMON = "chaosshard_defiled_demon";
    public static final String TAG_LIGHT = "chaosshard_order_light";
    static final String TAG_CALLED = "chaosshard_called_light";

    /** Демоны Скверны: обычные чаще, крупные реже. */
    static final String[] DEMONS = {"trite", "trite", "belph", "belph", "pinky", "ghoul", "geist", "behemoth", "cacodemon"};
    /** Стражи света, которых зовут капитаны Ордена. */
    static final String[] LIGHT = {"aegis", "wisp", "sylph"};

    static boolean lycanites() {
        return Loader.isModLoaded("lycanitesmobs");
    }

    static EntityLiving create(World w, String name) {
        Entity e = EntityList.createEntityByIDFromName(new ResourceLocation("lycanitesmobs", name), w);
        return e instanceof EntityLiving ? (EntityLiving) e : null;
    }

    // ------------------------------------------------------------------ обитатели Скверны

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer) || !ChaosConfig.compat.defiledDemons
                || !lycanites() || event.world.getTotalWorldTime() % ChaosConfig.compat.demonInterval != 0) {
            return;
        }
        WorldServer ws = (WorldServer) event.world;
        for (BlockPos p : new ArrayList<>(AnchorData.get(ws).anchors)) {
            if (!ws.isBlockLoaded(p)) {
                continue;
            }
            TileEntity te = ws.getTileEntity(p);
            if (te instanceof TileAnchor && ((TileAnchor) te).isCaptured()) {
                populate(ws, (TileAnchor) te);
            }
        }
    }

    /** Подселить демона к Якорю, если их меньше предела. true — подселил. */
    public static boolean populate(WorldServer ws, TileAnchor a) {
        int r = a.radius();
        BlockPos c = a.getPos();
        int have = ws.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(c).grow(r, 32, r),
                x -> x.isEntityAlive() && x.getTags().contains(TAG_DEMON)).size();
        if (have >= ChaosConfig.compat.demonsBase + a.level()) {
            return false;
        }
        Random rand = ws.rand;
        for (int i = 0; i < 8; i++) {
            BlockPos ground = ws.getHeight(c.add(rand.nextInt(2 * r + 1) - r, 0, rand.nextInt(2 * r + 1) - r)).down();
            if (!Corruption.isCorrupted(ws.getBlockState(ground)) || ws.getClosestPlayer(ground.getX(), ground.getY(), ground.getZ(), 12, false) != null) {
                continue;
            }
            EntityLiving d = create(ws, DEMONS[rand.nextInt(Math.min(DEMONS.length, 5 + a.level()))]);
            if (d == null) {
                return false;
            }
            d.setLocationAndAngles(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, rand.nextFloat() * 360, 0);
            if (!d.getCanSpawnHere() && !d.isNotColliding()) {
                continue;
            }
            d.onInitialSpawn(ws.getDifficultyForLocation(ground), null);
            d.addTag(TAG_DEMON);
            if (!ws.spawnEntity(d)) {
                continue;   // не пустили (незагруженный чанк, чужой мод) — пробуем другое место
            }
            ws.spawnParticle(EnumParticleTypes.PORTAL, d.posX, d.posY + 1, d.posZ, 30, 0.4, 0.8, 0.4, 0.4);
            return true;
        }
        return false;
    }

    /** Враги демонов Скверны и стражей Лорда: Орден Света и войско Гаррота. */
    public static boolean isOrderSide(Entity e) {
        return e instanceof EntityOrderKnight || e instanceof ru.death.chaosshard.hero.EntityHero
                || e.getTags().contains(GarrotRaids.TAG_ARMY) || e.getTags().contains(TAG_LIGHT);
    }

    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        if (e.world.isRemote || !(e instanceof EntityCreature)) {
            return;
        }
        EntityCreature c = (EntityCreature) e;
        if (e.getTags().contains(TAG_DEMON)) {
            c.targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(c, EntityLivingBase.class, 10, true, false,
                    x -> x != null && isOrderSide(x) && !Servants.isServant(x)));
        } else if (e.getTags().contains(TAG_LIGHT)) {
            c.targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(c, EntityLivingBase.class, 10, true, false,
                    x -> x instanceof EntityPlayer || x != null && (Servants.isServant(x) || x.getTags().contains(TAG_DEMON))));
        }
    }

    /** Демоны не трогают Осколка и слуг; стражи света не трогают Орден. */
    @SubscribeEvent
    public static void onTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase who = event.getEntityLiving(), target = event.getTarget();
        if (target == null || who.world.isRemote || !(who instanceof EntityLiving)) {
            return;
        }
        if (who.getTags().contains(TAG_DEMON) && (target instanceof EntityPlayer || Servants.isServant(target)
                || target.getTags().contains(TAG_DEMON))) {
            ((EntityLiving) who).setAttackTarget(null);
        } else if (who.getTags().contains(TAG_LIGHT) && isOrderSide(target)) {
            ((EntityLiving) who).setAttackTarget(null);
        } else if (who instanceof EntityOrderKnight && ((EntityOrderKnight) who).variant() == EntityOrderKnight.CAPTAIN
                && !who.getTags().contains(TAG_CALLED) && (target instanceof EntityPlayer || Servants.isServant(target))) {
            callLight((EntityLiving) who, target);
        }
    }

    /** Капитан Ордена зовёт стражей света — один раз за жизнь. */
    public static int callLight(EntityLiving captain, EntityLivingBase target) {
        captain.addTag(TAG_CALLED);
        if (!lycanites() || !ChaosConfig.compat.orderLight) {
            return 0;
        }
        WorldServer ws = (WorldServer) captain.world;
        int n = 1 + ws.rand.nextInt(2), made = 0;
        for (int i = 0; i < n; i++) {
            EntityLiving l = create(ws, LIGHT[ws.rand.nextInt(LIGHT.length)]);
            if (l == null) {
                break;
            }
            double a = ws.rand.nextDouble() * Math.PI * 2;
            l.setLocationAndAngles(captain.posX + Math.cos(a) * 2, captain.posY + 0.5, captain.posZ + Math.sin(a) * 2, 0, 0);
            l.onInitialSpawn(ws.getDifficultyForLocation(captain.getPosition()), null);
            l.addTag(TAG_LIGHT);
            ws.spawnEntity(l);
            l.setAttackTarget(target);
            ws.spawnParticle(EnumParticleTypes.END_ROD, l.posX, l.posY + 1, l.posZ, 25, 0.3, 0.6, 0.3, 0.05);
            made++;
        }
        return made;
    }

    // ------------------------------------------------------------------ добыча подземелий

    /** Что Хаос прячет в чужих подземельях. */
    static final Object[][] DUNGEON = {
            {"chaosshard:world_shard", 0, 1, 3, 20}, {"chaosshard:veil_shard", 0, 1, 2, 12}, {"chaosshard:chaos_essence", 0, 2, 4, 14}};
    /** Чьи сундуки: ванильные подземелья (их же берут катакомбы Roguelike), карманы DimDoors, подземелья Скверны. */
    static final String[] DUNGEON_TABLES = {"minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/stronghold_corridor", "minecraft:chests/stronghold_crossing", "minecraft:chests/stronghold_library",
            "minecraft:chests/desert_pyramid", "minecraft:chests/jungle_temple", "dimdoors:dungeon_chest",
            "defiledlands:chests/dungeon_defiled"};

    @SubscribeEvent
    public static void onLootLoad(net.minecraftforge.event.LootTableLoadEvent event) {
        String n = event.getName().toString();
        boolean match = false;
        for (String t : DUNGEON_TABLES) {
            match |= t.equals(n);
        }
        if (!match) {
            return;
        }
        List<net.minecraft.world.storage.loot.LootEntry> entries = new ArrayList<>();
        for (Object[] row : DUNGEON) {
            net.minecraft.item.Item it = net.minecraft.item.Item.getByNameOrId((String) row[0]);
            if (it == null) {
                continue;
            }
            entries.add(new net.minecraft.world.storage.loot.LootEntryItem(it, (Integer) row[4], 0,
                    new net.minecraft.world.storage.loot.functions.LootFunction[]{
                            new net.minecraft.world.storage.loot.functions.SetCount(new net.minecraft.world.storage.loot.conditions.LootCondition[0],
                                    new net.minecraft.world.storage.loot.RandomValueRange((Integer) row[2], (Integer) row[3]))},
                    new net.minecraft.world.storage.loot.conditions.LootCondition[0], (String) row[0]));
        }
        // сфера ритуала — случайная из простых
        net.minecraft.item.Item sphere = ru.death.chaosshard.init.ModItems.RITUAL_SPHERE;
        if (sphere != null) {
            entries.add(new net.minecraft.world.storage.loot.LootEntryItem(sphere, 6, 0,
                    new net.minecraft.world.storage.loot.functions.LootFunction[]{
                            new net.minecraft.world.storage.loot.functions.SetMetadata(new net.minecraft.world.storage.loot.conditions.LootCondition[0],
                                    new net.minecraft.world.storage.loot.RandomValueRange(0, 6))},
                    new net.minecraft.world.storage.loot.conditions.LootCondition[0], "chaosshard:ritual_sphere"));
        }
        entries.add(new net.minecraft.world.storage.loot.LootEntryEmpty(40, 0, new net.minecraft.world.storage.loot.conditions.LootCondition[0],
                "chaosshard:nothing"));
        event.getTable().addPool(new net.minecraft.world.storage.loot.LootPool(entries.toArray(new net.minecraft.world.storage.loot.LootEntry[0]),
                new net.minecraft.world.storage.loot.conditions.LootCondition[0], new net.minecraft.world.storage.loot.RandomValueRange(1, 2),
                new net.minecraft.world.storage.loot.RandomValueRange(0, 0), "chaosshard_dungeon"));
    }

    private WildChaos() {
    }
}
