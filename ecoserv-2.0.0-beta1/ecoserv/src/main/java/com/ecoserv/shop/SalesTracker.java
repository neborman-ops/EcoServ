package com.ecoserv.shop;

import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Суточные лимиты продажи: сколько игрок уже сдал каждой позиции. */
public final class SalesTracker {

    public static class Record {
        public long day;
        public int count;
    }

    private static final Type TYPE = new TypeToken<HashMap<String, Record>>() {
    }.getType();

    private static final Map<String, Record> records = new ConcurrentHashMap<String, Record>();
    private static volatile boolean dirty;

    private SalesTracker() {
    }

    public static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    private static String id(UUID player, String key) {
        return player.toString() + "|" + key;
    }

    public static int soldToday(UUID player, String key) {
        Record r = records.get(id(player, key));
        if (r == null || r.day != today()) {
            return 0;
        }
        return r.count;
    }

    public static void add(UUID player, String key, int count) {
        String id = id(player, key);
        Record r = records.get(id);
        long day = today();
        if (r == null) {
            r = new Record();
            records.put(id, r);
        }
        if (r.day != day) {
            r.day = day;
            r.count = 0;
        }
        r.count += count;
        dirty = true;
    }

    /** Сколько ещё можно продать (Integer.MAX_VALUE, если лимита нет). */
    public static int remaining(UUID player, ShopEntry entry) {
        if (entry.dailySellLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, entry.dailySellLimit - soldToday(player, entry.key));
    }

    public static void load() {
        records.clear();
        HashMap<String, Record> raw = JsonUtil.readType(DataStore.file("sales.json"), TYPE, new HashMap<String, Record>());
        long day = today();
        for (Map.Entry<String, Record> e : raw.entrySet()) {
            if (e.getValue() != null && e.getValue().day == day) {
                records.put(e.getKey(), e.getValue());
            }
        }
        dirty = false;
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        purgeOld();
        HashMap<String, Record> raw = new HashMap<String, Record>(records);
        if (JsonUtil.write(DataStore.file("sales.json"), raw)) {
            dirty = false;
        }
    }

    private static void purgeOld() {
        long day = today();
        Iterator<Map.Entry<String, Record>> it = records.entrySet().iterator();
        while (it.hasNext()) {
            Record r = it.next().getValue();
            if (r == null || r.day != day) {
                it.remove();
            }
        }
    }
}
