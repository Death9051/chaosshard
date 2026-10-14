package ru.death.chaosshard.hero;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.List;

/** Посох Вельмора: трофей третьей Опоры. ПКМ — шаг сквозь пространство на 10 блоков вперёд; раз в 3 секунды. */
public class ItemVelmorStaff extends Item {

    public static final int RANGE = 10, COOLDOWN = 60;

    public ItemVelmorStaff() {
        setMaxStackSize(1);
        setFull3D();
    }

    @Override
    public net.minecraft.util.EnumActionResult onItemUse(EntityPlayer player, World world, net.minecraft.util.math.BlockPos pos, EnumHand hand,
                                                        net.minecraft.util.EnumFacing facing, float hitX, float hitY, float hitZ) {
        return Trophies.place(player, world, pos, facing, hand);
    }


    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("item.chaosshard.velmor_staff.desc"));
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.translateToLocal("item.chaosshard.velmor_staff.lore"));
    }
}
