package com.ecoserv.games;

import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;

/** Цвета для мини-игр 2.0: бетон по мете, русские названия и цвет текста. */
final class GameColors {

    /** Мета бетона, название, код цвета чата. */
    static final int[] META = {14, 1, 4, 5, 3, 11, 10, 6, 0, 15};
    static final String[] NAME = {"красный", "оранжевый", "жёлтый", "лаймовый", "голубой", "синий", "фиолетовый",
            "розовый", "белый", "чёрный"};
    static final String[] CODE = {"&c", "&6", "&e", "&a", "&b", "&9", "&5", "&d", "&f", "&8"};

    private GameColors() {
    }

    static IBlockState concrete(int i) {
        return Blocks.CONCRETE.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.byMetadata(META[i % META.length]));
    }

    static IBlockState wool(int i) {
        return Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.byMetadata(META[i % META.length]));
    }

    static net.minecraft.item.ItemStack concreteStack(int i) {
        return new net.minecraft.item.ItemStack(Blocks.CONCRETE, 1, META[i % META.length]);
    }

    static String title(int i) {
        return CODE[i % CODE.length] + NAME[i % NAME.length];
    }
}
