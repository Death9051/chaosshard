package ru.death.chaosshard.servant;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

import java.util.List;

/** Улов Хаоса — лучшее, что приносит Кошка 5-го уровня: сытость, поглощение и регенерация. */
public class ItemChaosCatch extends ItemFood {

    public ItemChaosCatch() {
        super(10, 1.2F, false);
        setAlwaysEdible();
    }

    @Override
    protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 600, 1));
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 600, 0));
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("\u00a75\u00a7o" + net.minecraft.client.resources.I18n.format("item.chaosshard.chaos_catch.lore"));
    }
}
