package ru.death.chaosshard.lair;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;

import javax.annotation.Nullable;
import java.util.List;

/** Печать Цитадели: ПКМ — домой, к своей Цитадели, из любого мира. Раз в пять минут. */
public class ItemCitadelSigil extends Item {

    public static final int COOLDOWN = 20 * 60 * 5;

    public ItemCitadelSigil() {
        setMaxStackSize(1);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack s = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }
        Object[] c = LairData.get(world).citadelOf(player.getUniqueID());
        if (c == null) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.citadel.none"), true);
            return new ActionResult<>(EnumActionResult.FAIL, s);
        }
        if (ru.death.chaosshard.trial.TrialDimension.is(world) || ru.death.chaosshard.act5.RiftDimension.is(world)) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.citadel.blocked"), true);
            return new ActionResult<>(EnumActionResult.FAIL, s);
        }
        BlockPos at = ((BlockPos) c[1]).up();
        ChaosTeleporter.toPos((EntityPlayerMP) player, (Integer) c[0], at);
        player.getCooldownTracker().setCooldown(this, COOLDOWN);
        ru.death.chaosshard.quest.Milestones.grant((EntityPlayerMP) player, ru.death.chaosshard.quest.Milestones.CITADEL_RECALL);
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.translateToLocal("item.chaosshard.citadel_sigil.desc"));
    }
}
