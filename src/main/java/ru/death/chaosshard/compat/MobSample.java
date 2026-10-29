package ru.death.chaosshard.compat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/**
 * Образец моба: яйцо призыва или Ящик вечного заточения (EvilCraft) с пойманным духом.
 * Ящик годится везде, где сборка просит яйцо: образец Жатвы в Складе Якоря и спавнер (ПКМ ящиком).
 * Без зависимости от EvilCraft: ящик узнаём по имени предмета, моба — по NBT духа.
 */
public final class MobSample {

    public static final ResourceLocation BOX = new ResourceLocation("evilcraft", "box_of_eternal_closure");
    private static final String SPIRIT = "spiritTag", INNER = "innerEntity", PLAYER = "playerId";

    private MobSample() {
    }

    public static boolean isSpiritBox(ItemStack s) {
        return !s.isEmpty() && BOX.equals(s.getItem().getRegistryName());
    }

    /** Годится ли предмет как образец (яйцо или ящик с духом настоящего моба). */
    public static boolean isSample(ItemStack s) {
        return id(s) != null;
    }

    /** Моб из яйца или ящика; null — не образец. */
    @Nullable
    public static ResourceLocation id(ItemStack s) {
        if (s.isEmpty()) {
            return null;
        }
        if (s.getItem() instanceof ItemMonsterPlacer) {
            return ItemMonsterPlacer.getNamedIdFrom(s);
        }
        return isSpiritBox(s) ? idFromBoxTag(s.getTagCompound()) : null;
    }

    /** Моб духа по NBT ящика. Пустой ящик, рой духов и дух игрока образцом не считаются. */
    @Nullable
    public static ResourceLocation idFromBoxTag(@Nullable NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(SPIRIT, 10) || !tag.getString(PLAYER).isEmpty()) {
            return null;
        }
        NBTTagCompound spirit = tag.getCompoundTag(SPIRIT);
        if (!spirit.getString(PLAYER).isEmpty()) {
            return null;
        }
        String cls = spirit.getString(INNER);
        if (cls.isEmpty()) {
            return null;
        }
        try {
            Class<?> c = Class.forName(cls);
            if (!Entity.class.isAssignableFrom(c)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            ResourceLocation id = EntityList.getKey((Class<? extends Entity>) c);
            return id;
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Дух ушёл (в спавнер): ящик пустеет. */
    public static ItemStack emptied(ItemStack box) {
        ItemStack out = box.copy();
        out.setCount(1);
        out.setTagCompound(null);
        return out;
    }

    /** Переделать спавнер под моба. false — там не спавнер. */
    public static boolean applyToSpawner(World w, BlockPos pos, ResourceLocation id) {
        TileEntity te = w.getTileEntity(pos);
        if (!(te instanceof TileEntityMobSpawner)) {
            return false;
        }
        ((TileEntityMobSpawner) te).getSpawnerBaseLogic().setEntityId(id);
        te.markDirty();
        IBlockState st = w.getBlockState(pos);
        w.notifyBlockUpdate(pos, st, st, 3);
        return true;
    }

    public static Events events() {
        return new Events();
    }

    public static final class Events {

        /** ПКМ по спавнеру ящиком с духом — как яйцом: спавнер меняет моба, дух переходит в него. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
            ItemStack held = event.getItemStack();
            if (!isSpiritBox(held)) {
                return;
            }
            World w = event.getWorld();
            if (!(w.getTileEntity(event.getPos()) instanceof TileEntityMobSpawner)) {
                return;
            }
            ResourceLocation id = id(held);
            if (id == null) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            if (w.isRemote) {
                return;
            }
            EntityPlayer p = event.getEntityPlayer();
            if (!applyToSpawner(w, event.getPos(), id)) {
                return;
            }
            if (!p.isCreative()) {
                ItemStack empty = emptied(held);
                if (held.getCount() > 1) {
                    held.shrink(1);
                    ItemHandlerHelper.giveItemToPlayer(p, empty);
                } else {
                    p.setHeldItem(event.getHand(), empty);
                }
            }
            BlockPos b = event.getPos();
            ((WorldServer) w).spawnParticle(EnumParticleTypes.SPELL_WITCH, b.getX() + 0.5, b.getY() + 0.6, b.getZ() + 0.5,
                    24, 0.35, 0.35, 0.35, 0.05);
            w.playSound(null, b, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.BLOCKS, 0.8F, 0.9F);
        }

        /** Подсказка на ящике с духом: годится вместо яйца. */
        @SubscribeEvent
        public void onTooltip(ItemTooltipEvent event) {
            if (isSpiritBox(event.getItemStack()) && id(event.getItemStack()) != null) {
                event.getToolTip().add(TextFormatting.DARK_PURPLE + I18n.translateToLocal("chaosshard.mobsample.box"));
            }
        }
    }
}
