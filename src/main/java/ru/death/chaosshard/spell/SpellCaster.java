package ru.death.chaosshard.spell;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import ru.death.chaosshard.antechamber.AntechamberDimension;
import ru.death.chaosshard.antechamber.ChaosTeleporter;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.thread.ThreadManager;

/**
 * Серверная логика заклинаний Гримуара.
 */
public final class SpellCaster {

    private static final String TAG_SPELL = "spell";
    private static final String TAG_MODE = "mode";

    /** Режим Гримуара: false — записи (книга квестов), true — магия. */
    public static boolean magicMode(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(TAG_MODE);
    }

    public static void toggleMode(EntityPlayerMP player, ItemStack stack) {
        boolean magic = !magicMode(stack);
        tag(stack).setBoolean(TAG_MODE, magic);
        if (magic && selected(stack) == Spell.BOOK) {
            tag(stack).setInteger(TAG_SPELL, Spell.SPROUT.ordinal());
        }
        TextComponentTranslation msg = new TextComponentTranslation(magic ? "chaosshard.grimoire.mode.magic" : "chaosshard.grimoire.mode.notes");
        msg.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        status(player, msg);
        player.world.playSound(null, player.getPosition(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, magic ? 0.7F : 1.1F);
    }

    /** Выбрать заклинание (из колеса или окна Гримуара). */
    public static void select(EntityPlayerMP player, ItemStack stack, Spell spell) {
        if (spell == Spell.BOOK) {
            return;
        }
        if (spell.minThread > ru.death.chaosshard.thread.ThreadBonus.level(player) && !player.isCreative()) {
            status(player, new TextComponentTranslation("chaosshard.spell.locked", spell.minThread));
            return;
        }
        tag(stack).setInteger(TAG_SPELL, spell.ordinal());
        tag(stack).setBoolean(TAG_MODE, true);
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
    private static final String TAG_COOLDOWNS = "chaosshard_spell_cd";

    // ------------------------------------------------------------------ выбор

    public static Spell selected(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? Spell.BOOK : Spell.byIndex(tag.getInteger(TAG_SPELL));
    }

    /** Следующее доступное заклинание по кругу. */
    public static void cycle(EntityPlayerMP player, ItemStack stack) {
        int level = ru.death.chaosshard.thread.ThreadBonus.level(player);
        Spell current = selected(stack);
        Spell[] all = Spell.values();
        Spell next = Spell.BOOK;
        for (int step = 1; step <= all.length; step++) {
            Spell candidate = all[(current.ordinal() + step) % all.length];
            if (candidate.minThread <= level || player.isCreative()) {
                next = candidate;
                break;
            }
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger(TAG_SPELL, next.ordinal());
        TextComponentTranslation name = new TextComponentTranslation(next.key());
        name.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
        status(player, new TextComponentTranslation("chaosshard.spell.selected", name));
    }

    // ------------------------------------------------------------------ каст

    /** Заклинание без цели (или «в воздух»). */
    public static void castSelf(EntityPlayerMP player, Spell spell) {
        if (spell.onBlock) {
            status(player, new TextComponentTranslation("chaosshard.spell.need_block"));
            return;
        }
        if (!canCast(player, spell)) {
            return;
        }
        boolean ok;
        switch (spell) {
            case STARSIGHT:
                player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 20 * 180, 0, true, false));
                ok = true;
                break;
            case DASH:
                ok = dash(player);
                break;
            case BOLT:
                ok = bolt(player);
                break;
            case FORM:
                ok = form(player);
                break;
            case RECALL:
                if (AntechamberDimension.is(player.world) && !ru.death.chaosshard.breach.Breach.hasBreached(player)) {
                    status(player, new TextComponentTranslation("chaosshard.spell.recall.sealed"));
                    return;
                }
                ru.death.chaosshard.breach.Breach.travel(player);
                ok = true;
                break;
            default:
                ok = false;
        }
        finish(player, spell, ok, player.getPosition());
    }

    /** Заклинание по блоку. */
    public static void castOnBlock(EntityPlayerMP player, Spell spell, BlockPos pos, EnumFacing side) {
        if (!spell.onBlock) {
            castSelf(player, spell);
            return;
        }
        if (!canCast(player, spell)) {
            return;
        }
        World world = player.world;
        boolean ok;
        switch (spell) {
            case SPROUT:
                ok = sprout(world, pos);
                break;
            case BRIDGE:
                ok = bridge(world, player, pos);
                break;
            case SPRING:
                ok = spring(world, player, pos.offset(side));
                break;
            case RIFT:
                ok = rift(world, player, pos);
                break;
            default:
                ok = false;
        }
        finish(player, spell, ok, pos);
    }

    // ------------------------------------------------------------------ эффекты

    /** Рывок: бросок на ~8 блоков по взгляду, падение после рывка не ранит. */
    private static boolean dash(EntityPlayerMP player) {
        net.minecraft.util.math.Vec3d look = player.getLookVec();
        double power = 1.6;
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

    /** Стрела Хаоса: магический урон первой цели на 24 блока, сила растёт с Нитью. */
    private static boolean bolt(EntityPlayerMP player) {
        net.minecraft.util.math.Vec3d start = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLookVec();
        double range = 24;
        net.minecraft.util.math.Vec3d end = start.add(look.x * range, look.y * range, look.z * range);
        net.minecraft.util.math.RayTraceResult block = player.world.rayTraceBlocks(start, end, false, true, false);
        if (block != null) {
            end = block.hitVec;
        }
        net.minecraft.entity.EntityLivingBase target = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity e : player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().expand(look.x * range, look.y * range, look.z * range).grow(1))) {
            if (!(e instanceof net.minecraft.entity.EntityLivingBase) || !e.canBeCollidedWith()) {
                continue;
            }
            net.minecraft.util.math.RayTraceResult hit = e.getEntityBoundingBox().grow(0.3).calculateIntercept(start, end);
            if (hit != null) {
                double d = start.distanceTo(hit.hitVec);
                if (d < best) {
                    best = d;
                    target = (net.minecraft.entity.EntityLivingBase) e;
                }
            }
        }
        WorldServer ws = (WorldServer) player.world;
        net.minecraft.util.math.Vec3d stop = target != null ? target.getPositionVector().add(0, target.height / 2, 0) : end;
        double len = start.distanceTo(stop);
        for (double t = 0; t < len; t += 0.5) {
            ws.spawnParticle(EnumParticleTypes.SPELL_WITCH, start.x + look.x * t, start.y + look.y * t - 0.2, start.z + look.z * t,
                    2, 0.05, 0.05, 0.05, 0.0);
        }
        player.world.playSound(null, player.getPosition(), SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 0.8F, 1.6F);
        if (target == null) {
            return true;
        }
        float dmg = 6.0F + 1.5F * ru.death.chaosshard.thread.ThreadBonus.level(player);
        target.attackEntityFrom(net.minecraft.util.DamageSource.causeIndirectMagicDamage(player, player), dmg);
        ws.spawnParticle(EnumParticleTypes.DRAGON_BREATH, stop.x, stop.y, stop.z, 20, 0.3, 0.3, 0.3, 0.02);
        return true;
    }

    /** Форма Хаоса: 30 секунд силы, скорости, защиты и прыгучести. */
    private static boolean form(EntityPlayerMP player) {
        int t = 20 * 30;
        int amp = ru.death.chaosshard.thread.ThreadBonus.level(player) >= 4 ? 2 : 1;
        player.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, t, amp, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.SPEED, t, 1, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, t, 0, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, t, 1, false, true));
        player.addPotionEffect(new PotionEffect(MobEffects.HASTE, t, 1, false, true));
        ((WorldServer) player.world).spawnParticle(EnumParticleTypes.DRAGON_BREATH, player.posX, player.posY + 1, player.posZ,
                80, 0.5, 1.0, 0.5, 0.05);
        player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    /** Росток: корка → трава, хаосит → земля. */
    private static boolean sprout(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        if (block == ModBlocks.CHAOS_CRUST || block == Blocks.DIRT) {
            return world.setBlockState(pos, Blocks.GRASS.getDefaultState());
        }
        if (block == ModBlocks.CHAOS_STONE) {
            return world.setBlockState(pos, Blocks.DIRT.getDefaultState());
        }
        return false;
    }

    /** Мост пустоты: до 8 блоков хаосита вперёд по взгляду, на уровне блока-опоры. */
    private static boolean bridge(World world, EntityPlayer player, BlockPos from) {
        EnumFacing dir = player.getHorizontalFacing();
        int placed = 0;
        for (int i = 1; i <= 8; i++) {
            BlockPos p = from.offset(dir, i);
            if (!world.isAirBlock(p) && !world.getBlockState(p).getBlock().isReplaceable(world, p)) {
                break;
            }
            if (!player.canPlayerEdit(p, EnumFacing.UP, player.getHeldItemMainhand())) {
                break;
            }
            world.setBlockState(p, ModBlocks.CHAOS_STONE.getDefaultState());
            placed++;
        }
        return placed > 0;
    }

    /** Источник: вода на грани, куда указали. */
    private static boolean spring(World world, EntityPlayer player, BlockPos at) {
        if (world.provider.doesWaterVaporize()) {
            return false;
        }
        IBlockState state = world.getBlockState(at);
        if (!world.isAirBlock(at) && !state.getBlock().isReplaceable(world, at)) {
            return false;
        }
        if (!player.canPlayerEdit(at, EnumFacing.UP, player.getHeldItemMainhand())) {
            return false;
        }
        return world.setBlockState(at, Blocks.WATER.getDefaultState());
    }

    /** Разлом: дробит хаосит и корку в кубе 3×3×3, всё падает к ногам. */
    private static boolean rift(World world, EntityPlayer player, BlockPos center) {
        int broken = 0;
        for (BlockPos p : BlockPos.getAllInBox(center.add(-1, -1, -1), center.add(1, 1, 1))) {
            Block block = world.getBlockState(p).getBlock();
            if ((block == ModBlocks.CHAOS_STONE || block == ModBlocks.CHAOS_CRUST)
                    && player.canPlayerEdit(p, EnumFacing.UP, player.getHeldItemMainhand())) {
                world.destroyBlock(p, true);
                broken++;
            }
        }
        return broken > 0;
    }

    // ------------------------------------------------------------------ служебное

    private static boolean canCast(EntityPlayerMP player, Spell spell) {
        if (spell == Spell.BOOK) {
            return false;
        }
        if (!player.isCreative()) {
            if (ru.death.chaosshard.thread.ThreadBonus.level(player) < spell.minThread) {
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

    private static void finish(EntityPlayerMP player, Spell spell, boolean ok, BlockPos where) {
        if (!ok) {
            status(player, new TextComponentTranslation("chaosshard.spell.fizzle"));
            return;
        }
        if (!player.isCreative()) {
            ChaosEnergy.spend(player, cost(player, spell));
        }
        setCooldown(player, spell);
        ru.death.chaosshard.quest.Milestones.grant(player, ru.death.chaosshard.quest.Milestones.SPELL);
        World world = player.world;
        world.playSound(null, where, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.PLAYERS, 0.6F, 1.2F);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    where.getX() + 0.5, where.getY() + 1.0, where.getZ() + 0.5, 24, 0.6, 0.4, 0.6, 0.02);
        }
    }

    /** Цена заклинания: Жилы Хаоса (имплант) снижают её на четверть. */
    public static int cost(EntityPlayer player, Spell spell) {
        return ru.death.chaosshard.compat.body.Body.hasVeins(player) ? (int) Math.ceil(spell.energy * 0.75) : spell.energy;
    }

    private static long cooldownLeft(EntityPlayer player, Spell spell) {
        NBTTagCompound cd = player.getEntityData().getCompoundTag(TAG_COOLDOWNS);
        long readyAt = cd.getLong(spell.name());
        return readyAt - player.world.getTotalWorldTime();
    }

    private static void setCooldown(EntityPlayer player, Spell spell) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound cd = data.getCompoundTag(TAG_COOLDOWNS);
        int cooldown = player instanceof EntityPlayerMP && ru.death.chaosshard.stars.Perks.well(player)
                ? (int) (spell.cooldown * 0.75) : spell.cooldown;
        cd.setLong(spell.name(), player.world.getTotalWorldTime() + cooldown);
        data.setTag(TAG_COOLDOWNS, cd);
    }

    private static void status(EntityPlayer player, ITextComponent msg) {
        player.sendStatusMessage(msg, true);
    }

    private SpellCaster() {
    }
}
