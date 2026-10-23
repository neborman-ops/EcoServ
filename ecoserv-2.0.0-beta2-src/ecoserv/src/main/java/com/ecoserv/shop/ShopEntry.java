package com.ecoserv.shop;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ItemKey;
import net.minecraft.item.ItemStack;

/**
 * Позиция прайс-листа. Редактируется админами в GUI и лежит в config/ecoserv/shop.json.
 * <p>
 * Цена задаётся «приёмами»: {@link #batch} штук сдаются за один раз и за это
 * платится {@link #sellPrice}. Пример: приём 64 висмута, цена 58000 —
 * 64 висмута = 58000 монет, 128 = 116000, а 40 штук ждут, пока наберётся 64.
 * Старые прайсы без поля batch читаются как «приём 1 шт», то есть работают как раньше.
 */
public class ShopEntry {

    /** Сериализованный ItemKey: "minecraft:coal@0". */
    public String key = "minecraft:air@-1";

    /** «Приём»: сколько штук забирается за одну продажу. */
    public int batch = 1;

    /** «Цена»: сколько денег платится за один приём (за batch штук). 0 = не принимается. */
    public double sellPrice;

    /** Цена покупки у сервера за 1 шт (устаревшее, используется только при allow_buy_from_gui). */
    public double buyPrice;

    public boolean enabled = true;

    /** Категория для группировки в GUI. */
    public String category = "";

    /** Id квеста FTB Quests, без выполнения которого позиция недоступна. */
    public String requiredQuest = "";

    /** Лимит продажи на игрока в сутки, в штуках (0 = без лимита). */
    public int dailySellLimit;

    /** Участвует ли позиция в динамическом ценообразовании. */
    public boolean dynamic = true;

    /** Сколько всего продано штук (для динамических цен). */
    public long soldTotal;

    /** Время последнего пересчёта восстановления цены. */
    public long lastRecovery;

    private transient ItemKey cachedKey;
    private transient ItemStack cachedStack;

    public ShopEntry() {
    }

    /** Позиция «1 шт = sellPrice» (совместимость со старым API). */
    public ShopEntry(String key, double sellPrice, double buyPrice) {
        this.key = key;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
    }

    /** Позиция «batch шт = price». */
    public static ShopEntry batch(String key, int batch, double price) {
        ShopEntry e = new ShopEntry(key, price, 0.0D);
        e.batch = Math.max(1, batch);
        return e;
    }

    public ItemKey itemKey() {
        if (cachedKey == null) {
            cachedKey = ItemKey.parse(key);
        }
        return cachedKey;
    }

    public void invalidate() {
        cachedKey = null;
        cachedStack = null;
    }

    public ItemStack displayStack() {
        if (cachedStack == null) {
            cachedStack = itemKey().toStack(1);
        }
        return cachedStack;
    }

    public String displayName() {
        ItemStack stack = displayStack();
        if (stack.isEmpty()) {
            return key;
        }
        return stack.getDisplayName();
    }

    /** Размер приёма, минимум 1 (защита от 0 и отрицательных значений в json). */
    public int batchSize() {
        return batch < 1 ? 1 : batch;
    }

    public boolean canSell() {
        return enabled && sellPrice > 0.0D;
    }

    public boolean canBuy() {
        return enabled && buyPrice > 0.0D;
    }

    /** Цена за ОДИН ПРИЁМ с учётом множителя и динамики рынка. */
    public double getEffectiveSellPrice() {
        double multiplier = EcoConfig.priceMultiplier <= 0.0D ? 1.0D : EcoConfig.priceMultiplier;
        if (!dynamic || !EcoConfig.dynamicPricingEnabled || sellPrice <= 0.0D) {
            return sellPrice * multiplier;
        }
        applyRecovery();
        double decay = Math.max(0.0D, EcoConfig.dynamicPriceDecayPerItem);
        double factor = 1.0D - soldTotal * decay;
        double min = Math.max(0.0D, Math.min(1.0D, EcoConfig.dynamicPriceMinFactor));
        if (factor < min) {
            factor = min;
        }
        return sellPrice * factor * multiplier;
    }

    /** Сколько в среднем выходит за 1 штуку (только для подсказок). */
    public double getUnitPrice() {
        return getEffectiveSellPrice() / batchSize();
    }

    /** Цена покупки растёт вместе с дефицитом — обратная сторона динамики. */
    public double getEffectiveBuyPrice() {
        if (!dynamic || !EcoConfig.dynamicPricingEnabled || buyPrice <= 0.0D || sellPrice <= 0.0D) {
            return buyPrice;
        }
        double ratio = sellPrice <= 0.0D ? 1.0D : getEffectiveSellPrice() / sellPrice;
        double factor = 2.0D - ratio;
        if (factor < 1.0D) {
            factor = 1.0D;
        }
        if (factor > 2.0D) {
            factor = 2.0D;
        }
        return buyPrice * factor;
    }

    /** @param items сколько штук продано */
    public void onSold(int items) {
        if (dynamic && EcoConfig.dynamicPricingEnabled) {
            applyRecovery();
            soldTotal += items;
        }
    }

    private void applyRecovery() {
        long now = System.currentTimeMillis();
        if (lastRecovery == 0L) {
            lastRecovery = now;
            return;
        }
        double hours = (now - lastRecovery) / 3_600_000.0D;
        if (hours <= 0.0D) {
            return;
        }
        double decay = Math.max(1.0E-9D, EcoConfig.dynamicPriceDecayPerItem);
        double recoveredItems = hours * Math.max(0.0D, EcoConfig.dynamicPriceRecoveryPerHour) / decay;
        if (recoveredItems >= 1.0D) {
            soldTotal = Math.max(0L, soldTotal - (long) recoveredItems);
            lastRecovery = now;
        }
    }

    public ShopEntry copy() {
        ShopEntry e = new ShopEntry(key, sellPrice, buyPrice);
        e.batch = batch;
        e.enabled = enabled;
        e.category = category;
        e.requiredQuest = requiredQuest;
        e.dailySellLimit = dailySellLimit;
        e.dynamic = dynamic;
        e.soldTotal = soldTotal;
        e.lastRecovery = lastRecovery;
        return e;
    }
}
