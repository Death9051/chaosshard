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

    /** Дальность взгляда заклинания (с учётом ветки). */
    public static double range(EntityPlayer p, Spell s) {
        return SpellEffects.range(p, s);
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
        SpellHeat.roll(p, s);
        int cost = cost(p, s);
        boolean ok = effect(p, s, pos, side, target, air);
        if (ok && p.world.rand.nextDouble() < SpellMods.twice(p, s)) {
            effect(p, s, pos, side, target, air);
            ((WorldServer) p.world).spawnParticle(EnumParticleTypes.PORTAL, p.posX, p.posY + 1, p.posZ, 30, 0.4, 0.6, 0.4, 0.5);
        }
        finish(p, s, ok, pos != null ? pos : p.getPosition(), cost);
        SpellHeat.clear(p);
        return ok;
    }

    private static boolean effect(EntityPlayerMP p, Spell s, BlockPos pos, EnumFacing side, EntityLivingBase target, Vec3d air) {
        return SpellEffects.effect(p, s, pos, side, target, air);
    }

    // ------------------------------------------------------------------ общее для заклинаний

    /** Можно ли дробить: всё не прочнее обсидиана, без тайлов и жидкостей. */
    public static boolean breakable(World w, BlockPos q) {
        IBlockState st = w.getBlockState(q);
        if (st.getBlock().isAir(st, w, q) || st.getMaterial().isLiquid() || w.getTileEntity(q) != null) {
            return false;
        }
        float h = st.getBlockHardness(w, q);
        return h >= 0 && h <= 50.0F;
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

    // ------------------------------------------------------------------ Стрела Хаоса

    public static float boltDamage(EntityPlayer p) {
        double base = 6.0 + 1.5 * ThreadBonus.level(p);
        return (float) (base * SpellMods.power(p, Spell.BOLT));
    }

    static boolean hostileTo(EntityPlayer p, EntityLivingBase x) {
        if (x == p || !x.isEntityAlive() || Servants.isServantOf(x, p.getUniqueID()) || x instanceof EntityPlayer) {
            return false;
        }
        return x instanceof IMob || x instanceof EntityLiving && ((EntityLiving) x).getAttackTarget() == p;
    }

    static void hit(EntityPlayerMP p, EntityLivingBase t, float dmg) {
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
        for (EntityLivingBase e : p.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(at.x, at.y, at.z, at.x, at.y, at.z).grow(3 + SpellMods.area(p, Spell.BOLT)),
                x -> x != p && x.isEntityAlive() && !Servants.isServantOf(x, p.getUniqueID()) && !(x instanceof EntityPlayer))) {
            hit(p, e, dmg * 0.8F);
            n++;
        }
        return n;
    }

    static void beam(WorldServer ws, Vec3d a, Vec3d b) {
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
            if (ChaosEnergy.get(player) + SpellMods.extraEnergy(player, spell) < cost(player, spell)) {
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
        boolean free = false;
        if (!player.isCreative()) {
            free = SpellMods.pay(player, spell, cost);
        }
        if (!free) {
            setCooldown(player, spell);
        }
        SpellMods.after(player, spell, cost);
        ru.death.chaosshard.quest.Milestones.grant(player, ru.death.chaosshard.quest.Milestones.SPELL);
        World world = player.world;
        world.playSound(null, where, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 0.6F, 1.2F);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    where.getX() + 0.5, where.getY() + 1.0, where.getZ() + 0.5, 24, 0.6, 0.4, 0.6, 0.02);
        }
    }

    /** Цена заклинания: ветки, Жилы Хаоса (имплант), внешние модификаторы. */
    public static int cost(EntityPlayer player, Spell spell) {
        double c = spell.energy;
        if (ru.death.chaosshard.compat.body.Body.hasVeins(player)) {
            c *= 0.75;
        }
        if (spell == Spell.RECALL) {
            if (SpellData.a(player, spell, 1) && !player.isSneaking()) {
                c = SpellData.a(player, spell, 2) ? 6 : 10;           // Шаг Нити
            } else if (SpellData.b(player, spell, 1) && player.isSneaking()) {
                c = 1;                                                 // поставить метку
            } else if (SpellData.b(player, spell, 3)) {
                c *= 0.5;
            }
        }
        c *= SpellMods.cost(player, spell);
        return spell.energy == 0 ? 0 : Math.max(1, (int) Math.ceil(c));
    }

    /** Перезарядка заклинания в тиках с учётом веток. */
    public static int cooldown(EntityPlayer player, Spell spell) {
        double cd = spell.cooldown;
        if (spell == Spell.RECALL) {
            if (SpellData.a(player, spell, 1) && !player.isSneaking()) {
                cd = SpellData.a(player, spell, 3) ? 10 : 60;
            } else if (SpellData.b(player, spell, 1) && player.isSneaking()) {
                cd = 20;
            } else if (SpellData.b(player, spell, 2)) {
                cd = 600;
            }
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
        if (spell == Spell.DASH && data.getBoolean(SpellEffects.TAG_DASH_RESET)) {
            // Тень 2: цель убита — рывок сразу снова готов
            data.removeTag(SpellEffects.TAG_DASH_RESET);
            cooldown = 5;
        }
        cd.setLong(spell.name(), player.world.getTotalWorldTime() + cooldown);
        data.setTag(TAG_COOLDOWNS, cd);
    }

    /** Сбросить перезарядки (для автотеста и отладки). */
    public static void clearCooldowns(EntityPlayer p) {
        p.getEntityData().removeTag(TAG_COOLDOWNS);
    }

    static void status(EntityPlayer player, ITextComponent msg) {
        player.sendStatusMessage(msg, true);
    }

    private SpellCaster() {
    }
}
