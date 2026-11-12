package ru.death.chaosshard.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.SlotItemHandler;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.network.PacketEnchanterAction;

import java.util.Map;

/** Окно Стола Хаоса: предмет, лазурит, инвентарь. Действия приходят пакетом {@link PacketEnchanterAction}. */
public class ContainerChaosEnchanter extends Container {

    public static final int ITEM_X = 16, ITEM_Y = 26, LAPIS_X = 16, LAPIS_Y = 53;
    public static final int INV_X = 26, INV_Y = 167;

    public final TileChaosEnchanter tile;

    /** Что видит клиент об изучении (приходит свойствами окна). */
    public int researchNum = -1;
    public int partPermille;
    public boolean researchDone;
    public int secondsLeft;

    private int sentNum = Integer.MIN_VALUE, sentPart = -1, sentDone = -1, sentSec = -1;

    public ContainerChaosEnchanter(EntityPlayer player, TileChaosEnchanter tile) {
        this.tile = tile;
        InventoryPlayer inv = player.inventory;
        addSlotToContainer(new SlotItemHandler(tile.inventory, TileChaosEnchanter.SLOT_ITEM, ITEM_X, ITEM_Y));
        addSlotToContainer(new SlotItemHandler(tile.inventory, TileChaosEnchanter.SLOT_LAPIS, LAPIS_X, LAPIS_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        }
        if (player instanceof EntityPlayerMP) {
            tile.claim((EntityPlayerMP) player);
            EnchantKnowledge.sync((EntityPlayerMP) player);
        }
    }

    public ItemStack item() {
        return tile.inventory.getStackInSlot(TileChaosEnchanter.SLOT_ITEM);
    }

    public int lapis() {
        return tile.inventory.getStackInSlot(TileChaosEnchanter.SLOT_LAPIS).getCount();
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.getDistanceSq(tile.getPos().add(0.5, 0.5, 0.5)) <= 64;
    }

    // ---- синхронизация изучения ----

    private int num() {
        Enchantment e = EnchanterLogic.byId(tile.research());
        return e == null ? -1 : Enchantment.getEnchantmentID(e);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int n = num(), part = Math.round(tile.part() * 1000), done = tile.done() ? 1 : 0, sec = Math.min(32000, tile.secondsLeft());
        for (IContainerListener l : listeners) {
            if (n != sentNum) {
                l.sendWindowProperty(this, 0, n);
            }
            if (part != sentPart) {
                l.sendWindowProperty(this, 1, part);
            }
            if (done != sentDone) {
                l.sendWindowProperty(this, 2, done);
            }
            if (sec != sentSec) {
                l.sendWindowProperty(this, 3, sec);
            }
        }
        sentNum = n;
        sentPart = part;
        sentDone = done;
        sentSec = sec;
    }

    @Override
    public void addListener(IContainerListener l) {
        super.addListener(l);
        sentNum = Integer.MIN_VALUE;
        sentPart = sentDone = sentSec = -1;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void updateProgressBar(int id, int data) {
        switch (id) {
            case 0:
                researchNum = data;
                break;
            case 1:
                partPermille = data;
                break;
            case 2:
                researchDone = data != 0;
                break;
            case 3:
                secondsLeft = data;
                break;
            default:
                break;
        }
    }

    // ---- действия ----

    public void act(EntityPlayerMP p, int action, String id, int level) {
        Enchantment e = EnchanterLogic.byId(id);
        switch (action) {
            case PacketEnchanterAction.ENCHANT:
                if (e != null) {
                    enchant(p, e, level);
                }
                break;
            case PacketEnchanterAction.RESEARCH:
                if (e != null) {
                    research(p, e);
                }
                break;
            case PacketEnchanterAction.LEARN_ITEM:
                learnFromItem(p);
                break;
            case PacketEnchanterAction.CANCEL:
                cancel(p);
                break;
            default:
                break;
        }
        detectAndSendChanges();
    }

    private void enchant(EntityPlayerMP p, Enchantment e, int level) {
        ItemStack stack = item();
        if (!EnchantKnowledge.knows(p, e) || !EnchanterLogic.fits(e, stack) || EnchanterLogic.conflict(stack, e) != null) {
            return;
        }
        int cur = EnchanterLogic.currentLevel(stack, e);
        float power = EnchanterLogic.tablePower(EnchanterLogic.shelfPower(p.world, tile.getPos()));
        if (level <= cur || level > EnchanterLogic.maxLevelAt(e, power)) {
            return;
        }
        int xp = EnchanterLogic.xpCost(e, level, cur, EnchanterLogic.others(stack, e));
        int lap = EnchanterLogic.lapisCost(level, cur);
        boolean free = p.capabilities.isCreativeMode;
        if (!free && (p.experienceLevel < xp || lapis() < lap)) {
            return;
        }
        ItemStack out;
        if (EnchanterLogic.isBook(stack)) {
            out = new ItemStack(Items.ENCHANTED_BOOK);
            Map<Enchantment, Integer> had = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
            had.put(e, level);
            for (Map.Entry<Enchantment, Integer> en : had.entrySet()) {
                ItemEnchantedBook.addEnchantment(out, new EnchantmentData(en.getKey(), en.getValue()));
            }
            if (stack.hasDisplayName()) {
                out.setStackDisplayName(stack.getDisplayName());
            }
        } else {
            out = stack.copy();
            Map<Enchantment, Integer> had = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(out);
            had.put(e, level);
            net.minecraft.enchantment.EnchantmentHelper.setEnchantments(had, out);
        }
        if (!free) {
            p.onEnchant(out, xp);
            tile.inventory.extractItem(TileChaosEnchanter.SLOT_LAPIS, lap, false);
        }
        tile.inventory.setStackInSlot(TileChaosEnchanter.SLOT_ITEM, out);
        p.addStat(StatList.ITEM_ENCHANTED);
        p.world.playSound(null, tile.getPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0F,
                p.world.rand.nextFloat() * 0.1F + 0.9F);
    }

    private void research(EntityPlayerMP p, Enchantment e) {
        if (tile.busy() || EnchantKnowledge.knows(p, e) || !EnchanterLogic.researchable(e)) {
            return;
        }
        int cost = EnchanterLogic.researchLapis(e);
        boolean free = p.capabilities.isCreativeMode;
        if (!free && lapis() < cost) {
            return;
        }
        if (!free) {
            tile.inventory.extractItem(TileChaosEnchanter.SLOT_LAPIS, cost, false);
        }
        tile.start(p, e, EnchanterLogic.researchTicks(e, EnchanterLogic.shelfPower(p.world, tile.getPos())));
        p.world.playSound(null, tile.getPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.7F, 0.6F);
    }

    /** Изучить всё, что стоит на предмете в столе (книге, оружии, броне…). Предмет остаётся целым. */
    private void learnFromItem(EntityPlayerMP p) {
        int n = 0;
        for (Enchantment e : EnchanterLogic.on(item()).keySet()) {
            if (e != null && EnchantKnowledge.learn(p, e)) {
                p.sendMessage(Breach.grimoire("chaosshard.enchanter.learned", new TextComponentTranslation(e.getName())));
                n++;
            }
        }
        if (n > 0) {
            p.world.playSound(null, tile.getPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.6F, 1.4F);
        }
    }

    /** Отменить изучение: лазурит возвращается. */
    private void cancel(EntityPlayerMP p) {
        if (!tile.busy() || tile.done()) {
            return;
        }
        Enchantment e = EnchanterLogic.byId(tile.research());
        tile.cancel();
        if (e != null && !p.capabilities.isCreativeMode) {
            ItemStack back = new ItemStack(Items.DYE, EnchanterLogic.researchLapis(e), 4);
            back = tile.inventory.insertItem(TileChaosEnchanter.SLOT_LAPIS, back, false);
            ItemHandlerHelper.giveItemToPlayer(p, back);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!mergeItemStack(stack, 2, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (TileChaosEnchanter.isLapis(stack)) {
            if (!mergeItemStack(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            Slot target = inventorySlots.get(0);
            if (target.getHasStack()) {
                return ItemStack.EMPTY;
            }
            target.putStack(stack.splitStack(1));
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }
}
