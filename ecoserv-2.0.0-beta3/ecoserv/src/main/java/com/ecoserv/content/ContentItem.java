package com.ecoserv.content;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Слот своего предмета (2.0 beta3): своя текстура или 3D-модель, имя, описание, действие по ПКМ. */
public class ContentItem extends Item {

    public final int slot;

    public ContentItem(int slot) {
        this.slot = slot;
        setRegistryName(Ref.MODID, "citem_" + slot);
        setTranslationKey(Ref.MODID + ".citem");
        setCreativeTab(ModTabs.MAIN);
    }

    @Nullable
    ContentPack.ItemDef def() {
        return CustomContent.itemDef(slot);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        ContentPack.ItemDef d = def();
        return d == null ? "Пустой слот предмета " + slot : com.ecoserv.util.ChatUtil.color(d.name);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        ContentPack.ItemDef d = def();
        return d == null ? 64 : Math.max(1, Math.min(64, d.maxStack));
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        ContentPack.ItemDef d = def();
        return d != null && d.glow;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        ContentPack.ItemDef d = def();
        if (d != null) {
            for (String l : d.lore) {
                tooltip.add(com.ecoserv.util.ChatUtil.color("&7" + l));
            }
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack st = player.getHeldItem(hand);
        ContentPack.ItemDef d = def();
        if (d == null || d.steps.isEmpty()) {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, st);
        }
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            com.ecoserv.script.ScriptRunner.startSteps((EntityPlayerMP) player, "citem:" + slot, d.steps, "citem_" + slot);
            if (d.cooldown > 0) {
                player.getCooldownTracker().setCooldown(this, d.cooldown * 20);
            }
            if (d.consume && !player.isCreative()) {
                st.shrink(1);
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, st);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab) && def() != null) {
            items.add(new ItemStack(this));
        }
    }
}
