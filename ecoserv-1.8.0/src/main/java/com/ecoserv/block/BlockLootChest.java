package com.ecoserv.block;

import com.ecoserv.Ref;
import com.ecoserv.content.LootStore;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileLootChest;
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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Сундук добычи (п. 14): у каждого игрока своя копия содержимого — один раз
 * или с пополнением по таймеру. Админ: Shift+ПКМ — заполнить образец и настроить.
 */
public class BlockLootChest extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final String NAME = "loot_chest";

    public BlockLootChest() {
        super(Material.WOOD);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setSoundType(SoundType.WOOD);
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
        return new TileLootChest();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileLootChest) || !(player instanceof EntityPlayerMP) || hand != EnumHand.MAIN_HAND) {
            return true;
        }
        TileLootChest chest = (TileLootChest) te;
        EntityPlayerMP p = (EntityPlayerMP) player;
        if (player.isSneaking() && Perms.isAdmin(player)) {
            new LootChestScreen(p, pos).open();
            return true;
        }
        if (chest.isTemplateEmpty()) {
            ChatUtil.actionBar(p, Perms.isAdmin(p) ? "&eСундук пуст: Shift+ПКМ — заполнить образец" : "&7Сундук пуст.");
            return true;
        }
        LootStore.open(p, chest);
        return true;
    }

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
}
