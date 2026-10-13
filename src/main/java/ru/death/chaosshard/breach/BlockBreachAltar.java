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
        if (player.getHeldItem(hand).getItem() == ModItems.GRIMOIRE) {
            // Старшие ритуалы (подношения брошены на Алтарь) или Вознесение зверя
            if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
                ru.death.chaosshard.ritual.AltarRituals.tryStart((EntityPlayerMP) player, pos);
            }
            return true;
        }
        if (player.getHeldItem(hand).getItem() != ModItems.BREACH_BOOK) {
            if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
                // Без книги — просто показать, чего не хватает в постройке
                BreachHints.show((EntityPlayerMP) player, pos);
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
            BreachHints.show(mp, pos);
            return true;
        }
        BreachRitual.start(mp, pos);
        return true;
    }

    @Override
    public boolean isOpaqueCube(net.minecraft.block.state.IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(net.minecraft.block.state.IBlockState state) {
        return false;
    }

    @Override
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT;
    }

    @Override
    public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(net.minecraft.world.IBlockAccess world,
            net.minecraft.block.state.IBlockState state, net.minecraft.util.math.BlockPos pos, net.minecraft.util.EnumFacing face) {
        return face == net.minecraft.util.EnumFacing.DOWN ? net.minecraft.block.state.BlockFaceShape.SOLID
                : net.minecraft.block.state.BlockFaceShape.UNDEFINED;
    }
}
