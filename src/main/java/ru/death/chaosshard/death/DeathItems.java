package ru.death.chaosshard.death;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Смерть Осколка: Гримуар не выпадает — остаётся при нём; остальные выпавшие вещи запоминаются
 * (и не исчезают со временем), чтобы сфера «Возврат» перенесла их с места гибели — без копий.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class DeathItems {

    private static final String TAG_KEPT = "chaosshard_kept_on_death";
    /** Сколько последних смертей помнить. */
    private static final int REMEMBER = 5;

    // ------------------------------------------------------------------ Гримуар остаётся

    /** Последним из обработчиков смерти (смерть на арене Вызова отменена раньше и сюда не доходит). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || event.getEntityLiving() instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.getEntityLiving();
        if (p.world.isRemote || p.world.getGameRules().getBoolean("keepInventory")) {
            return;
        }
        NBTTagList kept = persisted(p).getTagList(TAG_KEPT, 10);
        List<net.minecraft.util.NonNullList<ItemStack>> parts = new ArrayList<>();
        parts.add(p.inventory.mainInventory);
        parts.add(p.inventory.offHandInventory);
        parts.add(p.inventory.armorInventory);
        for (net.minecraft.util.NonNullList<ItemStack> part : parts) {
            for (int i = 0; i < part.size(); i++) {
                ItemStack s = part.get(i);
                if (!s.isEmpty() && s.getItem() == ModItems.GRIMOIRE) {
                    kept.appendTag(s.writeToNBT(new NBTTagCompound()));
                    part.set(i, ItemStack.EMPTY);
                }
            }
        }
        if (kept.tagCount() > 0) {
            persisted(p).setTag(TAG_KEPT, kept);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            returnKept((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP && event.player.isEntityAlive()) {
            returnKept((EntityPlayerMP) event.player);
        }
    }

    private static void returnKept(EntityPlayerMP p) {
        NBTTagCompound per = persisted(p);
        if (!per.hasKey(TAG_KEPT)) {
            return;
        }
        NBTTagList kept = per.getTagList(TAG_KEPT, 10);
        per.removeTag(TAG_KEPT);
        for (int i = 0; i < kept.tagCount(); i++) {
            ItemStack s = new ItemStack(kept.getCompoundTagAt(i));
            if (!s.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(p, s);
            }
        }
    }

    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ место гибели

    /** Последним: что осталось лежать после всех модов — запоминаем и не даём исчезнуть. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(PlayerDropsEvent event) {
        EntityPlayer p = event.getEntityPlayer();
        if (p.world.isRemote || event.isCanceled() || event.getDrops().isEmpty() || p instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        NBTTagCompound rec = new NBTTagCompound();
        rec.setInteger("Dim", p.dimension);
        rec.setLong("Pos", p.getPosition().toLong());
        rec.setLong("Time", p.world.getTotalWorldTime());
        NBTTagList ids = new NBTTagList();
        for (EntityItem e : event.getDrops()) {
            e.setNoDespawn();
            e.lifespan = Integer.MAX_VALUE;
            NBTTagCompound id = new NBTTagCompound();
            id.setUniqueId("U", e.getUniqueID());
            ids.appendTag(id);
        }
        rec.setTag("Items", ids);
        Data d = Data.get(p.world);
        NBTTagList list = d.deaths(p.getUniqueID());
        list.appendTag(rec);
        while (list.tagCount() > REMEMBER) {
            list.removeTag(0);
        }
        d.markDirty();
    }

    // ------------------------------------------------------------------ сфера «Возврат»

    /** Перенести вещи с мест последних смертей к игроку. false — нечего возвращать (сфера цела). */
    public static boolean reclaim(EntityPlayerMP p) {
        Data d = Data.get(p.world);
        NBTTagList list = d.deaths(p.getUniqueID());
        int items = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound rec = list.getCompoundTagAt(i);
            WorldServer w = p.getServer().getWorld(rec.getInteger("Dim"));
            if (w == null) {
                continue;
            }
            BlockPos at = BlockPos.fromLong(rec.getLong("Pos"));
            // вещи могли откатиться: подгрузить чанки вокруг
            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    w.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
                }
            }
            NBTTagList ids = rec.getTagList("Items", 10);
            for (int k = 0; k < ids.tagCount(); k++) {
                UUID id = ids.getCompoundTagAt(k).getUniqueId("U");
                Entity e = w.getEntityFromUuid(id);
                if (!(e instanceof EntityItem) || !e.isEntityAlive()) {
                    continue;
                }
                ItemStack s = ((EntityItem) e).getItem().copy();
                e.setDead();
                if (!s.isEmpty()) {
                    ItemHandlerHelper.giveItemToPlayer(p, s);
                    items += s.getCount();
                }
            }
        }
        if (items == 0) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.ritual.reclaim.none"), true);
            return false;
        }
        d.clear(p.getUniqueID());
        p.sendMessage(Breach.grimoire("chaosshard.ritual.reclaim.done", items));
        return true;
    }

    /** Записи о смертях: на весь сервер, по игрокам. */
    public static class Data extends WorldSavedData {
        public static final String NAME = "chaosshard_deaths";
        private NBTTagCompound tag = new NBTTagCompound();

        public Data(String name) {
            super(name);
        }

        public static Data get(World any) {
            World w = DimensionManager.getWorld(0) != null ? DimensionManager.getWorld(0) : any;
            Data d = (Data) w.getMapStorage().getOrLoadData(Data.class, NAME);
            if (d == null) {
                d = new Data(NAME);
                w.getMapStorage().setData(NAME, d);
            }
            return d;
        }

        NBTTagList deaths(UUID player) {
            String k = player.toString();
            if (!tag.hasKey(k)) {
                tag.setTag(k, new NBTTagList());
            }
            return tag.getTagList(k, 10);
        }

        void clear(UUID player) {
            tag.removeTag(player.toString());
            markDirty();
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            tag = nbt.getCompoundTag("Deaths");
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            nbt.setTag("Deaths", tag);
            return nbt;
        }
    }

    private DeathItems() {
    }
}
