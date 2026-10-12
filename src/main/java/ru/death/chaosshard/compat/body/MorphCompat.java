package ru.death.chaosshard.compat.body;

import mchorse.metamorph.api.events.AcquireMorphEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ru.death.chaosshard.quest.Milestones;

/** Metamorph: вехи за поглощённые формы (первая и пятая). */
public final class MorphCompat {

    private static final String TAG = "chaosshard_morphs";

    static void init() {
        MinecraftForge.EVENT_BUS.register(new MorphCompat());
    }

    @SubscribeEvent
    public void onAcquire(AcquireMorphEvent.Post event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        NBTTagCompound data = p.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG);
        int n = data.getInteger(TAG) + 1;
        data.setInteger(TAG, n);
        p.getEntityData().setTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG, data);
        Milestones.grant(p, Milestones.MORPH_1);
        if (n >= 5) {
            Milestones.grant(p, Milestones.MORPH_5);
        }
    }
}
