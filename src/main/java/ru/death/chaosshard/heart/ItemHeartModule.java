package ru.death.chaosshard.heart;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Вставной модификатор Сердца Острова. */
public class ItemHeartModule extends Item {

    public enum Type {
        /** Пульс: наросты растут быстрее. */
        PULSE,
        /** Ёмкость: больше наростов одновременно. */
        CAPACITY,
        /** Богатство: редкие руды чаще. */
        RICH,
        /** Плодовитость: шанс вырастить два нароста сразу. */
        FERTILE,
        /** Двигатель: Сердце питается RF/FE от машин; с током каждый двигатель добавляет ещё один шаг роста за тик. */
        ENGINE
    }

    public final Type type;

    public ItemHeartModule(Type type) {
        this.type = type;
        setMaxStackSize(16);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format("chaosshard.heart_module." + type.name().toLowerCase() + ".desc"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("chaosshard.heart_module.hint"));
    }
}
