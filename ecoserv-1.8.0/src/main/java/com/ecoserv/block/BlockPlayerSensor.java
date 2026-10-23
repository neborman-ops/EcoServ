package com.ecoserv.block;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TilePlayerSensor;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
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

/** Датчик игрока (п. 14): редстоун-сигнал, когда рядом игрок. Настройка — ПКМ (владелец или админ). */
public class BlockPlayerSensor extends Block {

    public static final String NAME = "player_sensor";

    public BlockPlayerSensor() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(2.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setCreativeTab(ModTabs.MAIN);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TilePlayerSensor();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        TileEntity te = world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TilePlayerSensor && placer instanceof EntityPlayer) {
            ((TilePlayerSensor) te).setOwner(placer.getUniqueID());
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TilePlayerSensor) || hand != EnumHand.MAIN_HAND || !(player instanceof EntityPlayerMP)) {
            return true;
        }
        TilePlayerSensor sensor = (TilePlayerSensor) te;
        if (!player.getUniqueID().equals(sensor.getOwner()) && !Perms.isAdmin(player)) {
            ChatUtil.actionBar(player, "&7Датчик игрока: радиус " + sensor.getRadius() + ", сигнал " + sensor.getOutput());
            return true;
        }
        new SensorScreen((EntityPlayerMP) player, pos).open();
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TilePlayerSensor ? ((TilePlayerSensor) te).getOutput() : 0;
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        return true;
    }
}
