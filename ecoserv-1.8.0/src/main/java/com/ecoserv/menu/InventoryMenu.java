package com.ecoserv.menu;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IInventory;

import javax.annotation.Nullable;

/**
 * Окно-сундук, где все верхние слоты «живые» (привязаны к инвентарю): личная добыча,
 * ГЛИТЧ-сундук и т.п. При закрытии вызывается onClose (сохранить содержимое).
 */
public class InventoryMenu extends ChestMenu {

    @Nullable
    private final Runnable onClose;

    public InventoryMenu(EntityPlayerMP player, String title, IInventory inventory, @Nullable Runnable onClose) {
        super(player, title, Math.max(1, Math.min(6, (inventory.getSizeInventory() + 8) / 9)),
                content(inventory, inventory.getSizeInventory()));
        this.onClose = onClose;
    }

    @Override
    protected void buildButtons() {
        // кнопок нет — пустые слоты ряда (если инвентарь не кратен 9) остаются пустыми
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
    }

    @Override
    protected void onClosed() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
