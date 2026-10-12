package ru.death.chaosshard.village;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.Random;
import java.util.UUID;

/** Дань деревни: раз в день складывается в Знак (или в соседний сундук). */
public class TilePatronSigil extends TileEntity implements ITickable {

    private final ItemStackHandler stored = new ItemStackHandler(9);
    private UUID owner;
    private int timer;
    private int xp;
    private boolean evolved;
    private int pulse;
    /** Сколько осквернённых блоков было в кайме при последнем подсчёте. */
    private int border;

    // ------------------------------------------------------------------ уровни

    public int level() {
        int[] t = ChaosConfig.order.sigilLevels;
        int lvl = 1;
        for (int i = 1; i < t.length; i++) {
            if (xp >= t[i]) {
                lvl = i + 1;
            }
        }
        return lvl;
    }

    public static int maxLevel() {
        return ChaosConfig.order.sigilLevels.length;
    }

    /** На сколько блоков Скверна подходит ближе к деревне на этом уровне. */
    public static int shrink(int level) {
        return level >= 2 ? level - 1 : 0;
    }

    public int getXp() {
        return xp;
    }

    public int nextThreshold() {
        int[] t = ChaosConfig.order.sigilLevels;
        int l = level();
        return l < t.length ? t[l] : -1;
    }

    public boolean isEvolved() {
        return evolved;
    }

    public int getBorder() {
        return border;
    }

    public void addXp(int n) {
        int before = level();
        xp += n;
        markDirty();
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l);
        }
    }

    /** Для отладки. */
    public void setXp(int n) {
        int before = level();
        xp = Math.max(0, n);
        int after = level();
        for (int l = before + 1; l <= after; l++) {
            onLevelUp(l);
        }
        markDirty();
    }

    private EntityPlayerMP ownerOnline() {
        return owner == null || world.getMinecraftServer() == null ? null
                : world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
    }

    private void onLevelUp(int lvl) {
        EntityPlayerMP p = ownerOnline();
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.util.SoundCategory.BLOCKS, 0.7F, 0.7F);
        ((net.minecraft.world.WorldServer) world).spawnParticle(net.minecraft.util.EnumParticleTypes.SPELL_WITCH,
                pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
        if (p != null) {
            ThreadManager.addPoints(p, ChaosConfig.order.sigilLevelThread);
            p.sendMessage(Breach.grimoire("chaosshard.village.level_up", pos.getX(), pos.getZ(), lvl, shrink(lvl),
                    ChaosConfig.order.sigilLevelThread));
        }
        if (lvl >= maxLevel() && !evolved) {
            evolved = true;
            markDirty();
            int n = VillageEvolution.evolve(world, pos);
            if (p != null) {
                p.sendMessage(Breach.grimoire("chaosshard.village.evolved", pos.getX(), pos.getZ(), n));
                ru.death.chaosshard.quest.Milestones.grant(p, ru.death.chaosshard.quest.Milestones.VILLAGE_EVOLVED);
            }
        }
    }

    /** Раз в минуту: кайма (то, что Скверна заняла благодаря сближению) кормит Знак; Обитель обращает новых жителей. */
    private void tickPulse() {
        if (++pulse < 1200) {
            return;
        }
        pulse = 0;
        if (evolved) {
            VillageEvolution.evolve(world, pos);
        }
        int lvl = level();
        if (lvl < 2) {
            border = 0;
            return;
        }
        Village v = world.getVillageCollection().getNearestVillage(pos, 32);
        if (v == null) {
            return;
        }
        border = VillageEvolution.countBorder(world, v, shrink(lvl));
        markDirty();
        if (border > 0) {
            addXp(Math.max(1, border / 4));
            EntityPlayerMP p = ownerOnline();
            if (p != null && border >= 16) {
                ThreadManager.addPoints(p, border / 16);
            }
        }
    }

    public void setOwner(EntityPlayerMP player) {
        owner = player.getUniqueID();
        markDirty();
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        tickPulse();
        if (++timer < ChaosConfig.order.tributeInterval) {
            return;
        }
        timer = 0;
        Village v = world.getVillageCollection().getNearestVillage(pos, 32);
        if (v == null) {
            return;
        }
        int doors = Math.max(1, v.getNumVillageDoors());
        Random r = world.rand;
        ItemStack[] tribute = {
                new ItemStack(Items.EMERALD, 1 + r.nextInt(1 + doors / 4)),
                new ItemStack(Items.BREAD, 3 + r.nextInt(4)),
                new ItemStack(r.nextBoolean() ? Items.WHEAT : (r.nextBoolean() ? Items.CARROT : Items.POTATO), 4 + r.nextInt(8)),
                r.nextInt(3) == 0 ? new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(3)) : ItemStack.EMPTY
        };
        // Уровень Знака приумножает дань, Обитель Хаоса добавляет свои дары
        double mult = 1 + 0.25 * (level() - 1);
        for (ItemStack t : tribute) {
            t.setCount(Math.min(64, (int) Math.round(t.getCount() * mult)));
        }
        if (evolved) {
            tribute = java.util.Arrays.copyOf(tribute, tribute.length + 2);
            tribute[tribute.length - 2] = new ItemStack(ru.death.chaosshard.init.ModItems.VEIL_SHARD, 1 + r.nextInt(2));
            tribute[tribute.length - 1] = r.nextInt(3) == 0 ? new ItemStack(ru.death.chaosshard.init.ModItems.MEMORY_FRAGMENT) : ItemStack.EMPTY;
        }
        for (ItemStack t : tribute) {
            if (!t.isEmpty()) {
                ItemStack left = pushToNeighbour(t);
                if (!left.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(stored, left, false);
                }
            }
        }
        markDirty();
        if (owner != null && world.getMinecraftServer() != null) {
            EntityPlayerMP p = world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
            if (p != null) {
                ThreadManager.addPoints(p, ChaosConfig.order.tributeThread);
                p.sendStatusMessage(Breach.grimoire("chaosshard.village.tribute", pos.getX(), pos.getZ()), true);
            }
        }
    }

    private ItemStack pushToNeighbour(ItemStack stack) {
        for (EnumFacing f : EnumFacing.VALUES) {
            TileEntity te = world.getTileEntity(pos.offset(f));
            if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite())) {
                IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite());
                if (h != null) {
                    stack = ItemHandlerHelper.insertItemStacked(h, stack, false);
                    if (stack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return stack;
    }

    public void giveTo(EntityPlayer player) {
        boolean any = false;
        for (int i = 0; i < stored.getSlots(); i++) {
            ItemStack s = stored.extractItem(i, 64, false);
            if (!s.isEmpty()) {
                any = true;
                ItemHandlerHelper.giveItemToPlayer(player, s);
            }
        }
        if (!any) {
            int left = Math.max(0, (ChaosConfig.order.tributeInterval - timer) / 20);
            player.sendMessage(Breach.grimoire("chaosshard.village.empty", left / 60));
        }
        markDirty();
    }

    public void dropAll() {
        for (int i = 0; i < stored.getSlots(); i++) {
            ItemStack s = stored.getStackInSlot(i);
            if (!s.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), s);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("stored", stored.serializeNBT());
        tag.setInteger("timer", timer);
        tag.setInteger("xp", xp);
        tag.setBoolean("evolved", evolved);
        tag.setInteger("pulse", pulse);
        tag.setInteger("border", border);
        if (owner != null) {
            tag.setUniqueId("owner", owner);
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        stored.deserializeNBT(tag.getCompoundTag("stored"));
        timer = tag.getInteger("timer");
        xp = tag.getInteger("xp");
        evolved = tag.getBoolean("evolved");
        pulse = tag.getInteger("pulse");
        border = tag.getInteger("border");
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
