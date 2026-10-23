package com.ecoserv.block;

import com.ecoserv.EcoServ;
import com.ecoserv.Ref;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.gui.GuiHandler;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileVending;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Блок продажи (торговый автомат).
 * Ломается вместе с содержимым и кассой — как шалкер.
 */
public class BlockVending extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final String NAME = "vending";

    public BlockVending() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(3.5F);
        setResistance(30.0F);
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
        return new TileVending();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (player.isSneaking()) {
            return false;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileVending) || !(player instanceof EntityPlayerMP)) {
            return true;
        }
        TileVending vending = (TileVending) te;

        // хозяин настраивает, остальные покупают
        if (vending.canAccess(player)) {
            player.openGui(EcoServ.INSTANCE, GuiHandler.GUI_VENDING, world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        buy((EntityPlayerMP) player, vending, world, pos);
        return true;
    }

    /** Покупка в один клик + чек над хотбаром. */
    private void buy(EntityPlayerMP player, TileVending vending, World world, BlockPos pos) {
        TileVending.BuyResult result = vending.buy(player);
        String message;
        switch (result.status) {
            case OK:
                message = "§a✔ §fОплата прошла §7| §c-" + Fmt.money(result.price)
                        + " §7| осталось: §e" + Fmt.money(result.balance)
                        + " §7| получено: §f" + result.itemName + " x" + result.amount;
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        SoundCategory.BLOCKS, 0.6F, 1.4F);
                break;
            case NO_MONEY:
                message = "§c✖ Не хватает денег: нужно §f" + Fmt.money(result.price)
                        + "§c, у тебя §f" + Fmt.money(result.balance);
                break;
            case OUT_OF_STOCK:
                message = "§cТовар закончился";
                break;
            case NOT_CONFIGURED:
                message = "§eАвтомат ещё не настроен владельцем";
                break;
            case DISABLED:
                message = "§cБлоки продажи отключены на сервере";
                break;
            default:
                message = "§cПокупка не прошла";
                break;
        }
        player.sendStatusMessage(new TextComponentString(message), true);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) placer;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileVending)) {
            return;
        }
        TileVending vending = (TileVending) te;
        // владельцем всегда становится тот, кто поставил — содержимое и касса переезжают вместе с блоком
        vending.setOwner(player.getUniqueID(), player.getName());
        ChatUtil.send(player, "&aАвтомат установлен. Открыть можешь только ты, остальные покупают кликом."
                + " &7Вход для автоматики — грань с красной точкой.");
    }

    // --- поведение шалкера: содержимое остаётся в блоке --------------------

    private ItemStack makeDrop(TileVending vending) {
        ItemStack drop = new ItemStack(this);
        NBTTagCompound tag = vending.writeItemTag();
        drop.setTagInfo("BlockEntityTag", tag);
        return drop;
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player,
                                   boolean willHarvest) {
        if (willHarvest) {
            return true;
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
                             @Nullable TileEntity te, ItemStack tool) {
        if (te instanceof TileVending && !world.isRemote) {
            TileVending vending = (TileVending) te;
            vending.setHarvested(true);
            if (!player.capabilities.isCreativeMode) {
                spawnAsEntity(world, pos, makeDrop(vending));
            }
            player.addStat(StatList.getBlockStats(this));
            player.addExhaustion(0.005F);
        } else {
            super.harvestBlock(world, player, pos, state, te, tool);
        }
        world.setBlockToAir(pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileVending && !world.isRemote) {
            TileVending vending = (TileVending) te;
            if (!vending.isHarvested()) {
                // взрыв, поршень, команда — товар всё равно не теряется
                spawnAsEntity(world, pos, makeDrop(vending));
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state,
                         int fortune) {
        // предмет выдаётся вручную в harvestBlock/breakBlock вместе с NBT
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileVending) {
            return makeDrop((TileVending) te);
        }
        return new ItemStack(this);
    }

    /** Чужой не должен уносить автомат вместе с товаром. */
    public static boolean canBreak(World world, BlockPos pos, EntityPlayer player) {
        if (!EcoConfig.vendingProtectFromOthers) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileVending)) {
            return true;
        }
        return ((TileVending) te).canBreak(player);
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

    /** Сигнал компаратора = остаток товара на складе автомата. */
    @Override
    public int getComparatorInputOverride(IBlockState blockState, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileVending)) {
            return 0;
        }
        TileVending vending = (TileVending) te;
        int stock = vending.countStock();
        if (stock <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(15, stock / Math.max(1, vending.getAmount())));
    }
}
