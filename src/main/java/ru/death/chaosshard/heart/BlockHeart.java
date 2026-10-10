package ru.death.chaosshard.heart;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.breach.Breach;
import ru.death.chaosshard.thread.ThreadManager;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Сердце Острова. Неразрушимо. ПКМ — состояние, Shift+ПКМ — прокачать (плата берётся из инвентаря).
 */
public class BlockHeart extends Block {

    public BlockHeart() {
        super(Material.ROCK);
        setSoundType(SoundType.GLASS);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(0.8F);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileHeart();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileHeart)) {
            return true;
        }
        TileHeart heart = (TileHeart) te;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (player.isSneaking()) {
            upgrade(mp, heart);
        } else {
            info(mp, heart);
        }
        return true;
    }

    private static void info(EntityPlayerMP player, TileHeart heart) {
        int tier = heart.getTier();
        player.sendMessage(Breach.grimoire("chaosshard.heart.info", tier, HeartTiers.maxTier(),
                heart.countGrowths(HeartTiers.pool(tier)), heart.maxGrowths(), heart.interval() / 20));
        if (tier >= HeartTiers.maxTier()) {
            player.sendMessage(Breach.grimoire("chaosshard.heart.max"));
            return;
        }
        int target = tier + 1;
        player.sendMessage(Breach.grimoire("chaosshard.heart.next", target, HeartTiers.threadFor(target)));
        for (HeartTiers.Cost c : HeartTiers.costFor(target)) {
            int have = HeartTiers.count(player, c);
            TextComponentString line = new TextComponentString("  " + c.displayName() + ": " + Math.min(have, c.count) + "/" + c.count);
            line.getStyle().setColor(have >= c.count ? TextFormatting.GREEN : TextFormatting.GRAY);
            player.sendMessage(line);
        }
    }

    private static void upgrade(EntityPlayerMP player, TileHeart heart) {
        int tier = heart.getTier();
        if (tier >= HeartTiers.maxTier()) {
            player.sendMessage(Breach.grimoire("chaosshard.heart.max"));
            return;
        }
        int target = tier + 1;
        if (!player.isCreative()) {
            if (ThreadManager.getLevel(player) < HeartTiers.threadFor(target)) {
                player.sendMessage(Breach.grimoire("chaosshard.heart.thin", HeartTiers.threadFor(target)));
                return;
            }
            List<HeartTiers.Cost> costs = HeartTiers.costFor(target);
            for (HeartTiers.Cost c : costs) {
                if (HeartTiers.count(player, c) < c.count) {
                    player.sendMessage(Breach.grimoire("chaosshard.heart.poor"));
                    info(player, heart);
                    return;
                }
            }
            for (HeartTiers.Cost c : costs) {
                HeartTiers.take(player, c);
            }
            player.inventoryContainer.detectAndSendChanges();
        }
        heart.setTier(target);
        World world = heart.getWorld();
        world.playSound(null, heart.getPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 0.6F);
        world.playSound(null, heart.getPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.3F, 1.6F);
        player.sendMessage(Breach.grimoire("chaosshard.heart.upgraded." + Math.min(target, 4), target));
        heart.grow(true);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        for (int i = 0; i < 3; i++) {
            world.spawnParticle(EnumParticleTypes.PORTAL,
                    pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 2, pos.getY() + rand.nextDouble() * 1.5,
                    pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 2,
                    (rand.nextDouble() - 0.5) * 0.5, -rand.nextDouble() * 0.5, (rand.nextDouble() - 0.5) * 0.5);
        }
    }
}
