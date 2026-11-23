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
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(World world, IBlockState state) {
        return new TileBreachAltar();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileBreachAltar) {
            ((TileBreachAltar) te).dropAll();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        net.minecraft.item.ItemStack held = player.getHeldItem(hand);
        if (hand == EnumHand.MAIN_HAND && held.getItem() != ModItems.GRIMOIRE && held.getItem() != ModItems.BREACH_BOOK) {
            // пустая рука — окно Алтаря; предмет — положить подношение (Shift — всю стопку)
            if (!world.isRemote) {
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
                if (held.isEmpty()) {
                    player.openGui(ru.death.chaosshard.ChaosShard.INSTANCE, ru.death.chaosshard.heart.HeartGuiHandler.ALTAR,
                            world, pos.getX(), pos.getY(), pos.getZ());
                    BreachHints.show((EntityPlayerMP) player, pos);   // подсветить недостающие шпили
                } else if (te instanceof TileBreachAltar) {
                    ((TileBreachAltar) te).insert(player, held, player.isSneaking());
                }
            }
            return true;
        }
        if (held.getItem() == ModItems.GRIMOIRE) {
            // Старшие ритуалы (подношения брошены на Алтарь) или Вознесение зверя
            if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
                ru.death.chaosshard.ritual.AltarRituals.tryStart((EntityPlayerMP) player, pos);
            }
            return true;
        }
        if (held.getItem() != ModItems.BREACH_BOOK) {
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
