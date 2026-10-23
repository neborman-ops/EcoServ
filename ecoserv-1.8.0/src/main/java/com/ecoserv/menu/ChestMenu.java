package com.ecoserv.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;

/**
 * Серверное меню, которое клиент рисует как обычный сундук.
 * <p>
 * Клиенту не нужен никакой код мода: сервер отправляет ванильный пакет открытия
 * окна и сам обрабатывает клики. Часть верхних слотов может быть «живой»
 * (инвентарь блока или предложение в обмене), остальные — кнопки, которые
 * нельзя забрать.
 */
public abstract class ChestMenu extends Container {

    /** Свой id окна: ванильные выдаются 1..100. */
    private static final int WINDOW_ID = 101;

    /** Поставщик нестандартных слотов верхней части окна. */
    public interface SlotProvider {
        @Nullable
        Slot create(int index, int x, int y);
    }

    protected final EntityPlayerMP player;
    protected final int rows;
    protected final int topSize;
    protected final InventoryBasic buttons;
    protected final String title;

    private final int[] buttonIndex;
    private boolean closing;

    protected ChestMenu(EntityPlayerMP player, String title, int rows, @Nullable SlotProvider provider) {
        this.player = player;
        this.title = title;
        this.rows = Math.max(1, Math.min(6, rows));
        this.topSize = this.rows * 9;
        this.buttonIndex = new int[topSize];

        Slot[] custom = new Slot[topSize];
        int buttonCount = 0;
        for (int i = 0; i < topSize; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 18 + (i / 9) * 18;
            Slot slot = provider == null ? null : provider.create(i, x, y);
            custom[i] = slot;
            if (slot == null) {
                buttonIndex[i] = buttonCount++;
            } else {
                buttonIndex[i] = -1;
            }
        }

        this.buttons = new InventoryBasic("menu", false, Math.max(1, buttonCount));

        for (int i = 0; i < topSize; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 18 + (i / 9) * 18;
            if (custom[i] != null) {
                addSlotToContainer(custom[i]);
            } else {
                addSlotToContainer(new ButtonSlot(buttons, buttonIndex[i], x, y));
            }
        }

        int invY = 32 + this.rows * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, invY + 58));
        }
    }

    /** Простое меню без «живых» слотов. */
    protected ChestMenu(EntityPlayerMP player, String title, int rows) {
        this(player, title, rows, null);
    }

    /** Провайдер для окна с инвентарём блока в первых слотах. */
    public static SlotProvider content(final IInventory inventory, final int count) {
        return new SlotProvider() {
            @Override
            public Slot create(int index, int x, int y) {
                return index < count ? new Slot(inventory, index, x, y) : null;
            }
        };
    }

    /** Слот-кнопка: не даётся в руки и не принимает предметы. */
    public static class ButtonSlot extends Slot {
        public ButtonSlot(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }

    /** Слот «только смотреть» поверх чужого инвентаря. */
    public static class ViewSlot extends Slot {
        public ViewSlot(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }

    public void open() {
        player.closeScreen();
        player.connection.sendPacket(new SPacketOpenWindow(WINDOW_ID, "minecraft:container",
                new TextComponentString(title), topSize));
        this.windowId = WINDOW_ID;
        player.openContainer = this;
        addListener(player);
        refresh();
    }

    /** Перерисовать кнопки и отправить окно игроку. */
    public void refresh() {
        buildButtons();
        detectAndSendChanges();
        player.connection.sendPacket(new SPacketSetSlot(-1, -1, ItemStack.EMPTY));
    }

    protected void setButton(int slot, ItemStack stack) {
        if (slot < 0 || slot >= topSize) {
            return;
        }
        int index = buttonIndex[slot];
        if (index >= 0 && index < buttons.getSizeInventory()) {
            buttons.setInventorySlotContents(index, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    protected void fillRow(int row, ItemStack stack) {
        for (int i = 0; i < 9; i++) {
            setButton(row * 9 + i, stack.copy());
        }
    }

    protected boolean isButton(int slot) {
        return slot >= 0 && slot < topSize && buttonIndex[slot] >= 0;
    }

    /** Заполнение кнопок; вызывается при каждом обновлении окна. */
    protected abstract void buildButtons();

    /** Нажатие на слот-кнопку (slot — позиция в окне). */
    protected abstract void onButton(int slot, ClickType type, int dragType);

    /** Сколько «взведённая» кнопка двойного подтверждения ждёт второго нажатия. */
    protected static final long ARM_MS = 10000L;
    /** Второе нажатие раньше этого — часть двойного щелчка, а не подтверждение. */
    protected static final long CONFIRM_DELAY_MS = 300L;

    /** Взвести кнопку подтверждения: вернуть момент, до которого ждём второго нажатия. */
    protected static long arm() {
        return System.currentTimeMillis() + ARM_MS;
    }

    /** Кнопка взведена (для подсказки на ней). */
    protected static boolean armed(long until) {
        return System.currentTimeMillis() < until;
    }

    /** Второе нажатие засчитывается: кнопка взведена, и не только что (двойной щелчок не подтверждает). */
    protected static boolean confirmed(long until) {
        long now = System.currentTimeMillis();
        return now < until && now >= until - ARM_MS + CONFIRM_DELAY_MS;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer clicker) {
        if (slotId >= 0 && slotId < topSize && isButton(slotId)) {
            if (clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.THROW) {
                // третий пакет двойного щелчка, протаскивание предмета по окну, Q над кнопкой — не нажатия
                return ItemStack.EMPTY;
            }
            onButton(slotId, clickType, dragType);
            if (player.openContainer == this) {
                refresh();
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, clicker);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer clicker, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        if (index < topSize && isButton(index)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();

        if (index < topSize) {
            if (!mergeItemStack(stack, topSize, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!mergeIntoTop(stack)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }

    /** Куда класть предметы по shift-клику из инвентаря игрока. */
    protected boolean mergeIntoTop(ItemStack stack) {
        int end = 0;
        while (end < topSize && !isButton(end)) {
            end++;
        }
        return end > 0 && mergeItemStack(stack, 0, end, false);
    }

    @Override
    public boolean canInteractWith(EntityPlayer clicker) {
        return !closing;
    }

    @Override
    public void onContainerClosed(EntityPlayer clicker) {
        closing = true;
        super.onContainerClosed(clicker);
        onClosed();
    }

    protected void onClosed() {
    }

    public EntityPlayerMP getPlayer() {
        return player;
    }
}
