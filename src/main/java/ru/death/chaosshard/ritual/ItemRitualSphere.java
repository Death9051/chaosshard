package ru.death.chaosshard.ritual;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Сфера ритуала: Чаша запечатывает в неё длительный ритуал. ПКМ по блоку — сфера встаёт и действует там.
 * Порыв Скверны срабатывает сразу, если рядом (до 48 блоков) есть Якорь.
 * NBT «Ticks» — сколько ещё осталось (у поднятой обратно сферы).
 */
public class ItemRitualSphere extends Item {

    public static final String TICKS = "Ticks";

    public ItemRitualSphere() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
    }

    public static ItemStack of(Ritual r, int ticks) {
        ItemStack s = new ItemStack(ru.death.chaosshard.init.ModItems.RITUAL_SPHERE, 1, Math.max(0, r.sphereIndex()));
        if (ticks > 0 && ticks < r.duration) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger(TICKS, ticks);
            s.setTagCompound(t);
        }
        return s;
    }

    public static int ticks(ItemStack s) {
        Ritual r = Ritual.sphere(s.getMetadata());
        return s.hasTagCompound() && s.getTagCompound().hasKey(TICKS) ? s.getTagCompound().getInteger(TICKS) : r.duration;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return "item.chaosshard.ritual_sphere." + Ritual.sphere(stack.getMetadata()).name().toLowerCase();
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) {
            for (Ritual r : Ritual.SPHERES) {
                items.add(new ItemStack(this, 1, r.sphereIndex()));
            }
        }
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        Ritual r = Ritual.sphere(stack.getMetadata());
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        if (r == Ritual.CORRUPTION) {
            TileAnchor a = RitualEffects.nearestAnchor(world, pos);
            if (a == null) {
                player.sendStatusMessage(Breach.grimoire("chaosshard.ritual.no_anchor"), true);
                return EnumActionResult.FAIL;
            }
            int done = a.forceSpread(400);
            player.sendMessage(Breach.grimoire("chaosshard.ritual.corruption.done", done));
            ((WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    60, 0.6, 0.6, 0.6, 0.05);
            world.playSound(null, pos, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.BLOCKS, 1.0F, 0.5F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            return EnumActionResult.SUCCESS;
        }
        BlockPos at = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(facing);
        if (!world.getBlockState(at).getBlock().isReplaceable(world, at) || !player.canPlayerEdit(at, facing, stack)) {
            return EnumActionResult.FAIL;
        }
        world.setBlockState(at, ModBlocks.RITUAL_SPHERE.getDefaultState(), 3);
        TileEntity te = world.getTileEntity(at);
        if (te instanceof TileRitualSphere) {
            ((TileRitualSphere) te).start(r, ticks(stack), player);
        }
        world.playSound(null, at, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 0.8F, 1.2F);
        if (player instanceof EntityPlayerMP) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.ritual.sphere.placed", I18n.translateToLocal(r.key())), true);
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Ritual r = Ritual.sphere(stack.getMetadata());
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(r.key() + ".desc"));
        if (r.duration > 0) {
            int t = ticks(stack);
            tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.translateToLocalFormatted("chaosshard.ritual.sphere.left", t / 1200, (t / 20) % 60));
            tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("chaosshard.ritual.sphere.hint"));
        } else {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.translateToLocal("chaosshard.ritual.sphere.hint_anchor"));
        }
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }
}
