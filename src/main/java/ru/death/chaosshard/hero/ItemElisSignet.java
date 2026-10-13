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
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (!world.isRemote) {
            for (int i = 0; i < 2; i++) {
                EntityOrderKnight k = new EntityOrderKnight(world);
                k.setup(i == 0 ? EntityOrderKnight.CAPTAIN : EntityOrderKnight.GUARD);
                k.removeTag(EntityOrderKnight.TAG);
                k.setCustomNameTag(TextFormatting.GOLD + I18n.translateToLocal("chaosshard.hero.elis.royal"));
                k.getEntityData().setUniqueId(Servants.TAG_OWNER, p.getUniqueID());
                k.getEntityData().setLong(UNTIL, world.getTotalWorldTime() + SERVICE);
                double a = i * Math.PI + world.rand.nextDouble();
                k.setLocationAndAngles(p.posX + Math.cos(a) * 2, p.posY, p.posZ + Math.sin(a) * 2, p.rotationYaw, 0);
                world.spawnEntity(k);
                ((WorldServer) world).spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, k.posX, k.posY + 1, k.posZ, 30, 0.3, 0.8, 0.3, 0.05);
            }
            world.playSound(null, p.getPosition(), SoundEvents.BLOCK_NOTE_BASS, SoundCategory.PLAYERS, 2.0F, 0.6F);
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
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal("item.chaosshard.elis_signet.desc"));
        tooltip.add(TextFormatting.DARK_PURPLE + "" + TextFormatting.ITALIC + I18n.translateToLocal("item.chaosshard.elis_signet.lore"));
    }
}
