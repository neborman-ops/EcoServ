package com.ecoserv.gui;

import com.ecoserv.tile.TileMarket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Контейнер блока-скупщика: 18 слотов приёма + инвентарь игрока. */
public class ContainerMarket extends Container {

    public static final int MARKET_SLOTS = TileMarket.SIZE;

    private final TileMarket market;

    public ContainerMarket(EntityPlayer player, TileMarket market) {
        this.market = market;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new SlotItemHandler(market.getInventory(), row * 9 + col,
                        8 + col * 18, 20 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 138 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 198));
        }
    }

    public TileMarket getMarket() {
        return market;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (market.isInvalid()) {
            return false;
        }
        if (player.getDistanceSq(market.getPos().getX() + 0.5D, market.getPos().getY() + 0.5D,
                market.getPos().getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return market.canAccess(player);
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

        if (index < MARKET_SLOTS) {
            if (!mergeItemStack(stack, MARKET_SLOTS, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!mergeItemStack(stack, 0, MARKET_SLOTS, false)) {
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
