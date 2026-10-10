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

/**
 * Наросты Сердца — цельные куски руды: при добыче выпадает сам блок руды
 * (уголь, редстоун, лазурит, алмаз и т.д.), а не осколки. Так руду можно
 * удваивать в машинах и класть в рецепты страниц ресурсов.
 */
public class HeartDrops {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        World world = event.getWorld();
        if (world.isRemote || event.isSilkTouching() || !ChaosConfig.heart.dropOreBlocks) {
            return;
        }
        BlockPos pos = event.getPos();
        IBlockState state = normalize(event.getState());
        boolean growth = false;
        for (HeartTiers.Growth g : HeartTiers.pool(HeartTiers.maxTier())) {
            if (g.state == state) {
                growth = true;
                break;
            }
        }
        if (!growth) {
            return;
        }
        TileHeart heart = findHeart(world, pos);
        if (heart == null) {
            return;
        }
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == net.minecraft.init.Items.AIR) {
            return;
        }
        ItemStack drop = new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
        event.getDrops().clear();
        event.getDrops().add(drop.copy());
        // «Богатство»: шанс, что нарост окажется вдвое богаче
        if (world.rand.nextFloat() < heart.richChance()) {
            event.getDrops().add(drop.copy());
        }
        event.setDropChance(1.0F);
    }

    /** Сердце, в кольце которого стоит этот блок (наросты растут на уровне Сердца и на блок выше). */
    private static TileHeart findHeart(World world, BlockPos pos) {
        int r = TileHeart.RADIUS;
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-r, -1, -r), pos.add(r, 0, r))) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileHeart) {
                return (TileHeart) te;
            }
        }
        return null;
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
