package ru.death.chaosshard.anchor;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import ru.death.chaosshard.garrot.GarrotRaids;
import ru.death.chaosshard.servant.Servants;

import java.util.UUID;

/** Данные Дозора для окна: точки на карте сети и «взгляд» на землю Якоря. */
public final class AnchorView {

    public static final int SCRY = 96;

    /** Тип точки: 0 — враг, 1 — отряд, 2 — чужой игрок, 3 — свой (ты и слуги). */
    private static int type(EntityLivingBase e, UUID owner) {
        if (e.getTags().contains(GarrotRaids.TAG_ARMY)) {
            return 1;
        }
        if (e instanceof EntityPlayer) {
            return owner != null && owner.equals(e.getUniqueID()) ? 3 : e instanceof FakePlayer ? -1 : 2;
        }
        if (owner != null && Servants.isServantOf(e, owner)) {
            return 3;
        }
        return e instanceof IMob ? 0 : -1;
    }

    /** Отряды и чужие игроки на загруженной земле сети. */
    public static NBTTagList dots(WorldServer w, UUID owner) {
        NBTTagList out = new NBTTagList();
        for (BlockPos p : AnchorNet.members(w, owner)) {
            TileAnchor a = AnchorClusters.at(w, p);
            if (a == null) {
                continue;
            }
            for (EntityLivingBase e : w.getEntitiesWithinAABB(EntityLivingBase.class, AnchorSpecs.land(a),
                    x -> x.isEntityAlive() && AnchorSpecs.inLand(a, x))) {
                int t = type(e, owner);
                if (t != 1 && t != 2) {
                    continue;
                }
                NBTTagCompound d = new NBTTagCompound();
                d.setInteger("x", (int) e.posX);
                d.setInteger("z", (int) e.posZ);
                d.setByte("t", (byte) t);
                out.appendTag(d);
                if (out.tagCount() >= 200) {
                    return out;
                }
            }
        }
        return out;
    }

    /** Вид сверху на 96×96 блоков вокруг точки: цвета карты и существа. */
    public static NBTTagCompound scry(WorldServer w, BlockPos center, UUID owner) {
        NBTTagCompound t = new NBTTagCompound();
        int x0 = center.getX() - SCRY / 2, z0 = center.getZ() - SCRY / 2;
        byte[] c = new byte[SCRY * SCRY];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < SCRY; dz++) {
            for (int dx = 0; dx < SCRY; dx++) {
                int x = x0 + dx, z = z0 + dz;
                int y = w.getHeight(x, z) - 1;
                IBlockState s = w.getBlockState(m.setPos(x, Math.max(0, y), z));
                c[dz * SCRY + dx] = (byte) s.getMapColor(w, m).colorIndex;
            }
        }
        t.setByteArray("c", c);
        t.setInteger("x0", x0);
        t.setInteger("z0", z0);
        NBTTagList ents = new NBTTagList();
        for (EntityLivingBase e : w.getEntitiesWithinAABB(EntityLivingBase.class,
                new AxisAlignedBB(x0, 0, z0, x0 + SCRY, 256, z0 + SCRY), EntityLivingBase::isEntityAlive)) {
            int ty = type(e, owner);
            if (ty < 0) {
                continue;
            }
            NBTTagCompound d = new NBTTagCompound();
            d.setInteger("x", (int) e.posX - x0);
            d.setInteger("z", (int) e.posZ - z0);
            d.setByte("t", (byte) ty);
            ents.appendTag(d);
            if (ents.tagCount() >= 300) {
                break;
            }
        }
        t.setTag("e", ents);
        t.setLong("at", center.toLong());
        return t;
    }

    private AnchorView() {
    }
}
