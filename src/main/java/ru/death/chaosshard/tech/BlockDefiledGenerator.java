package ru.death.chaosshard.tech;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Скверный генератор. ПКМ — сколько даёт и почему стоит. */
public class BlockDefiledGenerator extends Block {

    public BlockDefiledGenerator() {
        super(Material.ROCK);
        setSoundType(SoundType.STONE);
        setHardness(3.0F);
        setResistance(15F);
        setLightLevel(0.4F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDefiledGenerator();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND || !player.getHeldItem(hand).isEmpty()) {
            return false;
        }
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileDefiledGenerator) {
                TileDefiledGenerator g = (TileDefiledGenerator) te;
                int why = g.reason();
                TextComponentTranslation msg = why == 0
                        ? new TextComponentTranslation("chaosshard.generator.info", g.anchorLevel(), g.rate(), g.stored())
                        : new TextComponentTranslation(why == 1 ? "chaosshard.generator.no_defile" : "chaosshard.generator.no_anchor");
                msg.getStyle().setColor(why == 0 ? TextFormatting.LIGHT_PURPLE : TextFormatting.GRAY);
                player.sendStatusMessage(msg, true);
            }
        }
        return true;
    }
}
