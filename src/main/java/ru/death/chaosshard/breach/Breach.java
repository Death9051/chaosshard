package ru.death.chaosshard.breach;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.antechamber.ChaosTeleporter;

/**
 * Прорыв сквозь Завесу: флаг «прорвался», путешествие Книгой Прорыва, голоса Гримуара и Лорда.
 */
public final class Breach {

    private static final String TAG_BREACHED = "chaosshard_breached";
    private static final String TAG_RETURN = "chaosshard_luch_return";

    public static boolean hasBreached(EntityPlayer player) {
        return persisted(player).getBoolean(TAG_BREACHED);
    }

    public static void setBreached(EntityPlayer player, boolean value) {
        persisted(player).setBoolean(TAG_BREACHED, value);
    }

    /** Главный мир Лучезарии — первое измерение из списка в конфиге. */
    public static int luchezariyaDim() {
        int[] dims = ChaosConfig.stars.luchezariyaDimensions;
        return dims.length > 0 ? dims[0] : 0;
    }

    /** Запомнить, где Осколок был в Лучезарии, чтобы вернуть его туда же. */
    public static void rememberReturnPoint(EntityPlayerMP player) {
        if (player.dimension != luchezariyaDim()) {
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        BlockPos p = player.getPosition();
        tag.setInteger("x", p.getX());
        tag.setInteger("y", p.getY());
        tag.setInteger("z", p.getZ());
        persisted(player).setTag(TAG_RETURN, tag);
    }

    /** Возвращался ли игрок из Лучезарии в Преддверие (точка возврата запомнена). */
    public static boolean hasReturnPoint(EntityPlayer player) {
        return persisted(player).getCompoundTag(TAG_RETURN).hasKey("y");
    }

    /** В Лучезарию: к точке возврата или к точке появления мира. */
    public static void toLuchezariya(EntityPlayerMP player) {
        NBTTagCompound tag = persisted(player).getCompoundTag(TAG_RETURN);
        int dim = luchezariyaDim();
        net.minecraft.world.WorldServer target = player.getServer().getWorld(dim);
        if (target != null) {
            ru.death.chaosshard.village.EntranceVillage.ensure(target);
        }
        if (tag.hasKey("y")) {
            ChaosTeleporter.toPos(player, dim, new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
        } else {
            ChaosTeleporter.toWorldSpawn(player, dim);
        }
    }

    /** Переход Книгой (или заклинанием): из Преддверия — в Лучезарию, отовсюду — в Преддверие. */
    public static void travel(EntityPlayerMP player) {
        if (AntechamberDimension.is(player.world)) {
            toLuchezariya(player);
        } else {
            rememberReturnPoint(player);
            ChaosTeleporter.toAntechamber(player);
        }
    }

    /** Запись в Гримуаре Осколка: «✎» и фиолетовый курсив. */
    public static ITextComponent grimoire(String key, Object... args) {
        net.minecraft.util.text.TextComponentString pen = new net.minecraft.util.text.TextComponentString("\u270E ");
        pen.getStyle().setColor(TextFormatting.DARK_GRAY);
        TextComponentTranslation msg = new TextComponentTranslation(key, args);
        msg.getStyle().setColor(TextFormatting.LIGHT_PURPLE).setItalic(true);
        return pen.appendSibling(msg);
    }

    public static ITextComponent lord(String key, Object... args) {
        TextComponentTranslation msg = new TextComponentTranslation(key, args);
        msg.getStyle().setColor(TextFormatting.DARK_RED).setBold(true);
        return msg;
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private Breach() {
    }
}
