package com.ecoserv.dev;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.util.Perms;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Блок-программа (2.0). Shift+ПКМ админа — настройка, ПКМ игрока — срабатывание (если триггер «нажатие»). */
public class BlockLogic extends Block {

    public static final String NAME = "logic_block";

    public BlockLogic() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(3.0F);
        setResistance(6000000.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(ModTabs.MAIN);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileLogic();
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
        if (!(te instanceof TileLogic)) {
            return true;
        }
        TileLogic t = (TileLogic) te;
        if (Perms.isAdmin(player) && (player.isSneaking() || !"click".equals(t.trigger))) {
            new LogicScreen((EntityPlayerMP) player, pos.toImmutable()).open();
            return true;
        }
        if ("click".equals(t.trigger) && t.enabled) {
            t.fire((EntityPlayerMP) player, "click");
        }
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileLogic ? ((TileLogic) te).output() : 0;
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        return true;
    }

    @Override
    public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player, World world, BlockPos pos) {
        return Perms.isAdmin(player) ? super.getPlayerRelativeBlockHardness(state, player, world, pos) : 0.0F;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }
}
