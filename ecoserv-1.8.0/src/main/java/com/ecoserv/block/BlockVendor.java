package com.ecoserv.block;

import com.ecoserv.EcoServ;
import com.ecoserv.Ref;
import com.ecoserv.gui.GuiHandler;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.util.ChatUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Блок «Склад продавца» — товар для аукциона. */
public class BlockVendor extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final String NAME = "vendor";

    public BlockVendor() {
        super(Material.WOOD);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(3.0F);
        setResistance(15.0F);
        setSoundType(SoundType.WOOD);
        setHarvestLevel("axe", 0);
        setCreativeTab(ModTabs.MAIN);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileVendor();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileVendor)) {
            return true;
        }
        TileVendor vendor = (TileVendor) te;
        if (!vendor.canAccess(player)) {
            ChatUtil.send(player, "&cСклад принадлежит игроку &f" + vendor.getOwnerName()
                    + "&c. Купить его товар можно на аукционе.");
            return true;
        }
        player.openGui(EcoServ.INSTANCE, GuiHandler.GUI_VENDOR, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) placer;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileVendor) {
            ((TileVendor) te).setOwner(player.getUniqueID(), player.getName());
            EnumFacing back = state.getValue(FACING).getOpposite();
            ChatUtil.send(player, "&aСклад установлен. Открыть можешь только ты. "
                    + "&7Автоматика подаёт предметы с задней стороны (" + side(back) + ").");
        }
    }

    private static String side(EnumFacing facing) {
        switch (facing) {
            case NORTH:
                return "север";
            case SOUTH:
                return "юг";
            case WEST:
                return "запад";
            case EAST:
                return "восток";
            default:
                return facing.getName();
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileVendor) {
            TileVendor vendor = (TileVendor) te;
            for (int i = 0; i < vendor.getInventory().getSlots(); i++) {
                ItemStack stack = vendor.getInventory().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    InventoryHelper.spawnItemStack(world, pos.getX() + 0.5D, pos.getY() + 0.5D,
                            pos.getZ() + 0.5D, stack);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    // --- состояние ----------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            facing = EnumFacing.NORTH;
        }
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState blockState, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileVendor)) {
            return 0;
        }
        TileVendor vendor = (TileVendor) te;
        int slots = vendor.getInventory().getSlots();
        int used = 0;
        for (int i = 0; i < slots; i++) {
            if (!vendor.getInventory().getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        return slots == 0 ? 0 : (int) Math.round(used * 15.0D / slots);
    }
}
