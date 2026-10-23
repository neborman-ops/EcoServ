package com.ecoserv.trade;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

/**
 * Окно обмена. Слева — своё предложение (можно менять), справа — чужое (только смотреть).
 * Оба игрока видят одни и те же предметы: контейнеры смотрят на общие серверные слоты.
 */
public class ContainerTrade extends Container {

    public static final int OFFER_SLOTS = TradeSession.SLOTS;
    private static final int OWN_START = 0;
    private static final int OTHER_START = OFFER_SLOTS;
    private static final int PLAYER_START = OFFER_SLOTS * 2;

    private final TradeSession session;
    private final EntityPlayer player;
    private long lastSyncedChange = -1L;

    public ContainerTrade(EntityPlayer player, TradeSession session) {
        this.player = player;
        this.session = session;

        IItemHandler own = session.ownOffer(player.getUniqueID());
        IItemHandler other = session.otherOffer(player.getUniqueID());

        // своё предложение: 4 столбца x 3 ряда
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(new SlotItemHandler(own, row * 4 + col, 8 + col * 18, 22 + row * 18));
            }
        }
        // чужое предложение
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                addSlotToContainer(new ReadOnlySlot(other, row * 4 + col, 98 + col * 18, 22 + row * 18));
            }
        }
        // инвентарь игрока
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 198));
        }
    }

    public TradeSession getSession() {
        return session;
    }

    private static class ReadOnlySlot extends SlotItemHandler {
        ReadOnlySlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return session != null && !session.finished;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (player.world != null && !player.world.isRemote && session.lastChange != lastSyncedChange) {
            lastSyncedChange = session.lastChange;
            TradeManager.syncState(session);
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        if (playerIn.world != null && !playerIn.world.isRemote && !session.finished) {
            TradeManager.abort(session, playerIn.getName() + " закрыл окно");
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return result;
        }
        ItemStack stack = slot.getStack();
        result = stack.copy();

        if (index < OTHER_START) {
            // из своего предложения в инвентарь
            if (!mergeItemStack(stack, PLAYER_START, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_START) {
            // чужое предложение трогать нельзя
            return ItemStack.EMPTY;
        } else {
            // из инвентаря в своё предложение
            if (!mergeItemStack(stack, OWN_START, OTHER_START, false)) {
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
