package ru.death.chaosshard.trial;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadBonus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ключ обломка. Собран из заметки другого Осколка: ведёт в обломок погибшего мира.
 * Ступень (1–3) задаётся Нитью при первом использовании, вместе с условиями (по одному на ступень).
 */
public class ItemTrialKey extends Item {

    public ItemTrialKey() {
        setMaxStackSize(1);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        if (TrialDimension.is(world)) {
            p.sendStatusMessage(Breach.grimoire("chaosshard.trial.already"), true);
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("tier")) {
            tag = roll(ThreadBonus.level(p), world.rand);
            stack.setTagCompound(tag);
        }
        int tier = tag.getInteger("tier");
        int cond = tag.getInteger("cond");
        int theme = tag.getInteger("theme");
        if (!p.isCreative()) {
            stack.shrink(1);
        }
        TrialManager.enter(p, tier, cond, theme);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    public static NBTTagCompound roll(int threadLevel, java.util.Random r) {
        int tier = Math.max(1, Math.min(3, 1 + threadLevel / 2));
        List<TrialCondition> all = new ArrayList<>();
        Collections.addAll(all, TrialCondition.values());
        Collections.shuffle(all, r);
        int mask = 0;
        for (int i = 0; i < tier; i++) {
            mask |= 1 << all.get(i).ordinal();
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("tier", tier);
        tag.setInteger("cond", mask);
        tag.setInteger("theme", r.nextInt(TrialTheme.values().length));
        return tag;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.format("item.chaosshard.trial_key.lore"));
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("tier")) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format("item.chaosshard.trial_key.tier", tag.getInteger("tier")));
            tooltip.add(TextFormatting.GRAY + I18n.format(TrialTheme.values()[Math.floorMod(tag.getInteger("theme"), TrialTheme.values().length)].key()));
            for (TrialCondition c : TrialCondition.values()) {
                if (TrialCondition.has(tag.getInteger("cond"), c)) {
                    tooltip.add(TextFormatting.GRAY + " • " + I18n.format(c.key()));
                }
            }
        } else {
            tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.trial_key.unknown"));
        }
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("item.chaosshard.trial_key.hint"));
    }
}
