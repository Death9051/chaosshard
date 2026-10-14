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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.structure.EntityOrderKnight;

import javax.annotation.Nullable;
import java.util.List;

/** Перстень Элис: трофей пятой Опоры. ПКМ — двое из королевской стражи служат мне 2 минуты; раз в 5 минут. */
public class ItemElisSignet extends Item {

    public static final int COOLDOWN = 6000, SERVICE = 2400;
    /** Метка: до какого времени мира служит стражник по перстню. */
    public static final String UNTIL = "chaosshard_until";

    public ItemElisSignet() {
        setMaxStackSize(1);
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
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("item.chaosshard.elis_signet.desc"));
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.translateToLocal("item.chaosshard.elis_signet.lore"));
    }
}
