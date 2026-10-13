package ru.death.chaosshard.hero;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
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
import ru.death.chaosshard.servant.Servants;

import javax.annotation.Nullable;
import java.util.List;

/** Лютня Лиана: трофей четвёртой Опоры. ПКМ — песнь доблести мне и моим слугам рядом; раз в 2 минуты. */
public class ItemLianLute extends Item {

    public static final int COOLDOWN = 2400, DURATION = 600;

    public ItemLianLute() {
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (!world.isRemote) {
            p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, DURATION, 0));
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, DURATION, 0));
            for (EntityLiving m : world.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(16),
                    x -> Servants.isServantOf(x, p.getUniqueID()))) {
                m.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, DURATION, 0));
                m.addPotionEffect(new PotionEffect(MobEffects.SPEED, DURATION, 0));
                ((WorldServer) world).spawnParticle(EnumParticleTypes.NOTE, m.posX, m.posY + m.height + 0.3, m.posZ, 4, 0.3, 0.2, 0.3, 1.0);
            }
            ((WorldServer) world).spawnParticle(EnumParticleTypes.NOTE, p.posX, p.posY + 2.2, p.posZ, 12, 1.0, 0.4, 1.0, 1.0);
            for (int i = 0; i < 3; i++) {
                world.playSound(null, p.getPosition(), SoundEvents.BLOCK_NOTE_HARP, SoundCategory.PLAYERS, 1.0F, 0.8F + i * 0.25F);
            }
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
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("item.chaosshard.lian_lute.desc"));
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.translateToLocal("item.chaosshard.lian_lute.lore"));
    }
}
