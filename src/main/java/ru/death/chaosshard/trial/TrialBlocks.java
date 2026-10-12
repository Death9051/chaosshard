package ru.death.chaosshard.trial;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/** Блоки обломка: Печать (несокрушимая дверь), Руна и Разрыв-выход. */
public final class TrialBlocks {

    /** Несокрушимый камень обломка. */
    public static class Seal extends Block {
        public Seal() {
            super(Material.BARRIER);
            setBlockUnbreakable();
            setResistance(6000000F);
            setSoundType(SoundType.STONE);
            setLightLevel(0.4F);
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Items.AIR;
        }

        @Override
        public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
            if (rand.nextInt(4) == 0) {
                world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + rand.nextDouble(), pos.getY() + rand.nextDouble(),
                        pos.getZ() + rand.nextDouble(), 0, 0, 0);
            }
        }
    }

    /** Руна: ПКМ — зажечь. Четыре за 20 секунд — проход открыт. */
    public static class Rune extends Block {
        public static final PropertyBool LIT = PropertyBool.create("lit");

        public Rune() {
            super(Material.ROCK);
            setBlockUnbreakable();
            setResistance(6000000F);
            setSoundType(SoundType.STONE);
            setDefaultState(blockState.getBaseState().withProperty(LIT, false));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, LIT);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(LIT, meta == 1);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(LIT) ? 1 : 0;
        }

        @Override
        public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
            return state.getValue(LIT) ? 12 : 3;
        }

        @Override
        public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                        EnumFacing facing, float hitX, float hitY, float hitZ) {
            if (hand != EnumHand.MAIN_HAND) {
                return false;
            }
            if (!world.isRemote) {
                world.setBlockState(pos, state.withProperty(LIT, true), 3);
                world.scheduleUpdate(pos, this, 400);
                TrialManager.onRune(world, pos, player);
            }
            return true;
        }

        @Override
        public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
            // руна гаснет через 20 секунд, если проход ещё закрыт
            TrialData.Trial t = TrialData.get(world).at(pos);
            if (state.getValue(LIT) && (t == null || t.stage == 2)) {
                world.setBlockState(pos, state.withProperty(LIT, false), 3);
            }
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Items.AIR;
        }
    }

    /** Разрыв: ПКМ — вернуться туда, откуда пришёл. */
    public static class Exit extends Block {
        public Exit() {
            super(Material.PORTAL);
            setBlockUnbreakable();
            setResistance(6000000F);
            setLightLevel(1.0F);
        }

        @Override
        public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                        EnumFacing facing, float hitX, float hitY, float hitZ) {
            if (!world.isRemote && hand == EnumHand.MAIN_HAND && player instanceof EntityPlayerMP) {
                TrialManager.finish((EntityPlayerMP) player, world, pos);
            }
            return true;
        }

        @Override
        public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + rand.nextDouble(), pos.getY() + rand.nextDouble() * 2,
                        pos.getZ() + rand.nextDouble(), (rand.nextDouble() - 0.5) * 0.5, 0.2, (rand.nextDouble() - 0.5) * 0.5);
            }
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Items.AIR;
        }
    }

    private TrialBlocks() {
    }
}
