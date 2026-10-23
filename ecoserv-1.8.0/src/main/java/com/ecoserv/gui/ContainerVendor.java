package com.ecoserv.gui;

import com.ecoserv.tile.TileVendor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Контейнер склада продавца: 27 слотов + инвентарь игрока. */
public class ContainerVendor extends Container {

    public static final int VENDOR_SLOTS = TileVendor.SIZE;

    private final TileVendor vendor;

    public ContainerVendor(EntityPlayer player, TileVendor vendor) {
        this.vendor = vendor;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new SlotItemHandler(vendor.getInventory(), row * 9 + col,
                        8 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 160 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 220));
        }
    }

    public TileVendor getVendor() {
        return vendor;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (vendor.isInvalid()) {
            return false;
        }
        if (player.getDistanceSq(vendor.getPos().getX() + 0.5D, vendor.getPos().getY() + 0.5D,
                vendor.getPos().getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return vendor.canAccess(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return result;
        }
        ItemStack stack = slot.getStack();
        result = stack.copy();

        if (index < VENDOR_SLOTS) {
            if (!mergeItemStack(stack, VENDOR_SLOTS, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!mergeItemStack(stack, 0, VENDOR_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }
}
