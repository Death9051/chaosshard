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

    private static final Map<UUID, UUID> SELECTED = new HashMap<>();

    public static void select(EntityPlayer p, Entity servant) {
        SELECTED.put(p.getUniqueID(), servant.getUniqueID());
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
            m.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        return false;
    }

    // ------------------------------------------------------------------ закрепление за сундуком

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer p = event.getEntityPlayer();
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND || p.getHeldItemMainhand().getItem() != ModItems.GRIMOIRE) {
            return;
        }
        UUID id = SELECTED.get(p.getUniqueID());
        if (id == null) {
            return;
        }
        event.setCanceled(true);
        Entity e = ((WorldServer) event.getWorld()).getEntityFromUuid(id);
        if (!(e instanceof EntityCreature) || !e.isEntityAlive() || !Servants.isServantOf(e, p.getUniqueID())) {
            SELECTED.remove(p.getUniqueID());
            return;
        }
        if (handler(event.getWorld(), event.getPos()) == null) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.work.not_chest"), true);
            return;
        }
        SELECTED.remove(p.getUniqueID());
        EntityCreature m = (EntityCreature) e;
        unbind(m);
        m.getEntityData().setLong(Servants.TAG_CHEST, event.getPos().toLong());
        m.getEntityData().setInteger(Servants.TAG_DIR, p.getHorizontalFacing().getHorizontalIndex());
        m.getEntityData().removeTag(Servants.TAG_GUARD);
        m.setHomePosAndDistance(event.getPos(), 32);
        m.setAttackTarget(null);
        ((WorldServer) m.world).spawnParticle(EnumParticleTypes.SPELL_WITCH, event.getPos().getX() + 0.5, event.getPos().getY() + 1,
                event.getPos().getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
        Servants.Kind k = Servants.kindOf(m);
        p.sendMessage(Breach.grimoire(k == Servants.Kind.MINER ? "chaosshard.work.bound.miner" : "chaosshard.work.bound",
                new TextComponentTranslation("chaosshard.servant.kind." + k.key())));
        if (p instanceof EntityPlayerMP) {
            Milestones.grant((EntityPlayerMP) p, "servant_bind");
        }
    }
}
