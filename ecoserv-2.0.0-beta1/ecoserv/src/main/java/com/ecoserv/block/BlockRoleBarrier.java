package com.ecoserv.block;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileRoleBarrier;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Барьер ролей (п. 13) — как барьер квестов, только пропускает игроков с нужной ролью.
 * Остальных (и мобов) не пускает. Админы проходят всегда. Настройка — ПКМ пустой рукой.
 */
public class BlockRoleBarrier extends Block {

    public static final String NAME = "role_barrier";

    public BlockRoleBarrier() {
        super(Material.BARRIER);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightOpacity(0);
        setSoundType(SoundType.GLASS);
        setCreativeTab(ModTabs.MAIN);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRoleBarrier();
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean isActualState) {
        if (entity instanceof EntityPlayer) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileRoleBarrier && ((TileRoleBarrier) te).canPass((EntityPlayer) entity)) {
                return;
            }
        }
        super.addCollisionBoxToList(state, world, pos, entityBox, collidingBoxes, entity, isActualState);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (hand == EnumHand.MAIN_HAND && player.getHeldItem(hand).isEmpty() && player instanceof EntityPlayerMP
                && Perms.isAdmin(player)) {
            new com.ecoserv.block.RoleBarrierScreen((EntityPlayerMP) player, pos).open();
            return true;
        }
        return false;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof EntityPlayer) {
            // копируем настройки соседнего барьера — стену можно строить подряд
            for (EnumFacing side : EnumFacing.values()) {
                TileEntity near = world.getTileEntity(pos.offset(side));
                TileEntity self = world.getTileEntity(pos);
                if (near instanceof TileRoleBarrier && self instanceof TileRoleBarrier) {
                    ((TileRoleBarrier) self).copyFrom((TileRoleBarrier) near);
                    return;
                }
            }
            ChatUtil.actionBar((EntityPlayer) placer, "&eБарьер ролей: ПКМ пустой рукой — выбрать роли");
        }
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
    public boolean causesSuffocation(IBlockState state) {
        return false;
    }

    @Override
    public boolean canEntitySpawn(IBlockState state, Entity entity) {
        return false;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
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

    /** Как у стекла: между соседними барьерами грани не рисуются. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (world.getBlockState(pos.offset(side)).getBlock() == this) {
            return false;
        }
        return super.shouldSideBeRendered(state, world, pos, side);
    }
}
