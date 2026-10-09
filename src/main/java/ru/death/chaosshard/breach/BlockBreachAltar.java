package ru.death.chaosshard.breach;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;

/** Алтарь Прорыва. ПКМ Книгой Прорыва — начать ритуал. */
public class BlockBreachAltar extends ModBlocks.SimpleBlock {

    public BlockBreachAltar() {
        super(Material.ROCK, SoundType.STONE, 3.0F, 30.0F, 0.6F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.getHeldItem(hand).getItem() != ModItems.BREACH_BOOK) {
            if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
                player.sendStatusMessage(Breach.grimoire("chaosshard.breach.need_book"), true);
            }
            return hand == EnumHand.MAIN_HAND;
        }
        if (world.isRemote) {
            return true;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (BreachRitual.isRunning(mp)) {
            return true;
        }
        String error = BreachRitual.check(world, pos, mp);
        if (error != null) {
            mp.sendMessage(Breach.grimoire(error, ru.death.chaosshard.ChaosConfig.breach.threadLevel));
            return true;
        }
        BreachRitual.start(mp, pos);
        return true;
    }
}
