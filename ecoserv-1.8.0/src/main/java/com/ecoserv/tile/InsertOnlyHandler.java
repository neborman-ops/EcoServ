package com.ecoserv.tile;

import com.ecoserv.shop.ShopService;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * Обёртка для автоматики (воронки, трубы, конвейеры): внутрь класть можно, доставать — нет.
 * Можно ограничить диапазон слотов (например, не давать трогать слот-витрину)
 * и отфильтровать предметы, которых нет в прайсе.
 */
public class InsertOnlyHandler implements IItemHandler {

    private final IItemHandler wrapped;
    private final boolean filterUnsellable;
    private final int firstSlot;
    /** Какой прайс проверять (у подмиров со своими квестами — свой). */
    private final java.util.function.Supplier<com.ecoserv.shop.ShopList> shop;

    public InsertOnlyHandler(IItemHandler wrapped, boolean filterUnsellable) {
        this(wrapped, filterUnsellable, 0);
    }

    public InsertOnlyHandler(IItemHandler wrapped, boolean filterUnsellable, int firstSlot) {
        this(wrapped, filterUnsellable, firstSlot, com.ecoserv.shop.ShopList::get);
    }

    public InsertOnlyHandler(IItemHandler wrapped, boolean filterUnsellable, int firstSlot,
                             java.util.function.Supplier<com.ecoserv.shop.ShopList> shop) {
        this.wrapped = wrapped;
        this.filterUnsellable = filterUnsellable;
        this.firstSlot = Math.max(0, firstSlot);
        this.shop = shop;
    }

    @Override
    public int getSlots() {
        return wrapped.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return wrapped.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot < firstSlot) {
            return stack;
        }
        if (filterUnsellable && !ShopService.isSellable(stack, shop.get())) {
            return stack;
        }
        return wrapped.insertItem(slot, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return wrapped.getSlotLimit(slot);
    }
}
