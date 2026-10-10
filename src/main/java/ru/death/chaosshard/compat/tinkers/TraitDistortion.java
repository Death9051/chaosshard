package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import slimeknights.tconstruct.library.traits.AbstractTrait;

/** «Искажение»: 15% шанс, что удар продолжится вспышкой Хаоса (+4 магического урона). */
public class TraitDistortion extends AbstractTrait {

    public TraitDistortion() {
        super("chaosshard_distortion", TinkersCompat.DISTORTED_COLOR);
    }

    @Override
    public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damageDealt,
                         boolean wasCritical, boolean wasHit) {
        if (!wasHit || target.world.isRemote || target.world.rand.nextFloat() > 0.15F) {
            return;
        }
        target.hurtResistantTime = 0;
        target.attackEntityFrom(DamageSource.MAGIC, 4.0F);
        ((WorldServer) target.world).spawnParticle(EnumParticleTypes.SPELL_WITCH, target.posX, target.posY + target.height / 2,
                target.posZ, 20, 0.3, 0.4, 0.3, 0.05);
    }
}
