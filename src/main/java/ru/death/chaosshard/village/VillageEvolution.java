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
        // Четыре ремесла Адептов — Обитель торгует почти всем, что нужно. Ремесло выпадает каждому жителю случайно.
        VillagerRegistry.VillagerCareer keeper = new VillagerRegistry.VillagerCareer(ADEPT, "chaos_keeper");
        keeper.addTrade(1, sell(ModItems.CHALK, 3, 5), sell(ModItems.VEIL_SHARD, 5, 8), buy(net.minecraft.init.Items.ROTTEN_FLESH, 18, 24));
        keeper.addTrade(2, sell(ModItems.CHAOS_ESSENCE, 6, 9), sell(ModItems.MEMORY_FRAGMENT, 9, 14), buy(net.minecraft.init.Items.BONE, 10, 14));
        keeper.addTrade(3, sell(net.minecraft.init.Items.ENDER_PEARL, 4, 7), bulk(Item.getItemFromBlock(ru.death.chaosshard.init.ModBlocks.CHAOS_STONE), 12, 16),
                bulk(Item.getItemFromBlock(ru.death.chaosshard.init.ModBlocks.CHAOS_CRUST), 8, 12));
        for (Item seal : ModItems.SEALS.values()) {
            keeper.addTrade(4, sell(seal, 24, 32));
        }

        VillagerRegistry.VillagerCareer smith = new VillagerRegistry.VillagerCareer(ADEPT, "chaos_smith");
        smith.addTrade(1, buy(net.minecraft.init.Items.COAL, 14, 18), bulk(net.minecraft.init.Items.IRON_INGOT, 2, 3),
                new OreForEmeralds("ingotCopper", 2, 4), new OreForEmeralds("ingotTin", 2, 4));
        smith.addTrade(2, sell(net.minecraft.init.Items.GOLD_INGOT, 1, 2), new OreForEmeralds("ingotLead", 2, 3),
                new OreForEmeralds("ingotSilver", 1, 2), enchanted(net.minecraft.init.Items.IRON_PICKAXE, 7, 10),
                enchanted(net.minecraft.init.Items.IRON_SWORD, 7, 10));
        smith.addTrade(3, sell(net.minecraft.init.Items.DIAMOND, 8, 12), new OreForEmeralds("ingotNickel", 1, 2),
                new OreForEmeralds("ingotOsmium", 2, 3), new OreForEmeralds("ingotAluminum", 2, 4),
                enchanted(net.minecraft.init.Items.IRON_CHESTPLATE, 10, 14));
        smith.addTrade(4, enchanted(net.minecraft.init.Items.DIAMOND_PICKAXE, 16, 22), enchanted(net.minecraft.init.Items.DIAMOND_SWORD, 14, 20),
                enchanted(net.minecraft.init.Items.DIAMOND_CHESTPLATE, 20, 28), sell(ModItems.DISTORTED_STARMETAL, 10, 14));

        VillagerRegistry.VillagerCareer scribe = new VillagerRegistry.VillagerCareer(ADEPT, "chaos_scribe");
        scribe.addTrade(1, buy(net.minecraft.init.Items.PAPER, 24, 30), sell(net.minecraft.init.Items.BOOK, 1, 2),
                bulk(Item.getItemFromBlock(net.minecraft.init.Blocks.GLASS), 4, 6), bulk(net.minecraft.init.Items.DYE, 3, 5));
        scribe.addTrade(2, new EntityVillager.ListEnchantedBookForEmeralds(), new OreForEmeralds("gemLapis", 4, 6),
                sell(net.minecraft.init.Items.EXPERIENCE_BOTTLE, 2, 4), sell(Item.getItemFromBlock(net.minecraft.init.Blocks.BOOKSHELF), 3, 5));
        scribe.addTrade(3, new EntityVillager.ListEnchantedBookForEmeralds(), sell(net.minecraft.init.Items.ENDER_EYE, 7, 10),
                sell(net.minecraft.init.Items.NAME_TAG, 12, 18), new OreForEmeralds("dustRedstone", 4, 8));
        scribe.addTrade(4, new EntityVillager.ListEnchantedBookForEmeralds(), new EntityVillager.ListEnchantedBookForEmeralds(),
                sell(ModItems.MEMORY_FRAGMENT, 8, 12), new OreForEmeralds("gemQuartz", 6, 10));

        VillagerRegistry.VillagerCareer grower = new VillagerRegistry.VillagerCareer(ADEPT, "chaos_grower");
        grower.addTrade(1, buy(net.minecraft.init.Items.WHEAT, 18, 22), buy(net.minecraft.init.Items.POTATO, 15, 19),
                bulk(net.minecraft.init.Items.BREAD, 3, 5), bulk(net.minecraft.init.Items.COOKED_BEEF, 3, 5));
        grower.addTrade(2, bulk(Item.getItemFromBlock(net.minecraft.init.Blocks.SAPLING), 3, 5), bulk(net.minecraft.init.Items.WHEAT_SEEDS, 8, 12),
                bulk(Item.getItemFromBlock(net.minecraft.init.Blocks.WOOL), 2, 4), bulk(net.minecraft.init.Items.LEATHER, 2, 4));
        grower.addTrade(3, bulk(net.minecraft.init.Items.STRING, 5, 8), sell(net.minecraft.init.Items.SLIME_BALL, 2, 4),
                bulk(net.minecraft.init.Items.COOKED_PORKCHOP, 4, 6), bulk(net.minecraft.init.Items.SUGAR, 6, 10));
        grower.addTrade(4, bulk(net.minecraft.init.Items.GOLDEN_CARROT, 2, 4), sell(net.minecraft.init.Items.CAKE, 1, 2),
                bulk(Item.getItemFromBlock(net.minecraft.init.Blocks.MYCELIUM), 2, 3), bulk(net.minecraft.init.Items.NETHER_WART, 4, 8));
    }

    /** Продаёт 1 предмет за изумруды (min..max). */
    private static EntityVillager.ITradeList sell(Item item, int min, int max) {
        return new EntityVillager.ListItemForEmeralds(item, new EntityVillager.PriceInfo(min, max));
    }

    /** Продаёт пачку (min..max штук) за 1 изумруд. */
    private static EntityVillager.ITradeList bulk(Item item, int min, int max) {
        return new EntityVillager.ListItemForEmeralds(item, new EntityVillager.PriceInfo(-max, -min));
    }

    /** Покупает min..max штук за 1 изумруд. */
    private static EntityVillager.ITradeList buy(Item item, int min, int max) {
        return new EntityVillager.EmeraldForItems(item, new EntityVillager.PriceInfo(min, max));
    }

    private static EntityVillager.ITradeList enchanted(Item item, int min, int max) {
        return new EntityVillager.ListEnchantedItemForEmeralds(item, new EntityVillager.PriceInfo(min, max));
    }

    /** Пачка предмета из оредикта за 1 изумруд; если такого металла в сборке нет — сделка не появляется. */
    private static final class OreForEmeralds implements EntityVillager.ITradeList {
        private final String ore;
        private final int min, max;

        OreForEmeralds(String ore, int min, int max) {
            this.ore = ore;
            this.min = min;
            this.max = max;
        }

        @Override
        public void addMerchantRecipe(net.minecraft.entity.IMerchant merchant, net.minecraft.village.MerchantRecipeList list, java.util.Random rand) {
            List<net.minecraft.item.ItemStack> ores = net.minecraftforge.oredict.OreDictionary.getOres(ore, false);
            if (ores.isEmpty()) {
                return;
            }
            net.minecraft.item.ItemStack out = ores.get(0).copy();
            if (out.getMetadata() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) {
                out.setItemDamage(0);
            }
            out.setCount(min + rand.nextInt(max - min + 1));
            list.add(new net.minecraft.village.MerchantRecipe(new net.minecraft.item.ItemStack(net.minecraft.init.Items.EMERALD), out));
        }
    }

    /** Вся деревня (радиус + 8) становится биомом Скверны; блоки и дома не трогаются. */
    public static void corruptBiome(World world, BlockPos sigil) {
        Village v = world.getVillageCollection().getNearestVillage(sigil, 32);
        BlockPos c = v != null ? v.getCenter() : sigil;
        int r = (v != null ? v.getVillageRadius() : 32) + 8;
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                BlockPos col = new BlockPos(c.getX() + dx, 64, c.getZ() + dz);
                if (world.isBlockLoaded(col) && Corruption.corruptBiome(world, col)) {
                    chunks.add(net.minecraft.util.math.ChunkPos.asLong(col.getX() >> 4, col.getZ() >> 4));
                }
            }
        }
        Corruption.resendChunks((WorldServer) world, chunks);
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
