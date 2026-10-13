package ru.death.chaosshard.compat.life;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.servant.Servants;

/**
 * Еда слуг: раз в минуту голодный слуга берёт самую сытную еду из своего сундука (или из любого в 6 блоках)
 * и, пока сыт, крепче. Сытость и сила зависят от еды — блюда Pam's HarvestCraft кормят лучше простой.
 *   сытость 1–5  — Регенерация I;
 *   6–9          — + Сила I;
 *   10 и больше  — + Сопротивление I.
 * Длительность — минута за каждую единицу сытости, но не больше потолка.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ServantMeals {

    public static final String TAG_FED = "chaosshard_fed_until";

    @SubscribeEvent
    public static void onUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e.ticksExisted % 1200 != 600 || !ChaosConfig.compat.servantMeals
                || !(e instanceof EntityLiving) || !Servants.isServant(e)) {
            return;
        }
        tryEat((EntityLiving) e);
    }

    /** Поесть, если голоден и есть еда. Возвращает сытость съеденного или 0. */
    public static int tryEat(EntityLiving e) {
        if (e.getEntityData().getLong(TAG_FED) > e.world.getTotalWorldTime()) {
            return 0;
        }
        IItemHandler best = null;
        int bestSlot = -1, bestFood = 0;
        for (IItemHandler h : pantries(e)) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.getItem() instanceof ItemFood) {
                    int f = ((ItemFood) s.getItem()).getHealAmount(s);
                    if (f > bestFood && !h.extractItem(i, 1, true).isEmpty()) {
                        best = h;
                        bestSlot = i;
                        bestFood = f;
                    }
                }
            }
        }
        if (best == null) {
            return 0;
        }
        best.extractItem(bestSlot, 1, false);
        feed(e, bestFood);
        net.minecraft.entity.player.EntityPlayer o = Servants.owner(e);
        if (o instanceof net.minecraft.entity.player.EntityPlayerMP) {
            ru.death.chaosshard.quest.Milestones.grant((net.minecraft.entity.player.EntityPlayerMP) o, ru.death.chaosshard.quest.Milestones.SERVANT_FED);
        }
        return bestFood;
    }

    public static void feed(EntityLiving e, int food) {
        int ticks = Math.min(ChaosConfig.compat.mealMaxMinutes, food) * 1200;
        e.getEntityData().setLong(TAG_FED, e.world.getTotalWorldTime() + ticks);
        e.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, ticks, 0, true, true));
        if (food >= 6) {
            e.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, ticks, 0, true, true));
        }
        if (food >= 10) {
            e.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, ticks, 0, true, true));
        }
        e.heal(food);
        ((WorldServer) e.world).spawnParticle(EnumParticleTypes.HEART, e.posX, e.posY + e.height + 0.3, e.posZ, 3, 0.3, 0.2, 0.3, 0);
    }

    /** Свой сундук слуги (если назначен), иначе хранилища в 6 блоках. */
    static java.util.List<IItemHandler> pantries(EntityLiving e) {
        java.util.List<IItemHandler> out = new java.util.ArrayList<>();
        if (e.getEntityData().hasKey(Servants.TAG_CHEST)) {
            add(e, BlockPos.fromLong(e.getEntityData().getLong(Servants.TAG_CHEST)), out);
            if (!out.isEmpty()) {
                return out;
            }
        }
        BlockPos c = e.getPosition();
        for (BlockPos p : BlockPos.getAllInBox(c.add(-6, -2, -6), c.add(6, 2, 6))) {
            add(e, p, out);
        }
        return out;
    }

    static void add(EntityLiving e, BlockPos p, java.util.List<IItemHandler> out) {
        if (!e.world.isBlockLoaded(p)) {
            return;
        }
        TileEntity te = e.world.getTileEntity(p);
        if (te instanceof net.minecraft.inventory.IInventory || te != null && te.getClass().getName().contains("Drawer")) {
            IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (h != null) {
                out.add(h);
            }
        }
    }

    private ServantMeals() {
    }
}
