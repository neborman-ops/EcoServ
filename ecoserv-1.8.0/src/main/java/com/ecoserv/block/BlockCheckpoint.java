package com.ecoserv.block;

import com.ecoserv.Ref;
import com.ecoserv.content.Checkpoints;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileCheckpoint;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** Чекпоинт (п. 14): плита — наступил, и здесь твоя точка возрождения в этом мире. */
public class BlockCheckpoint extends Block {

    public static final String NAME = "checkpoint";
    private static final AxisAlignedBB PLATE = new AxisAlignedBB(0.0625D, 0.0D, 0.0625D, 0.9375D, 0.0625D, 0.9375D);

    public BlockCheckpoint() {
        super(Material.IRON);
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setSoundType(SoundType.METAL);
        setLightLevel(0.5F);
        setCreativeTab(ModTabs.MAIN);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileCheckpoint();
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityPlayerMP) {
            TileEntity te = world.getTileEntity(pos);
            Checkpoints.touch((EntityPlayerMP) entity, pos, te instanceof TileCheckpoint ? ((TileCheckpoint) te).getName() : "");
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (hand != EnumHand.MAIN_HAND || !player.getHeldItem(hand).isEmpty() || !(player instanceof EntityPlayerMP)
                || !Perms.isAdmin(player)) {
            return false;
        }
        final BlockPos at = pos.toImmutable();
        TileEntity current = world.getTileEntity(at);
        String name = current instanceof TileCheckpoint ? ((TileCheckpoint) current).getName() : "";
        new com.ecoserv.ui.PromptScreen((EntityPlayerMP) player, "Контрольная точка",
                "&7Название показывается игроку, когда он встаёт на точку (& — цвета).", "Название", name,
                "Старт паркура", new net.minecraft.item.ItemStack(this), 64, (who, text) -> {
                    TileEntity te = who.world.getTileEntity(at);
                    if (!(te instanceof TileCheckpoint)) {
                        throw new com.ecoserv.ui.UiError("Точки здесь больше нет.");
                    }
                    ((TileCheckpoint) te).setName(text);
                    ChatUtil.send(who, "&aЧекпоинт: &f" + text);
                }, null).open();
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return PLATE;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
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
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
}
