package com.ecoserv.item;

import com.ecoserv.Ref;
import com.ecoserv.content.FillJobs;
import com.ecoserv.init.ModTabs;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Selection;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
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
 * Палочка заполнения (п. 20): ПКМ — точка 1, ПКМ — точка 2, Shift+ПКМ — заполнить пустоту
 * блоком из второй руки. Тратит в 2 раза больше блоков. Крафт сложный.
 */
public class ItemFillWand extends Item {

    public static final String TOOL = "fill";

    public ItemFillWand() {
        setRegistryName(Ref.MODID, "fill_wand");
        setTranslationKey(Ref.MODID + ".fill_wand");
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
        setFull3D();
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote || !(player instanceof EntityPlayerMP)) {
            return EnumActionResult.SUCCESS;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        if (p.isSneaking()) {
            startOrStop(p);
            return EnumActionResult.SUCCESS;
        }
        Selection.Step step = Selection.click(p.getUniqueID(), TOOL, p.dimension, pos);
        if (step == Selection.Step.FIRST) {
            ChatUtil.actionBar(p, "&eТочка 1: &f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        } else {
            Selection.Sel sel = Selection.get(p.getUniqueID(), TOOL);
            ChatUtil.send(p, "&eТочка 2. Область: &f" + (sel == null ? "?" : sel.describe())
                    + " &7— Shift+ПКМ, чтобы заполнить блоком из второй руки (расход ×2).");
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (hand == EnumHand.MAIN_HAND && !world.isRemote && player.isSneaking() && player instanceof EntityPlayerMP) {
            startOrStop((EntityPlayerMP) player);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    private static void startOrStop(EntityPlayerMP p) {
        if (FillJobs.running(p.getUniqueID())) {
            FillJobs.cancel(p.getUniqueID());
            ChatUtil.send(p, "&eЗаполнение остановлено.");
            return;
        }
        Selection.Sel sel = Selection.complete(p.getUniqueID(), TOOL, p.dimension);
        if (sel == null) {
            ChatUtil.send(p, "&cСначала выдели область: ПКМ по двум углам.");
            return;
        }
        FillJobs.start(p, sel);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§7ПКМ по блоку — точка 1, ПКМ ещё раз — точка 2.");
        tooltip.add("§7Shift+ПКМ — заполнить пустоту блоком из второй руки.");
        tooltip.add("§6Тратит по 2 блока за каждый поставленный.");
        tooltip.add("§8Shift+ПКМ во время работы — остановить.");
    }
}
