package com.ecoserv.restrict;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Проверка инвентаря по запретам текущего мира и работа буфера:
 * запрещённое к ношению уходит в буфер, разрешённое возвращается из буфера,
 * как только в инвентаре есть место.
 */
public final class RestrictionService {

    private static final Map<UUID, Long> LAST_WARN = new HashMap<UUID, Long>();
    private static final Map<UUID, Long> LAST_WAIT_NOTICE = new HashMap<UUID, Long>();

    private RestrictionService() {
    }

    /**
     * Полная проверка.
     *
     * @param arrived игрок только что вошёл на сервер или сменил мир — сообщить подробно
     */
    public static void scan(EntityPlayerMP player, boolean arrived) {
        // мёртвому игроку ничего не выдаём: инвентарь трупа при возрождении пропадёт
        if (player == null || !isAlive(player)) {
            return;
        }
        UUID id = player.getUniqueID();
        boolean bypass = ItemRules.bypass(player);
        int dim = player.dimension;
        boolean changed = false;

        Map<String, Integer> moved = new LinkedHashMap<String, Integer>();
        if (!bypass && ItemRules.hasCarryRules()) {
            changed |= stashFrom(player, player.inventory.mainInventory, dim, moved);
            changed |= stashFrom(player, player.inventory.armorInventory, dim, moved);
            changed |= stashFrom(player, player.inventory.offHandInventory, dim, moved);
        }

        Map<String, Integer> returned = new LinkedHashMap<String, Integer>();
        int waiting = 0;
        List<ItemStack> stash = Stash.peek(id);
        if (stash != null && !stash.isEmpty()) {
            Iterator<ItemStack> it = stash.iterator();
            while (it.hasNext()) {
                ItemStack stack = it.next();
                if (stack.isEmpty()) {
                    it.remove();
                    Stash.markDirty();
                    continue;
                }
                if (!bypass && ItemRules.denies(dim, stack, RestrictAction.CARRY)) {
                    continue;
                }
                String name = stack.getDisplayName();
                int inserted = insert(player.inventory, stack);
                if (inserted > 0) {
                    changed = true;
                    Stash.markDirty();
                    add(returned, name, inserted);
                }
                if (stack.isEmpty()) {
                    it.remove();
                } else {
                    waiting += stack.getCount();
                }
            }
        }

        if (changed) {
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            if (player.openContainer != player.inventoryContainer) {
                player.openContainer.detectAndSendChanges();
            }
        }

        if (!moved.isEmpty()) {
            ChatUtil.send(player, "&eВ этом мире нельзя носить: &f" + describe(moved)
                    + "&e. Предметы ушли в буфер и вернутся сами в мире, где они разрешены. &7(/stash)");
        }
        if (!returned.isEmpty()) {
            if (arrived) {
                ChatUtil.send(player, "&aИз буфера возвращено: &f" + describe(returned));
            } else {
                ChatUtil.actionBar(player, "&aИз буфера: &f" + describe(returned));
            }
        }
        if (waiting > 0) {
            long now = System.currentTimeMillis();
            Long last = LAST_WAIT_NOTICE.get(id);
            if (arrived || last == null || now - last > 120_000L) {
                LAST_WAIT_NOTICE.put(id, now);
                ChatUtil.send(player, "&eВ буфере ждут &f" + waiting
                        + " &eпредм. — освободи место в инвентаре, и они перенесутся сами. &7(/stash)");
            }
        }
    }

    /** Живой и в игре (isDead становится true только через секунду после смерти). */
    public static boolean isAlive(EntityPlayer player) {
        return player != null && player.isEntityAlive() && player.getHealth() > 0.0F;
    }

    private static boolean stashFrom(EntityPlayerMP player, NonNullList<ItemStack> list, int dim,
                                     Map<String, Integer> moved) {
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (stack.isEmpty() || !ItemRules.denies(dim, stack, RestrictAction.CARRY)) {
                continue;
            }
            Stash.add(player.getUniqueID(), stack.copy());
            add(moved, stack.getDisplayName(), stack.getCount());
            list.set(i, ItemStack.EMPTY);
            changed = true;
        }
        return changed;
    }

    /**
     * Положить в основной инвентарь сколько влезет (сначала в такие же стаки, потом в пустые слоты).
     * Не использует addItemStackToInventory: в креативе тот удаляет то, что не влезло.
     *
     * @return сколько штук положено; stack уменьшается на это количество
     */
    public static int insert(InventoryPlayer inventory, ItemStack stack) {
        int start = stack.getCount();
        NonNullList<ItemStack> main = inventory.mainInventory;
        int limit = inventory.getInventoryStackLimit();
        for (int i = 0; i < main.size() && !stack.isEmpty(); i++) {
            ItemStack existing = main.get(i);
            if (!existing.isEmpty() && ItemHandlerHelper.canItemStacksStack(existing, stack)) {
                int space = Math.min(existing.getMaxStackSize(), limit) - existing.getCount();
                if (space > 0) {
                    int n = Math.min(space, stack.getCount());
                    existing.grow(n);
                    stack.shrink(n);
                }
            }
        }
        for (int i = 0; i < main.size() && !stack.isEmpty(); i++) {
            if (main.get(i).isEmpty()) {
                int n = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), limit));
                ItemStack put = stack.copy();
                put.setCount(n);
                main.set(i, put);
                stack.shrink(n);
            }
        }
        return start - stack.getCount();
    }

    /** Убрать запрещённый результат из слотов крафта (каждый тик, пока открыт верстак). */
    public static void clearCraftResults(EntityPlayerMP player) {
        Container container = player.openContainer;
        if (container == null) {
            return;
        }
        boolean cleared = false;
        List<Slot> slots = container.inventorySlots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot instanceof SlotCrafting && slot.getHasStack()
                    && ItemRules.denies(player, slot.getStack(), RestrictAction.CRAFT)) {
                slot.putStack(ItemStack.EMPTY);
                cleared = true;
            }
        }
        if (cleared) {
            container.detectAndSendChanges();
            warn(player, EcoConfig.restrictMessageCraft);
        }
    }

    /** Предупреждение над хотбаром не чаще раза в 1,5 секунды. */
    public static void warn(EntityPlayer player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(player.getUniqueID());
        if (last != null && now - last < 1500L) {
            return;
        }
        LAST_WARN.put(player.getUniqueID(), now);
        ChatUtil.actionBar(player, message);
    }

    public static void forget(UUID player) {
        LAST_WARN.remove(player);
        LAST_WAIT_NOTICE.remove(player);
    }

    private static void add(Map<String, Integer> map, String name, int count) {
        Integer prev = map.get(name);
        map.put(name, prev == null ? count : prev + count);
    }

    private static String describe(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            if (i > 5) {
                sb.append("и ещё ").append(map.size() - 5);
                break;
            }
            sb.append(e.getKey()).append(" x").append(e.getValue());
        }
        return sb.toString();
    }
}
