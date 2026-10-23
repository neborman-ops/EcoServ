package com.ecoserv.content;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/** Слот своего блока (2.0 beta3): что это за блок, решает набор контента. */
public class ContentBlock extends Block {

    private static final AxisAlignedBB CROSS = new AxisAlignedBB(0.15D, 0.0D, 0.15D, 0.85D, 0.85D, 0.85D);
    public final int slot;

    public ContentBlock(int slot) {
        super(Material.ROCK);
        this.slot = slot;
        setRegistryName(Ref.MODID, "cslot_" + slot);
        setTranslationKey(Ref.MODID + ".cslot");
        setHardness(1.5F);
        setCreativeTab(ModTabs.MAIN);
    }

    @Nullable
    ContentPack.BlockDef def() {
        return CustomContent.blockDef(slot);
    }

    private boolean solid() {
        ContentPack.BlockDef d = def();
        return d == null || ("cube".equals(d.shape) || "column".equals(d.shape)) && !d.cutout && !d.translucent;
    }

    @Override
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        ContentPack.BlockDef d = def();
        return d == null ? 1.5F : (float) d.hardness;
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, @Nullable Entity exploder, Explosion explosion) {
        ContentPack.BlockDef d = def();
        return d == null ? 6.0F : (float) d.resistance;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        ContentPack.BlockDef d = def();
        return d == null ? 0 : Math.max(0, Math.min(15, d.light));
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return solid() ? 255 : 0;
    }

    @Override
    public SoundType getSoundType(IBlockState state, World world, BlockPos pos, @Nullable Entity entity) {
        ContentPack.BlockDef d = def();
        switch (d == null ? "" : d.sound) {
            case "wood":
                return SoundType.WOOD;
            case "metal":
                return SoundType.METAL;
            case "glass":
                return SoundType.GLASS;
            case "grass":
                return SoundType.PLANT;
            case "sand":
                return SoundType.SAND;
            case "cloth":
                return SoundType.CLOTH;
            case "snow":
                return SoundType.SNOW;
            default:
                return SoundType.STONE;
        }
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return solid();
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        ContentPack.BlockDef d = def();
        return d == null || !"cross".equals(d.shape) && !"model".equals(d.shape);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        ContentPack.BlockDef d = def();
        if (d != null && (!d.collision || "cross".equals(d.shape))) {
            return NULL_AABB;
        }
        return FULL_BLOCK_AABB;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        ContentPack.BlockDef d = def();
        return d != null && "cross".equals(d.shape) ? CROSS : FULL_BLOCK_AABB;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        ContentPack.BlockDef d = def();
        return d != null && (!d.collision || "cross".equals(d.shape));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        ContentPack.BlockDef d = def();
        if (d == null) {
            return BlockRenderLayer.SOLID;
        }
        if (d.translucent) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        if (d.cutout || "cross".equals(d.shape) || "glass".equals(d.shape) || "model".equals(d.shape)) {
            return BlockRenderLayer.CUTOUT;
        }
        return BlockRenderLayer.SOLID;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        ContentPack.BlockDef d = def();
        if (d != null && ("glass".equals(d.shape) || d.translucent)) {
            IBlockState other = world.getBlockState(pos.offset(side));
            if (other.getBlock() == this) {
                return false;
            }
        }
        return super.shouldSideBeRendered(state, world, pos, side);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        ContentPack.BlockDef d = def();
        return d == null || d.dropsSelf ? Item.getItemFromBlock(this) : Items.AIR;
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        ContentPack.BlockDef d = def();
        if (d != null && !d.hidden) {
            items.add(new ItemStack(this));
        }
    }
}
