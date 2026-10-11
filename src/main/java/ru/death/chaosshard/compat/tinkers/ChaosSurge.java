package ru.death.chaosshard.compat.tinkers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import ru.death.chaosshard.ChaosShard;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.modifiers.IModifier;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.modifiers.TinkerGuiException;
import slimeknights.tconstruct.library.tinkering.TinkersItem;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * «Всплеск Хаоса»: инструмент на время получает случайный модификатор Tinkers', который к нему подходит.
 * Слот модификатора не тратится. По окончании инструмент пересобирается из прежней основы —
 * прочность, опыт уровней и прочие данные остаются как есть.
 */
public final class ChaosSurge {

    private static final String TAG = "chaosshard_surge";
    /** Не выдавать: служебные, творческие и сама Нить. */
    private static final Set<String> BLACKLIST = new HashSet<>(Arrays.asList(
            "chaosshard_thread", "creative", "toolleveling", "harvestwidth", "harvestheight", "tconevo.artifact"));

    public static boolean active(ItemStack tool) {
        return tool.hasTagCompound() && tool.getTagCompound().hasKey(TAG);
    }

    /** Итог последней попытки — для команды /oh surge и лога. */
    public static String lastReport = "";

    /** Наложить случайный модификатор. Возвращает модификатор или null, если ни один не встал. */
    public static IModifier start(ItemStack tool, World world, int durationTicks) {
        if (!(tool.getItem() instanceof TinkersItem) || !tool.hasTagCompound()) {
            lastReport = "не инструмент Tinkers'";
            return null;
        }
        if (active(tool)) {
            lastReport = "всплеск уже идёт";
            return null;
        }
        NBTTagCompound original = tool.getTagCompound();
        NBTTagCompound baseBackup = TagUtil.getBaseTag(original).copy();
        NBTTagList enchBackup = original.getTagList("ench", 10).copy();

        List<IModifier> pool = new ArrayList<>();
        for (IModifier m : TinkerRegistry.getAllModifiers()) {
            String id = m.getIdentifier();
            if (m.isHidden() || !m.hasItemsToApplyWith() || BLACKLIST.contains(id) || id.startsWith("extratrait")) {
                continue;
            }
            if (TinkerUtil.hasModifier(original, id)) {
                continue;
            }
            pool.add(m);
        }
        java.util.Collections.shuffle(pool, new Random(world.rand.nextLong()));
        int tried = 0;
        String lastError = "";
        for (IModifier mod : pool) {
            if (tried >= 40) {
                break;
            }
            tried++;
            ItemStack work = tool.copy();
            NBTTagCompound root = work.getTagCompound();
            try {
                boolean applied = false;
                // Многие модификаторы набирают уровень из нескольких предметов — докладываем, пока не появится 1-й уровень
                for (int i = 0; i < 128; i++) {
                    giveFreeSlot(root);
                    if (!mod.canApply(work, tool)) {
                        break;
                    }
                    mod.apply(work);
                    applied = true;
                    NBTTagCompound mt = TinkerUtil.getModifierTag(root, mod.getIdentifier());
                    if (!mt.isEmpty() && ModifierNBT.readTag(mt).level >= 1) {
                        break;
                    }
                }
                if (!applied) {
                    continue;
                }
                ToolBuilder.rebuildTool(root, (TinkersItem) work.getItem());
                if (!TinkerUtil.hasModifier(root, mod.getIdentifier())) {
                    continue;
                }
            } catch (TinkerGuiException e) {
                lastError = e.getMessage();
                continue;
            } catch (Throwable t) {
                lastError = t.toString();
                continue;
            }
            NBTTagCompound surge = new NBTTagCompound();
            surge.setString("id", mod.getIdentifier());
            surge.setLong("until", world.getTotalWorldTime() + durationTicks);
            surge.setTag("base", baseBackup);
            surge.setTag("ench", enchBackup);
            root.setTag(TAG, surge);
            tool.setTagCompound(root);
            lastReport = "встал «" + name(mod) + "» (" + mod.getIdentifier() + "), попыток: " + tried + " из " + pool.size();
            return mod;
        }
        lastReport = "ни один модификатор не встал: кандидатов " + pool.size() + ", попыток " + tried
                + (lastError.isEmpty() ? "" : ", последняя ошибка: " + lastError);
        ChaosShard.LOG.info("Всплеск Хаоса: {}", lastReport);
        return null;
    }

    /** Вызывается каждый тик из свойства: снять модификатор, когда время вышло. */
    public static boolean tick(ItemStack tool, World world) {
        if (!active(tool)) {
            return false;
        }
        NBTTagCompound surge = tool.getTagCompound().getCompoundTag(TAG);
        if (world.getTotalWorldTime() < surge.getLong("until")) {
            return false;
        }
        restore(tool, surge.getCompoundTag("base"), surge.getTagList("ench", 10), surge.getString("id"));
        return true;
    }

    private static void restore(ItemStack tool, NBTTagCompound base, NBTTagList ench, String id) {
        NBTTagCompound root = tool.getTagCompound();
        root.removeTag(TAG);
        TagUtil.setBaseTag(root, base.copy());
        NBTTagList mods = TagUtil.getModifiersTagList(root);
        int idx = TinkerUtil.getIndexInCompoundList(mods, id);
        if (idx >= 0) {
            mods.removeTag(idx);
            TagUtil.setModifiersTagList(root, mods);
        }
        if (ench.isEmpty()) {
            root.removeTag("ench");
        } else {
            root.setTag("ench", ench.copy());
        }
        try {
            ToolBuilder.rebuildTool(root, (TinkersItem) tool.getItem());
        } catch (Throwable t) {
            ChaosShard.LOG.warn("Всплеск Хаоса: не удалось пересобрать инструмент: {}", t.toString());
        }
    }

    private static void giveFreeSlot(NBTTagCompound root) {
        NBTTagCompound stats = root.getCompoundTag("Stats");
        if (stats.getInteger("FreeModifiers") < 1) {
            stats.setInteger("FreeModifiers", 1);
            root.setTag("Stats", stats);
        }
    }

    public static String name(IModifier mod) {
        try {
            return mod.getLocalizedName();
        } catch (Throwable t) {
            return mod.getIdentifier();
        }
    }

    /** Чтобы игрок, у которого инструмент забрали, всё равно получил его без всплеска. */
    public static void cleanup(EntityPlayer player) {
        for (ItemStack s : player.inventory.mainInventory) {
            if (active(s)) {
                NBTTagCompound surge = s.getTagCompound().getCompoundTag(TAG);
                restore(s, surge.getCompoundTag("base"), surge.getTagList("ench", 10), surge.getString("id"));
            }
        }
    }

    private ChaosSurge() {
    }
}
