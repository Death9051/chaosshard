package ru.death.chaosshard.compat.body;

import flaxbeard.cyberware.api.CyberwareAPI;
import flaxbeard.cyberware.common.entity.EntityCyberZombie;
import flaxbeard.cyberware.common.item.ItemBlueprint;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Добыча Вызова «Сборщики» (только при Cyberware — класс трогается после проверки Loader.isModLoaded("cyberware")).
 * Детали и нейростимулятор — за волны и за победу; импланты — «снятые с трупа» (Cyberware: качество «scavenged»),
 * на высоких ступенях; изредка — чертёж.
 */
public final class CyberLoot {

    private static List<ItemStack> implants;

    /** Сделать кибер-зомби громилой (до появления в мире: импланты он получит сам, по-громильи). */
    public static void brute(EntityLiving m) {
        if (m instanceof EntityCyberZombie) {
            ((EntityCyberZombie) m).setBrute();
        }
    }

    public static boolean isBrute(EntityLiving m) {
        return m instanceof EntityCyberZombie && ((EntityCyberZombie) m).isBrute();
    }

    public static boolean isScavenged(ItemStack s) {
        return isImplant(s) && CyberwareAPI.getQualityTag(s) == CyberwareAPI.QUALITY_SCAVENGED;
    }

    public static int implantKinds() {
        return implants().size();
    }

    /** Все импланты Cyberware, кроме органов по умолчанию и творческого конденсатора. */
    static List<ItemStack> implants() {
        if (implants == null) {
            List<ItemStack> out = new ArrayList<>();
            for (Item it : ForgeRegistries.ITEMS) {
                ResourceLocation id = it.getRegistryName();
                if (id == null || !"cyberware".equals(id.getNamespace())
                        || id.getPath().equals("creative_battery") || id.getPath().equals("body_part")) {
                    continue;
                }
                NonNullList<ItemStack> subs = NonNullList.create();
                try {
                    it.getSubItems(CreativeTabs.SEARCH, subs);
                } catch (Throwable t) {
                    subs.add(new ItemStack(it));
                }
                for (ItemStack s : subs) {
                    if (!s.isEmpty() && CyberwareAPI.isCyberware(s)) {
                        out.add(s.copy());
                    }
                }
            }
            implants = out;
        }
        return implants;
    }

    public static boolean isImplant(ItemStack s) {
        ResourceLocation id = s.isEmpty() ? null : s.getItem().getRegistryName();
        return id != null && "cyberware".equals(id.getNamespace()) && CyberwareAPI.isCyberware(s);
    }

    /** Случайный имплант «снятый с трупа». */
    public static ItemStack scavenged(Random r) {
        List<ItemStack> all = implants();
        if (all.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = all.get(r.nextInt(all.size())).copy();
        s.setCount(1);
        return CyberwareAPI.writeQualityTag(s, CyberwareAPI.QUALITY_SCAVENGED);
    }

    private static List<ItemStack> components(Random r, int n) {
        Item c = Item.getByNameOrId("cyberware:component");
        List<ItemStack> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        int[] byMeta = new int[10];
        for (int i = 0; i < n; i++) {
            byMeta[r.nextInt(10)]++;
        }
        for (int m = 0; m < byMeta.length; m++) {
            if (byMeta[m] > 0) {
                out.add(new ItemStack(c, byMeta[m], m));
            }
        }
        return out;
    }

    private static ItemStack neuropozyne(int n) {
        Item it = Item.getByNameOrId("cyberware:neuropozyne");
        return it == null || n <= 0 ? ItemStack.EMPTY : new ItemStack(it, n);
    }

    /** За отбитую волну: 1–3 детали (с модификаторами больше). */
    public static List<ItemStack> wave(Random r, int tier, double mul) {
        return components(r, (int) Math.round((1 + r.nextInt(1 + Math.max(1, tier) / 2)) * mul));
    }

    /**
     * За победу: детали (2 + 2 за ступень), нейростимулятор со 2-й ступени, импланты с трупа с 4-й (1, на 5-й — 2),
     * на 5-й — шанс 30% (с модификаторами выше) на чертёж случайного импланта.
     */
    public static List<ItemStack> victory(Random r, int tier, double mul) {
        tier = Math.max(1, tier);
        List<ItemStack> out = components(r, (int) Math.round((2 + 2 * tier) * mul));
        if (tier >= 2) {
            ItemStack n = neuropozyne(1 + tier / 3);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        for (int i = 0; i < tier - 3; i++) {
            ItemStack s = scavenged(r);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        if (tier >= 5 && r.nextDouble() < 0.3 * mul) {
            List<ItemStack> all = implants();
            if (!all.isEmpty()) {
                ItemStack bp = ItemBlueprint.getBlueprintForItem(all.get(r.nextInt(all.size())).copy());
                if (bp != null && !bp.isEmpty()) {
                    out.add(bp);
                }
            }
        }
        return out;
    }

    private CyberLoot() {
    }
}
