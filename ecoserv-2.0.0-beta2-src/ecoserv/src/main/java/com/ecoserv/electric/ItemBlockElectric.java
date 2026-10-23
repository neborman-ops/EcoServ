package com.ecoserv.electric;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Предмет электро-блока: 16 видов по мете. */
public class ItemBlockElectric extends ItemBlock {

    public ItemBlockElectric(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage & 15;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "§e⚡ §f" + ElectricTypes.TITLES[stack.getMetadata() & 15];
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int t = stack.getMetadata() & 15;
        tooltip.add("§7" + ElectricTypes.HINTS[t]);
        if (ElectricTypes.USE[t] > 0) {
            tooltip.add("§8Потребляет: " + ElectricTypes.USE[t] + " ед.");
        }
        tooltip.add("§8Соединяй проводом: ПКМ по источнику, потом по приёмнику.");
    }
}
