package ru.death.chaosshard.anchor;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.UseHoeEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Хозяйство на Скверне: скверная трава и земля Defiled Lands пашутся мотыгой в обычную пашню, а саженцы и цветы
 * на них приживаются (последнее — миксин MixinDefiledSoil).
 */
public class DefiledFarming {

    /** Скверная трава или земля. */
    public static boolean soil(Block b) {
        if (b == null || b.getRegistryName() == null) {
            return false;
        }
        String id = b.getRegistryName().toString();
        return "defiledlands:grass_defiled".equals(id) || "defiledlands:dirt_defiled".equals(id);
    }

    @SubscribeEvent
    public void onHoe(UseHoeEvent event) {
        World w = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState s = w.getBlockState(pos);
        if (!soil(s.getBlock()) || !w.isAirBlock(pos.up())) {
            return;
        }
        w.playSound(event.getEntityPlayer(), pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (!w.isRemote) {
            w.setBlockState(pos, Blocks.FARMLAND.getDefaultState(), 11);
        }
        event.setResult(Event.Result.ALLOW);
    }
}
