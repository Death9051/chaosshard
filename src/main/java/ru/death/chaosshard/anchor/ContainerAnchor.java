package ru.death.chaosshard.anchor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.SlotItemHandler;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketAnchorView;

import java.util.List;

/**
 * Окно Якоря: места под печати (ставит и снимает только владелец) и инвентарь игрока.
 * Сведения о Якоре и сети приходят на клиент отдельным пакетом раз в секунду.
 */
public class ContainerAnchor extends Container {

    public static final int SEAL_X = 8;
    public static final int SEAL_Y = 98;
    public static final int INV_Y = 140;

    public static final int STORAGE_FIRST = TileAnchor.MAX_SEALS;
    public static final int SAMPLE_SLOT = STORAGE_FIRST + 27;
    public static final int INV_FIRST = SAMPLE_SLOT + 1;

    public final TileAnchor tile;
    private final EntityPlayer player;
    private int tick;
    /** Взгляд Дозора: на какой Якорь сети смотрим (null — ни на какой). */
    public BlockPos scry;

    public ContainerAnchor(EntityPlayer player, TileAnchor tile) {
        this.tile = tile;
        this.player = player;
        final boolean mine = canEdit(player, tile);
        for (int i = 0; i < TileAnchor.MAX_SEALS; i++) {
            final int idx = i;
            addSlotToContainer(new SlotItemHandler(tile.sealInv, i, SEAL_X + (i % 6) * 18, SEAL_Y + (i / 6) * 18) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return mine && idx < tile.sealSlots() && super.isItemValid(stack);
                }

                @Override
                public boolean canTakeStack(EntityPlayer p) {
                    return mine;
                }

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }
            });
        }
        for (int i = 0; i < 27; i++) {
            addSlotToContainer(new SlotItemHandler(tile.storage, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean canTakeStack(EntityPlayer p) {
                    return mine;
                }
            });
        }
        addSlotToContainer(new SlotItemHandler(tile.sample, 0, 8, 104) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return mine && super.isItemValid(stack);
            }

            @Override
            public boolean canTakeStack(EntityPlayer p) {
                return mine;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, INV_Y + 58));
        }
    }

    public static boolean canEdit(EntityPlayer p, TileAnchor t) {
        return p.isCreative() || t.getOwner() == null || t.getOwner().equals(p.getUniqueID());
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return !tile.isInvalid() && p.getDistanceSq(tile.getPos()) <= 100;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (player instanceof EntityPlayerMP && tick++ % 20 == 0) {
            NBTTagCompound v = view(tile, player);
            if (scry != null && tile.netStage(AnchorSpec.WATCH) > 0 && (tick / 20) % 2 == 0) {
                v.setTag("scry", AnchorView.scry((WorldServer) tile.getWorld(), scry, tile.getOwner()));
            }
            ChaosNetwork.CHANNEL.sendTo(new PacketAnchorView(v), (EntityPlayerMP) player);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        int seals = TileAnchor.MAX_SEALS;
        if (index < INV_FIRST) {
            if (!slot.canTakeStack(p) || !mergeItemStack(stack, INV_FIRST, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof net.minecraft.item.ItemMonsterPlacer && canEdit(p, tile)) {
                Slot sm = inventorySlots.get(SAMPLE_SLOT);
                if (sm.getHasStack() || !sm.isItemValid(stack)) {
                    return ItemStack.EMPTY;
                }
                sm.putStack(stack.splitStack(1));
                slot.onSlotChanged();
                return copy;
            }
            if (!(stack.getItem() instanceof ItemAnchorSeal) || !canEdit(p, tile)) {
                return ItemStack.EMPTY;
            }
            boolean moved = false;
            for (int i = 0; i < Math.min(seals, tile.sealSlots()) && !stack.isEmpty(); i++) {
                Slot s = inventorySlots.get(i);
                if (!s.getHasStack() && s.isItemValid(stack)) {
                    s.putStack(stack.splitStack(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }

    /** Сведения для окна: вкладка «Якорь» и вкладка «Сеть». */
    public static NBTTagCompound view(TileAnchor a, EntityPlayer viewer) {
        NBTTagCompound t = new NBTTagCompound();
        int[] th = ChaosConfig.anchor.levelThresholds;
        int lvl = a.level();
        t.setString("owner", a.getOwnerName());
        t.setInteger("level", lvl);
        t.setInteger("max", TileAnchor.maxLevel());
        t.setInteger("xp", a.getCorrupted());
        t.setInteger("from", th[Math.min(lvl, th.length) - 1]);
        t.setInteger("next", a.nextThreshold());
        t.setInteger("radius", a.radius());
        t.setInteger("cap", a.radiusCap());
        t.setInteger("land", a.getLand());
        t.setInteger("capture", th.length > 1 ? th[1] : 0);
        t.setBoolean("captured", a.isCaptured());
        t.setInteger("blood", a.blood.getFluidAmount());
        t.setInteger("bloodCap", a.blood.getCapacity());
        t.setInteger("slots", a.sealSlots());
        t.setInteger("hum", a.hum.working());
        t.setBoolean("edit", canEdit(viewer, a));
        WorldServer w = (WorldServer) a.getWorld();
        List<TileAnchor> c = AnchorClusters.cluster(w, a);
        int raid = -1;
        for (TileAnchor x : c) {
            if (AnchorClusters.isLead(c, x) && x.isCaptured() && x.raidTimer() >= 0) {
                raid = x.raidTimer();
            }
        }
        t.setInteger("raid", raid);
        // сеть
        boolean net = AnchorNet.isNetWorld(w) && a.getOwner() != null;
        t.setBoolean("net", net);
        int size = net ? AnchorNet.size(w, a.getOwner()) : 1;
        t.setInteger("size", size);
        t.setDouble("eLand", AnchorNet.landMultiplier(size));
        t.setDouble("eOut", net ? AnchorNet.outsideMultiplier(size) : 1.0);
        t.setInteger("reserve", AnchorNet.reserve(w, a.getOwner()));
        t.setInteger("garrison", AnchorClusters.garrison(w, c).size());
        t.setInteger("garrisonMax", AnchorClusters.garrisonLimit(c));
        NBTTagList list = new NBTTagList();
        if (net) {
            AnchorData d = AnchorData.get(w);
            for (BlockPos p : AnchorNet.members(w, a.getOwner())) {
                AnchorData.Rec r = d.rec(p);
                NBTTagCompound e = new NBTTagCompound();
                e.setInteger("x", p.getX());
                e.setInteger("y", p.getY());
                e.setInteger("sp", r.spec);
                e.setInteger("z", p.getZ());
                e.setInteger("l", r.level);
                e.setInteger("s", r.level >= TileAnchor.maxLevel() ? 2 : r.captured ? 1 : 0);
                e.setBoolean("here", p.equals(a.getPos()));
                list.appendTag(e);
            }
        }
        t.setTag("list", list);
        // специализация
        AnchorSpec sp = a.getSpec();
        t.setInteger("spec", sp == null ? -1 : sp.ordinal());
        t.setBoolean("stage", a.stage());
        t.setInteger("storage", sp != null ? sp.storage : a.hasFx(AnchorFx.VEIN_LOOT) ? AnchorSpec.VEIN.storage : 0);
        t.setBoolean("alpha", a.isAlpha());
        t.setInteger("alphaLvl", a.alphaLevel());
        t.setInteger("alphaXp", a.getAlphaXp());
        t.setBoolean("alphaAwake", a.alphaAwake());
        t.setInteger("netAlpha", a.netAlpha());
        if (a.isAlpha()) {
            int al = a.alphaLevel();
            t.setInteger("alphaFrom", al <= 1 ? 0 : TileAnchor.ALPHA_T[al - 2]);
            t.setInteger("alphaNext", al >= TileAnchor.ALPHA_MAX_LEVEL ? -1 : TileAnchor.ALPHA_T[al - 1]);
        }
        t.setInteger("alphaAllowed", net ? AnchorNet.alphaAllowed(w, a.getOwner()) : 0);
        t.setInteger("alphaCount", net ? AnchorNet.alphas(w, a.getOwner()).size() : 0);
        t.setInteger("borrowSlots", a.borrowSlots());
        t.setIntArray("borrow", a.getBorrow());
        int[] free = new int[AnchorSpec.values().length];
        if (net) {
            for (AnchorSpec x : AnchorSpec.values()) {
                free[x.ordinal()] = AnchorNet.count(w, a.getOwner(), x, false) - AnchorNet.borrowers(w, a.getOwner(), x, null);
            }
        }
        t.setIntArray("borrowFree", free);
        t.setInteger("fieldXp", a.fieldXp);
        t.setInteger("forgeRate", sp == AnchorSpec.FORGE ? AnchorSpecs.forgeRate(a) : 0);
        t.setInteger("forgeEnergy", a.forgeEnergy);
        t.setBoolean("path", net && a.net(AnchorSpec.PATH) > 0);
        t.setBoolean("pathFree", net && a.netStage(AnchorSpec.PATH) > 0);
        t.setBoolean("watch", net && a.net(AnchorSpec.WATCH) > 0);
        t.setBoolean("watchStage", net && a.netStage(AnchorSpec.WATCH) > 0);
        NBTTagList specs = new NBTTagList();
        for (AnchorSpec x : AnchorSpec.values()) {
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("n", net ? AnchorNet.count(w, a.getOwner(), x, false) : (sp == x ? 1 : 0));
            specs.appendTag(e);
        }
        t.setTag("specs", specs);
        if (net && a.net(AnchorSpec.WATCH) > 0) {
            t.setTag("dots", AnchorView.dots(w, a.getOwner()));
        }
        return t;
    }
}
