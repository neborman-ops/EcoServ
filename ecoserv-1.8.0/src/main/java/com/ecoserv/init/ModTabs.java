package com.ecoserv.init;

import com.ecoserv.Ref;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class ModTabs {

    /** Название вкладки берётся из ключа локализации itemGroup.ecoserv */
    public static final CreativeTabs MAIN = new CreativeTabs(Ref.MODID) {
        @Override
        public ItemStack createIcon() {
            return ModRegistry.MARKET == null
                    ? new ItemStack(Items.GOLD_INGOT)
                    : new ItemStack(ModRegistry.MARKET);
        }
    };

    private ModTabs() {
    }
}
