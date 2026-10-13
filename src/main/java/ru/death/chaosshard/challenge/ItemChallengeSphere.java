package ru.death.chaosshard.challenge;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Сфера Вызова: складывается на Алтаре Прорыва. ПКМ по блоку рядом со своим Якорем — враги идут на Якорь;
 * ПКМ в другом месте или в воздух — арена в архиве. Метаданные — сторона (для облика), состав — в NBT.
 */
public class ItemChallengeSphere extends Item {

    public static final String SPEC = "Challenge";

    public ItemChallengeSphere() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
    }

    public static ItemStack of(ChallengeSpec spec) {
        ItemStack s = new ItemStack(ru.death.chaosshard.init.ModItems.CHALLENGE_SPHERE, 1, spec.faction.ordinal());
        NBTTagCompound t = new NBTTagCompound();
        t.setTag(SPEC, spec.write());
        s.setTagCompound(t);
        return s;
    }

    public static ChallengeSpec spec(ItemStack s) {
        ChallengeSpec spec = ChallengeSpec.read(s.hasTagCompound() ? s.getTagCompound().getCompoundTag(SPEC) : null);
        if (!s.hasTagCompound()) {
            spec.faction = ChallengeFaction.values()[Math.max(0, Math.min(2, s.getMetadata()))];
        }
        return spec;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return "item.chaosshard.challenge_sphere";
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) {
            for (ChallengeFaction f : ChallengeFaction.values()) {
                ChallengeSpec s = new ChallengeSpec();
                s.faction = f;
                s.tier = 1;
                items.add(of(s));
            }
        }
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        return use((EntityPlayerMP) player, hand, pos) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(use((EntityPlayerMP) player, hand, null) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL, stack);
    }

    private static boolean use(EntityPlayerMP p, EnumHand hand, @Nullable BlockPos at) {
        ItemStack stack = p.getHeldItem(hand);
        ChallengeSpec spec = spec(stack);
        ItemStack keep = stack.copy();
        if (!p.isCreative()) {
            stack.shrink(1);
        }
        if (!ChallengeManager.start(p, spec, at)) {
            if (!p.isCreative()) {
                p.setHeldItem(hand, keep);
            }
            return false;
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        ChallengeSpec s = spec(stack);
        tooltip.add(TextFormatting.GOLD + I18n.translateToLocal(s.faction.key()));
        tooltip.add(TextFormatting.GRAY + (s.endless() ? I18n.translateToLocal("chaosshard.challenge.endless")
                : I18n.translateToLocalFormatted("chaosshard.challenge.tier", s.tier, s.waves())));
        for (ChallengeMod m : ChallengeMod.values()) {
            if (ChallengeMod.has(s.mods, m)) {
                tooltip.add(TextFormatting.RED + "• " + I18n.translateToLocal(m.key()));
            }
        }
        if (s.mods != 0) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.translateToLocalFormatted("chaosshard.challenge.bonus", (int) Math.round((s.rewardMul() - 1) * 100)));
        }
        tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("chaosshard.challenge.hint"));
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }
}
