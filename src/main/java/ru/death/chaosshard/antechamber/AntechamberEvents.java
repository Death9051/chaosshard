package ru.death.chaosshard.antechamber;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.init.ModItems;

/**
 * Первый вход в мир и возрождение Осколка.
 */
public class AntechamberEvents {

    private static final String TAG_ARRIVED = "chaosshard_arrived";
    private static final String TAG_GRIMOIRE = "chaosshard_grimoire_given";

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        NBTTagCompound persisted = persisted(player);
        // Гримуар выдаётся один раз, в том числе в мирах, созданных до его появления
        if (!persisted.getBoolean(TAG_GRIMOIRE)) {
            persisted.setBoolean(TAG_GRIMOIRE, true);
            giveGrimoire(player);
        }
        if (persisted.getBoolean(TAG_ARRIVED) || !ChaosConfig.antechamber.teleportOnFirstJoin) {
            return;
        }
        persisted.setBoolean(TAG_ARRIVED, true);
        ChaosTeleporter.toAntechamber(player);
        TextComponentTranslation hello = new TextComponentTranslation("chaosshard.antechamber.first_arrival");
        hello.getStyle().setColor(TextFormatting.DARK_PURPLE).setItalic(true);
        player.sendMessage(hello);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.isEndConquered() || !ChaosConfig.antechamber.respawnInAntechamber) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // Есть кровать в мире, где игрок возродился, — уважаем её
        if (player.getBedLocation(player.dimension) != null) {
            return;
        }
        boolean alreadyHere = AntechamberDimension.is(player.world);
        // Даже если Осколок уже в Преддверии, переносим в центр: там гарантированно
        // достроится площадка (центр острова могли раскопать — иначе вечное падение в пустоту)
        ChaosTeleporter.toAntechamber(player);
        if (alreadyHere) {
            return;
        }
        TextComponentTranslation msg = new TextComponentTranslation("chaosshard.antechamber.reassembled");
        msg.getStyle().setColor(TextFormatting.DARK_PURPLE).setItalic(true);
        player.sendMessage(msg);
    }

    /** Положить Гримуар в инвентарь (или под ноги, если места нет). */
    public static void giveGrimoire(EntityPlayerMP player) {
        ItemStack book = new ItemStack(ModItems.GRIMOIRE);
        if (!player.inventory.addItemStackToInventory(book)) {
            player.dropItem(book, false);
        }
    }

    /** Данные, переживающие смерть игрока. */
    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
