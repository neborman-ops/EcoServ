package com.ecoserv.item;

import com.ecoserv.Ref;
import com.ecoserv.block.GlitchSelectScreen;
import com.ecoserv.init.ModTabs;
import com.ecoserv.tile.TileGlitchChest;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Палка GLITCH SELECT (п. 17, только админам, не крафтится): ПКМ по ГЛИТЧ-сундуку — выбрать, какие
 * виды пропавших предметов в него попадают (снесённые блоки, упавшее в бездну и другие).
 */
public class ItemGlitchSelect extends Item {

    public static final String NAME = "glitch_select";

    public ItemGlitchSelect() {
        setRegistryName(Ref.MODID, NAME);
        setTranslationKey(Ref.MODID + "." + NAME);
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
        setFull3D();
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            return world.getTileEntity(pos) instanceof TileGlitchChest ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileGlitchChest) || !(player instanceof EntityPlayerMP)) {
            return EnumActionResult.PASS;
        }
        if (!Perms.isAdmin(player)) {
            ChatUtil.actionBar(player, "&cGLITCH SELECT — только для админов");
            return EnumActionResult.SUCCESS;
        }
        new GlitchSelectScreen((EntityPlayerMP) player, pos.toImmutable()).open();
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§dПКМ по ГЛИТЧ-сундуку — что в него попадает");
        tooltip.add("§7Только для админов");
    }
}
