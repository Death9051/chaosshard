package ru.death.chaosshard.hero;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;

import javax.annotation.Nullable;
import java.util.List;

/** Клинок Каэля: трофей первой Опоры. Удар ослепляет и подсвечивает цель. */
public class ItemKaelBlade extends ItemSword {

    public static final Item.ToolMaterial MATERIAL = EnumHelper.addToolMaterial("CHAOSSHARD_KAEL", 3, 2400, 9.0F, 5.0F, 22);

    public ItemKaelBlade() {
        super(MATERIAL);
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (!attacker.world.isRemote) {
            target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0));
            target.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 100, 0));
        }
        return super.hitEntity(stack, target, attacker);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("item.chaosshard.kael_blade.desc"));
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.translateToLocal("item.chaosshard.kael_blade.lore"));
    }
}
