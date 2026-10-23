package com.ecoserv.content;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Предмет своего блока: имя из набора. */
public class ContentItemBlock extends ItemBlock {

    private final ContentBlock block;

    public ContentItemBlock(ContentBlock block) {
        super(block);
        this.block = block;
        setRegistryName(block.getRegistryName());
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        ContentPack.BlockDef d = block.def();
        return d == null ? "Пустой слот блока " + block.slot : com.ecoserv.util.ChatUtil.color(d.name);
    }
}
