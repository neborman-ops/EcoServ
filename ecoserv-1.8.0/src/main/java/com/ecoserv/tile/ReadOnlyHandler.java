package com.ecoserv.tile;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * Инвентарь «только посмотреть»: отдаётся модам, которые спрашивают
 * капабилити без указания стороны (сканеры, подсказки, беспроводные сети).
 * Через него нельзя ни положить, ни забрать — приватный блок остаётся приватным.
 */
public class ReadOnlyHandler implements IItemHandler {

    private final IItemHandler wrapped;

    public ReadOnlyHandler(IItemHandler wrapped) {
        this.wrapped = wrapped;
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
        return stack;
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
