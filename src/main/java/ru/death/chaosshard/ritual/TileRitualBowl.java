package ru.death.chaosshard.ritual;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.anchor.TileAnchor;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.quest.Milestones;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Чаша ритуала: подношения, проверка круга, идущий ритуал. */
public class TileRitualBowl extends TileEntity implements ITickable {

    public static final int SLOTS = 8;
    /** Знаки мела: по сторонам и диагоналям на расстоянии 2. */
    public static final int[][] RING = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int active = -1;
    private int ticksLeft;
    private int age;

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public Ritual getActive() {
        return Ritual.byIndex(active);
    }

    // ------------------------------------------------------------------ подношения

    public void insert(EntityPlayer player, ItemStack held, boolean all) {
        if (active >= 0) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.ritual.busy"), true);
            return;
        }
        int amount = all ? held.getCount() : 1;
        for (int i = 0; i < SLOTS && amount > 0; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemHandlerHelper.canItemStacksStack(s, held)) {
                int add = Math.min(amount, s.getMaxStackSize() - s.getCount());
                s.grow(add);
                amount -= add;
                if (!player.isCreative()) {
                    held.shrink(add);
                }
            }
        }
        for (int i = 0; i < SLOTS && amount > 0; i++) {
            if (items.get(i).isEmpty()) {
                ItemStack put = held.copy();
                put.setCount(amount);
                items.set(i, put);
                if (!player.isCreative()) {
                    held.shrink(amount);
                }
                amount = 0;
            }
        }
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.4F, 0.8F);
        changed();
    }

    public void takeLast(EntityPlayer player) {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, items.get(i));
                items.set(i, ItemStack.EMPTY);
                changed();
                return;
            }
        }
    }

    public void dropAll() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 0.5, pos.getZ(), s);
            }
        }
        items.clear();
    }

    // ------------------------------------------------------------------ запуск

    public int chalkCount() {
        int n = 0;
        for (int[] r : RING) {
            if (world.getBlockState(pos.add(r[0], 0, r[1])).getBlock() == ModBlocks.CHALK) {
                n++;
            }
        }
        return n;
    }

    public void tryStart(EntityPlayerMP player) {
        if (active >= 0) {
            say(player, Breach.grimoire("chaosshard.ritual.busy"));
            return;
        }
        int chalk = chalkCount();
        if (chalk < RING.length) {
            say(player, Breach.grimoire("chaosshard.ritual.no_circle", chalk, RING.length));
            return;
        }
        Ritual r = Ritual.find(items);
        if (r == null) {
            ru.death.chaosshard.servant.ServantUpgrade up = ru.death.chaosshard.servant.ServantUpgrade.find(items);
            if (up != null) {
                if (ru.death.chaosshard.servant.ServantUpgrade.perform(player, up, pos)) {
                    items.clear();
                    changed();
                }
                return;
            }
            say(player, Breach.grimoire("chaosshard.ritual.unknown"));
            return;
        }
        if (ThreadBonus.level(player) < r.minThread && !player.isCreative()) {
            say(player, Breach.grimoire("chaosshard.ritual.thin", r.minThread));
            return;
        }
        if (r.isSummon() && servantKind(r).unique()) {
            long left = ru.death.chaosshard.servant.Beasts.inThread(player, ru.death.chaosshard.servant.BeastData.Beast.of(servantKind(r)));
            if (left > 0) {
                say(player, Breach.grimoire("chaosshard.beast.in_thread", (left / 20 + 59) / 60));
                return;
            }
        }
        if (r.isSummon() && servantKind(r).unique() && ru.death.chaosshard.servant.Servants.hasUnique(player, servantKind(r))) {
            say(player, Breach.grimoire("chaosshard.servant.unique"));
            return;
        }
        if (r.isSummon() && !servantKind(r).unique() && ru.death.chaosshard.servant.Servants.count(player) >= ru.death.chaosshard.servant.Servants.limit(player)
                && !player.isCreative()) {
            say(player, Breach.grimoire("chaosshard.servant.limit", ru.death.chaosshard.servant.Servants.limit(player)));
            return;
        }
        String fail = precheck(r);
        if (fail != null) {
            say(player, Breach.grimoire(fail));
            return;
        }
        items.clear();
        WorldServer ws = (WorldServer) world;
        ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 1.5, 0.5, 1.5, 0.03);
        world.playSound(null, pos, SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, SoundCategory.BLOCKS, 1.0F, 0.7F);
        Milestones.grant(player, Milestones.RITUAL);
        say(player, Breach.grimoire("chaosshard.ritual.started", new net.minecraft.util.text.TextComponentTranslation(r.key())));
        if (r.duration <= 0) {
            instant(r, player);
        } else {
            active = r.ordinal();
            ticksLeft = r.duration;
        }
        changed();
    }

    private static void say(EntityPlayer player, ITextComponent msg) {
        player.sendMessage(msg);
    }

    /** Проверки до траты подношений. */
    private String precheck(Ritual r) {
        switch (r) {
            case SKY:
                return world.provider.hasSkyLight() && !world.provider.doesWaterVaporize()
                        && !ru.death.chaosshard.antechamber.AntechamberDimension.is(world) ? null : "chaosshard.ritual.no_sky";
            case CORRUPTION:
                return nearestAnchor() != null ? null : "chaosshard.ritual.no_anchor";
            default:
                return null;
        }
    }

    static ru.death.chaosshard.servant.Servants.Kind servantKind(Ritual r) {
        switch (r) {
            case SERVANT_BONE: return ru.death.chaosshard.servant.Servants.Kind.BONE;
            case SERVANT_EXECUTIONER: return ru.death.chaosshard.servant.Servants.Kind.EXECUTIONER;
            case SERVANT_HARE: return ru.death.chaosshard.servant.Servants.Kind.HARE;
            case SERVANT_CAT: return ru.death.chaosshard.servant.Servants.Kind.CAT;
            case SERVANT_TIGER: return ru.death.chaosshard.servant.Servants.Kind.TIGER;
            case SERVANT_REAPER: return ru.death.chaosshard.servant.Servants.Kind.REAPER;
            case SERVANT_LUMBERJACK: return ru.death.chaosshard.servant.Servants.Kind.LUMBERJACK;
            case SERVANT_MINER: return ru.death.chaosshard.servant.Servants.Kind.MINER;
            default: return ru.death.chaosshard.servant.Servants.Kind.HUSK;
        }
    }

    private TileAnchor nearestAnchor() {
        BlockPos near = AnchorData.get(world).nearest(pos, null);
        if (near == null || near.distanceSq(pos) > 48 * 48) {
            return null;
        }
        TileEntity te = world.getTileEntity(near);
        return te instanceof TileAnchor ? (TileAnchor) te : null;
    }

    private void instant(Ritual r, EntityPlayerMP player) {
        Random rand = world.rand;
        switch (r) {
            case SKY: {
                boolean rain = world.getWorldInfo().isRaining();
                world.getWorldInfo().setRaining(!rain);
                world.getWorldInfo().setRainTime(rain ? 12000 + rand.nextInt(12000) : 6000);
                if (rain) {
                    world.getWorldInfo().setThundering(false);
                }
                break;
            }
            case SUMMON: {
                String[] mobs = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:enderman",
                        "minecraft:witch", "minecraft:creeper", "minecraft:slime"};
                for (int i = 0; i < 3; i++) {
                    int[] at = RING[rand.nextInt(RING.length)];
                    Entity e = EntityList.createEntityByIDFromName(new ResourceLocation(mobs[rand.nextInt(mobs.length)]), world);
                    if (e instanceof EntityLiving) {
                        BlockPos p = pos.add(at[0], 0, at[1]);
                        e.setLocationAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, rand.nextFloat() * 360, 0);
                        ((EntityLiving) e).onInitialSpawn(world.getDifficultyForLocation(p), null);
                        world.spawnEntity(e);
                        ((WorldServer) world).spawnParticle(EnumParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5,
                                40, 0.3, 0.8, 0.3, 0.5);
                    }
                }
                break;
            }
            case SERVANT_HUSK:
            case SERVANT_BONE:
            case SERVANT_EXECUTIONER:
            case SERVANT_HARE:
            case SERVANT_CAT:
            case SERVANT_TIGER:
            case SERVANT_REAPER:
            case SERVANT_LUMBERJACK:
            case SERVANT_MINER: {
                ru.death.chaosshard.servant.Servants.Kind kind = servantKind(r);
                ru.death.chaosshard.servant.Servants.summon(player, kind, pos.add(1, 0, 1));
                say(player, Breach.grimoire("chaosshard.servant.summoned", ru.death.chaosshard.servant.Servants.count(player),
                        ru.death.chaosshard.servant.Servants.limit(player)));
                break;
            }
            case CORRUPTION: {
                TileAnchor a = nearestAnchor();
                if (a != null) {
                    int done = a.forceSpread(400);
                    say(player, Breach.grimoire("chaosshard.ritual.corruption.done", done));
                }
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ идущий ритуал

    @Override
    public void update() {
        age++;
        if (world.isRemote || active < 0) {
            return;
        }
        Ritual r = getActive();
        if (r == null || --ticksLeft <= 0 || chalkCount() < RING.length) {
            active = -1;
            ticksLeft = 0;
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6F, 0.8F);
            changed();
            return;
        }
        WorldServer ws = (WorldServer) world;
        if (age % 10 == 0) {
            int[] at = RING[(age / 10) % RING.length];
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + at[0] + 0.5, pos.getY() + 0.3, pos.getZ() + at[1] + 0.5,
                    4, 0.1, 0.2, 0.1, 0.0);
        }
        switch (r) {
            case GROWTH:
                if (age % 10 == 0) {
                    growSome(4);
                }
                break;
            case WARD:
                if (age % 20 == 0) {
                    RitualEvents.ward(world, pos, world.getTotalWorldTime() + 40);
                }
                break;
            case STORM:
                if (age % 40 == 0) {
                    strike();
                }
                break;
            default:
                break;
        }
    }

    private void growSome(int tries) {
        Random rand = world.rand;
        for (int i = 0; i < tries; i++) {
            BlockPos p = pos.add(rand.nextInt(17) - 8, rand.nextInt(5) - 2, rand.nextInt(17) - 8);
            IBlockState s = world.getBlockState(p);
            Block b = s.getBlock();
            if (b instanceof IGrowable && b != Blocks.GRASS && b != Blocks.TALLGRASS && b != Blocks.DOUBLE_PLANT) {
                IGrowable g = (IGrowable) b;
                if (g.canGrow(world, p, s, false) && g.canUseBonemeal(world, rand, p, s)) {
                    g.grow(world, rand, p, s);
                    ((WorldServer) world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, p.getX() + 0.5, p.getY() + 0.5,
                            p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
    }

    private void strike() {
        List<EntityLivingBase> mobs = world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(pos).grow(24),
                e -> e instanceof IMob && e.isEntityAlive());
        if (mobs.isEmpty()) {
            return;
        }
        EntityLivingBase t = mobs.get(world.rand.nextInt(mobs.size()));
        world.addWeatherEffect(new EntityLightningBolt(world, t.posX, t.posY, t.posZ, true));
        t.attackEntityFrom(DamageSource.LIGHTNING_BOLT, 10.0F);
        t.setFire(4);
    }

    // ------------------------------------------------------------------ сохранение и синхронизация

    private void changed() {
        markDirty();
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeShared(tag);
        return tag;
    }

    private void writeShared(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (ItemStack s : items) {
            list.appendTag(s.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("items", list);
        tag.setInteger("active", active);
        tag.setInteger("ticks", ticksLeft);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readShared(tag);
    }

    private void readShared(NBTTagCompound tag) {
        items.clear();
        NBTTagList list = tag.getTagList("items", 10);
        for (int i = 0; i < list.tagCount() && i < SLOTS; i++) {
            items.set(i, new ItemStack(list.getCompoundTagAt(i)));
        }
        active = tag.hasKey("active") ? tag.getInteger("active") : -1;
        ticksLeft = tag.getInteger("ticks");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        writeShared(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readShared(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readShared(pkt.getNbtCompound());
    }

    public int getAge() {
        return age;
    }

    @SuppressWarnings("unused")
    private static List<ItemStack> copy(List<ItemStack> in) {
        return new ArrayList<>(in);
    }
}
