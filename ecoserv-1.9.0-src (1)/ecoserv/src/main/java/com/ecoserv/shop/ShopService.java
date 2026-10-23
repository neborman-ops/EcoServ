package com.ecoserv.shop;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.integration.FTBQuestsBridge;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Общая логика купли-продажи: используется блоком-скупщиком, GUI и командами. */
public final class ShopService {

    public enum BuyStatus {
        OK,
        UNKNOWN_ITEM,
        NOT_FOR_SALE,
        NO_MONEY,
        QUEST_LOCKED,
        DISABLED
    }

    public static class BuyResult {
        public BuyStatus status = BuyStatus.OK;
        public long price;
        public int count;
        public String itemName = "";
    }

    /** Оценка выручки для GUI: считаются только полные приёмы. */
    public static final class Preview {
        public long gross;
        public long net;
        public int batches;
        /** Чего не хватает до полного приёма: имя -> {есть, нужно}. */
        public final Map<String, int[]> waiting = new LinkedHashMap<String, int[]>();
    }

    private ShopService() {
    }

    /**
     * Продаёт содержимое инвентаря ПРИЁМАМИ и начисляет деньги владельцу.
     * <p>
     * Одинаковые предметы из всех слотов суммируются: 40 + 30 висмута при приёме 64
     * дают один приём (64 шт), 6 шт остаются лежать до следующей партии.
     * Деньги всегда ПРИБАВЛЯЮТСЯ к балансу (EconomyManager.give), даже если владелец офлайн.
     */
    public static SaleResult sell(IItemHandlerModifiable inv, @Nullable UUID owner) {
        return sell(inv, owner, ShopList.get());
    }

    /** Продажа по прайсу shop (у подмира со своими квестами — свой прайс). */
    public static SaleResult sell(IItemHandlerModifiable inv, @Nullable UUID owner, ShopList shop) {
        SaleResult result = new SaleResult();
        if (inv == null || owner == null) {
            return result;
        }
        EntityPlayerMP ownerEntity = PlayerUtil.online(owner);

        // 1) группируем слоты по позициям прайса
        Map<ShopEntry, List<Integer>> groups = new LinkedHashMap<ShopEntry, List<Integer>>();
        Map<ShopEntry, Integer> totals = new LinkedHashMap<ShopEntry, Integer>();
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ShopEntry entry = shop.find(stack);
            if (entry == null || !entry.canSell()) {
                if (EcoConfig.marketVoidUnsellable && entry == null) {
                    inv.setStackInSlot(slot, ItemStack.EMPTY);
                }
                continue;
            }
            List<Integer> slots = groups.get(entry);
            if (slots == null) {
                slots = new ArrayList<Integer>();
                groups.put(entry, slots);
                totals.put(entry, 0);
            }
            slots.add(slot);
            totals.put(entry, totals.get(entry) + stack.getCount());
        }

        // 2) по каждой позиции продаём столько полных приёмов, сколько есть
        double grossExact = 0.0D;
        for (Map.Entry<ShopEntry, List<Integer>> group : groups.entrySet()) {
            ShopEntry entry = group.getKey();
            int available = totals.get(entry);
            int batch = entry.batchSize();
            String name = entry.displayName();

            if (!entry.requiredQuest.isEmpty() && !FTBQuestsBridge.isCompleted(ownerEntity, entry.requiredQuest)) {
                result.questBlocked = true;
                continue;
            }

            int allowed = SalesTracker.remaining(owner, entry);
            int batches = Math.min(available, allowed) / batch;
            if (available / batch > batches) {
                result.limitReached = true;
            }
            int rest = available - batches * batch;
            if (rest > 0 && rest < batch) {
                result.addWaiting(name, rest, batch);
            }
            if (batches <= 0) {
                continue;
            }

            double pricePerBatch = entry.getEffectiveSellPrice();
            double sum = pricePerBatch * batches;
            if (sum <= 0.0D) {
                continue;
            }
            int items = batches * batch;
            removeItems(inv, group.getValue(), items);

            grossExact += sum;
            result.itemsSold += items;
            result.batches += batches;
            result.stacksSold++;
            Integer prev = result.byItem.get(name);
            result.byItem.put(name, prev == null ? items : prev + items);

            entry.onSold(items);
            SalesTracker.add(owner, entry.key, items);
        }

        if (result.itemsSold == 0) {
            return result;
        }

        long gross = (long) Math.floor(grossExact);
        long tax = (long) Math.floor(gross * Math.max(0.0D, EcoConfig.marketTaxPercent) / 100.0D);
        long net = Math.max(0L, gross - tax);

        result.gross = gross;
        result.tax = tax;
        result.net = net;

        if (net > 0L) {
            // ПРИБАВИТЬ к балансу, а не установить
            EconomyManager.give(owner, net);
            PlayerStats stats = StatsStore.get(owner);
            stats.totalEarned += net;
            stats.totalSold += result.itemsSold;
            StatsStore.markDirty();
        }
        return result;
    }

    /** Забрать amount штук из перечисленных слотов. */
    private static void removeItems(IItemHandlerModifiable inv, List<Integer> slots, int amount) {
        int left = amount;
        for (int i = 0; i < slots.size() && left > 0; i++) {
            int slot = slots.get(i);
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            if (take >= stack.getCount()) {
                inv.setStackInSlot(slot, ItemStack.EMPTY);
            } else {
                ItemStack rest = stack.copy();
                rest.shrink(take);
                inv.setStackInSlot(slot, rest);
            }
            left -= take;
        }
    }

    /** Покупка предмета игроком у сервера (только при allow_buy_from_gui=true). */
    public static BuyResult buy(EntityPlayerMP player, String key, int count) {
        BuyResult result = new BuyResult();
        if (!EcoConfig.allowBuyFromGui) {
            result.status = BuyStatus.DISABLED;
            return result;
        }
        if (player == null || count <= 0) {
            result.status = BuyStatus.UNKNOWN_ITEM;
            return result;
        }
        ShopEntry entry = ShopList.forPlayer(player).byKey(key);
        if (entry == null) {
            result.status = BuyStatus.UNKNOWN_ITEM;
            return result;
        }
        if (!entry.canBuy()) {
            result.status = BuyStatus.NOT_FOR_SALE;
            return result;
        }
        if (!entry.requiredQuest.isEmpty() && !FTBQuestsBridge.isCompleted(player, entry.requiredQuest)) {
            result.status = BuyStatus.QUEST_LOCKED;
            return result;
        }

        ItemStack sample = entry.displayStack();
        if (sample.isEmpty()) {
            result.status = BuyStatus.UNKNOWN_ITEM;
            return result;
        }
        int max = Math.min(count, sample.getMaxStackSize() * 4);
        long price = (long) Math.ceil(entry.getEffectiveBuyPrice() * max);
        if (!EconomyManager.take(player.getUniqueID(), price)) {
            result.status = BuyStatus.NO_MONEY;
            result.price = price;
            return result;
        }

        int left = max;
        while (left > 0) {
            int portion = Math.min(left, sample.getMaxStackSize());
            ItemStack give = entry.itemKey().toStack(portion);
            PlayerUtil.giveOrDrop(player, give);
            left -= portion;
        }

        PlayerStats stats = StatsStore.get(player.getUniqueID());
        stats.totalSpent += price;
        StatsStore.markDirty();

        result.price = price;
        result.count = max;
        result.itemName = entry.displayName();
        return result;
    }

    /**
     * Оценка выручки за набор стаков (для GUI терминала). Одинаковые предметы
     * суммируются, в зачёт идут только полные приёмы, налог — присланный сервером.
     */
    public static Preview preview(List<ItemStack> stacks, double taxPercent) {
        return preview(stacks, taxPercent, ShopList.get());
    }

    public static Preview preview(List<ItemStack> stacks, double taxPercent, ShopList shop) {
        Preview p = new Preview();
        Map<ShopEntry, Integer> totals = new LinkedHashMap<ShopEntry, Integer>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ShopEntry entry = shop.find(stack);
            if (entry == null || !entry.canSell()) {
                continue;
            }
            Integer prev = totals.get(entry);
            totals.put(entry, (prev == null ? 0 : prev) + stack.getCount());
        }
        double gross = 0.0D;
        for (Map.Entry<ShopEntry, Integer> e : totals.entrySet()) {
            ShopEntry entry = e.getKey();
            int batch = entry.batchSize();
            int batches = e.getValue() / batch;
            int rest = e.getValue() - batches * batch;
            gross += entry.getEffectiveSellPrice() * batches;
            p.batches += batches;
            if (rest > 0) {
                p.waiting.put(entry.displayName(), new int[]{rest, batch});
            }
        }
        p.gross = (long) Math.floor(gross);
        long tax = (long) Math.floor(p.gross * Math.max(0.0D, taxPercent) / 100.0D);
        p.net = Math.max(0L, p.gross - tax);
        return p;
    }

    /** Выручка за один стак (только полные приёмы, с налогом сервера). */
    public static long previewSell(ItemStack stack) {
        return preview(Collections.singletonList(stack), EcoConfig.marketTaxPercent).net;
    }

    public static boolean isSellable(ItemStack stack) {
        return isSellable(stack, ShopList.get());
    }

    public static boolean isSellable(ItemStack stack, ShopList shop) {
        ShopEntry entry = shop.find(stack);
        return entry != null && entry.canSell();
    }

    public static boolean isAdminContext(EntityPlayer player) {
        return com.ecoserv.util.Perms.isAdmin(player);
    }
}
