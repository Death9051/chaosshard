package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import slimeknights.tconstruct.library.traits.AbstractTrait;

/** Черты сплавов Хаоса. */
public final class AlloyTraits {

    /** Кристаллизованный Хаос — «Накопитель»: удары копят энергию в оружии; полный кристалл — +15% урона. */
    public static class Crystal extends AbstractTrait {
        public Crystal() {
            super("chaosshard_crystal_store", TinkersCompat.CRYSTAL_COLOR);
        }

        @Override
        public float damage(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damage, float newDamage, boolean isCritical) {
            int cap = ChaosMaterials.storeCap(tool);
            return cap > 0 && ChaosMaterials.store(tool) >= cap ? newDamage * 1.15F : newDamage;
        }

        @Override
        public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damageDealt, boolean wasCritical, boolean wasHit) {
            if (wasHit && !player.world.isRemote) {
                ChaosMaterials.setStore(tool, ChaosMaterials.store(tool) + 2);
            }
        }
    }

    /** Кровавая платина — «Вампиризм»: 1% урона за уровень материала возвращается здоровьем. */
    public static class Blood extends AbstractTrait {
        public Blood() {
            super("chaosshard_blood_price", TinkersCompat.BLOOD_COLOR);
        }

        @Override
        public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damageDealt, boolean wasCritical, boolean wasHit) {
            if (wasHit && !player.world.isRemote && damageDealt > 0) {
                player.heal(damageDealt * 0.01F * Math.max(1, ChaosMaterials.level(tool, ChaosMaterials.BLOOD)));
            }
        }
    }

    /** Свет звезды — «Звёздный удар»: удар даёт опыт перков Astral. */
    public static class Star extends AbstractTrait {
        public Star() {
            super("chaosshard_star_strike", TinkersCompat.STAR_COLOR);
        }

        @Override
        public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damageDealt, boolean wasCritical, boolean wasHit) {
            if (wasHit && player instanceof EntityPlayerMP && Loader.isModLoaded("astralsorcery")) {
                ru.death.chaosshard.compat.astral.AstralOverflow.give((EntityPlayerMP) player, 2);
            }
        }
    }

    private AlloyTraits() {
    }
}
