package ru.death.chaosshard;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.init.ModItems;

/** Вкладка «Осколок Хаоса» в творческом режиме. */
public final class ChaosTab extends CreativeTabs {

    public static final ChaosTab TAB = new ChaosTab();

    private ChaosTab() {
        super(Tags.MOD_ID);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack createIcon() {
        return new ItemStack(ModItems.GRIMOIRE);
    }
}
