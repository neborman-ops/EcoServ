package com.ecoserv.block;

import com.ecoserv.EcoServ;
import com.ecoserv.Ref;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.gui.GuiHandler;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileMarket;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
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

/** Блок «Торговый терминал». */
public class BlockMarket extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final String NAME = "market";

    public BlockMarket() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(3.5F);
        setResistance(20.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
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
        return new TileMarket();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMarket)) {
            return true;
        }
        TileMarket market = (TileMarket) te;
        if (!market.canAccess(player)) {
            if (market.getOwnerName().isEmpty()) {
                ChatUtil.send(player, "&cУ терминала нет владельца. Админ может назначить: &f/ecoadmin setowner <ник>"
                        + " &7(смотря на блок)");
            } else {
                ChatUtil.send(player, "&cЭтот терминал принадлежит игроку &f" + market.getOwnerName());
            }
            return true;
        }
        player.openGui(EcoServ.INSTANCE, GuiHandler.GUI_MARKET, world, pos.getX(), pos.getY(), pos.getZ());
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
        if (te instanceof TileMarket) {
            TileMarket market = (TileMarket) te;
            market.setOwner(player.getUniqueID(), player.getName());
            if (stack.hasDisplayName()) {
                // имя из наковальни ни на что не влияет, но пусть будет в логах
                EcoServ.LOG.debug("Терминал {} установлен игроком {}", stack.getDisplayName(), player.getName());
            }
            ChatUtil.send(player, "&aТерминал установлен. Владелец: &f" + player.getName()
                    + "&a. Налог сервера: &f" + EcoConfig.marketTaxPercent + "%");
            if (!Perms.has(player, Perms.MARKET_PLACE)) {
                ChatUtil.send(player, "&eУ тебя нет права " + Perms.MARKET_PLACE + " — терминал не будет продавать.");
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileMarket) {
            TileMarket market = (TileMarket) te;
            for (int i = 0; i < market.getInventory().getSlots(); i++) {
                ItemStack stack = market.getInventory().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    InventoryHelper.spawnItemStack(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    // --- состояние/поворот --------------------------------------------------

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

    /** Редстоун-сигнал = заполненность терминала, удобно для автоматики. */
    @Override
    public int getComparatorInputOverride(IBlockState blockState, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMarket)) {
            return 0;
        }
        TileMarket market = (TileMarket) te;
        int slots = market.getInventory().getSlots();
        int used = 0;
        for (int i = 0; i < slots; i++) {
            if (!market.getInventory().getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        return slots == 0 ? 0 : (int) Math.round(used * 15.0D / slots);
    }
}
