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
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (!world.isRemote) {
            Vec3d eye = new Vec3d(p.posX, p.posY + p.getEyeHeight(), p.posZ);
            Vec3d end = eye.add(p.getLookVec().scale(RANGE));
            RayTraceResult hit = world.rayTraceBlocks(eye, end, false, true, false);
            Vec3d to = end;
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos b = hit.getBlockPos().offset(hit.sideHit);
                to = new Vec3d(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            } else {
                to = to.subtract(0, p.getEyeHeight(), 0);
            }
            BlockPos feet = new BlockPos(to);
            if (!world.isAirBlock(feet) || !world.isAirBlock(feet.up())) {
                return new ActionResult<>(EnumActionResult.FAIL, s);
            }
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.8, 0.3, 0.5);
            p.setPositionAndUpdate(to.x, to.y, to.z);
            p.fallDistance = 0;
            ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.8, 0.3, 0.5);
            world.playSound(null, p.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 1.3F);
        }
        p.getCooldownTracker().setCooldown(this, COOLDOWN);
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
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
