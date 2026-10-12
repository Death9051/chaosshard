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
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/** Блоки обломка: Печать (несокрушимая дверь), Руна и Разрыв-выход. */
public final class TrialBlocks {

    /** Барьер обломка: светящееся поле в проходе. Прозрачное на вид, непроходимое и несокрушимое. */
    public static class Seal extends Block {
        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }

        /** Стыки соседних полей не рисуются: стена выглядит одним полотном. */
        @SideOnly(Side.CLIENT)
        @Override
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
        }

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

    /** Разрыв: щель в пространстве в два блока высотой. Шагнуть в неё (или ПКМ) — вернуться туда, откуда пришёл. */
    public static class Exit extends Block {
        public static final PropertyBool TOP = PropertyBool.create("top");
        private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.2, 0, 0.375, 0.8, 1, 0.625);

        public Exit() {
            super(Material.PORTAL);
            setBlockUnbreakable();
            setResistance(6000000F);
            setLightLevel(1.0F);
            setDefaultState(blockState.getBaseState().withProperty(TOP, false));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, TOP);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(TOP, meta == 1);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(TOP) ? 1 : 0;
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            return SHAPE;
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return NULL_AABB;
        }

        @Override
        public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return net.minecraft.block.state.BlockFaceShape.UNDEFINED;
        }

        private static void leave(World world, BlockPos pos, EntityPlayer player) {
            if (world.isRemote || !(player instanceof EntityPlayerMP) || world.getMinecraftServer() == null) {
                return;
            }
            EntityPlayerMP p = (EntityPlayerMP) player;
            if (p.getEntityData().getLong("chaosshard_rift_used") > world.getTotalWorldTime() - 40) {
                return;
            }
            p.getEntityData().setLong("chaosshard_rift_used", world.getTotalWorldTime());
            BlockPos base = world.getBlockState(pos).getValue(TOP) ? pos.down() : pos;
            // не посреди движения игрока — на следующем шаге сервера
            world.getMinecraftServer().addScheduledTask(() -> TrialManager.finish(p, world, base));
        }

        @Override
        public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                        EnumFacing facing, float hitX, float hitY, float hitZ) {
            if (hand == EnumHand.MAIN_HAND) {
                leave(world, pos, player);
            }
            return true;
        }

        @Override
        public void onEntityCollision(World world, BlockPos pos, IBlockState state, net.minecraft.entity.Entity entity) {
            if (entity instanceof EntityPlayer) {
                leave(world, pos, (EntityPlayer) entity);
            }
        }

        @Override
        public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
            for (int i = 0; i < 2; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.6, pos.getY() + rand.nextDouble(),
                        pos.getZ() + 0.5, (rand.nextDouble() - 0.5) * 0.6, 0.1, (rand.nextDouble() - 0.5) * 0.6);
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
