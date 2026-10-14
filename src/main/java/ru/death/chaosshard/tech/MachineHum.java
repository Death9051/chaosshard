package ru.death.chaosshard.tech;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.Tags;

import java.util.HashMap;
import java.util.Map;

/**
 * Гул машин: Скверна у Якоря растёт быстрее, когда рядом работают чужие машины.
 * «Работает» — у машины на RF/FE меняется запас энергии между двумя замерами (она что-то ест или копит).
 * Свои блоки Хаоса не в счёт.
 */
public final class MachineHum {

    /** Прошлые замеры: позиция машины → запас. Свой у каждого Якоря. */
    private final Map<Long, Integer> last = new HashMap<>();
    private int working;

    static final String[] CARRIERS = {"cable", "duct", "conduit", "pipe", "wire", "transmitter", "cell", "cube", "battery",
            "capacitor", "bank", "transfernode", "powermanager"};

    /** Чей это тайл: по пространству имён в реестре или по пакету класса (Cyclic регистрирует тайлы под minecraft:). */
    public static String modOf(TileEntity te) {
        String cls = te.getClass().getName();
        if (cls.startsWith("mekanism.")) {
            return "mekanism";
        }
        if (cls.startsWith("cofh.thermal")) {
            return "thermal";
        }
        if (cls.startsWith("com.rwtema.extrautils2")) {
            return "extrautils2";
        }
        if (cls.startsWith("com.lothrazar.cyclicmagic")) {
            return "cyclic";
        }
        ResourceLocation id = TileEntity.getKey(te.getClass());
        return id == null ? "" : id.getNamespace();
    }

    /** Провода и хранилища — не машины: они гудят от любого тока. */
    public static boolean carrier(String path) {
        String s = path.toLowerCase();
        for (String c : CARRIERS) {
            if (s.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** Машины с RF/FE у Якоря (по последнему замеру), не больше потолка гула. */
    public java.util.List<Long> positions() {
        java.util.List<Long> out = new java.util.ArrayList<>();
        for (Long k : last.keySet()) {
            if (out.size() >= ChaosConfig.tech.humMax) {
                break;
            }
            out.add(k);
        }
        return out;
    }

    /** Сколько машин работает (по последнему замеру). */
    public int working() {
        return working;
    }

    /** Замер. Вызывать раз в несколько секунд. Возвращает число работающих машин (не больше потолка). */
    public int sample(World world, BlockPos anchor) {
        int r = ChaosConfig.tech.humRadius;
        Map<Long, Integer> now = new HashMap<>();
        int n = 0;
        for (TileEntity te : world.loadedTileEntityList) {
            BlockPos p = te.getPos();
            if (te.isInvalid() || Math.abs(p.getX() - anchor.getX()) > r || Math.abs(p.getZ() - anchor.getZ()) > r
                    || Math.abs(p.getY() - anchor.getY()) > r) {
                continue;
            }
            ResourceLocation id = TileEntity.getKey(te.getClass());
            // у ванильных тайлов RF/FE нет; Cyclic пишет свои тайлы под minecraft: — поэтому смотрим ещё и класс
            if (id == null || Tags.MOD_ID.equals(id.getNamespace()) || carrier(id.getPath())
                    || carrier(te.getClass().getSimpleName()) || !te.hasCapability(CapabilityEnergy.ENERGY, null)) {
                continue;
            }
            IEnergyStorage es = te.getCapability(CapabilityEnergy.ENERGY, null);
            if (es == null) {
                continue;
            }
            int e = es.getEnergyStored();
            Integer before = last.get(p.toLong());
            now.put(p.toLong(), e);
            if (before != null && before != e) {
                n++;
            }
        }
        last.clear();
        last.putAll(now);
        working = Math.min(n, ChaosConfig.tech.humMax);
        return working;
    }
}
