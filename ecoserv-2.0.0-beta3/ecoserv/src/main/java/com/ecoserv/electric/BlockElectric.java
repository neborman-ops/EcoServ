package com.ecoserv.electric;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Электро-блок (2.0): 16 видов по мете, свечение — из блока-сущности. */
public class BlockElectric extends Block {

    public static final String NAME = "electric";
    public static final PropertyInteger TYPE = PropertyInteger.create("type", 0, 15);
    public static final PropertyBool LIT = PropertyBool.create("lit");

    public BlockElectric() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(1.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(ModTabs.MAIN);
        setDefaultState(blockState.getBaseState().withProperty(TYPE, 0).withProperty(LIT, false));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileElectric();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TYPE, LIT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TYPE, meta & 15);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TYPE);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return state.withProperty(LIT, te instanceof TileElectric && ((TileElectric) te).lit);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (int i = 0; i < 16; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getBlock() != this) {
            return 0;
        }
        int t = state.getValue(TYPE);
        if (t != ElectricTypes.LAMP && t != ElectricTypes.ALARM) {
            return 0;
        }
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileElectric && ((TileElectric) te).lit ? (t == ElectricTypes.LAMP ? 15 : 9) : 0;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return state.getValue(TYPE) == ElectricTypes.RS_OUT;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (state.getValue(TYPE) != ElectricTypes.RS_OUT) {
            return 0;
        }
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileElectric && ((TileElectric) te).lit ? 15 : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return getWeakPower(state, world, pos, side);
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
        if (!(te instanceof TileElectric) || !(player instanceof EntityPlayerMP)) {
            return true;
        }
        TileElectric t = (TileElectric) te;
        ItemStack held = player.getHeldItemMainhand();
        if (ElectricFeature.isWire(held)) {
            return true; // провод обрабатывает событие
        }
        int type = state.getValue(TYPE);
        if (type == ElectricTypes.GENERATOR && !held.isEmpty()) {
            int add = fuelOf(held);
            if (add > 0) {
                t.fuel += add;
                held.shrink(1);
                t.changed();
                com.ecoserv.util.ChatUtil.actionBar(player, "&eТоплива: " + t.fuel / 20 + " сек");
                return true;
            }
        }
        if (type == ElectricTypes.SWITCH && !player.isSneaking()) {
            t.on = !t.on;
            t.changed();
            world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_LEVER_CLICK, net.minecraft.util.SoundCategory.BLOCKS,
                    0.5F, t.on ? 0.7F : 0.5F);
            com.ecoserv.util.ChatUtil.actionBar(player, t.on ? "&aВключено" : "&7Выключено");
            return true;
        }
        new ElectricScreen((EntityPlayerMP) player, pos.toImmutable()).open();
        return true;
    }

    static int fuelOf(ItemStack s) {
        if (s.getItem() == Items.COAL) {
            return 20 * 90;
        }
        if (s.getItem() == Item2.of(Blocks.COAL_BLOCK)) {
            return 20 * 900;
        }
        if (s.getItem() == Items.LAVA_BUCKET) {
            return 0;
        }
        if (s.getItem() == Item2.of(Blocks.LOG) || s.getItem() == Item2.of(Blocks.LOG2)) {
            return 20 * 30;
        }
        if (s.getItem() == Item2.of(Blocks.PLANKS)) {
            return 20 * 8;
        }
        if (s.getItem() == Items.BLAZE_ROD) {
            return 20 * 120;
        }
        return 0;
    }

    private static final class Item2 {
        static net.minecraft.item.Item of(Block b) {
            return net.minecraft.item.Item.getItemFromBlock(b);
        }
    }
}
