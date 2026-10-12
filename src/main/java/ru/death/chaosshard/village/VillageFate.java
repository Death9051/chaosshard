package ru.death.chaosshard.village;

import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadManager;

import java.util.List;

/** Выбор для деревни: покровительство (Знак) или Скверна. */
public final class VillageFate {

    /** Скверна не трогает деревню под покровительством. */
    public static boolean protects(World world, int x, int z) {
        return protector(world, x, z) != null;
    }

    /**
     * Знак, который защищает точку (x, z), или null. Граница защиты — радиус деревни + 8,
     * со 2-го уровня Знака она сжимается на блок за уровень: Скверна подходит ближе.
     */
    public static BlockPos protector(World world, int x, int z) {
        VillageData data = VillageData.get(world);
        if (data.sigils.isEmpty()) {
            return null;
        }
        for (Village v : world.getVillageCollection().getVillageList()) {
            BlockPos sigil = data.sigilOf(v);
            if (sigil == null) {
                continue;
            }
            BlockPos c = v.getCenter();
            double dx = c.getX() - x, dz = c.getZ() - z;
            double r = v.getVillageRadius() + 8 - TilePatronSigil.shrink(levelOf(world, sigil));
            if (dx * dx + dz * dz <= r * r) {
                return sigil;
            }
        }
        return null;
    }

    public static int levelOf(World world, BlockPos sigil) {
        if (!world.isBlockLoaded(sigil)) {
            return 1;
        }
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(sigil);
        return te instanceof TilePatronSigil ? ((TilePatronSigil) te).level() : 1;
    }

    /** Скверна попыталась войти в деревню и отбита — Знак набирается опыта. */
    public static void repelled(World world, BlockPos sigil) {
        if (!world.isBlockLoaded(sigil)) {
            return;
        }
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(sigil);
        if (te instanceof TilePatronSigil) {
            ((TilePatronSigil) te).addXp(1);
        }
    }

    /** Якорь с радиусом r накрыл деревни — те, что без покровителя, падают. */
    public static void checkAnchor(World world, BlockPos anchor, int r, EntityPlayerMP owner) {
        VillageData data = VillageData.get(world);
        for (Village v : world.getVillageCollection().getVillageList()) {
            BlockPos c = v.getCenter();
            double dx = c.getX() - anchor.getX(), dz = c.getZ() - anchor.getZ();
            if (dx * dx + dz * dz > (double) r * r || data.isPatron(v) || data.isFallen(v)) {
                continue;
            }
            fall(world, v, owner);
            data.fallen.add(c.toImmutable());
            data.markDirty();
        }
    }

    private static void fall(World world, Village v, EntityPlayerMP owner) {
        int r = v.getVillageRadius();
        BlockPos c = v.getCenter();
        List<EntityVillager> villagers = world.getEntitiesWithinAABB(EntityVillager.class,
                new AxisAlignedBB(c.add(-r, -16, -r), c.add(r, 16, r)));
        for (EntityVillager villager : villagers) {
            EntityZombieVillager z = new EntityZombieVillager(world);
            z.copyLocationAndAnglesFrom(villager);
            z.setForgeProfession(villager.getProfessionForge());
            z.setChild(villager.isChild());
            z.enablePersistence();
            if (villager.hasCustomName()) {
                z.setCustomNameTag(villager.getCustomNameTag());
            }
            world.removeEntity(villager);
            world.spawnEntity(z);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, z.posX, z.posY + 1, z.posZ, 20, 0.3, 0.5, 0.3, 0.05);
        }
        world.playSound(null, c, net.minecraft.init.SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1.0F, 0.6F);
        if (owner != null) {
            ThreadManager.addPoints(owner, ChaosConfig.order.fallenVillageReward);
            owner.sendMessage(Breach.grimoire("chaosshard.village.fallen", c.getX(), c.getZ(), villagers.size(),
                    ChaosConfig.order.fallenVillageReward));
            Milestones.grant(owner, Milestones.VILLAGE_FALLEN);
        }
    }

    private VillageFate() {
    }
}
