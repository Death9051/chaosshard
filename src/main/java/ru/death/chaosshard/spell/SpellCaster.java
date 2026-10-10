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
            if (spell.hunger > 0 && player.getFoodStats().getFoodLevel() < spell.hunger + 1) {
                status(player, new TextComponentTranslation("chaosshard.spell.hungry"));
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
            player.addExhaustion(spell.hunger * 4.0F);
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

    private static long cooldownLeft(EntityPlayer player, Spell spell) {
        NBTTagCompound cd = player.getEntityData().getCompoundTag(TAG_COOLDOWNS);
        long readyAt = cd.getLong(spell.name());
        return readyAt - player.world.getTotalWorldTime();
    }

    private static void setCooldown(EntityPlayer player, Spell spell) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound cd = data.getCompoundTag(TAG_COOLDOWNS);
        cd.setLong(spell.name(), player.world.getTotalWorldTime() + spell.cooldown);
        data.setTag(TAG_COOLDOWNS, cd);
    }

    private static void status(EntityPlayer player, ITextComponent msg) {
        player.sendStatusMessage(msg, true);
    }

    private SpellCaster() {
    }
}
