package ru.death.chaosshard.challenge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.breach.Breach;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Мешочек с добычей Вызова: награда за волну или за весь Вызов не сыплется в инвентарь, а лежит в мешочке.
 * ПКМ — всё содержимое переходит к игроку (лишнее — к ногам), мешочек пропадает, Гримуар перечисляет, что было внутри.
 */
public class ItemLootBag extends Item {

    private static final String ITEMS = "Items", FACTION = "Faction", WAVE = "Wave", TIER = "Tier", FINAL = "Final";

    public ItemLootBag() {
        setMaxStackSize(1);
    }

    /** Мешочек за отбитую волну (final = false) или за победу (final = true; wave — ступень). */
    public static ItemStack of(ChallengeFaction f, int wave, boolean fin, List<ItemStack> loot) {
        ItemStack bag = new ItemStack(ru.death.chaosshard.init.ModItems.LOOT_BAG);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger(FACTION, f.ordinal());
        t.setInteger(fin ? TIER : WAVE, wave);
        t.setBoolean(FINAL, fin);
        NBTTagList l = new NBTTagList();
        for (ItemStack s : merge(loot)) {
            NBTTagCompound e = s.writeToNBT(new NBTTagCompound());
            e.setInteger("BigCount", s.getCount());
            l.appendTag(e);
        }
        t.setTag(ITEMS, l);
        bag.setTagCompound(t);
        return bag;
    }

    /** Одинаковое — в одну строку (сколько угодно штук; в инвентарь всё равно ляжет стопками). */
    static List<ItemStack> merge(List<ItemStack> loot) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : loot) {
            if (s.isEmpty()) {
                continue;
            }
            boolean added = false;
            for (ItemStack o : out) {
                if (ItemHandlerHelper.canItemStacksStack(o, s) || o.getItem() == s.getItem() && o.getMetadata() == s.getMetadata()
                        && ItemStack.areItemStackTagsEqual(o, s)) {
                    o.grow(s.getCount());
                    added = true;
                    break;
                }
            }
            if (!added) {
                out.add(s.copy());
            }
        }
        return out;
    }

    public static List<ItemStack> contents(ItemStack bag) {
        List<ItemStack> out = new ArrayList<>();
        if (!bag.hasTagCompound()) {
            return out;
        }
        NBTTagList l = bag.getTagCompound().getTagList(ITEMS, 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            ItemStack s = new ItemStack(e);
            if (!s.isEmpty()) {
                if (e.hasKey("BigCount")) {
                    s.setCount(e.getInteger("BigCount"));
                }
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack bag = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, bag);
        }
        List<ItemStack> loot = contents(bag);
        player.setHeldItem(hand, ItemStack.EMPTY);
        ITextComponent list = new TextComponentString("");
        int k = 0;
        for (ItemStack s : loot) {
            ITextComponent name = s.getTextComponent();   // [Название] с подсказкой при наведении
            if (k++ > 0) {
                list.appendSibling(new TextComponentString(", "));
            }
            list.appendSibling(new TextComponentString(s.getCount() + "× ")).appendSibling(name);
            int left = s.getCount();
            while (left > 0) {
                ItemStack part = s.copy();
                part.setCount(Math.min(left, Math.max(1, s.getMaxStackSize())));
                left -= part.getCount();
                ItemHandlerHelper.giveItemToPlayer(player, part);
            }
        }
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.BLOCK_CLOTH_BREAK, SoundCategory.PLAYERS, 1.0F, 0.8F);
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6F, 0.9F);
        player.sendMessage(Breach.grimoire(loot.isEmpty() ? "chaosshard.loot_bag.empty" : "chaosshard.loot_bag.opened", list));
        return new ActionResult<>(EnumActionResult.SUCCESS, ItemStack.EMPTY);
    }

    /** Мешочек за весь Вызов (а не за волну). */
    public static boolean isFinal(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(FINAL);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return isFinal(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        if (!stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound t = stack.getTagCompound();
        ChallengeFaction[] all = ChallengeFaction.values();
        ChallengeFaction f = all[Math.max(0, Math.min(all.length - 1, t.getInteger(FACTION)))];
        String side = I18n.translateToLocal(f.key());
        tooltip.add(TextFormatting.GOLD + (t.getBoolean(FINAL)
                ? I18n.translateToLocalFormatted("chaosshard.loot_bag.final", side, t.getInteger(TIER))
                : I18n.translateToLocalFormatted("chaosshard.loot_bag.wave", side, t.getInteger(WAVE))));
        List<ItemStack> loot = contents(stack);
        int shown = 0;
        for (ItemStack s : loot) {
            if (shown++ >= 8) {
                tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocalFormatted("chaosshard.loot_bag.more", loot.size() - 8));
                break;
            }
            tooltip.add(TextFormatting.GRAY + " " + s.getCount() + "× " + s.getDisplayName());
        }
        tooltip.add(TextFormatting.DARK_PURPLE + I18n.translateToLocal("chaosshard.loot_bag.hint"));
    }
}
