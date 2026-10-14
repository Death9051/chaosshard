package ru.death.chaosshard.servant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketBeasts;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.servant.BeastData.Beast;
import ru.death.chaosshard.servant.BeastData.Entry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Уникальные звери: опыт, уровни, перки, возвращение из Нити, Вознесение.
 * Звери — отголоски старых знакомств Осколка: по Нити приходит не тварь, а память о ней.
 */
public class Beasts {

    // ------------------------------------------------------------------ перки (индексы 0–3 — первая ветка, 4–7 — вторая)
    public static final int HARE_BAG = 0, HARE_MAGNET = 1, HARE_PICKY = 2, HARE_SWIFT = 3,
            HARE_WARM = 4, HARE_SHARED = 5, HARE_MOON = 6, HARE_CLEANSE = 7;
    public static final int CAT_GOURMET = 0, CAT_FISHER = 1, CAT_HOARD = 2, CAT_LUCKY = 3,
            CAT_PURR = 4, CAT_EYE = 5, CAT_VERMIN = 6, CAT_NINTH = 7;
    public static final int TIGER_BLEED = 0, TIGER_AMBUSH = 1, TIGER_DEVOUR = 2, TIGER_MARK = 3,
            TIGER_HIDE = 4, TIGER_PACK = 5, TIGER_DREAD = 6, TIGER_WARDEN = 7;

    public static final int RETURN_TICKS = 20 * 60 * 5, RETURN_NINTH = 20 * 60;
    public static final int TREAT_COOLDOWN = 20 * 60 * 10, TREAT_XP = 20;
    public static final int SAVE_COOLDOWN = 20 * 60 * 10;
    public static final String TAG_MARK = "chaosshard_mark";

    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Set<UUID> OPEN_BAGS = new HashSet<>();

    // ------------------------------------------------------------------ доступ

    public static Entry entry(World w, UUID owner, Beast b) {
        return BeastData.get(w).entry(owner, b);
    }

    public static Entry entry(EntityChaosBeast e) {
        UUID o = ownerId(e);
        return o == null ? null : entry(e.world, o, e.beast());
    }

    public static UUID ownerId(Entity e) {
        return Servants.isServant(e) ? e.getEntityData().getUniqueId(Servants.TAG_OWNER) : null;
    }

    public static boolean has(EntityPlayer p, Beast b, int perk) {
        return entry(p.world, p.getUniqueID(), b).has(perk);
    }

    public static void changed(World w, UUID owner) {
        BeastData.get(w).markDirty();
        DIRTY.add(owner);
    }

    public static void sync(EntityPlayerMP p) {
        DIRTY.remove(p.getUniqueID());
        ChaosNetwork.CHANNEL.sendTo(new PacketBeasts(BeastData.get(p.world).clientTag(p.getUniqueID())), p);
    }

    /** Найти живого зверя хозяина в мире хозяина. */
    public static EntityChaosBeast find(EntityPlayer p, Beast b, double radius) {
        List<EntityChaosBeast> l = p.world.getEntitiesWithinAABB(EntityChaosBeast.class, p.getEntityBoundingBox().grow(radius),
                e -> e.isEntityAlive() && e.beast() == b && Servants.isServantOf(e, p.getUniqueID()));
        return l.isEmpty() ? null : l.get(0);
    }

    /** Имя зверя для сообщений: данное хозяином или по виду. */
    public static net.minecraft.util.text.ITextComponent title(Beast b, Entry e) {
        return e != null && !e.name.isEmpty() ? new net.minecraft.util.text.TextComponentString(e.name)
                : new TextComponentTranslation("chaosshard.beast." + b.key());
    }

    public static final int NAME_MAX = 24;

    /** Переименовать зверя (пустое имя — вернуть имя по виду). */
    public static void rename(EntityPlayerMP p, Beast b, String raw) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        if (!e.known) {
            return;
        }
        String name = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(raw == null ? "" : raw).trim();
        name = net.minecraft.util.ChatAllowedCharacters.filterAllowedCharacters(name);
        if (name.length() > NAME_MAX) {
            name = name.substring(0, NAME_MAX);
        }
        e.name = name;
        changed(p.world, p.getUniqueID());
        EntityChaosBeast beast = find(p, b, 128);
        if (beast != null) {
            refresh(beast);
            ((WorldServer) p.world).spawnParticle(EnumParticleTypes.HEART, beast.posX, beast.posY + 0.9, beast.posZ, 4, 0.3, 0.2, 0.3, 0.0);
        }
        p.sendStatusMessage(Breach.grimoire(name.isEmpty() ? "chaosshard.beast.renamed_default" : "chaosshard.beast.renamed",
                title(b, e)), true);
        sync(p);
    }

    // ------------------------------------------------------------------ опыт

    /** Перелив Нити: опыт всем знакомым зверям хозяина, даже тем, что сейчас не рядом. Возвращает, скольким досталось. */
    public static int overflowXp(EntityPlayerMP p, int amount) {
        int n = 0;
        for (Beast b : Beast.values()) {
            Entry e = entry(p.world, p.getUniqueID(), b);
            if (!e.known || e.ascended || e.xp >= BeastData.XP[4]) {
                continue;
            }
            EntityChaosBeast live = find(p, b, 256);
            if (live != null) {
                addXp(live, amount);
            } else {
                e.xp = Math.min(BeastData.XP[4], e.xp + amount);
                changed(p.world, p.getUniqueID());
            }
            n++;
        }
        if (n > 0) {
            sync(p);
        }
        return n;
    }

    public static void addXp(EntityChaosBeast beast, int amount) {
        UUID o = ownerId(beast);
        if (o == null || amount <= 0) {
            return;
        }
        Entry e = entry(beast.world, o, beast.beast());
        int before = e.level();
        e.xp = Math.min(BeastData.XP[4], e.xp + amount);
        changed(beast.world, o);
        if (e.level() != before) {
            levelUp(beast, e);
        }
    }

    private static void levelUp(EntityChaosBeast beast, Entry e) {
        refresh(beast);
        WorldServer ws = (WorldServer) beast.world;
        ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, beast.posX, beast.posY + 0.8, beast.posZ, 50, 0.4, 0.6, 0.4, 0.1);
        beast.world.playSound(null, beast.getPosition(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.8F, 0.8F);
        EntityPlayer p = Servants.owner(beast);
        if (p != null) {
            p.sendMessage(Breach.grimoire("chaosshard.beast.levelup",
                    title(beast.beast(), e), e.level()));
            if (e.level() >= 3) {
                Milestones.grant((EntityPlayerMP) p, "beast_level3");
            }
        }
    }

    /** Характеристики и подпись зверя под его уровень. */
    public static void refresh(EntityChaosBeast beast) {
        Entry e = entry(beast);
        if (e == null) {
            return;
        }
        int lvl = e.level();
        beast.setLevel(lvl);
        double hp = beast.baseHp() + beast.hpPerLevel() * (lvl - 1) + 5 * beast.getEntityData().getInteger("chaosshard_thread_hp");
        beast.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(hp);
        beast.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(beast.baseDamage() + beast.damagePerLevel() * (lvl - 1));
        beast.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(
                beast.beast() == Beast.TIGER && e.has(TIGER_HIDE) ? 8 : 0);
        if (beast.getHealth() > beast.getMaxHealth()) {
            beast.setHealth(beast.getMaxHealth());
        }
        EntityPlayer o = Servants.owner(beast);
        String owner = o != null ? o.getName() : beast.getEntityData().getString("chaosshard_owner_name");
        if (o != null) {
            beast.getEntityData().setString("chaosshard_owner_name", o.getName());
        }
        String name = e.name.isEmpty() ? beast.beast().kind.name : e.name;
        beast.setCustomNameTag((lvl >= 5 ? "§6" : "§d") + name + " §7[" + lvl + "] (" + owner + ")");
        if (beast.getEntityData().hasKey(Servants.TAG_GUARD)) {
            int r = beast.beast() == Beast.TIGER && e.has(TIGER_WARDEN) ? 16 : 8;
            beast.setHomePosAndDistance(BlockPos.fromLong(beast.getEntityData().getLong(Servants.TAG_GUARD)), r);
        }
    }

    // ------------------------------------------------------------------ призыв и возвращение

    /** Зверь только что призван ритуалом. */
    public static void onSummoned(EntityPlayerMP p, EntityChaosBeast beast) {
        Entry e = entry(p.world, p.getUniqueID(), beast.beast());
        e.known = true;
        e.returnAt = 0;
        e.entity = beast.getUniqueID();
        beast.getEntityData().setString("chaosshard_owner_name", p.getName());
        beast.getEntityData().setInteger("chaosshard_thread_hp", ru.death.chaosshard.thread.ThreadBonus.level(p));
        changed(p.world, p.getUniqueID());
        refresh(beast);
    }

    /** Перед появлением в мире: этот зверь — тот самый (иначе onJoin примет его за старую копию). */
    public static void preSpawn(EntityPlayerMP p, EntityChaosBeast beast) {
        Entry e = entry(p.world, p.getUniqueID(), beast.beast());
        e.entity = beast.getUniqueID();
        e.returnAt = 0;
    }

    /** Сколько зверей игрока сейчас в Нити. */
    public static int countInThread(EntityPlayerMP p) {
        int n = 0;
        for (Beast b : Beast.values()) {
            if (inThread(p, b) > 0) {
                n++;
            }
        }
        return n;
    }

    /** Зов из Нити (старший ритуал): все звери, ушедшие в Нить, могут вернуться прямо сейчас. */
    public static int pullFromThread(EntityPlayerMP p) {
        int n = 0;
        long now = p.world.getTotalWorldTime();
        for (Beast b : Beast.values()) {
            Entry e = entry(p.world, p.getUniqueID(), b);
            if (e.returnAt > now) {
                e.returnAt = now;
                n++;
            }
        }
        if (n > 0) {
            changed(p.world, p.getUniqueID());
        }
        return n;
    }

    /** Ещё в Нити? Тогда ритуалом не позвать. Возвращает оставшиеся тики или 0. */
    public static long inThread(EntityPlayerMP p, Beast b) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        return e.returnAt > 0 ? Math.max(1, e.returnAt - p.world.getTotalWorldTime()) : 0;
    }

    public static final int RECALL_TICKS = 1200;
    private static final java.util.Map<String, Long> RECALL = new java.util.HashMap<>();

    /** Позвать знакомого зверя из Гримуара: живого — к себе, отпущенного — заново. */
    public static void recall(EntityPlayerMP p, Beast b) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        if (!e.known) {
            return;
        }
        if (e.returnAt > 0) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.beast.recall.thread"), true);
            return;
        }
        long now = p.world.getTotalWorldTime();
        String key = p.getUniqueID() + "/" + b.key();
        Long ready = RECALL.get(key);
        if (ready != null && now < ready && !p.isCreative()) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.beast.recall.wait", (ready - now) / 20 + 1), true);
            return;
        }
        RECALL.put(key, now + RECALL_TICKS);
        EntityChaosBeast found = null;
        if (e.entity != null) {
            for (WorldServer w : p.getServer().worlds) {
                net.minecraft.entity.Entity x = w.getEntityFromUuid(e.entity);
                if (x instanceof EntityChaosBeast && x.isEntityAlive()) {
                    found = (EntityChaosBeast) x;
                    break;
                }
            }
        }
        if (found != null && found.world == p.world) {
            BlockPos at = p.getPosition().offset(p.getHorizontalFacing().getOpposite(), 2);
            found.getEntityData().removeTag(Servants.TAG_GUARD);
            found.detachHome();
            found.getNavigator().clearPath();
            found.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            WorldServer ws = (WorldServer) p.world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, found.posX, found.posY + 0.5, found.posZ, 60, 0.4, 0.8, 0.4, 0.8);
            p.world.playSound(null, at, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.NEUTRAL, 0.8F, 0.8F);
            p.sendStatusMessage(Breach.grimoire("chaosshard.beast.recall.done", title(b, e)), true);
            return;
        }
        if (found != null) {
            found.setDead();
        }
        comeBack(p, b, e);
    }

    private static void comeBack(EntityPlayerMP p, Beast b, Entry e) {
        e.returnAt = 0;
        BlockPos at = p.getPosition().offset(p.getHorizontalFacing().getOpposite(), 2);
        net.minecraft.entity.EntityLiving m = Servants.spawn(p, b.kind, at);
        if (m instanceof EntityChaosBeast) {
            onSummoned(p, (EntityChaosBeast) m);
            WorldServer ws = (WorldServer) p.world;
            ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, m.posX, m.posY + 0.5, m.posZ, 80, 0.4, 0.8, 0.4, 0.05);
            ws.spawnParticle(EnumParticleTypes.PORTAL, m.posX, m.posY + 0.5, m.posZ, 80, 0.4, 0.8, 0.4, 0.8);
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, m.posX, m.posY + 1.5, m.posZ, 30, 0.2, 1.0, 0.2, 0.0);
            p.world.playSound(null, at, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.NEUTRAL, 1.0F, 0.8F);
            p.world.playSound(null, at, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.NEUTRAL, 0.8F, 0.6F);
            p.sendMessage(Breach.grimoire("chaosshard.beast.returned", title(b, e)));
        }
        changed(p.world, p.getUniqueID());
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 20 != 7) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        if (p.isDead) {
            return;
        }
        long now = p.world.getTotalWorldTime();
        for (Beast b : Beast.values()) {
            Entry e = entry(p.world, p.getUniqueID(), b);
            if (e.returnAt > 0 && now >= e.returnAt) {
                comeBack(p, b, e);
            }
        }
        // раз в минуту — звери рядом получают немного опыта просто за то, что они рядом
        if (p.ticksExisted % 1200 == 7) {
            for (EntityChaosBeast beast : p.world.getEntitiesWithinAABB(EntityChaosBeast.class, p.getEntityBoundingBox().grow(16),
                    x -> Servants.isServantOf(x, p.getUniqueID()))) {
                addXp(beast, 1);
            }
        }
        if (DIRTY.contains(p.getUniqueID())) {
            sync(p);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityChaosBeast)) {
            return;
        }
        EntityChaosBeast beast = (EntityChaosBeast) event.getEntity();
        Entry e = entry(beast);
        if (e == null) {
            return;
        }
        // зверь у Осколка один: если это не тот, что записан, — это старая копия
        if (e.entity != null && !e.entity.equals(beast.getUniqueID())) {
            beast.setDead();
            event.setCanceled(true);
            return;
        }
        refresh(beast);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase dead = event.getEntityLiving();
        if (dead.world.isRemote) {
            return;
        }
        // хозяин погибает — Лунный прыжок зайца
        if (dead instanceof EntityPlayerMP && lunarLeap((EntityPlayerMP) dead)) {
            event.setCanceled(true);
            return;
        }
        // зверь погиб — уходит в Нить
        if (dead instanceof EntityChaosBeast) {
            EntityChaosBeast beast = (EntityChaosBeast) dead;
            UUID o = ownerId(beast);
            if (o != null) {
                Entry e = entry(dead.world, o, beast.beast());
                if (beast.getUniqueID().equals(e.entity)) {
                    int lvl = e.level();
                    if (lvl < 5) {
                        int floor = BeastData.XP[lvl - 1];
                        e.xp = Math.max(floor, e.xp - (e.xp - floor) / 4);
                    }
                    boolean ninth = beast.beast() == Beast.CAT && e.has(CAT_NINTH);
                    e.returnAt = dead.world.getTotalWorldTime() + (ninth ? RETURN_NINTH : RETURN_TICKS);
                    e.entity = null;
                    changed(dead.world, o);
                    EntityPlayer p = dead.world.getMinecraftServer().getPlayerList().getPlayerByUUID(o);
                    if (p != null) {
                        p.sendMessage(Breach.grimoire("chaosshard.beast.to_thread",
                                title(beast.beast(), e), ninth ? 1 : 5));
                    }
                }
            }
            ((WorldServer) dead.world).spawnParticle(EnumParticleTypes.PORTAL, dead.posX, dead.posY + 0.5, dead.posZ, 60, 0.3, 0.5, 0.3, 0.8);
            return;
        }
        // убийство тигром
        Entity src = event.getSource().getTrueSource();
        if (src instanceof EntityChaosBeast && ((EntityChaosBeast) src).beast() == Beast.TIGER) {
            EntityChaosBeast tiger = (EntityChaosBeast) src;
            addXp(tiger, Math.max(1, (int) (dead.getMaxHealth() / 4)));
            Entry e = entry(tiger);
            if (e != null && e.has(TIGER_DEVOUR)) {
                tiger.heal(tiger.getMaxHealth() * 0.25F);
            }
            EntityPlayer p = Servants.owner(tiger);
            if (e != null && e.level() >= 5 && p instanceof EntityPlayerMP) {
                ru.death.chaosshard.spell.ChaosEnergy.set((EntityPlayerMP) p, ru.death.chaosshard.spell.ChaosEnergy.get(p) + 5);
                p.heal(2);
            }
        }
    }

    private static boolean lunarLeap(EntityPlayerMP p) {
        Entry e = entry(p.world, p.getUniqueID(), Beast.HARE);
        long now = p.world.getTotalWorldTime();
        if (e.level() < 5 || now < e.saveReady) {
            return false;
        }
        EntityChaosBeast hare = find(p, Beast.HARE, 48);
        if (hare == null) {
            return false;
        }
        e.saveReady = now + SAVE_COOLDOWN;
        changed(p.world, p.getUniqueID());
        p.setHealth(8);
        p.clearActivePotions();
        p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 200, 1));
        p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 200, 1));
        p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 100, 2));
        hare.setPositionAndUpdate(p.posX, p.posY, p.posZ);
        WorldServer ws = (WorldServer) p.world;
        ws.spawnParticle(EnumParticleTypes.END_ROD, p.posX, p.posY + 1, p.posZ, 60, 0.4, 0.8, 0.4, 0.15);
        p.world.playSound(null, p.getPosition(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        p.sendMessage(Breach.grimoire("chaosshard.beast.hare.saved"));
        return true;
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase hurt = event.getEntityLiving();
        if (hurt.world.isRemote) {
            return;
        }
        Entity src = event.getSource().getTrueSource();
        // Метка тигра: хозяин бьёт помеченного сильнее
        if (src instanceof EntityPlayer && hurt.getEntityData().getLong(TAG_MARK) > hurt.world.getTotalWorldTime()) {
            event.setAmount(event.getAmount() * 1.15F);
        }
        // Вожак стаи: остальные слуги рядом с тигром бьют сильнее
        if (src != null && Servants.isServant(src) && !(src instanceof EntityChaosBeast && ((EntityChaosBeast) src).beast() == Beast.TIGER)) {
            UUID o = ownerId(src);
            if (o != null && entry(src.world, o, Beast.TIGER).has(TIGER_PACK)) {
                List<EntityChaosBeast> t = src.world.getEntitiesWithinAABB(EntityChaosBeast.class, src.getEntityBoundingBox().grow(16),
                        x -> x.beast() == Beast.TIGER && Servants.isServantOf(x, o));
                if (!t.isEmpty()) {
                    event.setAmount(event.getAmount() * 1.2F);
                }
            }
        }
        // Владыка охоты: хозяин ранил врага издалека — тигр уже там
        if (src instanceof EntityPlayerMP && !Servants.isServant(hurt) && hurt != src && hurt instanceof EntityLiving
                && hurt.getDistanceSq(src) > 64) {
            EntityPlayerMP p = (EntityPlayerMP) src;
            Entry e = entry(p.world, p.getUniqueID(), Beast.TIGER);
            if (e.level() >= 5) {
                EntityChaosBeast tiger = find(p, Beast.TIGER, 64);
                if (tiger != null && tiger.huntReady()) {
                    WorldServer ws = (WorldServer) p.world;
                    ws.spawnParticle(EnumParticleTypes.PORTAL, tiger.posX, tiger.posY + 0.6, tiger.posZ, 40, 0.4, 0.5, 0.4, 0.6);
                    tiger.setPositionAndUpdate(hurt.posX + (hurt.world.rand.nextDouble() - 0.5) * 2, hurt.posY, hurt.posZ + (hurt.world.rand.nextDouble() - 0.5) * 2);
                    tiger.setAttackTarget(hurt);
                    ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, tiger.posX, tiger.posY + 0.6, tiger.posZ, 40, 0.4, 0.5, 0.4, 0.1);
                    p.world.playSound(null, tiger.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.NEUTRAL, 0.7F, 0.6F);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND || !(event.getTarget() instanceof EntityChaosBeast)) {
            return;
        }
        EntityChaosBeast beast = (EntityChaosBeast) event.getTarget();
        EntityPlayer p = event.getEntityPlayer();
        if (!Servants.isServantOf(beast, p.getUniqueID())) {
            return;
        }
        Entry e = entry(beast);
        ItemStack held = p.getHeldItemMainhand();
        // бирка с именем
        if (held.getItem() == Items.NAME_TAG && held.hasDisplayName()) {
            rename((EntityPlayerMP) p, beast.beast(), held.getDisplayName());
            if (!p.isCreative()) {
                held.shrink(1);
            }
            event.setCanceled(true);
            return;
        }
        // любимое лакомство
        if (!held.isEmpty() && isTreat(beast.beast(), held)) {
            long now = p.world.getTotalWorldTime();
            if (now < e.treatReady) {
                p.sendStatusMessage(Breach.grimoire("chaosshard.beast.full"), true);
            } else {
                e.treatReady = now + TREAT_COOLDOWN;
                if (!p.isCreative()) {
                    held.shrink(1);
                }
                beast.heal(beast.getMaxHealth());
                ((WorldServer) p.world).spawnParticle(EnumParticleTypes.HEART, beast.posX, beast.posY + 0.8, beast.posZ, 6, 0.3, 0.3, 0.3, 0.0);
                addXp(beast, TREAT_XP);
                p.sendStatusMessage(Breach.grimoire("chaosshard.beast.treat"), true);
            }
            event.setCanceled(true);
            return;
        }
        if (held.isEmpty() && !p.isSneaking()) {
            if (beast.beast() == Beast.HARE && e.has(HARE_BAG)) {
                openBag((EntityPlayerMP) p, e);
                event.setCanceled(true);
            } else if (beast.beast() == Beast.CAT && e.has(CAT_HOARD) && !e.stash.isEmpty()) {
                for (ItemStack s : e.stash) {
                    ItemHandlerHelper.giveItemToPlayer(p, s);
                }
                e.stash.clear();
                changed(p.world, p.getUniqueID());
                p.world.playSound(null, beast.getPosition(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8F, 1.0F);
                event.setCanceled(true);
            }
        }
    }

    public static boolean isTreat(Beast b, ItemStack s) {
        switch (b) {
            case HARE: return s.getItem() == Items.GOLDEN_CARROT;
            case CAT: return s.getItem() == Items.FISH;
            case TIGER: return s.getItem() == Items.BEEF;
            default: return false;
        }
    }

    // ------------------------------------------------------------------ мешок зайца

    public static boolean bagOpen(UUID owner) {
        return OPEN_BAGS.contains(owner);
    }

    private static void openBag(EntityPlayerMP p, Entry e) {
        InventoryBasic inv = new InventoryBasic("chaosshard.beast.hare.bag", false, 27) {
            @Override
            public void closeInventory(EntityPlayer player) {
                super.closeInventory(player);
                e.bag.clear();
                for (int i = 0; i < getSizeInventory(); i++) {
                    if (!getStackInSlot(i).isEmpty()) {
                        e.bag.add(getStackInSlot(i).copy());
                    }
                }
                OPEN_BAGS.remove(player.getUniqueID());
                changed(player.world, player.getUniqueID());
            }
        };
        for (int i = 0; i < e.bag.size() && i < 27; i++) {
            inv.setInventorySlotContents(i, e.bag.get(i).copy());
        }
        OPEN_BAGS.add(p.getUniqueID());
        p.displayGUIChest(inv);
    }

    /** Положить в мешок зайца. Возвращает то, что не влезло. */
    public static ItemStack bagInsert(Entry e, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack s : e.bag) {
            if (rest.isEmpty()) {
                break;
            }
            if (ItemHandlerHelper.canItemStacksStack(s, rest)) {
                int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                rest.shrink(move);
            }
        }
        while (!rest.isEmpty() && e.bag.size() < 27) {
            int move = Math.min(rest.getCount(), rest.getMaxStackSize());
            ItemStack part = rest.copy();
            part.setCount(move);
            e.bag.add(part);
            rest.shrink(move);
        }
        return rest;
    }

    // ------------------------------------------------------------------ перки из окна Гримуара

    public static int perkCost(Entry e) {
        return 2 * (e.spent() + 1);
    }

    public static void buyPerk(EntityPlayerMP p, Beast b, int perk) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        if (perk < 0 || perk >= BeastData.PERKS || e.has(perk) || !e.known) {
            return;
        }
        if (e.points() <= 0) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.beast.no_points"), true);
            return;
        }
        int cost = perkCost(e);
        if (!p.isCreative() && count(p, ModItems.CHAOS_ESSENCE) < cost) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.beast.no_essence", cost), true);
            return;
        }
        if (!p.isCreative()) {
            take(p, ModItems.CHAOS_ESSENCE, cost);
        }
        e.perks |= 1 << perk;
        changed(p.world, p.getUniqueID());
        EntityChaosBeast beast = find(p, b, 64);
        if (beast != null) {
            refresh(beast);
        }
        p.world.playSound(null, p.getPosition(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, 1.2F);
        Milestones.grant(p, "beast_perk");
        sync(p);
    }

    public static void resetPerks(EntityPlayerMP p, Beast b) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        if (e.perks == 0) {
            return;
        }
        if (!p.isCreative() && count(p, ModItems.WORLD_SHARD) < 1) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.beast.no_fragment"), true);
            return;
        }
        if (!p.isCreative()) {
            take(p, ModItems.WORLD_SHARD, 1);
        }
        // мешок без перка не пропадает — содержимое отдаётся хозяину
        if (b == Beast.HARE && e.has(HARE_BAG)) {
            for (ItemStack s : e.bag) {
                ItemHandlerHelper.giveItemToPlayer(p, s);
            }
            e.bag.clear();
        }
        e.perks = 0;
        changed(p.world, p.getUniqueID());
        EntityChaosBeast beast = find(p, b, 64);
        if (beast != null) {
            refresh(beast);
        }
        p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.PLAYERS, 0.5F, 1.5F);
        sync(p);
    }

    private static int count(EntityPlayer p, net.minecraft.item.Item item) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s.getItem() == item) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void take(EntityPlayer p, net.minecraft.item.Item item, int n) {
        for (ItemStack s : p.inventory.mainInventory) {
            if (n > 0 && s.getItem() == item) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }

    // ------------------------------------------------------------------ Вознесение

    /** null — можно; иначе ключ сообщения. */
    public static String canAscend(EntityPlayerMP p, Beast b, BlockPos bowl) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        if (e.ascended) {
            return "chaosshard.beast.ascend.already";
        }
        if (e.xp < BeastData.XP[4]) {
            return "chaosshard.beast.ascend.xp";
        }
        List<EntityChaosBeast> near = p.world.getEntitiesWithinAABB(EntityChaosBeast.class, new net.minecraft.util.math.AxisAlignedBB(bowl).grow(8),
                x -> x.isEntityAlive() && x.beast() == b && Servants.isServantOf(x, p.getUniqueID()));
        return near.isEmpty() ? "chaosshard.beast.ascend.near" : null;
    }

    public static void ascend(EntityPlayerMP p, Beast b) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        e.ascended = true;
        changed(p.world, p.getUniqueID());
        EntityChaosBeast beast = find(p, b, 32);
        if (beast != null) {
            refresh(beast);
            beast.setHealth(beast.getMaxHealth());
            WorldServer ws = (WorldServer) p.world;
            ws.addWeatherEffect(new net.minecraft.entity.effect.EntityLightningBolt(p.world, beast.posX, beast.posY, beast.posZ, true));
            ws.spawnParticle(EnumParticleTypes.END_ROD, beast.posX, beast.posY + 1, beast.posZ, 120, 0.5, 1.2, 0.5, 0.08);
        }
        p.sendMessage(Breach.grimoire("chaosshard.beast.ascended", title(b, e)));
        Milestones.grant(p, "beast_ascend");
        sync(p);
    }

    // ------------------------------------------------------------------ отладка: /oh beast <hare|cat|tiger> <xp N|asc|return|reset|info>

    public static String debug(EntityPlayerMP p, Beast b, String what, int n) {
        Entry e = entry(p.world, p.getUniqueID(), b);
        switch (what) {
            case "xp":
                e.known = true;
                EntityChaosBeast beast = find(p, b, 64);
                if (beast != null) {
                    addXp(beast, n);
                } else {
                    e.xp = Math.max(0, Math.min(BeastData.XP[4], e.xp + n));
                }
                break;
            case "asc":
                e.known = true;
                e.xp = BeastData.XP[4];
                ascend(p, b);
                break;
            case "return":
                if (e.returnAt > 0) {
                    e.returnAt = p.world.getTotalWorldTime();
                }
                break;
            case "reset":
                e.xp = 0;
                e.ascended = false;
                e.perks = 0;
                e.returnAt = 0;
                break;
            default:
                break;
        }
        changed(p.world, p.getUniqueID());
        EntityChaosBeast beast = find(p, b, 64);
        if (beast != null) {
            refresh(beast);
        }
        sync(p);
        return b.key() + ": ур. " + e.level() + ", опыт " + e.xp + ", перки " + Integer.toBinaryString(e.perks)
                + ", очков " + e.points() + (e.returnAt > 0 ? ", в Нити ещё " + (e.returnAt - p.world.getTotalWorldTime()) / 20 + " с" : "");
    }

    /** Сервер остановлен (в одиночной игре — выход из мира): ничего не тянется в следующий мир. */
    public static void clearState() {
        DIRTY.clear();
        OPEN_BAGS.clear();
        RECALL.clear();
    }
}
