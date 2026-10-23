package com.ecoserv.shop;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Прайс-лист сервера. Файл: config/ecoserv/shop.json
 * Один и тот же список используется блоком-скупщиком, GUI магазина и API.
 * <p>
 * У подмиров со своими квестами (п. 6) — свой прайс: config/ecoserv/shops/&lt;подмир&gt;.json,
 * он действует в измерениях подмира (см. forDim / forPlayer).
 */
public final class ShopList {

    private static final Type LIST_TYPE = new TypeToken<ArrayList<ShopEntry>>() {
    }.getType();

    private static ShopList instance = new ShopList("");
    /** Свои прайсы подмиров (сервер). */
    private static final Map<String, ShopList> SCOPES = new HashMap<String, ShopList>();
    /** Какой прайс действует в измерении: id подмира со своим прайсом или null (общий). */
    private static java.util.function.IntFunction<String> scopeResolver = dim -> null;

    /** "" — общий прайс, иначе id подмира. */
    private final String scope;

    private ShopList(String scope) {
        this.scope = scope;
    }

    private final List<ShopEntry> entries = new ArrayList<ShopEntry>();
    private final Map<String, ShopEntry> index = new HashMap<String, ShopEntry>();
    private final List<ShopEntry> nbtEntries = new ArrayList<ShopEntry>();
    private int revision;

    /** Общий прайс сервера (на клиенте — присланный сервером прайс для текущего мира). */
    public static ShopList get() {
        return instance;
    }

    /** Подмиры подключают свой определитель прайса по измерению. */
    public static void setScopeResolver(java.util.function.IntFunction<String> resolver) {
        scopeResolver = resolver == null ? dim -> null : resolver;
    }

    /** Прайс, действующий в измерении (сервер). */
    public static ShopList forDim(int dim) {
        String id = scopeResolver.apply(dim);
        return id == null || id.isEmpty() ? instance : scope(id);
    }

    public static ShopList forPlayer(@Nullable net.minecraft.entity.player.EntityPlayer player) {
        return player == null ? instance : forDim(player.dimension);
    }

    /** Свой прайс подмира (загружается при первом обращении). */
    public static synchronized ShopList scope(String id) {
        ShopList list = SCOPES.get(id);
        if (list == null) {
            list = new ShopList(id);
            list.load();
            SCOPES.put(id, list);
        }
        return list;
    }

    /** Перечитать свои прайсы подмиров с диска при следующем обращении. */
    public static synchronized void reloadScopes() {
        SCOPES.clear();
    }

    /**
     * Подмир удалён: забыть его прайс и убрать файл (иначе прайс вернулся бы с подмиром того же имени).
     * Файл не стирается, а переименовывается в &lt;подмир&gt;.json.deleted-&lt;время&gt; — его можно вернуть.
     */
    public static synchronized void dropScope(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        SCOPES.remove(id);
        File f = new ShopList(id).file();
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        if (tmp.isFile() && !tmp.delete()) {
            EcoServ.LOG.warn("Не удалось удалить {}", tmp);
        }
        if (f.exists()) {
            File kept = new File(f.getAbsolutePath() + ".deleted-" + System.currentTimeMillis());
            if (f.renameTo(kept)) {
                EcoServ.LOG.info("Прайс удалённого подмира {} сохранён как {}", id, kept.getName());
            } else if (!f.delete()) {
                EcoServ.LOG.warn("Не удалось убрать прайс удалённого подмира: {}", f);
            }
        }
    }

    /** Сохранить загруженные прайсы подмиров (в них тоже копится «продано», от которого зависит цена). */
    public static synchronized void saveScopes() {
        for (ShopList list : SCOPES.values()) {
            list.save();
        }
    }

    /** "" — общий прайс, иначе id подмира. */
    public String scopeName() {
        return scope;
    }

    public boolean isGlobal() {
        return scope.isEmpty();
    }

    /** Скопировать позиции из другого прайса (кнопка «взять общий прайс»). */
    public void copyFrom(ShopList other) {
        entries.clear();
        for (ShopEntry e : other.entries) {
            entries.add(JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(e), ShopEntry.class));
        }
        reindex();
    }

    public List<ShopEntry> entries() {
        return entries;
    }

    public int revision() {
        return revision;
    }

    public int size() {
        return entries.size();
    }

    /** Поиск позиции для конкретного стака: сначала точное совпадение, затем meta=* . */
    @Nullable
    public ShopEntry find(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (int i = 0; i < nbtEntries.size(); i++) {
            ShopEntry e = nbtEntries.get(i);
            if (e.itemKey().matches(stack)) {
                return e;
            }
        }
        String id = stack.getItem().getRegistryName() == null ? "" : stack.getItem().getRegistryName().toString();
        ShopEntry exact = index.get(id + "@" + stack.getItemDamage());
        if (exact != null) {
            return exact;
        }
        return index.get(id + "@" + ItemKey.ANY_META);
    }

    @Nullable
    public ShopEntry byKey(String key) {
        if (key == null) {
            return null;
        }
        ShopEntry e = index.get(key);
        if (e != null) {
            return e;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key.equals(key)) {
                return entries.get(i);
            }
        }
        return null;
    }

    public ShopEntry put(ItemStack stack, double sellPrice, double buyPrice) {
        ItemKey key = ItemKey.of(stack, false, true);
        return put(key.serialize(), sellPrice, buyPrice);
    }

    public ShopEntry put(String key, double sellPrice, double buyPrice) {
        ShopEntry existing = byKey(key);
        if (existing != null) {
            // старый вариант API: цена за 1 штуку
            existing.batch = 1;
            existing.sellPrice = sellPrice;
            existing.buyPrice = buyPrice;
            existing.enabled = true;
            reindex();
            return existing;
        }
        ShopEntry entry = new ShopEntry(key, sellPrice, buyPrice);
        entries.add(entry);
        reindex();
        return entry;
    }

    /** Добавить/обновить позицию «batch шт = price» для предмета из руки. */
    public ShopEntry putBatch(ItemStack stack, int batch, double price) {
        String key = ItemKey.of(stack, false, true).serialize();
        ShopEntry existing = byKey(key);
        if (existing != null) {
            existing.batch = Math.max(1, batch);
            existing.sellPrice = Math.max(0.0D, price);
            existing.enabled = true;
            reindex();
            return existing;
        }
        ShopEntry entry = ShopEntry.batch(key, batch, Math.max(0.0D, price));
        entries.add(entry);
        reindex();
        return entry;
    }

    public void addOrReplace(ShopEntry entry) {
        if (entry == null || entry.key == null) {
            return;
        }
        entry.invalidate();
        ShopEntry existing = byKey(entry.key);
        if (existing != null) {
            int idx = entries.indexOf(existing);
            // сохраняем рыночную статистику при редактировании цены
            entry.soldTotal = existing.soldTotal;
            entry.lastRecovery = existing.lastRecovery;
            entries.set(idx, entry);
        } else {
            entries.add(entry);
        }
        reindex();
    }

    public boolean remove(String key) {
        ShopEntry e = byKey(key);
        if (e == null) {
            return false;
        }
        entries.remove(e);
        reindex();
        return true;
    }

    public void clear() {
        entries.clear();
        reindex();
    }

    public Set<String> categories() {
        Set<String> set = new LinkedHashSet<String>();
        for (int i = 0; i < entries.size(); i++) {
            String c = entries.get(i).category;
            if (c != null && !c.isEmpty()) {
                set.add(c);
            }
        }
        return set;
    }

    public void sortByName() {
        Collections.sort(entries, new Comparator<ShopEntry>() {
            @Override
            public int compare(ShopEntry a, ShopEntry b) {
                int c = a.category.compareToIgnoreCase(b.category);
                return c != 0 ? c : a.displayName().compareToIgnoreCase(b.displayName());
            }
        });
        reindex();
    }

    private void reindex() {
        index.clear();
        nbtEntries.clear();
        for (int i = 0; i < entries.size(); i++) {
            ShopEntry e = entries.get(i);
            e.invalidate();
            ItemKey k = e.itemKey();
            if (k.nbt != null) {
                nbtEntries.add(e);
            } else {
                index.put(k.id + "@" + k.meta, e);
            }
        }
        revision++;
    }

    // --- хранение ----------------------------------------------------------

    private File file() {
        if (scope.isEmpty()) {
            return new File(EcoConfig.getConfigDir(), "shop.json");
        }
        return new File(new File(EcoConfig.getConfigDir(), "shops"), scope + ".json");
    }

    public void load() {
        entries.clear();
        ArrayList<ShopEntry> loaded = JsonUtil.readType(file(), LIST_TYPE, new ArrayList<ShopEntry>());
        if (loaded.isEmpty() && scope.isEmpty() && !file().exists()) {
            loaded = defaults();
            entries.addAll(loaded);
            reindex();
            save();
        } else {
            entries.addAll(loaded);
            reindex();
        }
        if (scope.isEmpty()) {
            EcoServ.LOG.info("Прайс-лист: {} позиций", entries.size());
        }
    }

    public void save() {
        JsonUtil.write(file(), entries);
    }

    public String toJson() {
        return JsonUtil.GSON.toJson(entries);
    }

    /** Применить присланный сервером список (клиент). */
    public void fromJson(String json) {
        entries.clear();
        try {
            ArrayList<ShopEntry> parsed = JsonUtil.GSON.fromJson(json, LIST_TYPE);
            if (parsed != null) {
                entries.addAll(parsed);
            }
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось разобрать присланный прайс-лист", e);
        }
        reindex();
    }

    /**
     * Стартовый прайс, чтобы сервер сразу работал из коробки.
     * Формат: сколько штук за один приём -> сколько денег за этот приём.
     */
    private static ArrayList<ShopEntry> defaults() {
        ArrayList<ShopEntry> list = new ArrayList<ShopEntry>();
        list.add(category(ShopEntry.batch("minecraft:coal@0", 64, 256), "Ресурсы"));
        list.add(category(ShopEntry.batch("minecraft:iron_ingot@0", 64, 768), "Ресурсы"));
        list.add(category(ShopEntry.batch("minecraft:gold_ingot@0", 64, 1536), "Ресурсы"));
        list.add(category(ShopEntry.batch("minecraft:diamond@0", 1, 120), "Ресурсы"));
        list.add(category(ShopEntry.batch("minecraft:emerald@0", 1, 100), "Ресурсы"));
        list.add(category(ShopEntry.batch("minecraft:redstone@0", 64, 192), "Ресурсы"));
        list.add(category(ShopEntry.batch("minecraft:cobblestone@0", 64, 12), "Блоки"));
        list.add(category(ShopEntry.batch("minecraft:dirt@0", 64, 6), "Блоки"));
        list.add(category(ShopEntry.batch("minecraft:wheat@0", 64, 128), "Ферма"));
        list.add(category(ShopEntry.batch("minecraft:potato@0", 64, 128), "Ферма"));
        list.add(category(ShopEntry.batch("minecraft:carrot@0", 64, 128), "Ферма"));
        list.add(category(ShopEntry.batch("minecraft:sapling@-1", 16, 48), "Ферма"));
        return list;
    }

    private static ShopEntry category(ShopEntry e, String category) {
        e.category = category;
        return e;
    }
}
