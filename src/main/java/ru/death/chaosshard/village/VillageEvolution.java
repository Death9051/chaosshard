package ru.death.chaosshard.village;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.anchor.Corruption;
import ru.death.chaosshard.init.ModItems;

import java.util.List;

/**
 * Эволюция деревни под покровительством: на последнем уровне Знака жители становятся Адептами Хаоса —
 * своя профессия, свой облик и торговля дарами Хаоса (Осколки, Эссенция, Обрывки, мел, печати).
 * Дети и новые жители обращаются, когда подрастут/появятся (Знак проверяет раз в минуту).
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class VillageEvolution {

    public static final String PROFESSION = Tags.MOD_ID + ":adept";
    public static VillagerRegistry.VillagerProfession ADEPT;

    @SubscribeEvent
    public static void registerProfessions(RegistryEvent.Register<VillagerRegistry.VillagerProfession> event) {
        ADEPT = new VillagerRegistry.VillagerProfession(PROFESSION, Tags.MOD_ID + ":textures/entity/villager_adept.png",
                "minecraft:textures/entity/zombie_villager/zombie_villager.png");
        event.getRegistry().register(ADEPT);
        VillagerRegistry.VillagerCareer career = new VillagerRegistry.VillagerCareer(ADEPT, "chaos_adept");
        // 1-я ступень: мел и Осколки за изумруды, изумруды за гниль
        career.addTrade(1,
                new EntityVillager.ListItemForEmeralds(ModItems.CHALK, new EntityVillager.PriceInfo(3, 5)),
                new EntityVillager.ListItemForEmeralds(ModItems.VEIL_SHARD, new EntityVillager.PriceInfo(5, 8)),
                new EntityVillager.EmeraldForItems(net.minecraft.init.Items.ROTTEN_FLESH, new EntityVillager.PriceInfo(18, 24)));
        // 2-я: Эссенция и Обрывки памяти, изумруды за кости
        career.addTrade(2,
                new EntityVillager.ListItemForEmeralds(ModItems.CHAOS_ESSENCE, new EntityVillager.PriceInfo(6, 9)),
                new EntityVillager.ListItemForEmeralds(ModItems.MEMORY_FRAGMENT, new EntityVillager.PriceInfo(9, 14)),
                new EntityVillager.EmeraldForItems(net.minecraft.init.Items.BONE, new EntityVillager.PriceInfo(10, 14)));
        // 3-я: жемчуг Края, хаосит пачкой
        career.addTrade(3,
                new EntityVillager.ListItemForEmeralds(net.minecraft.init.Items.ENDER_PEARL, new EntityVillager.PriceInfo(4, 7)),
                new EntityVillager.ListItemForEmeralds(Item.getItemFromBlock(ru.death.chaosshard.init.ModBlocks.CHAOS_STONE),
                        new EntityVillager.PriceInfo(-16, -12)));
        // 4-я: печати Якоря
        for (Item seal : ModItems.SEALS.values()) {
            career.addTrade(4, new EntityVillager.ListItemForEmeralds(seal, new EntityVillager.PriceInfo(24, 32)));
        }
    }

    /** Обратить жителей деревни, в которой стоит Знак. Возвращает, сколько обращено сейчас. */
    public static int evolve(World world, BlockPos sigil) {
        if (ADEPT == null) {
            return 0;
        }
        Village v = world.getVillageCollection().getNearestVillage(sigil, 32);
        BlockPos c = v != null ? v.getCenter() : sigil;
        int r = v != null ? v.getVillageRadius() : 32;
        List<EntityVillager> list = world.getEntitiesWithinAABB(EntityVillager.class,
                new AxisAlignedBB(c.add(-r, -16, -r), c.add(r, 16, r)));
        int n = 0;
        for (EntityVillager villager : list) {
            if (villager.isChild() || villager.getProfessionForge() == ADEPT) {
                continue;
            }
            // Пересоздаём жителя из его же данных, но с новой профессией и без старых сделок
            NBTTagCompound tag = villager.writeToNBT(new NBTTagCompound());
            tag.setString("id", "minecraft:villager");
            tag.setString("ProfessionName", PROFESSION);
            tag.setInteger("Career", 0);
            tag.setInteger("CareerLevel", 0);
            tag.removeTag("Offers");
            tag.removeTag("UUIDMost");
            tag.removeTag("UUIDLeast");
            net.minecraft.entity.Entity fresh = EntityList.createEntityFromNBT(tag, world);
            if (!(fresh instanceof EntityVillager)) {
                continue;
            }
            EntityVillager adept = (EntityVillager) fresh;
            adept.setProfession(ADEPT);
            adept.enablePersistence();
            world.removeEntity(villager);
            world.spawnEntity(adept);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, adept.posX, adept.posY + 1, adept.posZ,
                    30, 0.3, 0.6, 0.3, 0.03);
            n++;
        }
        if (n > 0) {
            world.playSound(null, c, net.minecraft.init.SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.NEUTRAL, 1.0F, 0.6F);
        }
        return n;
    }

    /** Сколько осквернённых столбцов в кайме: между сжатой границей защиты и прежней (радиус деревни + 8). */
    public static int countBorder(World world, Village v, int shrink) {
        if (shrink <= 0) {
            return 0;
        }
        BlockPos c = v.getCenter();
        int outer = v.getVillageRadius() + 8;
        int inner = outer - shrink;
        int count = 0;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > outer * outer || d2 <= inner * inner) {
                    continue;
                }
                BlockPos col = new BlockPos(c.getX() + dx, 0, c.getZ() + dz);
                if (!world.isBlockLoaded(col)) {
                    continue;
                }
                BlockPos top = world.getHeight(col).down();
                if (Corruption.isCorrupted(world.getBlockState(top))) {
                    count++;
                }
            }
        }
        return count;
    }

    private VillageEvolution() {
    }
}
