package ru.death.chaosshard.servant.work;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.servant.Servants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Добытчики у сундука. ПКМ Гримуаром по добытчику — выбрать, затем ПКМ Гримуаром по сундуку — закрепить.
 * Shift+ПКМ Гримуаром по добытчику — открепить (снова ходит за хозяином).
 * Добыча уходит в сундук по Нити — добытчику не нужно носить её самому.
 */
public class Work {

    private static final Map<UUID, java.util.LinkedHashSet<UUID>> SELECTED = new HashMap<>();
    private static final Map<UUID, Long> SELECTED_AT = new HashMap<>();

    // ------------------------------------------------------------------ совместная работа у одного сундука

    /** Чьи цели (растение, дерево) у какого сундука: рабочие одного сундука не идут к одному и тому же. */
    private static final Map<Long, Map<BlockPos, Object[]>> CLAIMS = new HashMap<>();
    private static final long CLAIM_TTL = 600;

    /** Занята ли цель другим рабочим этого сундука. */
    public static boolean taken(Entity m, BlockPos target) {
        BlockPos c = chest(m);
        if (c == null) {
            return false;
        }
        Map<BlockPos, Object[]> map = CLAIMS.get(c.toLong());
        if (map == null) {
            return false;
        }
        Object[] cl = map.get(target);
        return cl != null && !m.getUniqueID().equals(cl[0]) && m.world.getTotalWorldTime() - (Long) cl[1] < CLAIM_TTL;
    }

    public static void claim(Entity m, BlockPos target) {
        BlockPos c = chest(m);
        if (c != null && target != null) {
            Map<BlockPos, Object[]> map = CLAIMS.computeIfAbsent(c.toLong(), k -> new HashMap<>());
            map.entrySet().removeIf(e -> m.getUniqueID().equals(e.getValue()[0]));
            map.put(target.toImmutable(), new Object[]{m.getUniqueID(), m.world.getTotalWorldTime()});
        }
    }

    public static void release(Entity m) {
        BlockPos c = chest(m);
        if (c != null && CLAIMS.containsKey(c.toLong())) {
            CLAIMS.get(c.toLong()).entrySet().removeIf(e -> m.getUniqueID().equals(e.getValue()[0]));
        }
    }

    /** Добавить слугу к выбору (можно выбрать несколько подряд, выбор живёт 30 секунд с последнего щелчка). Возвращает, сколько выбрано. */
    public static int select(EntityPlayer p, Entity servant) {
        long now = p.world.getTotalWorldTime();
        java.util.LinkedHashSet<UUID> set = SELECTED.get(p.getUniqueID());
        if (set == null || now - SELECTED_AT.getOrDefault(p.getUniqueID(), 0L) > 600) {
            set = new java.util.LinkedHashSet<>();
            SELECTED.put(p.getUniqueID(), set);
        }
        set.add(servant.getUniqueID());
        SELECTED_AT.put(p.getUniqueID(), now);
        return set.size();
    }

    public static BlockPos chest(Entity m) {
        return m.getEntityData().hasKey(Servants.TAG_CHEST) ? BlockPos.fromLong(m.getEntityData().getLong(Servants.TAG_CHEST)) : null;
    }

    public static EnumFacing dir(Entity m) {
        EnumFacing[] h = {EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST};
        return h[Math.floorMod(m.getEntityData().getInteger(Servants.TAG_DIR), 4)];
    }

    public static void unbind(EntityCreature m) {
        m.getEntityData().removeTag(Servants.TAG_CHEST);
        m.getEntityData().removeTag(WorkMiner.TAG_MINE);
        m.detachHome();
    }

    public static IItemHandler handler(World w, BlockPos pos) {
        if (pos == null || !w.isBlockLoaded(pos)) {
            return null;
        }
        TileEntity te = w.getTileEntity(pos);
        if (te == null) {
            return null;
        }
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP)) {
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        }
        return te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                ? te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) : null;
    }

    /** Положить добычу в сундук (по Нити). Не влезло — ложится на сундук. */
    public static void deposit(EntityCreature m, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        BlockPos c = chest(m);
        IItemHandler h = handler(m.world, c);
        ItemStack rest = h == null ? stack : ItemHandlerHelper.insertItemStacked(h, stack.copy(), false);
        if (!rest.isEmpty()) {
            BlockPos at = c != null ? c.up() : m.getPosition();
            EntityItem it = new EntityItem(m.world, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, rest);
            it.motionX = it.motionY = it.motionZ = 0;
            m.world.spawnEntity(it);
        }
        if (c != null && m.world.rand.nextInt(4) == 0) {
            ((WorldServer) m.world).spawnParticle(EnumParticleTypes.PORTAL, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.3);
        }
    }

    /** Взять из сундука первый подходящий предмет (1 шт.). */
    public static ItemStack take(EntityCreature m, Predicate<ItemStack> what) {
        IItemHandler h = handler(m.world, chest(m));
        if (h == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && what.test(s)) {
                return h.extractItem(i, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Сколько в сундуке подходящих предметов. */
    public static int count(EntityCreature m, Predicate<ItemStack> what) {
        IItemHandler h = handler(m.world, chest(m));
        int n = 0;
        if (h != null) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && what.test(s)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Сундук на месте? Нет — добытчик открепляется. */
    public static boolean alive(EntityCreature m) {
        BlockPos c = chest(m);
        if (c == null) {
            return false;
        }
        if (!m.world.isBlockLoaded(c)) {
            return false;
        }
        if (handler(m.world, c) == null) {
            unbind(m);
            EntityPlayer o = Servants.owner(m);
            if (o != null) {
                o.sendMessage(Breach.grimoire("chaosshard.work.lost",
                        new TextComponentTranslation("chaosshard.servant.kind." + Servants.kindOf(m).key())));
            }
            return false;
        }
        return true;
    }

    /** Подойти к точке; застрял надолго — шагнуть по Нити. Возвращает true, если уже рядом. */
    static boolean approach(EntityCreature m, BlockPos p, double reach, int[] stuck) {
        double d = m.getDistanceSq(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        if (d <= reach * reach) {
            stuck[0] = 0;
            return true;
        }
        if (++stuck[0] % 20 == 1) {
            m.getNavigator().tryMoveToXYZ(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
        }
        if (stuck[0] > 160) {
            stuck[0] = 0;
            BlockPos at = p;
            for (int up = 0; up < 4 && !(m.world.isAirBlock(at) && m.world.isAirBlock(at.up())); up++) {
                at = at.up();
            }
            ((WorldServer) m.world).spawnParticle(EnumParticleTypes.PORTAL, m.posX, m.posY + 1, m.posZ, 20, 0.3, 0.5, 0.3, 0.5);
            WorkChunks.touch(m, at);
            m.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            ru.death.chaosshard.servant.Resync.later(m, 3);
        }
        return false;
    }

    // ------------------------------------------------------------------ бойцы: гарнизон Якоря или «стереги здесь»

    private static void fighter(EntityPlayer p, EntityCreature m, World w, BlockPos clicked) {
        BlockPos anchorPos = clicked;
        if (w.getBlockState(clicked).getBlock() instanceof ru.death.chaosshard.anchor.BlockAnchorPart) {
            anchorPos = ru.death.chaosshard.anchor.BlockAnchorPart.base(w, clicked);
        }
        ru.death.chaosshard.anchor.TileAnchor a = anchorPos == null ? null : ru.death.chaosshard.anchor.AnchorClusters.at(w, anchorPos);
        Servants.Kind kind = Servants.kindOf(m);
        TextComponentTranslation name = new TextComponentTranslation("chaosshard.servant.kind." + kind.key());
        if (a != null) {
            if (a.getOwner() == null || !a.getOwner().equals(p.getUniqueID())) {
                p.sendStatusMessage(Breach.grimoire("chaosshard.garrison.not_yours"), true);
                return;
            }
            java.util.List<ru.death.chaosshard.anchor.TileAnchor> c = ru.death.chaosshard.anchor.AnchorClusters.cluster(w, a);
            int limit = ru.death.chaosshard.anchor.AnchorClusters.garrisonLimit(c);
            java.util.List<net.minecraft.entity.EntityLiving> g = ru.death.chaosshard.anchor.AnchorClusters.garrison((WorldServer) w, c);
            g.remove(m);
            if (g.size() >= limit && !p.isCreative()) {
                p.sendMessage(Breach.grimoire("chaosshard.garrison.full", g.size(), limit));
                return;
            }
            m.getEntityData().removeTag(Servants.TAG_GUARD);
            m.getEntityData().setLong(Servants.TAG_ANCHOR, a.getPos().toLong());
            m.setHomePosAndDistance(a.getPos(), 16);
            m.setAttackTarget(null);
            ((WorldServer) w).spawnParticle(EnumParticleTypes.SPELL_WITCH, a.getPos().getX() + 0.5, a.getPos().getY() + 1.5,
                    a.getPos().getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.05);
            if (m.getDistanceSq(a.getPos()) > 16 * 16) {
                BlockPos at = w.getHeight(a.getPos().add(2, 0, 2));
                m.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                ru.death.chaosshard.servant.Resync.later(m, 3);
            }
            p.sendMessage(Breach.grimoire("chaosshard.garrison.bound", name, g.size() + 1, limit, c.size()));
            if (p instanceof EntityPlayerMP) {
                Milestones.grant((EntityPlayerMP) p, "anchor_garrison");
                if (c.size() >= 2) {
                    Milestones.grant((EntityPlayerMP) p, "anchor_cluster");
                }
            }
            return;
        }
        // любое другое место — стеречь там (из гарнизона так не уйти: сначала Shift+ПКМ)
        if (m.getEntityData().hasKey(Servants.TAG_ANCHOR)) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.garrison.leave_first"), true);
            return;
        }
        BlockPos at = clicked.up();
        m.getEntityData().setLong(Servants.TAG_GUARD, at.toLong());
        m.setHomePosAndDistance(at, 8);
        m.getNavigator().tryMoveToXYZ(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.1);
        p.sendStatusMessage(Breach.grimoire("chaosshard.servant.guard"), true);
    }

    // ------------------------------------------------------------------ закрепление за сундуком

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer p = event.getEntityPlayer();
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND || !ru.death.chaosshard.spell.SpellCaster.binding(p)) {
            return;
        }
        java.util.LinkedHashSet<UUID> ids = SELECTED.get(p.getUniqueID());
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // выбор живёт 30 секунд
        if (p.world.getTotalWorldTime() - SELECTED_AT.getOrDefault(p.getUniqueID(), 0L) > 600) {
            SELECTED.remove(p.getUniqueID());
            return;
        }
        event.setCanceled(true);
        SELECTED.remove(p.getUniqueID());
        boolean chest = handler(event.getWorld(), event.getPos()) != null;
        boolean notChest = false;
        for (UUID id : ids) {
            Entity e = ((WorldServer) event.getWorld()).getEntityFromUuid(id);
            if (!(e instanceof EntityCreature) || !e.isEntityAlive() || !Servants.isServantOf(e, p.getUniqueID())) {
                continue;
            }
            Servants.Kind kind = Servants.kindOf(e);
            if (kind != null && !kind.gatherer()) {
                fighter(p, (EntityCreature) e, event.getWorld(), event.getPos());
                continue;
            }
            if (!chest) {
                notChest = true;
                continue;
            }
            bindChest(p, (EntityCreature) e, event.getPos());
        }
        if (notChest) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.work.not_chest"), true);
        }
    }

    private static void bindChest(EntityPlayer p, EntityCreature m, BlockPos pos) {
        unbind(m);
        m.getEntityData().setLong(Servants.TAG_CHEST, pos.toLong());
        m.getEntityData().setInteger(Servants.TAG_DIR, p.getHorizontalFacing().getHorizontalIndex());
        m.getEntityData().removeTag(Servants.TAG_GUARD);
        m.setHomePosAndDistance(pos, 32);
        m.setAttackTarget(null);
        ((WorldServer) m.world).spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5, pos.getY() + 1,
                pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
        Servants.Kind k = Servants.kindOf(m);
        p.sendMessage(Breach.grimoire(k == Servants.Kind.MINER ? "chaosshard.work.bound.miner" : "chaosshard.work.bound",
                new TextComponentTranslation("chaosshard.servant.kind." + k.key())));
        if (p instanceof EntityPlayerMP) {
            Milestones.grant((EntityPlayerMP) p, "servant_bind");
        }
    }
}
