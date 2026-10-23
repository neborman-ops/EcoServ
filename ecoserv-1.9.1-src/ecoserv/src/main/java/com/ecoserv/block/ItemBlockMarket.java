package com.ecoserv.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Предмет блока с запасным названием: если по какой-то причине языковой файл
 * не подхватился, в инвентаре не будет технической строки tile.ecoserv.market.name.
 */
public class ItemBlockMarket extends ItemBlock {

    public ItemBlockMarket(Block block) {
        super(block);
        setRegistryName(block.getRegistryName());
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        if (name == null || name.isEmpty() || name.startsWith("tile.") || name.endsWith(".name")) {
            return "Торговый терминал";
        }
        return name;
    }
}
