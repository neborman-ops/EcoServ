package com.ecoserv.item;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.zone.Zone;
import com.ecoserv.zone.ZoneManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Палочка разрешения (1.8.0): пока она в руке (любой), владелец может ЛОМАТЬ блоки в зонах BLOCK DESTROY
 * (и BLOCK DESTROY DANGER) — в выживании, в приключении и в мирах «строят только админы». Ставить блоки она не
 * разрешает. Можно привязать к одной зоне: админ — Shift+ПКМ, стоя в зоне (Shift+ПКМ вне зон — снова «все зоны»).
 * Ломать можно чем угодно: палочку держат во второй руке, кирку — в основной.
 */
public class ItemPermitWand extends Item {

    private static final String TAG_ZONE = "zone";

    public ItemPermitWand() {
        setRegistryName(Ref.MODID, "permit_wand");
        setTranslationKey(Ref.MODID + ".permit_wand");
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
        setFull3D();
    }

    /** Зона, к которой привязана палочка (-1 — все зоны). */
    public static int zoneOf(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_ZONE) ? tag.getInteger(TAG_ZONE) : -1;
    }

    /** Палочка в руке, которая разрешает ломать в этой зоне. */
    public static boolean permits(@Nullable EntityPlayer player, @Nullable Zone zone) {
        if (player == null || zone == null) {
            return false;
        }
        for (ItemStack s : new ItemStack[]{player.getHeldItemMainhand(), player.getHeldItemOffhand()}) {
            if (s.getItem() instanceof ItemPermitWand) {
                int z = zoneOf(s);
                if (z < 0 || z == zone.id) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean holds(@Nullable EntityPlayer player) {
        return player != null && (player.getHeldItemMainhand().getItem() instanceof ItemPermitWand
                || player.getHeldItemOffhand().getItem() instanceof ItemPermitWand);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking()) {
            if (!Perms.isAdmin(player)) {
                ChatUtil.actionBar(player, "&cПривязывать палочку к зоне может только админ");
                return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
            }
            Zone zone = ZoneManager.at(player.dimension, player.getPosition());
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            if (zone == null) {
                tag.removeTag(TAG_ZONE);
                ChatUtil.actionBar(player, "&aПалочка разрешения: &fвсе зоны BLOCK DESTROY");
            } else {
                tag.setInteger(TAG_ZONE, zone.id);
                tag.setString("zoneName", zone.describe());
                ChatUtil.actionBar(player, "&aПалочка разрешения: &fтолько зона #" + zone.id);
            }
            stack.setTagCompound(tag);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int zone = zoneOf(stack);
        tooltip.add(zone < 0 ? "§aРазрешает ломать во всех зонах BLOCK DESTROY" : "§aРазрешает ломать в зоне #" + zone);
        if (zone >= 0 && stack.getTagCompound() != null) {
            tooltip.add("§8" + stack.getTagCompound().getString("zoneName"));
        }
        tooltip.add("§7Держи в любой руке — работает и в приключении.");
        tooltip.add("§7Ставить блоки в зоне не разрешает.");
        tooltip.add("§8Админ: Shift+ПКМ в зоне — привязать к ней, вне зон — ко всем.");
    }
}
