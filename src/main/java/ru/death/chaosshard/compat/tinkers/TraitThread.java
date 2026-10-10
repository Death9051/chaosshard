package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
 * Раз в минуту, пока инструмент в руке, — «Всплеск Хаоса»: случайный подходящий модификатор Tinkers' на 30 секунд.
 */
public class TraitThread extends ModifierTrait {

    /** Хаосит на один модификатор (эссенция идёт за несколько). */
    public static final int COST = 64;


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

    /** Длительность всплеска, тики. */
    private static final int SURGE_TICKS = 20 * 30;

    @Override
    public void onUpdate(ItemStack tool, World world, Entity entity, int itemSlot, boolean isSelected) {
        if (world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;
        // Срок вышел — снять временный модификатор (даже если инструмент не в руке)
        if (ChaosSurge.tick(tool, world)) {
            TextComponentTranslation msg = new TextComponentTranslation("chaosshard.surge.end");
            msg.getStyle().setColor(TextFormatting.DARK_PURPLE);
            player.sendStatusMessage(msg, true);
            return;
        }
        if (!isSelected || (world.getTotalWorldTime() + entity.getEntityId()) % 1200 != 0) {
            return;
        }
        slimeknights.tconstruct.library.modifiers.IModifier mod = ChaosSurge.start(tool, world, SURGE_TICKS);
        if (mod == null) {
            return;
        }
        TextComponentTranslation msg = new TextComponentTranslation("chaosshard.surge", ChaosSurge.name(mod), SURGE_TICKS / 20);
        msg.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        player.sendStatusMessage(msg, true);
        world.playSound(null, player.posX, player.posY, player.posZ, net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.PLAYERS, 0.6F, 0.6F);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, player.posX, player.posY + 1.0, player.posZ,
                20, 0.4, 0.6, 0.4, 0.05);
    }
}
