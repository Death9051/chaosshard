package ru.death.chaosshard.act5;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/** Блоки Акта 5: барьер Света во дворце, врата Разлома, ядра пилонов Разлома. */
public final class Act5Blocks {

    /** Барьер Света: не ломается, пропускает свет, держится, пока горят маяки столицы. */
    public static class Barrier extends Block {
        public Barrier() {
            super(Material.GLASS);
            setSoundType(SoundType.GLASS);
            setBlockUnbreakable();
            setResistance(6000000F);
            setLightLevel(0.8F);
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return net.minecraft.init.Items.AIR;
        }

        @Override
        public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
            if (!world.isRemote) {
                player.sendStatusMessage(ru.death.chaosshard.breach.Breach.grimoire("chaosshard.crown.barrier", Crown.lit(world)), true);
            }
        }
    }

    /** Врата Разлома за троном: кто входит с Ключом Разлома — уходит к Архонту. */
    public static class Gate extends Block {
        static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

        public Gate() {
            super(Material.PORTAL);
            setBlockUnbreakable();
            setResistance(6000000F);
            setLightLevel(1.0F);
        }

        @Nullable
        @Override
        public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return NULL_AABB;
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return BlockFaceShape.UNDEFINED;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return net.minecraft.init.Items.AIR;
        }

        @Override
        public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity e) {
            if (!world.isRemote && e instanceof EntityPlayerMP && !e.isRiding() && e.timeUntilPortal <= 0) {
                e.timeUntilPortal = 60;
                Rift.enter((EntityPlayerMP) e);
            }
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL, pos.getX() + rand.nextDouble(), pos.getY() + rand.nextDouble(),
                        pos.getZ() + rand.nextDouble(), (rand.nextDouble() - 0.5) * 0.5, -rand.nextDouble(), (rand.nextDouble() - 0.5) * 0.5);
            }
        }
    }

    /** Ядро пилона в Разломе (метаданные — герой). Пока цело, его свет хранит Архонта. */
    public static class Pylon extends Block {
        public static final PropertyInteger HERO = PropertyInteger.create("hero", 0, 4);

        public Pylon() {
            super(Material.GLASS);
            setSoundType(SoundType.GLASS);
            setHardness(6.0F);
            setResistance(30F);
            setLightLevel(1.0F);
            setDefaultState(blockState.getBaseState().withProperty(HERO, 0));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, HERO);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(HERO, Math.max(0, Math.min(4, meta)));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(HERO);
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return net.minecraft.init.Items.AIR;
        }

        @Override
        public void breakBlock(World world, BlockPos pos, IBlockState state) {
            super.breakBlock(world, pos, state);
            if (!world.isRemote) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        80, 0.4, 0.4, 0.4, 0.3);
                Rift.pylonBroken((WorldServer) world, state.getValue(HERO));
            }
        }

        @Override
        public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
            return ItemStack.EMPTY;
        }
    }

    private Act5Blocks() {
    }
}
