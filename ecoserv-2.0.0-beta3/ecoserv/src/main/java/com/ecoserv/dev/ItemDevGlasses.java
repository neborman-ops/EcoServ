package com.ecoserv.dev;

import com.ecoserv.Ref;
import com.ecoserv.init.ModTabs;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Очки разработчика (2.0): надеваются на голову — видно маркеры, зоны (красные/синие), провода, программы. */
public class ItemDevGlasses extends Item {

    public ItemDevGlasses() {
        setRegistryName(Ref.MODID, "dev_glasses");
        setTranslationKey(Ref.MODID + ".dev_glasses");
        setMaxStackSize(1);
        setCreativeTab(ModTabs.MAIN);
    }

    @Nullable
    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EntityEquipmentSlot.HEAD;
    }

    @Override
    public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot armorType, Entity entity) {
        return armorType == EntityEquipmentSlot.HEAD;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String n = super.getItemStackDisplayName(stack);
        return n == null || n.endsWith(".name") ? "Очки разработчика" : n;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§7Надень на голову: видно маркеры, зоны,");
        tooltip.add("§7блоки-программы и провода электроники.");
        tooltip.add("§cКрасные §7— глобальные, §9синие §7— локальные.");
    }
}
