package ru.death.chaosshard.death;

import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ru.death.chaosshard.breach.Breach;

import java.util.Random;

/**
 * Могила Осколка: встаёт на месте гибели и хранит всё, что выпало. Не ломается ни киркой, ни взрывом, не сдвигается
 * поршнем. ПКМ хозяина — вещи возвращаются (броня сразу надевается). Если могилу всё же снесло (постройку перезаписало),
 * вещи падают на землю и не исчезают со временем.
 */
public class BlockGrave extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.1875, 0, 0.0625, 0.8125, 0.78, 0.9375);

    public BlockGrave() {
        super(Material.ROCK);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setSoundType(SoundType.STONE);
        setLightLevel(0.25F);
        setDefaultState(blockState.getBaseState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BlockHorizontal.FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockHorizontal.FACING).getHorizontalIndex();
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileGrave();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return true;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileGrave)) {
            world.setBlockToAir(pos);
            return true;
        }
        TileGrave g = (TileGrave) te;
        boolean mine = g.owner() == null || g.owner().equals(player.getUniqueID());
        if (!mine && !player.capabilities.isCreativeMode && !(player instanceof EntityPlayerMP
                && ((EntityPlayerMP) player).server.getPlayerList().canSendCommands(player.getGameProfile()))) {
            player.sendStatusMessage(Breach.grimoire("chaosshard.grave.not_yours", g.ownerName()), true);
            return true;
        }
        int n = g.giveTo(player);
        open(world, pos);
        player.sendStatusMessage(Breach.grimoire("chaosshard.grave.opened", n), true);
        return true;
    }

    /** Могила опустела: убрать с дымком и звуком. */
    static void open(World world, BlockPos pos) {
        world.setBlockToAir(pos);
        world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.0F, 0.7F);
        world.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.BLOCKS, 0.4F, 1.6F);
        if (world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // снесло не хозяином (постройку перезаписало, команда) — вещи на землю, и пусть лежат
        TileEntity te = world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileGrave && !((TileGrave) te).isEmpty()) {
            for (ItemStack s : ((TileGrave) te).takeAll()) {
                while (!s.isEmpty()) {
                    ItemStack part = s.splitStack(Math.max(1, Math.min(s.getCount(), s.getMaxStackSize())));
                    EntityItem ei = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, part);
                    ei.setNoDespawn();
                    ei.lifespan = Integer.MAX_VALUE;
                    world.spawnEntity(ei);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public void onBlockExploded(World world, BlockPos pos, Explosion explosion) {
        // взрывы могилу не трогают
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return false;
    }

    @Override
    public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player, World world, BlockPos pos) {
        return 0F;
    }

    @Override
    public boolean canCreatureSpawn(IBlockState state, IBlockAccess world, BlockPos pos, EntityLiving.SpawnPlacementType type) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
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
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        // слабый огонёк над камнем — могилу видно издалека ночью
        if (rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.END_ROD, pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.3, pos.getY() + 1.0,
                    pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        }
    }
}
