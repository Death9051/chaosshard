package ru.death.chaosshard.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketEnchantKnown;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Изученные зачарования Осколка. Хранятся у игрока в сохраняемых данных (переживают смерть), ключи — имена
 * зачарований в реестре. Клиент получает копию пакетом при открытии Стола Хаоса и после каждого изучения.
 */
public final class EnchantKnowledge {

    static final String KEY = "chaosshard_known_ench";

    /** Клиентская копия. */
    public static volatile Set<String> client = Collections.emptySet();

    private EnchantKnowledge() {
    }

    private static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            d.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return d.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static Set<String> known(EntityPlayer p) {
        if (p.world.isRemote) {
            return client;
        }
        NBTTagList list = data(p).getTagList(KEY, Constants.NBT.TAG_STRING);
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            out.add(list.getStringTagAt(i));
        }
        return out;
    }

    public static boolean knows(EntityPlayer p, Enchantment e) {
        return known(p).contains(EnchanterLogic.id(e));
    }

    /** Изучить; true — если раньше не знал. */
    public static boolean learn(EntityPlayerMP p, Enchantment e) {
        String id = EnchanterLogic.id(e);
        if (id.isEmpty() || knows(p, e)) {
            return false;
        }
        NBTTagCompound d = data(p);
        NBTTagList list = d.getTagList(KEY, Constants.NBT.TAG_STRING);
        list.appendTag(new NBTTagString(id));
        d.setTag(KEY, list);
        sync(p);
        return true;
    }

    public static void sync(EntityPlayerMP p) {
        ChaosNetwork.CHANNEL.sendTo(new PacketEnchantKnown(known(p)), p);
    }

    /** Принять копию на клиенте. */
    public static void receive(Set<String> ids) {
        client = Collections.unmodifiableSet(new HashSet<>(ids));
    }
}
