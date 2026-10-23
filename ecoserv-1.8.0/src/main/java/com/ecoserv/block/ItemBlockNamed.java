package com.ecoserv.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Предмет блока с запасным названием: если языковой файл не подхватился,
 * в инвентаре не будет технической строки tile.ecoserv.*.name.
 */
public class ItemBlockNamed extends ItemBlock {

    private final String fallbackName;

    public ItemBlockNamed(Block block, String fallbackName) {
        super(block);
        this.fallbackName = fallbackName;
        setRegistryName(block.getRegistryName());
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        if (name == null || name.isEmpty() || name.startsWith("tile.") || name.endsWith(".name")) {
            return fallbackName;
        }
        return name;
    }
}
