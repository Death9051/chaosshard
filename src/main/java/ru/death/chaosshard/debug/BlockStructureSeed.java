package ru.death.chaosshard.debug;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Тестовый блок: при установке строит готовую структуру мода и исчезает.
 * Только для творческого режима и отладки.
 */
public class BlockStructureSeed extends Block {

    private final StructureBuilder.Kind kind;

    public BlockStructureSeed(StructureBuilder.Kind kind) {
        super(Material.ROCK);
        this.kind = kind;
        setSoundType(SoundType.STONE);
        setHardness(0.0F);
        setLightLevel(0.5F);
    }

    public StructureBuilder.Kind kind() {
        return kind;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        world.setBlockToAir(pos);
        EntityPlayerMP player = placer instanceof EntityPlayerMP ? (EntityPlayerMP) placer : null;
        String error = StructureBuilder.build(world, pos, kind, player);
        if (player != null) {
            TextComponentTranslation msg = error == null
                    ? new TextComponentTranslation("chaosshard.seed.built",
                    new TextComponentTranslation(getTranslationKey() + ".name"), pos.getX(), pos.getY(), pos.getZ())
                    : new TextComponentTranslation(error);
            msg.getStyle().setColor(error == null ? TextFormatting.LIGHT_PURPLE : TextFormatting.RED);
            player.sendMessage(msg);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + I18n.translateToLocal(getTranslationKey() + ".desc"));
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("chaosshard.seed.hint"));
    }
}
