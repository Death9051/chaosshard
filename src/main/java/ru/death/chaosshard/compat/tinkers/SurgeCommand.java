package ru.death.chaosshard.compat.tinkers;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

/** /oh surge: запустить или снять Всплеск Хаоса на инструменте в руке. Класс грузится только при Tinkers. */
public final class SurgeCommand {

    public static void run(EntityPlayerMP player, ICommandSender sender) {
        ItemStack tool = player.getHeldItemMainhand();
        if (ChaosSurge.active(tool)) {
            tool.getTagCompound().getCompoundTag("chaosshard_surge").setLong("until", 0);
            ChaosSurge.tick(tool, player.world);
            sender.sendMessage(new TextComponentString("Всплеск снят."));
            return;
        }
        ChaosSurge.start(tool, player.world, TraitThread.SURGE_TICKS);
        sender.sendMessage(new TextComponentString("Всплеск Хаоса: " + ChaosSurge.lastReport));
    }

    private SurgeCommand() {
    }
}
