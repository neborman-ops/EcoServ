package com.ecoserv.restrict;

import com.ecoserv.config.EcoConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;

/**
 * Слот результата крафта, из которого нельзя забрать запрещённый в этом мире предмет.
 * Container.slotClick проверяет canTakeStack при обычном клике, Shift-клике, выбросе и обмене
 * с хотбаром, поэтому обойти запрет быстрыми кликами нельзя. Всё остальное — как у ванильного слота.
 */
public class GuardedSlotCrafting extends SlotCrafting {

    public GuardedSlotCrafting(EntityPlayer player, InventoryCrafting matrix, IInventory result,
                               int index, int x, int y) {
        super(player, matrix, result, index, x, y);
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        if (player != null && !player.world.isRemote && ItemRules.denies(player, getStack(), RestrictAction.CRAFT)) {
            RestrictionService.warn(player, EcoConfig.restrictMessageCraft);
            return false;
        }
        return super.canTakeStack(player);
    }

    /**
     * Заменить ванильный слот результата у верстака и у крафта 2x2 в инвентаре (один раз на контейнер).
     * Слот 0 — результат, слот 1 — первая клетка сетки крафта (у обоих ванильных контейнеров).
     */
    public static void guard(Container container, EntityPlayer player) {
        if (!(container instanceof ContainerWorkbench) && !(container instanceof ContainerPlayer)) {
            return;
        }
        if (container.inventorySlots.size() < 2) {
            return;
        }
        Slot old = container.inventorySlots.get(0);
        // только ванильный слот: особые слоты модов не трогаем
        if (old.getClass() != SlotCrafting.class) {
            return;
        }
        IInventory grid = container.inventorySlots.get(1).inventory;
        if (!(grid instanceof InventoryCrafting)) {
            return;
        }
        GuardedSlotCrafting slot = new GuardedSlotCrafting(player, (InventoryCrafting) grid, old.inventory,
                old.getSlotIndex(), old.xPos, old.yPos);
        slot.slotNumber = old.slotNumber;
        container.inventorySlots.set(0, slot);
    }
}
