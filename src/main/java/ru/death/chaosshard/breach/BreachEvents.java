package ru.death.chaosshard.breach;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.stars.SkyRules;

/**
 * Откуда берутся материалы Прорыва:
 *  - Осколки Завесы — из природной корки (она сама нарастает на хаосит под открытым небом);
 *  - Осколки мира — из тварей в чужих мирах (Кладовки, Трещины, обломки), но не в Лучезарии и не в Преддверии.
 */
public class BreachEvents {

    @SubscribeEvent
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        // Осколки — только из природной корки: поставленная игроком «свежая» корка их не хранит
        if (!BlockCrust.isNatural(event.getState()) || event.isSilkTouching() || event.getWorld().isRemote) {
            return;
        }
        double chance = ChaosConfig.breach.veilShardChance * (1 + event.getFortuneLevel() * 0.5);
        if (event.getWorld().rand.nextDouble() < chance) {
            event.getDrops().add(new ItemStack(ModItems.VEIL_SHARD));
        }
    }

    /**
     * Осколки мира: Осколки — твари Хаоса — выбивают кусочек мира из любого его создания (звери, монстры, люди),
     * в любом мире, кроме Преддверия. Чем крепче создание, тем выше шанс; Добыча повышает; боссы роняют по нескольку.
     * Свои слуги, звери-отголоски и детёныши ничего не дают.
     */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase mob = event.getEntityLiving();
        World world = mob.world;
        if (world.isRemote || mob instanceof EntityPlayer || !(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        if (AntechamberDimension.is(world) || mob.isChild() || ru.death.chaosshard.servant.Servants.isServant(mob)
                || mob instanceof ru.death.chaosshard.servant.EntityChaosBeast) {
            return;
        }
        int n;
        int harvest = ru.death.chaosshard.enchant.ChaosEnchants.level(ru.death.chaosshard.enchant.ChaosEnchants.SHARD_HARVEST,
                (EntityPlayer) event.getSource().getTrueSource());
        if (!mob.isNonBoss()) {
            n = 3 + world.rand.nextInt(3) + harvest;
        } else {
            double strength = Math.max(0.5, Math.min(5.0, mob.getMaxHealth() / 20.0));
            double chance = ChaosConfig.breach.worldShardChance * strength * (1 + event.getLootingLevel() * 0.5) * (1 + harvest * 0.5);
            if (mob instanceof net.minecraft.entity.passive.EntityAnimal) {
                chance *= 0.5;   // мирные звери — скупее, иначе фермы
            }
            n = world.rand.nextDouble() < chance ? 1 : 0;
        }
        if (n > 0) {
            event.getDrops().add(new EntityItem(world, mob.posX, mob.posY, mob.posZ, new ItemStack(ModItems.WORLD_SHARD, n)));
        }
    }

    private static boolean isLuchezariya(int dim) {
        for (int d : ChaosConfig.stars.luchezariyaDimensions) {
            if (d == dim) {
                return true;
            }
        }
        return false;
    }
}
