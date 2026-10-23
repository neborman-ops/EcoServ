package com.ecoserv.gui;

import com.ecoserv.tile.TileVending;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Контейнер блока продажи: витрина + 27 слотов склада + инвентарь игрока. */
public class ContainerVending extends Container {

    public static final int TOTAL = TileVending.SIZE;

    private final TileVending vending;

    public ContainerVending(EntityPlayer player, TileVending vending) {
        this.vending = vending;

        // витрина (что продаём)
        addSlotToContainer(new SlotItemHandler(vending.getInventory(), TileVending.SAMPLE, 8, 18));

        // склад
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new SlotItemHandler(vending.getInventory(),
                        TileVending.STOCK_START + row * 9 + col, 8 + col * 18, 62 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 156 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 216));
        }
    }

    public TileVending getVending() {
        return vending;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (vending.isInvalid()) {
            return false;
        }
        if (player.getDistanceSq(vending.getPos().getX() + 0.5D, vending.getPos().getY() + 0.5D,
                vending.getPos().getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return vending.canAccess(player);
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

        if (index < TOTAL) {
            if (!mergeItemStack(stack, TOTAL, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // из инвентаря — сразу в склад, минуя витрину
            if (!mergeItemStack(stack, 1, TOTAL, false)) {
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
