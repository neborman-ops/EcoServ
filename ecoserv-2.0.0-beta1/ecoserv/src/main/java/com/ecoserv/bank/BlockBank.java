package com.ecoserv.bank;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.ui.PromptScreen;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Банкомат (2.0). ПКМ — окно банка: каждый видит ТОЛЬКО свои счета и свой кошелёк, поэтому чужой банкомат
 * безопасен (раньше у некоторых блоков мода чужие игроки могли попасть внутрь). Shift+ПКМ владельца — подпись.
 * Не ломается чужими, взрывами и мобами.
 */
public class BlockBank extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final String NAME = "atm";

    public BlockBank() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(4.0F);
        setResistance(6000000.0F);
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
        return new TileBank();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) placer;
        if (!BankManager.settings().playerAtms && !Perms.isAdmin(p)) {
            world.destroyBlock(pos, true);
            ChatUtil.send(p, "&cБанкоматы ставят только админы.");
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileBank) {
            ((TileBank) te).setOwner(p);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (hand != EnumHand.MAIN_HAND || !(player instanceof EntityPlayerMP)) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileBank)) {
            return true;
        }
        final TileBank bank = (TileBank) te;
        final BlockPos at = pos.toImmutable();
        if (player.isSneaking() && (bank.isOwner(player) || Perms.isAdmin(player))) {
            new PromptScreen((EntityPlayerMP) player, "Банкомат", "&7Подпись видна в заголовке окна.", "Подпись",
                    bank.getLabel(), "Банкомат", new ItemStack(this), 32, (who, text) -> {
                        TileEntity now = who.world.getTileEntity(at);
                        if (now instanceof TileBank) {
                            ((TileBank) now).setLabel(text);
                        }
                    }, null).open();
            return true;
        }
        BankScreen.open((EntityPlayerMP) player, at);
        return true;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileBank && !((TileBank) te).isOwner(player) && !Perms.isAdmin(player)
                && ((TileBank) te).ownerId() != null) {
            return 0.0F; // чужой банкомат не сломать
        }
        return super.getPlayerRelativeBlockHardness(state, player, world, pos);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing f = EnumFacing.byHorizontalIndex(meta & 3);
        return getDefaultState().withProperty(FACING, f);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }
}
