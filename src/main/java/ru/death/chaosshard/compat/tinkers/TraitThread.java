package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import ru.death.chaosshard.thread.ThreadBonus;
import slimeknights.tconstruct.library.modifiers.ModifierTrait;

/**
 * «Нить Хаоса» — свойство хаосита и Искажённого металла, а также модификатор (кладётся хаоситом).
 * Сила растёт с уровнем Нити владельца и работает для любого инструмента:
 * урон, скорость добычи, шанс не потратить прочность.
 * Раз в минуту, пока инструмент в руке, — «Всплеск Хаоса»: случайный временный эффект.
 */
public class TraitThread extends ModifierTrait {

    /** Хаосит на один модификатор (эссенция идёт за несколько). */
    public static final int COST = 64;

    private static final Potion[] SURGE = {
            MobEffects.HASTE, MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.REGENERATION,
            MobEffects.RESISTANCE, MobEffects.JUMP_BOOST, MobEffects.NIGHT_VISION, MobEffects.ABSORPTION,
            MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING
    };

    public TraitThread() {
        super("chaosshard_thread", TinkersCompat.COLOR, 1, COST);
    }

    private static int level(EntityLivingBase entity) {
        return entity instanceof EntityPlayer ? ThreadBonus.level((EntityPlayer) entity) : 0;
    }

    @Override
    public float damage(ItemStack tool, EntityLivingBase player, EntityLivingBase target, float damage, float newDamage, boolean isCritical) {
        return newDamage + 0.5F * level(player);
    }

    @Override
    public void miningSpeed(ItemStack tool, PlayerEvent.BreakSpeed event) {
        event.setNewSpeed(event.getNewSpeed() * (1.0F + 0.08F * level(event.getEntityPlayer())));
    }

    @Override
    public int onToolDamage(ItemStack tool, int damage, int newDamage, EntityLivingBase entity) {
        int lvl = level(entity);
        if (lvl > 0 && newDamage > 0 && entity.world.rand.nextFloat() < 0.05F * lvl) {
            return 0;
        }
        return newDamage;
    }

    @Override
    public void onUpdate(ItemStack tool, World world, Entity entity, int itemSlot, boolean isSelected) {
        if (world.isRemote || !isSelected || !(entity instanceof EntityPlayer)) {
            return;
        }
        if ((world.getTotalWorldTime() + entity.getEntityId()) % 1200 != 0) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;
        Potion potion = SURGE[world.rand.nextInt(SURGE.length)];
        int amp = level(player) >= 3 ? 1 : 0;
        player.addPotionEffect(new PotionEffect(potion, 20 * 20, amp, false, true));
        TextComponentTranslation name = new TextComponentTranslation(potion.getName());
        TextComponentTranslation msg = new TextComponentTranslation("chaosshard.surge", name);
        msg.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        player.sendStatusMessage(msg, true);
        world.playSound(null, player.posX, player.posY, player.posZ, net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.PLAYERS, 0.6F, 0.6F);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, player.posX, player.posY + 1.0, player.posZ,
                20, 0.4, 0.6, 0.4, 0.05);
    }
}
