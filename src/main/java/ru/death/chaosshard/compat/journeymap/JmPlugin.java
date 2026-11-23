package ru.death.chaosshard.compat.journeymap;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import ru.death.chaosshard.ChaosShard;
import ru.death.chaosshard.Tags;

/**
 * Связь с JourneyMap: метки Опор, названных Знамением. Пока Опора стоит, метка на карте;
 * пала — метка уходит. Метки не сохраняются самим JourneyMap: сервер присылает их заново при каждом входе.
 */
@ClientPlugin
public class JmPlugin implements IClientPlugin {

    public static final int PILLAR_COLOR = 0xF2D27A;
    private static IClientAPI api;

    @Override
    public void initialize(IClientAPI jm) {
        api = jm;
        refresh();
    }

    @Override
    public String getModId() {
        return Tags.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) {
    }

    /** Перерисовать метки по последнему списку с сервера. */
    public static void refresh() {
        if (api == null) {
            return;
        }
        try {
            api.removeAll(Tags.MOD_ID, DisplayType.Waypoint);
            NBTTagCompound t = PillarMarksClient.get();
            int dim = t.getInteger("dim");
            NBTTagList l = t.getTagList("marks", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound m = l.getCompoundTagAt(i);
                String id = m.getString("id");
                Waypoint w = new Waypoint(Tags.MOD_ID, "pillar_" + id, I18n.format("chaosshard.map.pillar." + id), dim,
                        new BlockPos(m.getInteger("x"), m.getInteger("y"), m.getInteger("z")));
                w.setColor(PILLAR_COLOR);
                w.setPersistent(false);
                w.setEditable(false);
                api.show(w);
            }
        } catch (Throwable e) {
            ChaosShard.LOG.warn("JourneyMap: не вышло поставить метки Опор", e);
        }
    }
}
