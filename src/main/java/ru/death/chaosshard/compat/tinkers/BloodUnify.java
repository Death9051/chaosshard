package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.ChaosShard;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.smeltery.AlloyRecipe;
import slimeknights.tconstruct.library.smeltery.CastingRecipe;
import slimeknights.tconstruct.library.smeltery.ICastingRecipe;
import slimeknights.tconstruct.library.smeltery.MeltingRecipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Одна кровь на сборку: кровь Tinkers («blood») заменяется кровью EvilCraft («evilcraftblood»).
 * Плавильня из гнилой плоти и из тварей, которых жжёт, даёт кровь EvilCraft; отливки и сплавы Tinkers на крови
 * принимают и её. Так кровь из плавильни идёт в машины EvilCraft, в Якорь и на Кровавую платину, и наоборот.
 */
public final class BloodUnify {

    public static final String TIC = "blood";
    public static final String EVIL = "evilcraftblood";

    private BloodUnify() {
    }

    /** Сколько чего переделано — для журнала и автотеста. */
    public static int melted, entities, casts, alloys;

    public static void run() {
        Fluid tic = FluidRegistry.getFluid(TIC), evil = FluidRegistry.getFluid(EVIL);
        if (tic == null || evil == null) {
            return;
        }
        try {
            meltings(tic, evil);
            entityMelting(evil);
            castings(tic, evil, TinkerRegistry.getAllTableCastingRecipes(), true);
            castings(tic, evil, TinkerRegistry.getAllBasinCastingRecipes(), false);
            for (AlloyRecipe r : new ArrayList<>(TinkerRegistry.getAlloys())) {
                boolean uses = false;
                List<FluidStack> in = new ArrayList<>();
                for (FluidStack f : r.getFluids()) {
                    uses |= f.getFluid() == tic;
                    in.add(f.getFluid() == tic ? new FluidStack(evil, f.amount) : f.copy());
                }
                if (uses) {
                    TinkerRegistry.registerAlloy(new AlloyRecipe(r.getResult().copy(), in.toArray(new FluidStack[0])));
                    alloys++;
                }
            }
        } catch (Throwable t) {
            ChaosShard.LOG.warn("Кровь Tinkers → EvilCraft: не вышло ({})", t.toString());
        }
        ChaosShard.LOG.info("Кровь Tinkers → EvilCraft: плавка {}, твари {}, отливки {}, сплавы {}", melted, entities, casts, alloys);
    }

    /** Плавка в кровь Tinkers теперь даёт кровь EvilCraft (список рецептов правится на месте). */
    @SuppressWarnings("unchecked")
    private static void meltings(Fluid tic, Fluid evil) throws ReflectiveOperationException {
        Field f = TinkerRegistry.class.getDeclaredField("meltingRegistry");
        f.setAccessible(true);
        List<MeltingRecipe> list = (List<MeltingRecipe>) f.get(null);
        for (int i = 0; i < list.size(); i++) {
            MeltingRecipe r = list.get(i);
            if (r.output != null && r.output.getFluid() == tic) {
                list.set(i, new MeltingRecipe(r.input, new FluidStack(evil, r.output.amount), r.temperature));
                melted++;
            }
        }
    }

    /** Твари в плавильне без своей жидкости по умолчанию дают кровь Tinkers — даём им кровь EvilCraft. */
    @SuppressWarnings("unchecked")
    private static void entityMelting(Fluid evil) throws ReflectiveOperationException {
        Field f = TinkerRegistry.class.getDeclaredField("entityMeltingRegistry");
        f.setAccessible(true);
        Map<ResourceLocation, FluidStack> map = (Map<ResourceLocation, FluidStack>) f.get(null);
        for (EntityEntry e : ForgeRegistries.ENTITIES.getValuesCollection()) {
            if (e.getEntityClass() == null || !EntityLivingBase.class.isAssignableFrom(e.getEntityClass())) {
                continue;
            }
            ResourceLocation id = EntityList.getKey(e.getEntityClass());
            if (id != null && !map.containsKey(id)) {
                map.put(id, new FluidStack(evil, 20));
                entities++;
            }
        }
        // свои жидкости-кровь у отдельных тварей тоже переводим
        for (Map.Entry<ResourceLocation, FluidStack> en : map.entrySet()) {
            if (en.getValue() != null && TIC.equals(en.getValue().getFluid().getName())) {
                en.setValue(new FluidStack(evil, en.getValue().amount));
            }
        }
    }

    /** Отливки на крови Tinkers: рядом такая же на крови EvilCraft. */
    private static void castings(Fluid tic, Fluid evil, List<ICastingRecipe> all, boolean table) {
        for (ICastingRecipe ir : new ArrayList<>(all)) {
            if (!(ir instanceof CastingRecipe)) {
                continue;
            }
            CastingRecipe r = (CastingRecipe) ir;
            FluidStack fl = r.getFluid();
            if (fl == null || fl.getFluid() != tic) {
                continue;
            }
            CastingRecipe copy = new CastingRecipe(r.getResult().copy(), r.cast, new FluidStack(evil, fl.amount), r.getTime(),
                    r.consumesCast(), r.switchOutputs());
            if (table) {
                TinkerRegistry.registerTableCasting(copy);
            } else {
                TinkerRegistry.registerBasinCasting(copy);
            }
            casts++;
        }
    }
}
