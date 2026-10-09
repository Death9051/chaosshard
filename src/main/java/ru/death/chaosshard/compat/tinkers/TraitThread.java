package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import ru.death.chaosshard.thread.ThreadBonus;
import slimeknights.tconstruct.library.traits.AbstractTrait;

/** «Нить Хаоса»: +0,5 урона за каждый уровень Нити владельца. */
public class TraitThread extends AbstractTrait {

    public TraitThread() {
        super("chaosshard_thread", TinkersCompat.COLOR);
    }

    @Override
    public float damage(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damage, float newDamage, boolean isCritical) {
        if (player instanceof EntityPlayer) {
            newDamage += 0.5F * ThreadBonus.level((EntityPlayer) player);
        }
        return newDamage;
    }
}
