package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;

/**
 * Наросты Сердца — цельные куски руды: при добыче выпадает сам блок руды
 * (уголь, редстоун, лазурит, алмаз и т.д.), а не осколки. Так руду можно
 * удваивать в машинах и класть в рецепты страниц ресурсов.
 */
public class HeartDrops {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        World world = event.getWorld();
        if (world.isRemote || event.isSilkTouching() || !ChaosConfig.heart.dropOreBlocks || !AntechamberDimension.is(world)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockPos hp = HeartPlacer.heartPos();
        if (Math.abs(pos.getX() - hp.getX()) > TileHeart.RADIUS || Math.abs(pos.getY() - hp.getY()) > TileHeart.RADIUS
                || Math.abs(pos.getZ() - hp.getZ()) > TileHeart.RADIUS) {
            return;
        }
        TileEntity te = world.getTileEntity(hp);
        if (!(te instanceof TileHeart)) {
            return;
        }
        IBlockState state = normalize(event.getState());
        for (HeartTiers.Growth g : HeartTiers.pool(HeartTiers.maxTier())) {
            if (g.state == state) {
                Item item = Item.getItemFromBlock(state.getBlock());
                if (item == net.minecraft.init.Items.AIR) {
                    return;
                }
                event.getDrops().clear();
                event.getDrops().add(new ItemStack(item, 1, state.getBlock().getMetaFromState(state)));
                event.setDropChance(1.0F);
                return;
            }
        }
    }

    /** Горящая редстоун-руда (после касания) — та же редстоун-руда. */
    private static IBlockState normalize(IBlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.LIT_REDSTONE_ORE) {
            return Blocks.REDSTONE_ORE.getDefaultState();
        }
        return state;
    }
}
