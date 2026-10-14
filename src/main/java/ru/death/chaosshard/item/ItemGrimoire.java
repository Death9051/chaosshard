package ru.death.chaosshard.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import ru.death.chaosshard.spell.Spell;
import ru.death.chaosshard.spell.SpellCaster;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.ChaosShard;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Гримуар Осколка: записи (книга квестов Better Questing), заклинания и катализатор Преобразования.
 */
public class ItemGrimoire extends Item {

    public ItemGrimoire() {
        setMaxStackSize(1);
        setCreativeTab(ru.death.chaosshard.ChaosTab.TAB);
    }

    /**
     * Книга: ПКМ — книга квестов. Магия: ПКМ — выбранное заклинание (по тому, что под взглядом).
     * Привязка: ПКМ только отдаёт указания слугам и сундукам (обрабатывают события слуг), сам Гримуар молчит.
     * Shift+ПКМ в воздух — следующий режим. Колесо заклинаний — K, окно магии — P, каст из любой руки — клавиша каста.
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote) {
                SpellCaster.toggleMode((EntityPlayerMP) player, stack);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        int mode = SpellCaster.mode(stack);
        if (mode == SpellCaster.MODE_BIND) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }
        if (mode == SpellCaster.MODE_BOOK) {
            if (world.isRemote) {
                if (Loader.isModLoaded("betterquesting")) {
                    ChaosShard.proxy.openGrimoire();
                } else {
                    player.sendMessage(new TextComponentTranslation("chaosshard.grimoire.no_bq"));
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        Spell spell = SpellCaster.selected(stack);
        if (!world.isRemote && spell != Spell.BOOK) {
            SpellCaster.cast((EntityPlayerMP) player, spell);
        }
        player.swingArm(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    /** ПКМ по блоку в режиме Магии — заклинание (оно само видит блок); в Привязке — ничего. */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        int mode = SpellCaster.mode(stack);
        if (mode == SpellCaster.MODE_BIND) {
            return EnumActionResult.SUCCESS;
        }
        Spell spell = SpellCaster.selected(stack);
        if (mode != SpellCaster.MODE_MAGIC || spell == Spell.BOOK) {
            return EnumActionResult.PASS;
        }
        if (!world.isRemote) {
            SpellCaster.cast((EntityPlayerMP) player, spell);
        }
        return EnumActionResult.SUCCESS;
    }

    /** Гримуар — катализатор Преобразования: в верстаке он не тратится. */
    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.format("item.chaosshard.grimoire.lore"));
        boolean magic = SpellCaster.magicMode(stack);
        tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format("item.chaosshard.grimoire.mode_" + SpellCaster.modeKey(stack)));
        if (magic) {
            Spell spell = SpellCaster.selected(stack);
            tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format("item.chaosshard.grimoire.current", I18n.format(spell.key())));
            if (net.minecraft.client.Minecraft.getMinecraft().player != null) {
                tooltip.add(TextFormatting.DARK_PURPLE + I18n.format("item.chaosshard.grimoire.form",
                        I18n.format(ru.death.chaosshard.spell.SpellPerks.formKey(spell,
                                ru.death.chaosshard.spell.SpellData.form(net.minecraft.client.Minecraft.getMinecraft().player, spell)))));
            }
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format(spell.key() + ".desc"));
        }
        tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.grimoire.use"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.grimoire.cycle"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.grimoire.keys"));
        tooltip.add(TextFormatting.GRAY + I18n.format("item.chaosshard.grimoire.craft"));
    }
}
