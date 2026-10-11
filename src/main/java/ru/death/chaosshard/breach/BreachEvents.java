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
 *  - Обрывки памяти — из тварей в чужих мирах (Кладовки, Трещины, обломки), но не в Лучезарии и не в Преддверии.
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

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase mob = event.getEntityLiving();
        World world = mob.world;
        if (world.isRemote || !(mob instanceof IMob) || !(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        if (AntechamberDimension.is(world) || isLuchezariya(world.provider.getDimension())) {
            return;
        }
        double chance = ChaosConfig.breach.memoryFragmentChance * (1 + event.getLootingLevel() * 0.5);
        if (world.rand.nextDouble() < chance) {
            event.getDrops().add(new EntityItem(world, mob.posX, mob.posY, mob.posZ, new ItemStack(ModItems.MEMORY_FRAGMENT)));
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
