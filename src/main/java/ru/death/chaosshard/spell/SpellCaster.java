package ru.death.chaosshard.spell;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.init.ModItems;
import ru.death.chaosshard.servant.Servants;
import ru.death.chaosshard.thread.ThreadBonus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Серверная логика заклинаний Гримуара.
 *
 * Режимы Гримуара: Книга (ПКМ — книга квестов), Магия (ПКМ — заклинание), Привязка (ПКМ — только указания слугам и сундукам).
 * С Гримуаром в любой руке заклинание читается клавишей каста в любом режиме.
 * Заклинание само смотрит, что под взглядом: существо, блок или пустота — и у каждого свой эффект.
 * Формы и перки — {@link SpellPerks}, прокачка игрока — {@link SpellData}, внешние модификаторы — {@link SpellMods}.
 */
public final class SpellCaster {

    private static final String TAG_SPELL = "spell";
    private static final String TAG_MODE = "mode";
    private static final String TAG_COOLDOWNS = "chaosshard_spell_cd";
    private static final String TAG_DASH_USED = "chaosshard_dash_used";

    public static final int MODE_BOOK = 0, MODE_MAGIC = 1, MODE_BIND = 2;
    private static final String[] MODE_KEYS = {"notes", "magic", "bind"};

    // ------------------------------------------------------------------ режимы

    public static int mode(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return MODE_BOOK;
        }
        int m = stack.getTagCompound().getInteger(TAG_MODE);
        return m < 0 || m > 2 ? MODE_BOOK : m;
    }

    public static boolean magicMode(ItemStack stack) {
        return mode(stack) == MODE_MAGIC;
    }

    public static boolean bindMode(ItemStack stack) {
        return mode(stack) == MODE_BIND;
    }

    /** Гримуар в основной руке в режиме Привязки. */
    public static boolean binding(EntityPlayer p) {
        ItemStack s = p.getHeldItemMainhand();
        return s.getItem() == ModItems.GRIMOIRE && bindMode(s);
    }

    public static String modeKey(ItemStack stack) {
        return MODE_KEYS[mode(stack)];
    }

    public static void setMode(EntityPlayerMP player, ItemStack stack, int m) {
        tag(stack).setInteger(TAG_MODE, m);
        if (m == MODE_MAGIC && selected(stack) == Spell.BOOK) {
            tag(stack).setInteger(TAG_SPELL, Spell.SPROUT.ordinal());
        }
        TextComponentTranslation msg = new TextComponentTranslation("chaosshard.grimoire.mode." + MODE_KEYS[m]);
        msg.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        status(player, msg);
        player.world.playSound(null, player.getPosition(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F,
                0.7F + 0.2F * m);
    }

    /** Книга → Магия → Привязка → Книга. */
    public static void toggleMode(EntityPlayerMP player, ItemStack stack) {
        setMode(player, stack, (mode(stack) + 1) % 3);
    }

    // ------------------------------------------------------------------ выбор

    public static Spell selected(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? Spell.BOOK : Spell.byIndex(tag.getInteger(TAG_SPELL));
    }

    /** Выбрать заклинание (из колеса или окна Гримуара). Если Гримуар в режиме Книги — переходит в Магию. */
    public static void select(EntityPlayerMP player, ItemStack stack, Spell spell) {
        if (spell == Spell.BOOK) {
            return;
        }
        if (spell.minThread > ThreadBonus.level(player) && !player.isCreative()) {
            status(player, new TextComponentTranslation("chaosshard.spell.locked", spell.minThread));
            return;
        }
        tag(stack).setInteger(TAG_SPELL, spell.ordinal());
        if (mode(stack) == MODE_BOOK) {
            tag(stack).setInteger(TAG_MODE, MODE_MAGIC);
        }
        TextComponentTranslation name = new TextComponentTranslation(spell.key());
        name.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        status(player, new TextComponentTranslation("chaosshard.spell.selected", name));
    }

    private static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    // ------------------------------------------------------------------ каст

    /** Клавиша каста: Гримуар в любой руке, любой режим. */
    public static void castKey(EntityPlayerMP p) {
        ItemStack g = p.getHeldItemMainhand().getItem() == ModItems.GRIMOIRE ? p.getHeldItemMainhand()
                : p.getHeldItemOffhand().getItem() == ModItems.GRIMOIRE ? p.getHeldItemOffhand() : ItemStack.EMPTY;
        if (g.isEmpty()) {
            status(p, new TextComponentTranslation("chaosshard.grimoire.need_hand"));
            return;
        }
        Spell s = selected(g);
        if (s == Spell.BOOK) {
            status(p, new TextComponentTranslation("chaosshard.spell.none"));
            return;
        }
        cast(p, s);
    }

    /** Дальность взгляда заклинания. */
    public static double range(EntityPlayer p, Spell s) {
        switch (s) {
            case BOLT:
                return 24;
            case DASH:
                return SpellData.form(p, s) == 1 ? 12 + 4 * SpellData.rank(p, s, "range") : 16;
            default:
                return 8;
        }
    }

    /** Прочитать заклинание: найти, что под взглядом, и применить. */
    public static void cast(EntityPlayerMP p, Spell s) {
        double r = range(p, s);
        Vec3d eye = p.getPositionEyes(1.0F);
        Vec3d look = p.getLookVec();
        Vec3d end = eye.add(look.x * r, look.y * r, look.z * r);
        RayTraceResult block = p.world.rayTraceBlocks(eye, end, false, true, false);
        double blockDist = block != null && block.typeOfHit == RayTraceResult.Type.BLOCK ? eye.distanceTo(block.hitVec) : Double.MAX_VALUE;
        EntityLivingBase target = null;
        if (s.onEntity) {
            Vec3d stop = block != null ? block.hitVec : end;
            double best = blockDist;
            for (Entity e : p.world.getEntitiesWithinAABBExcludingEntity(p,
                    p.getEntityBoundingBox().expand(look.x * r, look.y * r, look.z * r).grow(1))) {
                if (!(e instanceof EntityLivingBase) || !e.canBeCollidedWith() || !e.isEntityAlive()) {
                    continue;
                }
                RayTraceResult hit = e.getEntityBoundingBox().grow(0.3).calculateIntercept(eye, stop);
                if (hit != null) {
                    double d = eye.distanceTo(hit.hitVec);
                    if (d < best) {
                        best = d;
                        target = (EntityLivingBase) e;
                    }
                }
            }
        }
        BlockPos pos = blockDist < Double.MAX_VALUE ? block.getBlockPos() : null;
        EnumFacing side = pos != null ? block.sideHit : EnumFacing.UP;
        castAt(p, s, pos, side, target, end);
    }

    /**
     * Применить заклинание к цели (или в пустоту). Для автотеста — с явной целью.
     * @param pos    блок под взглядом или null
     * @param target существо под взглядом или null
     * @param air    точка в пустоте, куда смотрели (для Стрелы без цели)
     */
    public static boolean castAt(EntityPlayerMP p, Spell s, BlockPos pos, EnumFacing side, EntityLivingBase target, Vec3d air) {
        if (s.onBlock && !s.onEntity && pos == null) {
            status(p, new TextComponentTranslation("chaosshard.spell.need_block"));
            return false;
        }
        if (!canCast(p, s)) {
            return false;
        }
        int cost = cost(p, s);
        boolean ok = effect(p, s, pos, side, target, air);
        if (ok && p.world.rand.nextDouble() < SpellMods.twice(p, s)) {
            effect(p, s, pos, side, target, air);
            ((WorldServer) p.world).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 30, 0.4, 0.6, 0.4, 0.5);
        }
        finish(p, s, ok, pos != null ? pos : p.getPosition(), cost);
        return ok;
    }

    private static boolean effect(EntityPlayerMP p, Spell s, BlockPos pos, EnumFacing side, EntityLivingBase target, Vec3d air) {
        World world = p.world;
        int form = SpellData.form(p, s);
        switch (s) {
            case SPROUT:
                return sprout(p, pos, form);
            case BRIDGE:
                return bridge(p, pos, form);
            case SPRING:
                return target != null ? waterStrike(p, target) : spring(p, pos, side, form);
            case RIFT:
                return rift(p, pos, side, form);
            case STARSIGHT:
                return starsight(p, form);
            case DASH:
                return target != null ? dashTo(p, target) : form == 1 ? leap(p, pos, side) : dash(p);
            case BOLT:
                return bolt(p, target, pos, air, form);
            case FORM:
                return form(p);
            case RECALL:
                return recall(p, form);
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ Росток

    /** Росток: ускорение времени для растений, печей и машин. Точка или круг 3×3 (5×5 с «Широтой»). */
    static boolean sprout(EntityPlayerMP p, BlockPos pos, int form) {
        int flow = SpellData.rank(p, Spell.SPROUT, "flow");
        double power = SpellMods.power(p, Spell.SPROUT);
        int ticks = (int) ((200 + 100 * flow) * power);
        int extra = 2 + 2 * flow;
        float chance = 0.1F * (1 + flow);
        int r = form == 1 ? (SpellData.has(p, Spell.SPROUT, "wide") ? 2 : 1) : 0;
        int n = 0;
        for (BlockPos q : BlockPos.getAllInBox(pos.add(-r, -1, -r), pos.add(r, 1, r))) {
            if (SpellAccel.add(p.world, q, ticks, extra, chance)) {
                n++;
            }
        }
        if (n > 0 && SpellData.has(p, Spell.SPROUT, "humus")) {
            int back = 0;
            for (int i = 0; i < n; i++) {
                if (p.world.rand.nextFloat() < 0.15F) {
                    back++;
                }
            }
            if (back > 0) {
                ChaosEnergy.set(p, ChaosEnergy.get(p) + back);
            }
        }
        return n > 0;
    }

    // ------------------------------------------------------------------ Мост пустоты

    /** Мост пустоты: дорожка вперёд, лестница по наклону взгляда или площадка. */
    static boolean bridge(EntityPlayerMP p, BlockPos from, int form) {
        int range = SpellData.rank(p, Spell.BRIDGE, "range");
        EnumFacing dir = p.getHorizontalFacing();
        List<BlockPos> place = new ArrayList<>();
        if (form == 2) {
            int r = range > 0 ? 3 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    place.add(from.add(dx, 0, dz));
                }
            }
        } else {
            int len = 8 + 4 * range;
            int up = p.rotationPitch < -10 ? 1 : p.rotationPitch > 10 ? -1 : 0;
            for (int i = 1; i <= len; i++) {
                place.add(from.offset(dir, i).up(form == 1 ? up * i : 0));
            }
        }
        int placed = 0;
        for (BlockPos q : place) {
            if (q.getY() < 1 || q.getY() > 254) {
                continue;
            }
            IBlockState st = p.world.getBlockState(q);
            if (!p.world.isAirBlock(q) && !st.getBlock().isReplaceable(p.world, q)) {
                if (form != 2) {
                    break;
                }
                continue;
            }
            if (!p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY)) {
                continue;
            }
            p.world.setBlockState(q, ModBlocks.CHAOS_STONE.getDefaultState());
            placed++;
        }
        return placed > 0;
    }

    // ------------------------------------------------------------------ Источник

    private static boolean lava(EntityPlayer p) {
        return SpellData.has(p, Spell.SPRING, "lava") && p.isSneaking();
    }

    /** Источник: вода или лава; озеро заполняет яму до 3×3; осушение убирает воду в кубе 5×5×5. */
    static boolean spring(EntityPlayerMP p, BlockPos pos, EnumFacing side, int form) {
        World w = p.world;
        if (form == 2) {
            int n = 0;
            for (BlockPos q : BlockPos.getAllInBox(pos.add(-2, -2, -2), pos.add(2, 2, 2))) {
                if (w.getBlockState(q).getMaterial() == Material.WATER && p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY)) {
                    w.setBlockState(q, Blocks.AIR.getDefaultState(), 2);
                    n++;
                }
            }
            return n > 0;
        }
        boolean lava = lava(p);
        if (!lava && w.provider.doesWaterVaporize()) {
            return false;
        }
        IBlockState liquid = lava ? Blocks.LAVA.getDefaultState() : Blocks.WATER.getDefaultState();
        BlockPos at = pos.offset(side);
        List<BlockPos> spots = new ArrayList<>();
        if (form == 1) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    spots.add(at.add(dx, 0, dz));
                }
            }
        } else {
            spots.add(at);
        }
        int n = 0;
        for (BlockPos q : spots) {
            IBlockState st = w.getBlockState(q);
            if (!w.isAirBlock(q) && !st.getBlock().isReplaceable(w, q)) {
                continue;
            }
            if (form == 1 && w.isAirBlock(q.down()) && !q.equals(at)) {
                continue;   // озеро не выливается за края ямы
            }
            if (!p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY)) {
                continue;
            }
            w.setBlockState(q, liquid);
            n++;
        }
        return n > 0;
    }

    /** Водяной удар: струя отбрасывает цель и гасит огонь; огненным тварям и эндерменам — урон. */
    static boolean waterStrike(EntityPlayerMP p, EntityLivingBase t) {
        double power = SpellMods.power(p, Spell.SPRING);
        double dx = t.posX - p.posX, dz = t.posZ - p.posZ;
        double len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        t.motionX += dx / len * 1.4 * power;
        t.motionZ += dz / len * 1.4 * power;
        t.motionY += 0.35;
        t.velocityChanged = true;
        t.extinguish();
        if (t.isImmuneToFire() || t instanceof net.minecraft.entity.monster.EntityEnderman) {
            t.attackEntityFrom(DamageSource.causeIndirectMagicDamage(p, p), (float) (5 * power));
        }
        ((WorldServer) p.world).spawnParticle(EnumParticleTypes.WATER_SPLASH, t.posX, t.posY + t.height / 2, t.posZ, 40, 0.4, 0.4, 0.4, 0.2);
        p.world.playSound(null, t.getPosition(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    // ------------------------------------------------------------------ Разлом

    /** Можно ли дробить: всё не прочнее обсидиана, без тайлов и жидкостей. */
    public static boolean breakable(World w, BlockPos q) {
        IBlockState st = w.getBlockState(q);
        if (st.getBlock().isAir(st, w, q) || st.getMaterial().isLiquid() || w.getTileEntity(q) != null) {
            return false;
        }
        float h = st.getBlockHardness(w, q);
        return h >= 0 && h <= 50.0F;
    }

    /** Разлом: куб 3×3×3 (5×5×5 с «Глубиной») или тоннель 1×2 вглубь (6, 10, 14). */
    static boolean rift(EntityPlayerMP p, BlockPos pos, EnumFacing side, int form) {
        int depth = SpellData.rank(p, Spell.RIFT, "depth");
        boolean pull = SpellData.has(p, Spell.RIFT, "pull");
        List<BlockPos> list = new ArrayList<>();
        if (form == 1) {
            EnumFacing into = side.getOpposite();
            int len = 6 + 4 * depth;
            for (int i = 0; i < len; i++) {
                BlockPos q = pos.offset(into, i);
                list.add(q);
                list.add(into.getAxis() == EnumFacing.Axis.Y ? q.offset(p.getHorizontalFacing()) : q.down());
            }
        } else {
            int r = depth > 0 ? 2 : 1;
            for (BlockPos q : BlockPos.getAllInBox(pos.add(-r, -r, -r), pos.add(r, r, r))) {
                list.add(q.toImmutable());
            }
        }
        int n = 0;
        for (BlockPos q : list) {
            if (!breakable(p.world, q) || !p.canPlayerEdit(q, EnumFacing.UP, ItemStack.EMPTY)) {
                continue;
            }
            if (pull) {
                IBlockState st = p.world.getBlockState(q);
                NonNullList<ItemStack> drops = NonNullList.create();
                st.getBlock().getDrops(drops, p.world, q, st, 0);
                p.world.playEvent(2001, q, Block.getStateId(st));
                p.world.setBlockToAir(q);
                for (ItemStack d : drops) {
                    ItemHandlerHelper.giveItemToPlayer(p, d);
                }
            } else {
                p.world.destroyBlock(q, true);
            }
            n++;
        }
        return n > 0;
    }

    // ------------------------------------------------------------------ Звёздный взор

    static boolean starsight(EntityPlayerMP p, int form) {
        int radius = SpellData.rank(p, Spell.STARSIGHT, "radius");
        double power = SpellMods.power(p, Spell.STARSIGHT);
        if (form == 1) {
            double r = radius == 0 ? 24 : radius == 1 ? 32 : 48;
            int n = 0;
            for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, p.getEntityBoundingBox().grow(r),
                    x -> x instanceof IMob && x.isEntityAlive())) {
                e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, (int) (600 * power), 0, false, false));
                n++;
            }
            status(p, new TextComponentTranslation("chaosshard.spell.starsight.hunt", n));
            return true;
        }
        if (form == 2) {
            int r = 8 + 4 * radius;
            boolean sort = SpellData.has(p, Spell.STARSIGHT, "sort");
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
                int t = oreTier(st);
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
            status(p, new TextComponentTranslation("chaosshard.spell.starsight.ores", a.length));
            return true;
        }
        p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, (int) (20 * 180 * power), 0, true, false));
        return true;
    }

    private static final String[] HIGH = {"diamond", "emerald", "platinum", "iridium", "cobalt", "ardite", "mithril", "draconium", "uranium"};
    private static final String[] MID = {"gold", "lapis", "redstone", "silver", "aquamarine", "starmetal", "osmium", "quartz", "nickel"};

    /** Ценность руды: 0 — обычная, 1 — средняя, 2 — дорогая; -1 — не руда. */
    public static int oreTier(IBlockState st) {
        Block b = st.getBlock();
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(b);
        if (item == net.minecraft.init.Items.AIR) {
            return -1;
        }
        ItemStack stack = new ItemStack(item, 1, b.damageDropped(st));
        if (stack.isEmpty()) {
            return -1;
        }
        int best = -1;
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            if (!name.startsWith("ore")) {
                continue;
            }
            String low = name.toLowerCase(java.util.Locale.ROOT);
            int t = 0;
            for (String h : HIGH) {
                if (low.contains(h)) {
                    t = 2;
                }
            }
            if (t == 0) {
                for (String m : MID) {
                    if (low.contains(m)) {
                        t = 1;
                    }
                }
            }
            best = Math.max(best, t);
        }
        return best;
    }

    // ------------------------------------------------------------------ Рывок

    private static boolean dash(EntityPlayerMP player) {
        Vec3d look = player.getLookVec();
        double power = 1.6 + 0.5 * SpellData.rank(player, Spell.DASH, "range");
        player.motionX = look.x * power;
        player.motionY = Math.max(0.25, look.y * power * 0.6 + 0.2);
        player.motionZ = look.z * power;
        player.velocityChanged = true;
        player.fallDistance = 0;
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 20, 4, true, false));
        ((WorldServer) player.world).spawnParticle(EnumParticleTypes.PORTAL, player.posX, player.posY + 1, player.posZ,
                40, 0.4, 0.6, 0.4, 0.6);
        player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    /** Скачок: мгновенно на блок под взглядом. */
    static boolean leap(EntityPlayerMP p, BlockPos pos, EnumFacing side) {
        if (pos == null) {
            return false;
        }
        BlockPos at = pos.offset(side);
        if (side != EnumFacing.UP && p.world.isAirBlock(at.down())) {
            // попали в стену — встаём на верх ближайшего столба
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

    /** Бросок к цели и удар оружием из руки; «Удар с разбега» — ещё половина урона и отброс. */
    static boolean dashTo(EntityPlayerMP p, EntityLivingBase t) {
        Vec3d dir = new Vec3d(t.posX - p.posX, 0, t.posZ - p.posZ);
        double len = dir.length();
        if (len > 0.01) {
            dir = dir.scale(1 / len);
        }
        double x = t.posX - dir.x * 1.2, z = t.posZ - dir.z * 1.2;
        WorldServer ws = (WorldServer) p.world;
        ws.spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.6, 0.3, 0.6);
        p.setPositionAndUpdate(x, t.posY, z);
        p.rotationYaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90F;
        p.fallDistance = 0;
        p.resetCooldown();
        p.attackTargetEntityWithCurrentItem(t);
        if (SpellData.has(p, Spell.DASH, "rush") && t.isEntityAlive()) {
            double dmg = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * 0.5;
            t.hurtResistantTime = 0;
            t.attackEntityFrom(DamageSource.causePlayerDamage(p), (float) (dmg * SpellMods.power(p, Spell.DASH)));
            t.knockBack(p, 1.2F, -dir.x, -dir.z);
        }
        ws.spawnParticle(EnumParticleTypes.SWEEP_ATTACK, t.posX, t.posY + t.height / 2, t.posZ, 1, 0.0, 0.0, 0.0, 0.0);
        p.world.playSound(null, p.getPosition(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.9F);
        return true;
    }

    // ------------------------------------------------------------------ Стрела Хаоса

    public static float boltDamage(EntityPlayer p) {
        double base = 6.0 + 1.5 * ThreadBonus.level(p);
        return (float) (base * (1 + 0.2 * SpellData.rank(p, Spell.BOLT, "sharp")) * SpellMods.power(p, Spell.BOLT));
    }

    private static boolean hostileTo(EntityPlayer p, EntityLivingBase x) {
        if (x == p || !x.isEntityAlive() || Servants.isServantOf(x, p.getUniqueID()) || x instanceof EntityPlayer) {
            return false;
        }
        return x instanceof IMob || x instanceof EntityLiving && ((EntityLiving) x).getAttackTarget() == p;
    }

    /** Стрела: одиночная, цепь (3 цели, 4 со вторым рангом) или взрыв. Без цели по блоку — взрыв без разрушений. */
    static boolean bolt(EntityPlayerMP p, EntityLivingBase target, BlockPos pos, Vec3d air, int form) {
        WorldServer ws = (WorldServer) p.world;
        Vec3d start = p.getPositionEyes(1.0F);
        Vec3d stop = target != null ? target.getPositionVector().add(0, target.height / 2, 0)
                : pos != null ? new Vec3d(pos).add(0.5, 0.5, 0.5) : air;
        beam(ws, start, stop);
        p.world.playSound(null, p.getPosition(), SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 0.8F, 1.6F);
        float dmg = boltDamage(p);
        if (target == null) {
            if (pos != null) {
                blast(p, stop, dmg);
            }
            return true;
        }
        if (form == 2) {
            blast(p, stop, dmg);
            return true;
        }
        hit(p, target, dmg);
        if (form == 1) {
            int targets = SpellData.rank(p, Spell.BOLT, "chain") >= 2 ? 4 : 3;
            Set<EntityLivingBase> done = new HashSet<>();
            done.add(target);
            EntityLivingBase last = target;
            float d = dmg;
            for (int i = 1; i < targets; i++) {
                final EntityLivingBase from = last;
                EntityLivingBase next = null;
                double best = 36;
                for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, from.getEntityBoundingBox().grow(6),
                        x -> hostileTo(p, x) && !done.contains(x))) {
                    double dd = e.getDistanceSq(from);
                    if (dd < best) {
                        best = dd;
                        next = e;
                    }
                }
                if (next == null) {
                    break;
                }
                d *= 0.75F;
                beam(ws, from.getPositionVector().add(0, from.height / 2, 0), next.getPositionVector().add(0, next.height / 2, 0));
                hit(p, next, d);
                done.add(next);
                last = next;
            }
        }
        return true;
    }

    private static void hit(EntityPlayerMP p, EntityLivingBase t, float dmg) {
        t.hurtResistantTime = 0;
        t.attackEntityFrom(DamageSource.causeIndirectMagicDamage(p, p), dmg);
        ((WorldServer) p.world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, t.posX, t.posY + t.height / 2, t.posZ, 20, 0.3, 0.3, 0.3, 0.02);
    }

    /** Взрыв Хаоса: магический урон всем врагам в радиусе 3, блоки целы. */
    static int blast(EntityPlayerMP p, Vec3d at, float dmg) {
        WorldServer ws = (WorldServer) p.world;
        ws.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 60, 1.2, 0.8, 1.2, 0.03);
        p.world.playSound(null, new BlockPos(at), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 1.3F);
        int n = 0;
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(at.x, at.y, at.z, at.x, at.y, at.z).grow(3),
                x -> x != p && x.isEntityAlive() && !Servants.isServantOf(x, p.getUniqueID()) && !(x instanceof EntityPlayer))) {
            hit(p, e, dmg * 0.8F);
            n++;
        }
        return n;
    }

    private static void beam(WorldServer ws, Vec3d a, Vec3d b) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 0.01) {
            return;
        }
        Vec3d u = d.scale(1 / len);
        for (double t = 0; t < len; t += 0.5) {
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, a.x + u.x * t, a.y + u.y * t - 0.2, a.z + u.z * t, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------ Форма Хаоса

    /** Форма Хаоса: 30 / 45 / 60 секунд силы, скорости, защиты, прыгучести и спешки. */
    static boolean form(EntityPlayerMP player) {
        int t = (int) (20 * (30 + 15 * SpellData.rank(player, Spell.FORM, "long")) * SpellMods.power(player, Spell.FORM));
        int boost = SpellData.rank(player, Spell.FORM, "power");
        int amp = (ThreadBonus.level(player) >= 4 ? 2 : 1) + boost;
        player.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, t, amp, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.SPEED, t, 1 + boost, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, t, boost, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, t, 1 + boost, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.HASTE, t, 1 + boost, false, true));
        ((WorldServer) player.world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, player.posX, player.posY + 1, player.posZ,
                80, 0.5, 1.0, 0.5, 0.05);
        player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    // ------------------------------------------------------------------ Зов Нити

    /** Зов Нити: домой (Преддверие ↔ Лучезария), к своей Цитадели или к ближайшему своему Якорю в этом мире. */
    static boolean recall(EntityPlayerMP p, int form) {
        if (form == 1 || form == 2) {
            if (ru.death.chaosshard.trial.TrialDimension.is(p.world) || ru.death.chaosshard.act5.RiftDimension.is(p.world)) {
                status(p, Breach.grimoire("chaosshard.citadel.blocked"));
                return false;
            }
        }
        if (form == 1) {
            Object[] c = ru.death.chaosshard.lair.LairData.get(p.world).citadelOf(p.getUniqueID());
            if (c == null) {
                status(p, Breach.grimoire("chaosshard.citadel.none"));
                return false;
            }
            ChaosTeleporter.toPos(p, (Integer) c[0], ((BlockPos) c[1]).up());
            return true;
        }
        if (form == 2) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos a : ru.death.chaosshard.anchor.AnchorData.get(p.world).anchors) {
                TileEntity te = p.world.getTileEntity(a);
                if (te instanceof ru.death.chaosshard.anchor.TileAnchor
                        && p.getUniqueID().equals(((ru.death.chaosshard.anchor.TileAnchor) te).getOwner())) {
                    double d = a.distanceSq(p.getPosition());
                    if (d < bd) {
                        bd = d;
                        best = a;
                    }
                }
            }
            if (best == null) {
                status(p, Breach.grimoire("chaosshard.spell.recall.no_anchor"));
                return false;
            }
            BlockPos to = p.world.getTopSolidOrLiquidBlock(best.add(2, 0, 0));
            p.setPositionAndUpdate(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
            return true;
        }
        if (AntechamberDimension.is(p.world) && !Breach.hasBreached(p)) {
            status(p, new TextComponentTranslation("chaosshard.spell.recall.sealed"));
            return false;
        }
        Breach.travel(p);
        return true;
    }

    // ------------------------------------------------------------------ служебное

    private static boolean canCast(EntityPlayerMP player, Spell spell) {
        if (spell == Spell.BOOK) {
            return false;
        }
        if (!player.isCreative()) {
            if (ThreadBonus.level(player) < spell.minThread) {
                status(player, new TextComponentTranslation("chaosshard.spell.locked", spell.minThread));
                return false;
            }
            if (ChaosEnergy.get(player) < cost(player, spell)) {
                status(player, new TextComponentTranslation("chaosshard.spell.no_energy", cost(player, spell), (int) ChaosEnergy.get(player)));
                return false;
            }
        }
        long left = cooldownLeft(player, spell);
        if (left > 0) {
            status(player, new TextComponentTranslation("chaosshard.spell.cooldown", (left + 19) / 20));
            return false;
        }
        return true;
    }

    private static void finish(EntityPlayerMP player, Spell spell, boolean ok, BlockPos where, int cost) {
        if (!ok) {
            status(player, new TextComponentTranslation("chaosshard.spell.fizzle"));
            return;
        }
        if (!player.isCreative()) {
            ChaosEnergy.spend(player, cost);
        }
        setCooldown(player, spell);
        SpellMods.after(player, spell, cost);
        ru.death.chaosshard.quest.Milestones.grant(player, ru.death.chaosshard.quest.Milestones.SPELL);
        World world = player.world;
        world.playSound(null, where, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 0.6F, 1.2F);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    where.getX() + 0.5, where.getY() + 1.0, where.getZ() + 0.5, 24, 0.6, 0.4, 0.6, 0.02);
        }
    }

    /** Цена заклинания: перки, Жилы Хаоса (имплант), лава в Незере, внешние модификаторы. */
    public static int cost(EntityPlayer player, Spell spell) {
        double c = spell.energy;
        if (ru.death.chaosshard.compat.body.Body.hasVeins(player)) {
            c *= 0.75;
        }
        switch (spell) {
            case BOLT:
                if (SpellData.has(player, spell, "economy")) {
                    c *= 0.75;
                }
                break;
            case BRIDGE:
            case RECALL:
                if (SpellData.has(player, spell, "cheap")) {
                    c *= 0.5;
                }
                break;
            case SPRING:
                if (lava(player) && player.world.provider.doesWaterVaporize()) {
                    c *= 0.5;
                }
                break;
            default:
                break;
        }
        c *= SpellMods.cost(player, spell);
        return spell.energy == 0 ? 0 : Math.max(1, (int) Math.ceil(c));
    }

    /** Перезарядка заклинания в тиках с учётом перков. */
    public static int cooldown(EntityPlayer player, Spell spell) {
        double cd = spell.cooldown;
        if (spell == Spell.FORM && SpellData.has(player, spell, "memory")) {
            cd = 800;
        }
        if (spell == Spell.RECALL) {
            cd = 1200 - 400 * SpellData.rank(player, spell, "fast");
        }
        if (ru.death.chaosshard.stars.Perks.well(player)) {
            cd *= 0.75;
        }
        return (int) (cd * SpellMods.cooldown(player, spell));
    }

    private static long cooldownLeft(EntityPlayer player, Spell spell) {
        NBTTagCompound cd = player.getEntityData().getCompoundTag(TAG_COOLDOWNS);
        long readyAt = cd.getLong(spell.name());
        return readyAt - player.world.getTotalWorldTime();
    }

    private static void setCooldown(EntityPlayer player, Spell spell) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound cd = data.getCompoundTag(TAG_COOLDOWNS);
        int cooldown = cooldown(player, spell);
        if (spell == Spell.DASH && SpellData.has(player, spell, "charge")) {
            // Второй заряд: первый рывок почти без перезарядки, второй — полная
            if (!data.getBoolean(TAG_DASH_USED)) {
                data.setBoolean(TAG_DASH_USED, true);
                cooldown = 10;
            } else {
                data.setBoolean(TAG_DASH_USED, false);
            }
        }
        cd.setLong(spell.name(), player.world.getTotalWorldTime() + cooldown);
        data.setTag(TAG_COOLDOWNS, cd);
    }

    /** Сбросить перезарядки (для автотеста и отладки). */
    public static void clearCooldowns(EntityPlayer p) {
        p.getEntityData().removeTag(TAG_COOLDOWNS);
        p.getEntityData().removeTag(TAG_DASH_USED);
    }

    private static void status(EntityPlayer player, ITextComponent msg) {
        player.sendStatusMessage(msg, true);
    }

    private SpellCaster() {
    }
}
