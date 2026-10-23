package com.ecoserv.dev;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Предмет маркера: 16 видов по мете. */
public class ItemBlockMarker extends ItemBlock {

    public ItemBlockMarker(Block block) {
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
        return "§bМаркер: §f" + BlockMarker.TITLES[stack.getMetadata() & 15];
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("§7" + BlockMarker.HINTS[stack.getMetadata() & 15]);
        tooltip.add("§8Невидим для игроков. Видно в /dev или в очках разработчика.");
        tooltip.add("§8ПКМ админа по маркеру — имя и данные.");
    }
}
