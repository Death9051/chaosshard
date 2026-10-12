package ru.death.chaosshard.anchor;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.servant.Servants;

import java.util.Random;

/**
 * Скверна и здешняя тьма (EvilCraft). Хаос будит то тёмное, что в Лучезарии уже было:
 *  — стоячая вода на захваченной земле темнеет и становится кровью;
 *  — в осквернённом камне вызревает тёмная руда;
 *  — ночью на Скверне поднимаются духи мщения, и из них выбивается Эссенция Хаоса;
 *  — Кровавая печать Якоря: смерти тварей в краю питают Скверну.
 * Без EvilCraft работает только Кровавая печать.
 */
public class DarkCorruption {

    private static Block blood, darkOre;
    private static boolean looked;

    private static void lookup() {
        if (looked) {
            return;
        }
        looked = true;
        if (Loader.isModLoaded("evilcraft")) {
            blood = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("evilcraft", "block_blood"));
            darkOre = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("evilcraft", "dark_ore"));
            if (blood == Blocks.AIR) {
                blood = null;
            }
            if (darkOre == Blocks.AIR) {
                darkOre = null;
            }
        }
    }

    /** Источник воды на поверхности края — с шансом в кровь. */
    public static boolean waterToBlood(World world, BlockPos top, Random rand) {
        lookup();
        if (blood == null || rand.nextInt(6) != 0) {
            return false;
        }
        IBlockState s = world.getBlockState(top);
        if (s.getBlock() != Blocks.WATER || s.getValue(BlockLiquid.LEVEL) != 0) {
            return false;
        }
        world.setBlockState(top, blood.getDefaultState(), 3);
        ((WorldServer) world).spawnParticle(EnumParticleTypes.REDSTONE, top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5,
                12, 0.4, 0.2, 0.4, 0.0);
        return true;
    }

    /** Камень в толще края — изредка в тёмную руду. */
    public static boolean darkOre(World world, BlockPos pos, Random rand) {
        lookup();
        if (darkOre == null || rand.nextInt(40) != 0) {
            return false;
        }
        IBlockState s = world.getBlockState(pos);
        if (s.getBlock() != Blocks.STONE && s.getBlock() != ru.death.chaosshard.init.ModBlocks.CHAOS_STONE) {
            return false;
        }
        world.setBlockState(pos, darkOre.getDefaultState(), 2);
        return true;
    }

    private static boolean isSpirit(Entity e) {
        ResourceLocation k = EntityList.getKey(e);
        return k != null && "evilcraft".equals(k.getNamespace()) && k.getPath().contains("vengeance_spirit");
    }

    // ------------------------------------------------------------------ Кровавая печать

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase dead = event.getEntityLiving();
        if (dead.world.isRemote || dead instanceof EntityPlayer || Servants.isServant(dead)) {
            return;
        }
        TileAnchor a = TileAnchor.covering(dead.world, dead.getPosition());
        if (a == null) {
            return;
        }
        int seals = a.seals(ItemAnchorSeal.Type.BLOOD);
        if (seals <= 0) {
            return;
        }
        a.feed(3 * seals);
        ((WorldServer) dead.world).spawnParticle(EnumParticleTypes.REDSTONE, dead.posX, dead.posY + 0.5, dead.posZ,
                20, 0.3, 0.4, 0.3, 0.0);
    }

    // ------------------------------------------------------------------ духи мщения

    /** Дух мщения, убитый на земле Якоря, роняет Эссенцию Хаоса. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || !isSpirit(e) || !(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        if (TileAnchor.covering(e.world, e.getPosition()) == null) {
            return;
        }
        event.getDrops().add(new EntityItem(e.world, e.posX, e.posY, e.posZ,
                new ItemStack(ModItems.CHAOS_ESSENCE, 1 + e.world.rand.nextInt(2 + event.getLootingLevel()))));
    }

    /** Ночью на Скверне изредка поднимается дух мщения неподалёку от хозяина края. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 600 != 123) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        World w = p.world;
        lookup();
        if (blood == null || w.isDaytime() || w.getDifficulty() == net.minecraft.world.EnumDifficulty.PEACEFUL
                || w.rand.nextInt(3) != 0) {
            return;
        }
        TileAnchor a = TileAnchor.covering(w, p.getPosition());
        if (a == null || !a.isCaptured()) {
            return;
        }
        int near = w.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(32), DarkCorruption::isSpirit).size();
        if (near >= 2) {
            return;
        }
        Entity e = EntityList.createEntityByIDFromName(new ResourceLocation("evilcraft", "vengeance_spirit"), w);
        if (!(e instanceof EntityLiving)) {
            return;
        }
        double ang = w.rand.nextDouble() * Math.PI * 2;
        BlockPos at = w.getHeight(p.getPosition().add(Math.cos(ang) * 16, 0, Math.sin(ang) * 16));
        if (TileAnchor.covering(w, at) == null) {
            return;
        }
        e.setLocationAndAngles(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, w.rand.nextFloat() * 360, 0);
        ((EntityLiving) e).onInitialSpawn(w.getDifficultyForLocation(at), null);
        w.spawnEntity(e);
        ((WorldServer) w).spawnParticle(EnumParticleTypes.SPELL_WITCH, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.05);
    }
}
