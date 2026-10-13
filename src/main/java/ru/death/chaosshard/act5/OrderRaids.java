package ru.death.chaosshard.act5;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.anchor.AnchorClusters;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.garrot.GarrotRaids;
import ru.death.chaosshard.hero.HeroData;
import ru.death.chaosshard.hero.Lightbearer;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.structure.EntityOrderKnight;
import ru.death.chaosshard.thread.ThreadManager;

/**
 * Акт 5. Наступление Ордена: когда все Опоры пали, а Гаррот склонился, на захваченные края идёт сам Орден.
 * Отряд — рыцари, капитан, знаменосец Света (выжигает Скверну вокруг), на высоких уровнях — хранитель.
 * Отряд носит метку армии набегов (TAG_ARMY): к Якорю идёт и держит осаду так же, как войско Гаррота,
 * и уходит разом, когда пал капитан. С капитана — Печать Ордена: три печати ведут к столице.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class OrderRaids {

    public static final String TAG_RAID = "chaosshard_order_raid";
    public static final String TAG_CAPTAIN = "chaosshard_order_raid_captain";

    /** Наступление идёт: Опоры пали, Гаррот склонился. */
    public static boolean active(World w) {
        return ChaosConfig.order.orderRaids && HeroData.get(w).fallenCount() >= Lightbearer.values().length
                && ru.death.chaosshard.garrot.GarrotData.get(w).submitted;
    }

    /** Голос Ордена — глашатай. */
    public static void herald(EntityPlayer player, String key) {
        TextComponentTranslation name = new TextComponentTranslation("chaosshard.order.herald");
        name.getStyle().setColor(TextFormatting.GOLD).setBold(true);
        TextComponentTranslation line = new TextComponentTranslation(key);
        line.getStyle().setColor(TextFormatting.YELLOW);
        player.sendMessage(name.appendText(" ").appendSibling(line));
    }

    /** Набег Ордена на Якорь anchor уровня сети level. Возвращает капитана (или null). */
    public static EntityOrderKnight spawn(WorldServer world, BlockPos anchor, EntityPlayerMP target, int level, double angle) {
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return null;
        }
        int dist = 22 + world.rand.nextInt(8);
        BlockPos base = world.getHeight(new BlockPos(anchor.getX() + Math.cos(angle) * dist, 0, anchor.getZ() + Math.sin(angle) * dist));
        if (!world.isBlockLoaded(base)) {
            base = world.getHeight(target.getPosition().add(12, 0, 12));
        }
        EntityOrderKnight captain = knight(world, base, anchor, level, target, EntityOrderKnight.CAPTAIN);
        double hp = 70 + 20 * level;
        captain.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        captain.setHealth((float) hp);
        captain.addTag(TAG_CAPTAIN);
        captain.setGlowing(true);
        for (int i = 0; i < 2 + level; i++) {
            knight(world, base, anchor, level, target, EntityOrderKnight.GUARD);
        }
        if (level >= 2) {
            EntityOrderKnight b = knight(world, base, anchor, level, target, EntityOrderKnight.GUARD);
            b.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.BANNER, 1, 15));   // белое знамя
            b.addTag(GarrotRaids.TAG_BANNER);
            b.setGlowing(true);
            b.setCustomNameTag(TextFormatting.YELLOW + new TextComponentTranslation("chaosshard.order.bannerman").getUnformattedText());
        }
        if (level >= 4) {
            knight(world, base, anchor, level, target, EntityOrderKnight.KEEPER);
        }
        world.playSound(null, base, net.minecraft.init.SoundEvents.BLOCK_NOTE_BELL, SoundCategory.HOSTILE, 3.0F, 0.6F);
        world.spawnParticle(EnumParticleTypes.END_ROD, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 60, 2, 1, 2, 0.05);
        herald(target, "chaosshard.order.raid.start." + world.rand.nextInt(3));
        target.sendMessage(Breach.grimoire("chaosshard.order.raid.coming", level, base.getX(), base.getZ(), anchor.getX(), anchor.getZ()));
        return captain;
    }

    static EntityOrderKnight knight(WorldServer world, BlockPos base, BlockPos anchor, int level, EntityPlayerMP target, int variant) {
        EntityOrderKnight k = new EntityOrderKnight(world);
        BlockPos q = base.add(world.rand.nextInt(7) - 3, 0, world.rand.nextInt(7) - 3);
        world.getChunk(q);   // в незагруженный чанк spawnEntity молча не пускает
        BlockPos p = world.getHeight(q);
        k.setLocationAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, world.rand.nextFloat() * 360, 0);
        k.setup(variant);
        k.addTag(GarrotRaids.TAG_ARMY);
        k.addTag(TAG_RAID);
        k.getEntityData().setLong(GarrotRaids.TAG_RAID, anchor.toLong());
        k.getEntityData().setInteger(GarrotRaids.TAG_RAID_LVL, level);
        if (!world.spawnEntity(k)) {
            // не пустило (чанк, чужой мод) — вторая попытка прямо в точке сбора
            BlockPos b = world.getHeight(base);
            world.getChunk(b);
            k.setLocationAndAngles(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, k.rotationYaw, 0);
            if (!world.spawnEntity(k)) {
                ru.death.chaosshard.ChaosShard.LOG.warn("Рыцарь Ордена не появился у {}", b);
            }
        }
        k.setAttackTarget(target);
        return k;
    }

    /** Пал капитан — набег отбит: отряд уходит, Якорь крепнет, Нить и Печать. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity e = event.getEntityLiving();
        if (e.world.isRemote || !e.getTags().contains(TAG_CAPTAIN)) {
            return;
        }
        GarrotRaids.retreat(e.world);
        int lvl = Math.max(1, e.getEntityData().getInteger(GarrotRaids.TAG_RAID_LVL));
        TileAnchor a = AnchorClusters.at(e.world, BlockPos.fromLong(e.getEntityData().getLong(GarrotRaids.TAG_RAID)));
        if (a != null) {
            a.repelled(lvl);
        }
        EntityPlayerMP p = credited(e, event.getSource().getTrueSource(), a);
        if (p != null) {
            ThreadManager.addPoints(p, ChaosConfig.order.captainReward);
            herald(p, "chaosshard.order.raid.fall." + e.world.rand.nextInt(3));
            p.sendMessage(Breach.grimoire("chaosshard.order.raid.repelled", 50 * lvl));
            Milestones.grant(p, Milestones.ORDER_RAID);
        }
    }

    /** Кому засчитать: мне, хозяину слуги-убийцы или хозяину Якоря. */
    static EntityPlayerMP credited(Entity dead, Entity killer, TileAnchor a) {
        if (killer instanceof EntityPlayerMP) {
            return (EntityPlayerMP) killer;
        }
        if (ru.death.chaosshard.servant.Servants.isServant(killer) && ru.death.chaosshard.servant.Servants.owner(killer) instanceof EntityPlayerMP) {
            return (EntityPlayerMP) ru.death.chaosshard.servant.Servants.owner(killer);
        }
        if (a != null && a.getOwner() != null) {
            EntityPlayer o = dead.world.getPlayerEntityByUUID(a.getOwner());
            return o instanceof EntityPlayerMP ? (EntityPlayerMP) o : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        Entity e = event.getEntityLiving();
        if (e.world.isRemote || !e.getTags().contains(TAG_CAPTAIN)) {
            return;
        }
        int lvl = Math.max(1, e.getEntityData().getInteger(GarrotRaids.TAG_RAID_LVL));
        event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, new ItemStack(ModItems.ORDER_SEAL)));
        event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, new ItemStack(ModItems.CHAOS_ESSENCE, 1 + lvl)));
        if (e.world.rand.nextBoolean()) {
            event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ, new ItemStack(ModItems.WORLD_SHARD)));
        }
    }

    private OrderRaids() {
    }
}
