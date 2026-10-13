package ru.death.chaosshard.hero;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Чётки Ирены: трофей второй Опоры. ПКМ — затянуть раны и сбросить дурные эффекты; раз в минуту. */
public class ItemIrenaRosary extends Item {

    public static final int COOLDOWN = 1200;

    public ItemIrenaRosary() {
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (!world.isRemote) {
            p.heal(8.0F);
            List<PotionEffect> bad = new ArrayList<>();
            for (PotionEffect e : p.getActivePotionEffects()) {
                if (e.getPotion().isBadEffect()) {
                    bad.add(e);
                }
            }
            for (PotionEffect e : bad) {
                p.removePotionEffect(e.getPotion());
            }
            ((WorldServer) world).spawnParticle(EnumParticleTypes.HEART, p.posX, p.posY + 1.8, p.posZ, 6, 0.4, 0.3, 0.4, 0.0);
            world.playSound(null, p.getPosition(), SoundEvents.BLOCK_NOTE_BELL, SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
        p.getCooldownTracker().setCooldown(this, COOLDOWN);
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("item.chaosshard.irena_rosary.desc"));
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.translateToLocal("item.chaosshard.irena_rosary.lore"));
    }
}
