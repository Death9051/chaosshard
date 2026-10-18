package ru.death.chaosshard.spell;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.death.chaosshard.Tags;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Действие заклинаний с ветками (замечание №27, docs/ЗАКЛИНАНИЯ.md).
 * У каждого — базовое действие и две ветки (A и B), превращающие его в разные заклинания; ступени 2–3 развивают ветку.
 * Мирные: Росток, Тропа Хаоса, Пожирание, Разлом, Звёздный взор, Зов Нити. Боевые: Рывок, Стрела Хаоса, Форма Хаоса.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class SpellEffects {

    static final String TAG_TRAIL = "chaosshard_trail_until";
    static final String TAG_FORM_UNTIL = "chaosshard_form_until";
    static final String TAG_FORM_KIND = "chaosshard_form_kind";
    static final String TAG_FORM_TIER = "chaosshard_form_tier";
    static final String TAG_DASH_RESET = "chaosshard_dash_reset";

    private SpellEffects() {
    }

    static boolean effect(EntityPlayerMP p, Spell s, BlockPos pos, EnumFacing side, EntityLivingBase target, Vec3d air) {
        switch (s) {
            case SPROUT:
                return pos != null && sprout(p, pos);
            case BRIDGE:
                return trail(p, pos, side);
            case SPRING:
                return pos != null && devour(p, pos);
            case RIFT:
                return pos != null && rift(p, pos, side);
            case STARSIGHT:
                return starsight(p);
            case RECALL:
                return recall(p, pos, side);
            case DASH:
                return dash(p, target);
            case BOLT:
                return bolt(p, target, pos, air);
            case FORM:
                return form(p);
            default:
                return false;
        }
    }

    /** Дальность взгляда заклинания (с учётом ветки). */
    static double range(EntityPlayer p, Spell s) {
        switch (s) {
            case BOLT:
                return SpellData.b(p, s, 2) ? 40 : 24;
            case DASH:
                return SpellData.a(p, s, 2) ? 20 : 16;
            case BRIDGE:
                return SpellData.b(p, s, 2) ? 40 : SpellData.b(p, s, 1) ? 24 : 8;
            case RECALL:
                return SpellData.a(p, s, 2) ? 64 : SpellData.a(p, s, 1) ? 32 : 8;
            default:
                return 8;
        }
    }

    // ================================================================== Росток

    /**
     * База: ускорение времени одного блока. A «Сад»: круг 3×3 (5×5), на 3-й ступени ещё и подрастает всё в круге.
     * B «Петля времени»: один блок, втрое сильнее; дольше; на 3-й — петля держится две минуты и половина энергии возвращается.
     */
    static boolean sprout(EntityPlayerMP p, BlockPos pos) {
        Spell s = Spell.SPROUT;
        double power = SpellMods.power(p, s);
        int ticks = 200, extra = 2;
        float chance = 0.1F;
        int r = 0;
        if (SpellData.a(p, s, 1)) {
            r = SpellData.a(p, s, 2) ? 2 : 1;
        } else if (SpellData.b(p, s, 1)) {
            ticks = SpellData.b(p, s, 3) ? 2400 : SpellData.b(p, s, 2) ? 1200 : 600;
            extra = 6;
            chance = 0.3F;
        }
        r += SpellMods.area(p, s);
        ticks = (int) (ticks * power);
        int n = 0;
        for (BlockPos q : BlockPos.getAllInBox(pos.add(-r, -1, -r), pos.add(r, 1, r))) {
            if (SpellAccel.add(p.world, q, ticks, extra, chance)) {
                n++;
            }
            if (SpellData.a(p, s, 3)) {
                IBlockState st = p.world.getBlockState(q);
                if (st.getBlock() instanceof IGrowable) {
                    IGrowable g = (IGrowable) st.getBlock();
                    if (g.canGrow(p.world, q, st, false)) {
                        g.grow(p.world, p.world.rand, q, st);
                        ((WorldServer) p.world).spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, q.getX() + 0.5, q.getY() + 0.6, q.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
                        n++;
                    }
                }
            }
        }
        if (n > 0 && SpellData.b(p, s, 3)) {
            ChaosEnergy.set(p, ChaosEnergy.get(p) + SpellCaster.cost(p, s) / 2.0);
        }
        return n > 0;
    }

    // ================================================================== Тропа Хаоса

    /**
     * База: дорожка застывшего Хаоса вперёд от ног на 8 блоков, держится 20 с.
     * A «Поступь»: Хаос застывает под каждым шагом (воздух, вода, лава) 15 с (30 с; на 3-й — минута и скорость), рассыпается позади.
     * B «Перекинуть»: до точки под взглядом сразу протягивается лента (до 24 блоков, 40 на 2-й), держится минуту (две; три и шириной 3 на 3-й).
     */
    static boolean trail(EntityPlayerMP p, BlockPos pos, EnumFacing side) {
        Spell s = Spell.BRIDGE;
        double power = SpellMods.power(p, s);
        World w = p.world;
        BlockPos feet = new BlockPos(p.posX, p.posY - 0.5, p.posZ);
        if (SpellData.a(p, s, 1)) {
            int sec = SpellData.a(p, s, 3) ? 60 : SpellData.a(p, s, 2) ? 30 : 15;
            p.getEntityData().setLong(TAG_TRAIL, w.getTotalWorldTime() + (long) (sec * 20 * power));
            if (SpellData.a(p, s, 3)) {
                p.addPotionEffect(new PotionEffect(MobEffects.SPEED, (int) (sec * 20 * power), 1, false, false));
            }
            ((WorldServer) w).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 0.2, p.posZ, 30, 0.4, 0.1, 0.4, 0.3);
            SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.trail.walk", (int) (sec * power)));
            return true;
        }
        List<BlockPos> path = new ArrayList<>();
        int life;
        if (SpellData.b(p, s, 1)) {
            if (pos == null) {
                SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.need_block"));
                return false;
            }
            BlockPos to = side == EnumFacing.UP ? pos : pos.offset(side).down();
            int dx = to.getX() - feet.getX(), dz = to.getZ() - feet.getZ();
            int horiz = Math.max(Math.abs(dx), Math.abs(dz));
            int dy = MathHelper.clamp(to.getY() - feet.getY(), -horiz, horiz);
            int steps = Math.max(1, horiz * 2);
            int width = SpellData.b(p, s, 3) ? 1 : 0;
            double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            double px = -dz / len, pz = dx / len;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                for (int k = -width; k <= width; k++) {
                    BlockPos q = new BlockPos(Math.floor(feet.getX() + 0.5 + dx * t + px * k), feet.getY() + Math.round(dy * t),
                            Math.floor(feet.getZ() + 0.5 + dz * t + pz * k));
                    if (!path.contains(q)) {
                        path.add(q);
                    }
                }
            }
            life = SpellData.b(p, s, 3) ? 3600 : SpellData.b(p, s, 2) ? 2400 : 1200;
        } else {
            EnumFacing dir = p.getHorizontalFacing();
            int len = 8 + 4 * SpellMods.area(p, s);
            for (int i = 1; i <= len; i++) {
                path.add(feet.offset(dir, i));
            }
            life = 400;
        }
        life = (int) (life * power);
        int placed = 0;
        for (BlockPos q : path) {
            if (p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY) && BlockChaosTrail.place(w, q, life)) {
                placed++;
            }
        }
        if (placed > 0) {
            w.playSound(null, p.getPosition(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 0.8F, 0.6F);
        }
        return placed > 0;
    }

    /** Поступь: под ногами застывает Хаос, пока действует заклинание. Присев — плиты не ложатся (можно спуститься). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote) {
            return;
        }
        EntityPlayer p = e.player;
        long until = p.getEntityData().getLong(TAG_TRAIL);
        if (until <= 0) {
            return;
        }
        if (p.world.getTotalWorldTime() > until) {
            p.getEntityData().removeTag(TAG_TRAIL);
            return;
        }
        if (p.isSneaking() || p.isInWater() || p.isInLava() || p.isRiding()) {
            return;
        }
        BlockPos under = new BlockPos(p.posX, p.posY - 0.5, p.posZ);
        IBlockState st = p.world.getBlockState(under);
        if (st.getBlock() == ru.death.chaosshard.init.ModBlocks.CHAOS_TRAIL || p.world.isAirBlock(under) || st.getMaterial().isLiquid()) {
            BlockChaosTrail.place(p.world, under, 40);
            if (!p.onGround && p.motionY < 0) {
                p.motionY = 0;
                p.fallDistance = 0;
                p.velocityChanged = true;
            }
        }
    }

    // ================================================================== Пожирание

    /**
     * База: Осколок поглощает куб 3×3×3 у блока под взглядом — камень, землю, жидкости; ничего не остаётся.
     * A «Переплавка»: съеденное идёт в пользу — энергия Хаоса и иногда Эссенция; куб 5×5×5; на 3-й руды выходят слитками.
     * B «Просеивание»: руды и самоцветы выпадают целыми, прочее исчезает; куб 5×5×5; на 3-й — 7×7×7.
     */
    static boolean devour(EntityPlayerMP p, BlockPos pos) {
        Spell s = Spell.SPRING;
        World w = p.world;
        double power = SpellMods.power(p, s);
        boolean smelt = SpellData.a(p, s, 1), sift = SpellData.b(p, s, 1);
        int r = 1 + (SpellData.a(p, s, 2) || SpellData.b(p, s, 2) ? 1 : 0) + (SpellData.b(p, s, 3) ? 1 : 0) + SpellMods.area(p, s);
        int solids = 0, total = 0, essence = 0;
        List<ItemStack> loot = new ArrayList<>();
        for (BlockPos q : BlockPos.getAllInBox(pos.add(-r, -r, -r), pos.add(r, r, r))) {
            IBlockState st = w.getBlockState(q);
            if (st.getBlock().isAir(st, w, q) || !p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY)) {
                continue;
            }
            if (st.getMaterial().isLiquid()) {
                w.setBlockState(q, Blocks.AIR.getDefaultState(), 2);
                total++;
                continue;
            }
            if (!SpellCaster.breakable(w, q)) {
                continue;
            }
            int tier = SpellCaster.oreTier(st);
            if (tier >= 0 && (sift || SpellData.a(p, s, 3))) {
                ItemStack ore = new ItemStack(net.minecraft.item.Item.getItemFromBlock(st.getBlock()), 1, st.getBlock().damageDropped(st));
                if (sift) {
                    loot.add(ore);
                } else {
                    ItemStack ingot = FurnaceRecipes.instance().getSmeltingResult(ore);
                    if (!ingot.isEmpty()) {
                        loot.add(ingot.copy());
                    }
                }
            }
            w.setBlockState(q, Blocks.AIR.getDefaultState(), 2);
            solids++;
            total++;
            if (smelt && w.rand.nextFloat() < 0.02F * power) {
                essence++;
            }
        }
        if (total == 0) {
            return false;
        }
        if (smelt) {
            ChaosEnergy.set(p, ChaosEnergy.get(p) + solids / 6.0 * power);
            if (essence > 0) {
                loot.add(new ItemStack(ru.death.chaosshard.init.ModItems.CHAOS_ESSENCE, essence));
            }
        }
        for (ItemStack it : loot) {
            ItemHandlerHelper.giveItemToPlayer(p, it);
        }
        WorldServer ws = (WorldServer) w;
        ws.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30 + 10 * r, r * 0.6, r * 0.6, r * 0.6, 0.6);
        ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, r * 0.5, r * 0.5, r * 0.5, 0.05);
        w.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.0F, 0.4F);
        w.playSound(null, pos, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.5F, 0.5F);
        return true;
    }

    // ================================================================== Разлом

    /**
     * База: дробит куб 3×3×3, добыча падает.
     * A «Тоннель»: тоннель 1×2 вглубь на 8 (3×3 на 12; на 3-й — 20, добыча в сумку, факелы через 6 блоков).
     * B «Карьер»: шахта вниз 3×3 на 6 (5×5 на 8; на 3-й — 12, добыча в сумку).
     */
    static boolean rift(EntityPlayerMP p, BlockPos pos, EnumFacing side) {
        Spell s = Spell.RIFT;
        World w = p.world;
        List<BlockPos> list = new ArrayList<>();
        List<BlockPos> torches = new ArrayList<>();
        boolean pull = SpellData.a(p, s, 3) || SpellData.b(p, s, 3);
        int area = SpellMods.area(p, s);
        if (SpellData.a(p, s, 1)) {
            EnumFacing into = side.getOpposite();
            if (into.getAxis() == EnumFacing.Axis.Y) {
                into = p.getHorizontalFacing();
            }
            boolean wide = SpellData.a(p, s, 2);
            int len = SpellData.a(p, s, 3) ? 20 : wide ? 12 : 8;
            EnumFacing across = into.rotateY();
            BlockPos start = pos;
            for (int i = 0; i < len; i++) {
                BlockPos c = start.offset(into, i);
                int half = (wide ? 1 : 0) + area;
                for (int k = -half; k <= half; k++) {
                    for (int h = wide ? -1 : 0; h <= (wide ? 1 : 1); h++) {
                        if (!wide && h < 0) {
                            continue;
                        }
                        list.add(c.offset(across, k).up(wide ? h : -h));
                    }
                }
                if (SpellData.a(p, s, 3) && i % 6 == 3) {
                    torches.add(c.offset(across, wide ? 1 : 0).down(1));
                }
            }
        } else if (SpellData.b(p, s, 1)) {
            int half = (SpellData.b(p, s, 2) ? 2 : 1) + area;
            int depth = SpellData.b(p, s, 3) ? 12 : SpellData.b(p, s, 2) ? 8 : 6;
            for (int d = 0; d < depth; d++) {
                for (int dx = -half; dx <= half; dx++) {
                    for (int dz = -half; dz <= half; dz++) {
                        list.add(pos.add(dx, -d, dz));
                    }
                }
            }
        } else {
            int r = 1 + area;
            for (BlockPos q : BlockPos.getAllInBox(pos.add(-r, -r, -r), pos.add(r, r, r))) {
                list.add(q.toImmutable());
            }
        }
        int n = 0;
        for (BlockPos q : list) {
            if (!SpellCaster.breakable(w, q) || !p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY)) {
                continue;
            }
            if (pull) {
                IBlockState st = w.getBlockState(q);
                NonNullList<ItemStack> drops = NonNullList.create();
                st.getBlock().getDrops(drops, w, q, st, 0);
                w.playEvent(2001, q, Block.getStateId(st));
                w.setBlockToAir(q);
                for (ItemStack d : drops) {
                    ItemHandlerHelper.giveItemToPlayer(p, d);
                }
            } else {
                w.destroyBlock(q, true);
            }
            n++;
        }
        for (BlockPos at : torches) {
            if (w.isAirBlock(at) && Blocks.TORCH.canPlaceBlockAt(w, at)) {
                w.setBlockState(at, Blocks.TORCH.getDefaultState());
            }
        }
        return n > 0;
    }

    // ================================================================== Звёздный взор

    /**
     * База: ночное зрение на 3 минуты. A «Охотник»: ещё и твари в 24 блоках (48) светятся сквозь стены; на 3-й — дольше и все живые.
     * B «Рудознатец»: ещё и руды в 8 блоках (16) видны сквозь камень; на 3-й — по ценности, с сундуками и спавнерами.
     */
    static boolean starsight(EntityPlayerMP p) {
        Spell s = Spell.STARSIGHT;
        double power = SpellMods.power(p, s);
        int area = SpellMods.area(p, s);
        p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, (int) (20 * 180 * power), 0, true, false));
        if (SpellData.a(p, s, 1)) {
            double r = (SpellData.a(p, s, 2) ? 48 : 24) + 8 * area;
            boolean all = SpellData.a(p, s, 3);
            int n = 0;
            for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, p.getEntityBoundingBox().grow(r),
                    x -> x != p && x.isEntityAlive() && (all || x instanceof IMob))) {
                e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, (int) ((all ? 1200 : 600) * power), 0, false, false));
                n++;
            }
            SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.starsight.hunt", n));
        } else if (SpellData.b(p, s, 1)) {
            int r = (SpellData.b(p, s, 2) ? 16 : 8) + 4 * area;
            boolean sort = SpellData.b(p, s, 3);
            List<Long> pos = new ArrayList<>();
            List<Byte> tier = new ArrayList<>();
            BlockPos c = p.getPosition();
            for (BlockPos q : BlockPos.getAllInBoxMutable(c.add(-r, -r, -r), c.add(r, r, r))) {
                if (pos.size() >= 2000) {
                    break;
                }
                IBlockState st = p.world.getBlockState(q);
                if (st.getBlock() == Blocks.AIR || st.getBlock() == Blocks.STONE) {
                    continue;
                }
                int t = SpellCaster.oreTier(st);
                if (t < 0 && sort) {
                    TileEntity te = p.world.getTileEntity(q);
                    if (te instanceof net.minecraft.tileentity.TileEntityLockableLoot || te instanceof net.minecraft.tileentity.TileEntityMobSpawner) {
                        t = 3;
                    }
                }
                if (t < 0) {
                    continue;
                }
                pos.add(q.toLong());
                tier.add((byte) (sort ? t : 4));
            }
            long[] a = new long[pos.size()];
            byte[] b = new byte[pos.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = pos.get(i);
                b[i] = tier.get(i);
            }
            ru.death.chaosshard.network.ChaosNetwork.CHANNEL.sendTo(new ru.death.chaosshard.network.PacketOreSight(a, b, (int) (200 * power)), p);
            SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.starsight.ores", a.length));
        }
        return true;
    }

    // ================================================================== Зов Нити

    /** Метка ветки B: [измерение, x, y, z] или null. */
    static int[] mark(EntityPlayer p) {
        NBTTagCompound r = SpellData.root(p);
        return r.hasKey("_mark") ? r.getIntArray("_mark") : null;
    }

    /**
     * База: места по кругу — домой, к Цитадели, к ближайшему своему Якорю.
     * A «Шаг Нити»: заклинание — мгновенный шаг на блок под взглядом (до 32, 64 на 2-й; на 3-й почти без перезарядки);
     *   присев — прежний Зов. B «Метка»: присев — поставить метку где угодно, место «метка» — вернуться к ней;
     *   на 2-й перезарядка вдвое короче, на 3-й — цена вдвое меньше.
     */
    static boolean recall(EntityPlayerMP p, BlockPos pos, EnumFacing side) {
        Spell s = Spell.RECALL;
        if (SpellData.a(p, s, 1) && !p.isSneaking()) {
            if (pos == null) {
                SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.need_block"));
                return false;
            }
            BlockPos at = pos.offset(side);
            if (side != EnumFacing.UP && p.world.isAirBlock(at.down())) {
                at = at.down();
            }
            if (!p.world.isAirBlock(at) || !p.world.isAirBlock(at.up())) {
                return false;
            }
            WorldServer ws = (WorldServer) p.world;
            ws.spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 40, 0.3, 0.6, 0.3, 0.6);
            p.setPositionAndUpdate(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            p.fallDistance = 0;
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.6, 0.3, 0.1);
            p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.7F, 1.2F);
            return true;
        }
        if (SpellData.b(p, s, 1) && p.isSneaking()) {
            NBTTagCompound r = SpellData.root(p);
            r.setIntArray("_mark", new int[]{p.dimension, (int) Math.floor(p.posX), (int) Math.floor(p.posY), (int) Math.floor(p.posZ)});
            SpellData.sync(p);
            ((WorldServer) p.world).spawnParticle(EnumParticleTypes.END_ROD, p.posX, p.posY + 0.2, p.posZ, 20, 0.3, 0.1, 0.3, 0.02);
            SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.recall.mark_set"));
            return true;
        }
        int form = SpellData.form(p, s);
        if (form >= 1) {
            if (ru.death.chaosshard.trial.TrialDimension.is(p.world) || ru.death.chaosshard.act5.RiftDimension.is(p.world)) {
                SpellCaster.status(p, Breach.grimoire("chaosshard.citadel.blocked"));
                return false;
            }
        }
        if (form == 3) {
            int[] m = mark(p);
            if (m == null || m.length != 4) {
                SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.recall.no_mark"));
                return false;
            }
            ChaosTeleporter.toPos(p, m[0], new BlockPos(m[1], m[2], m[3]));
            return true;
        }
        if (form == 1) {
            Object[] c = ru.death.chaosshard.lair.LairData.get(p.world).citadelOf(p.getUniqueID());
            if (c == null) {
                SpellCaster.status(p, Breach.grimoire("chaosshard.citadel.none"));
                return false;
            }
            ChaosTeleporter.toPos(p, (Integer) c[0], ((BlockPos) c[1]).up());
            return true;
        }
        if (form == 2) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            ru.death.chaosshard.anchor.AnchorData ad = ru.death.chaosshard.anchor.AnchorData.get(p.world);
            for (BlockPos a : ad.anchors) {
                ru.death.chaosshard.anchor.AnchorData.Rec rec = ad.rec(a);
                boolean mine;
                if (rec != null && rec.owner != null) {
                    mine = p.getUniqueID().equals(rec.owner);
                } else {
                    TileEntity te = p.world.getTileEntity(a);
                    mine = te instanceof ru.death.chaosshard.anchor.TileAnchor
                            && p.getUniqueID().equals(((ru.death.chaosshard.anchor.TileAnchor) te).getOwner());
                }
                if (mine) {
                    double d = a.distanceSq(p.getPosition());
                    if (d < bd) {
                        bd = d;
                        best = a;
                    }
                }
            }
            if (best == null) {
                SpellCaster.status(p, Breach.grimoire("chaosshard.spell.recall.no_anchor"));
                return false;
            }
            BlockPos to = p.world.getTopSolidOrLiquidBlock(best.add(2, 0, 0));
            p.setPositionAndUpdate(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
            return true;
        }
        if (AntechamberDimension.is(p.world) && !Breach.hasBreached(p)) {
            SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.recall.sealed"));
            return false;
        }
        Breach.travel(p);
        return true;
    }

    // ================================================================== Рывок (боевое)

    /**
     * База: бросок к врагу под взглядом и удар оружием из руки.
     * A «Натиск»: проносишься сквозь цель, задевая всех на пути (дальше и с отбросом на 2-й; на 3-й — Сила после натиска).
     * B «Тень»: выходишь за спину цели, удар сильнее и невидимость на 3 с (на 2-й — убил: рывок сразу снова готов;
     *   на 3-й — цель ослеплена и замедлена).
     */
    static boolean dash(EntityPlayerMP p, EntityLivingBase t) {
        Spell s = Spell.DASH;
        if (t == null) {
            SpellCaster.status(p, new TextComponentTranslation("chaosshard.spell.dash.no_target"));
            return false;
        }
        double power = SpellMods.power(p, s);
        WorldServer ws = (WorldServer) p.world;
        Vec3d dir = new Vec3d(t.posX - p.posX, 0, t.posZ - p.posZ);
        double len = dir.length();
        if (len > 0.01) {
            dir = dir.scale(1 / len);
        }
        double weapon = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        Vec3d from = p.getPositionVector();
        ws.spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.6, 0.3, 0.6);
        if (SpellData.a(p, s, 1)) {
            // натиск: сквозь цель
            Vec3d land = new Vec3d(t.posX + dir.x * 1.5, t.posY, t.posZ + dir.z * 1.5);
            BlockPos lp = new BlockPos(land);
            if (!p.world.isAirBlock(lp) || !p.world.isAirBlock(lp.up())) {
                land = new Vec3d(t.posX - dir.x * 1.2, t.posY, t.posZ - dir.z * 1.2);
            }
            p.setPositionAndUpdate(land.x, land.y, land.z);
            p.attackTargetEntityWithCurrentItem(t);
            float kb = SpellData.a(p, s, 2) ? 1.5F : 0.6F;
            for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(from.x, from.y, from.z, land.x, land.y + 2, land.z).grow(1.5),
                    x -> x != t && SpellCaster.hostileTo(p, x))) {
                e.hurtResistantTime = 0;
                e.attackEntityFrom(DamageSource.causePlayerDamage(p), (float) (weapon * 0.8 * power));
                e.knockBack(p, kb, -dir.x, -dir.z);
            }
            t.knockBack(p, kb, -dir.x, -dir.z);
            if (SpellData.a(p, s, 3)) {
                p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 120, 0, false, true));
            }
        } else if (SpellData.b(p, s, 1)) {
            // тень: за спину
            double x = t.posX + dir.x * 1.2, z = t.posZ + dir.z * 1.2;
            BlockPos at = new BlockPos(x, t.posY, z);
            if (!p.world.isAirBlock(at) || !p.world.isAirBlock(at.up())) {
                x = t.posX - dir.x * 1.2;
                z = t.posZ - dir.z * 1.2;
            }
            p.setPositionAndUpdate(x, t.posY, z);
            p.rotationYaw = (float) (MathHelper.atan2(-dir.z, -dir.x) * 180 / Math.PI) - 90F;
            p.attackTargetEntityWithCurrentItem(t);
            if (t.isEntityAlive()) {
                t.hurtResistantTime = 0;
                t.attackEntityFrom(DamageSource.causePlayerDamage(p), (float) (weapon * 0.5 * power));
            }
            p.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 60, 0, false, false));
            if (SpellData.b(p, s, 3) && t.isEntityAlive()) {
                t.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0));
                t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 2));
            }
            if (SpellData.b(p, s, 2) && !t.isEntityAlive()) {
                p.getEntityData().setBoolean(TAG_DASH_RESET, true);
            }
        } else {
            double x = t.posX - dir.x * 1.2, z = t.posZ - dir.z * 1.2;
            p.setPositionAndUpdate(x, t.posY, z);
            p.rotationYaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90F;
            p.attackTargetEntityWithCurrentItem(t);
        }
        p.fallDistance = 0;
        ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, t.posX, t.posY + t.height / 2, t.posZ, 1, 0.0, 0.0, 0.0, 0.0);
        p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.9F);
        return true;
    }

    // ================================================================== Стрела Хаоса (боевое)

    /**
     * База: удар по первой цели; по блоку — взрыв без разрушений.
     * A «Цепь»: перескакивает на 3 цели (5 на 2-й; на 3-й урон не слабеет).
     * B «Копьё»: пронзает всех на линии (дальше и сильнее на 2-й; на 3-й — поджигает и иссушает).
     */
    static boolean bolt(EntityPlayerMP p, EntityLivingBase target, BlockPos pos, Vec3d air) {
        Spell s = Spell.BOLT;
        WorldServer ws = (WorldServer) p.world;
        Vec3d start = p.getPositionEyes(1.0F);
        float dmg = SpellCaster.boltDamage(p);
        p.world.playSound(null, p.getPosition(), SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 0.8F, 1.6F);
        if (SpellData.b(p, s, 1)) {
            double r = range(p, s);
            Vec3d look = p.getLookVec();
            Vec3d end = start.add(look.x * r, look.y * r, look.z * r);
            RayTraceResult wall = p.world.rayTraceBlocks(start, end, false, true, false);
            if (wall != null && wall.typeOfHit == RayTraceResult.Type.BLOCK) {
                end = wall.hitVec;
            }
            SpellCaster.beam(ws, start, end);
            if (SpellData.b(p, s, 2)) {
                dmg *= 1.2F;
            }
            int n = 0;
            for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(start.x, start.y, start.z, end.x, end.y, end.z).grow(1),
                    x -> x != p && x.isEntityAlive() && !Servants.isServantOf(x, p.getUniqueID()) && !(x instanceof EntityPlayer))) {
                if (e.getEntityBoundingBox().grow(0.3).calculateIntercept(start, end) == null) {
                    continue;
                }
                SpellCaster.hit(p, e, dmg);
                if (SpellData.b(p, s, 3)) {
                    e.setFire(5);
                    e.addPotionEffect(new PotionEffect(MobEffects.WITHER, 80, 0));
                }
                n++;
            }
            return true;
        }
        Vec3d stop = target != null ? target.getPositionVector().add(0, target.height / 2, 0)
                : pos != null ? new Vec3d(pos).add(0.5, 0.5, 0.5) : air;
        SpellCaster.beam(ws, start, stop);
        if (target == null) {
            if (pos != null) {
                SpellCaster.blast(p, stop, dmg);
            }
            return true;
        }
        SpellCaster.hit(p, target, dmg);
        if (SpellData.a(p, s, 1)) {
            int targets = SpellData.a(p, s, 2) ? 5 : 3;
            boolean keep = SpellData.a(p, s, 3);
            Set<EntityLivingBase> done = new HashSet<>();
            done.add(target);
            EntityLivingBase last = target;
            float d = dmg;
            for (int i = 1; i < targets; i++) {
                final EntityLivingBase from = last;
                EntityLivingBase next = null;
                double best = 36;
                for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, from.getEntityBoundingBox().grow(6),
                        x -> SpellCaster.hostileTo(p, x) && !done.contains(x))) {
                    double dd = e.getDistanceSq(from);
                    if (dd < best) {
                        best = dd;
                        next = e;
                    }
                }
                if (next == null) {
                    break;
                }
                if (!keep) {
                    d *= 0.75F;
                }
                SpellCaster.beam(ws, from.getPositionVector().add(0, from.height / 2, 0), next.getPositionVector().add(0, next.height / 2, 0));
                SpellCaster.hit(p, next, d);
                done.add(next);
                last = next;
            }
        }
        return true;
    }

    // ================================================================== Форма Хаоса (боевое)

    /**
     * База: 30 с силы, скорости и прыгучести.
     * A «Берсерк»: сильнее и быстрее бьёшь (45 с на 2-й; на 3-й удары лечат на пятую часть урона).
     * B «Монолит»: стойкость и запас здоровья, не отбрасывает (45 с на 2-й; на 3-й треть урона возвращается обидчику).
     */
    static boolean form(EntityPlayerMP p) {
        Spell s = Spell.FORM;
        int kind = SpellData.branch(p, s);
        int tier = SpellData.tier(p, s);
        int sec = tier >= 2 ? 45 : 30;
        int t = (int) (20 * sec * SpellMods.power(p, s));
        int base = ThreadBonus.level(p) >= 4 ? 1 : 0;
        if (kind == 1) {
            p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, t, base + 1, false, true));
            p.addPotionEffect(new PotionEffect(MobEffects.HASTE, t, 1, false, true));
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, t, 1, false, true));
        } else if (kind == 2) {
            p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, t, 1, false, true));
            p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, t, 1, false, true));
            p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, t, base, false, true));
        } else {
            p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, t, base, false, true));
            p.addPotionEffect(new PotionEffect(MobEffects.SPEED, t, 0, false, true));
            p.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, t, 0, false, true));
        }
        NBTTagCompound d = p.getEntityData();
        d.setLong(TAG_FORM_UNTIL, p.world.getTotalWorldTime() + t);
        d.setInteger(TAG_FORM_KIND, kind);
        d.setInteger(TAG_FORM_TIER, tier);
        ((WorldServer) p.world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, p.posX, p.posY + 1, p.posZ, 80, 0.5, 1.0, 0.5, 0.05);
        p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    /** Какая форма сейчас действует: 0 — нет/базовая, 1 — Берсерк, 2 — Монолит; tier — ступень. */
    private static int formKind(EntityPlayer p, int minTier) {
        NBTTagCompound d = p.getEntityData();
        if (d.getLong(TAG_FORM_UNTIL) < p.world.getTotalWorldTime() || d.getInteger(TAG_FORM_TIER) < minTier) {
            return 0;
        }
        return d.getInteger(TAG_FORM_KIND);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity().world.isRemote) {
            return;
        }
        // Берсерк 3: удары лечат
        if (e.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer a = (EntityPlayer) e.getSource().getTrueSource();
            if (a != e.getEntityLiving() && formKind(a, 3) == 1) {
                a.heal(e.getAmount() * 0.2F);
            }
        }
        // Монолит 3: треть урона — обидчику
        if (e.getEntityLiving() instanceof EntityPlayer && e.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityPlayer v = (EntityPlayer) e.getEntityLiving();
            EntityLivingBase src = (EntityLivingBase) e.getSource().getTrueSource();
            if (src != v && formKind(v, 3) == 2 && !"thorns".equals(e.getSource().damageType)) {
                src.attackEntityFrom(DamageSource.causeThornsDamage(v), e.getAmount() * 0.33F);
            }
        }
    }

    /** Монолит: не отбрасывает. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent e) {
        if (e.getEntityLiving() instanceof EntityPlayer && formKind((EntityPlayer) e.getEntityLiving(), 1) == 2) {
            e.setCanceled(true);
        }
    }
}
