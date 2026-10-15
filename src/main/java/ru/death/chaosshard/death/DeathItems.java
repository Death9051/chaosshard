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
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Смерть Осколка: Гримуар не выпадает — остаётся при нём; остальные выпавшие вещи ложатся в могилу на месте гибели
 * (а если ей негде встать — лежат и не исчезают со временем). Смерть запоминается, чтобы сфера «Возврат» перенесла вещи
 * к хозяину — без копий.
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
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
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
            graveNote((EntityPlayerMP) event.player);
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

    /**
     * Последним: всё, что выпало после всех модов, ложится в могилу на месте гибели. Если места для могилы нет —
     * вещи остаются на земле, но не исчезают со временем. Так или иначе смерть запоминается для сферы «Возврат».
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(PlayerDropsEvent event) {
        EntityPlayer p = event.getEntityPlayer();
        if (p.world.isRemote || event.isCanceled() || event.getDrops().isEmpty()) {
            return;
        }
        NBTTagCompound rec = new NBTTagCompound();
        rec.setInteger("Dim", p.dimension);
        rec.setLong("Pos", p.getPosition().toLong());
        rec.setLong("Time", p.world.getTotalWorldTime());
        BlockPos grave = bury(p, event.getDrops());
        if (grave != null) {
            rec.setLong("Grave", grave.toLong());
            remember(p, rec);
            NBTTagCompound note = new NBTTagCompound();
            note.setLong("Pos", grave.toLong());
            note.setString("Dim", p.world.provider.getDimensionType().getName());
            persisted(p).setTag(TAG_GRAVE_NOTE, note);
            return;
        }
        NBTTagList ids = new NBTTagList();
        for (EntityItem e : event.getDrops()) {
            e.setNoDespawn();
            e.lifespan = Integer.MAX_VALUE;
            NBTTagCompound id = new NBTTagCompound();
            id.setUniqueId("U", e.getUniqueID());
            ids.appendTag(id);
        }
        rec.setTag("Items", ids);
        remember(p, rec);
    }

    private static void remember(EntityPlayer p, NBTTagCompound rec) {
        Data d = Data.get(p.world);
        NBTTagList list = d.deaths(p.getUniqueID());
        list.appendTag(rec);
        while (list.tagCount() > REMEMBER) {
            list.removeTag(0);   // старые могилы стоят и дальше — их просто не достанет сфера
        }
        d.markDirty();
    }

    // ------------------------------------------------------------------ могила

    private static final String TAG_GRAVE_NOTE = "chaosshard_grave_note";
    private static final java.util.Set<String> KNOWN_DIMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "overworld", "the_nether", "the_end", "chaosshard_rift", "chaosshard_antechamber", "chaosshard_archive"));

    /** Положить выпавшее в могилу. Где она встала (null — места нет, вещи остаются лежать). */
    static BlockPos bury(EntityPlayer p, List<EntityItem> drops) {
        World w = p.world;
        BlockPos at = graveSpot(w, p.getPosition());
        if (at == null || ModBlocks.GRAVE == null) {
            return null;
        }
        if (!w.setBlockState(at, ModBlocks.GRAVE.getDefaultState().withProperty(net.minecraft.block.BlockHorizontal.FACING,
                p.getHorizontalFacing()), 3)) {
            return null;
        }
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(at);
        if (!(te instanceof TileGrave)) {
            w.setBlockToAir(at);
            return null;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (EntityItem e : drops) {
            stacks.add(e.getItem());
            e.setDead();
        }
        ((TileGrave) te).fill(p, stacks);
        drops.clear();
        return at;
    }

    /**
     * Где встать могиле: рядом с местом гибели, на воздухе (не в жидкости) и на твёрдом — если такое есть.
     * Упал в пустоту — на поверхность этого столба, а если её нет — у нижней границы мира.
     */
    public static BlockPos graveSpot(World w, BlockPos death) {
        int top = w.getActualHeight() - 2;
        BlockPos p = death;
        if (death.getY() < 1) {
            BlockPos h = w.getHeight(new BlockPos(death.getX(), 0, death.getZ()));
            p = h.getY() > 0 ? h : new BlockPos(death.getX(), 1, death.getZ());
        }
        p = new BlockPos(p.getX(), Math.max(1, Math.min(top, p.getY())), p.getZ());
        for (int pass = 0; pass < 3; pass++) {
            for (int r = 0; r <= 4; r++) {
                for (int k = 0; k <= 12; k++) {
                    int dy = (k + 1) / 2 * (k % 2 == 0 ? -1 : 1);   // 0, +1, -1, +2, -2 …
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            BlockPos q = p.add(dx, dy, dz);
                            if (q.getY() < 1 || q.getY() > top || !w.isBlockLoaded(q)) {
                                continue;
                            }
                            IBlockState st = w.getBlockState(q);
                            boolean free = st.getBlock().isReplaceable(w, q) && !st.getBlock().hasTileEntity(st);
                            boolean liquid = st.getMaterial().isLiquid();
                            boolean ground = w.getBlockState(q.down()).isSideSolid(w, q.down(), EnumFacing.UP);
                            if (free && (pass == 2 || !liquid) && (pass >= 1 || ground)) {
                                return q;
                            }
                        }
                    }
                }
            }
        }
        // всё вокруг занято — вверх по столбу до первого свободного места
        for (BlockPos q = p.up(); q.getY() <= top; q = q.up()) {
            IBlockState st = w.getBlockState(q);
            if (st.getBlock().isReplaceable(w, q) && !st.getBlock().hasTileEntity(st)) {
                return q;
            }
        }
        return null;
    }

    /** После возрождения: где могила. */
    private static void graveNote(EntityPlayerMP p) {
        NBTTagCompound per = persisted(p);
        if (!per.hasKey(TAG_GRAVE_NOTE)) {
            return;
        }
        NBTTagCompound note = per.getCompoundTag(TAG_GRAVE_NOTE);
        per.removeTag(TAG_GRAVE_NOTE);
        BlockPos g = BlockPos.fromLong(note.getLong("Pos"));
        String dim = note.getString("Dim");
        Object where = KNOWN_DIMS.contains(dim) ? new net.minecraft.util.text.TextComponentTranslation("chaosshard.dim." + dim) : dim;
        p.sendMessage(Breach.grimoire("chaosshard.grave.where", g.getX(), g.getY(), g.getZ(), where));
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
            if (rec.hasKey("Grave")) {
                // могила: подгрузить её чанк, забрать всё и убрать её
                BlockPos g = BlockPos.fromLong(rec.getLong("Grave"));
                w.getChunk(g);
                net.minecraft.tileentity.TileEntity te = w.getTileEntity(g);
                if (te instanceof TileGrave && !((TileGrave) te).isEmpty()) {
                    items += ((TileGrave) te).giveTo(p);
                    BlockGrave.open(w, g);
                }
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
