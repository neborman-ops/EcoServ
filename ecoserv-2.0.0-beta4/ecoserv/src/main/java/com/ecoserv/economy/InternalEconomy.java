package com.ecoserv.economy;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Встроенная экономика. Хранится в world/ecoserv/economy.json. */
public class InternalEconomy implements IEconomy {

    private static final Type MAP_TYPE = new TypeToken<HashMap<String, Long>>() {
    }.getType();

    private final Map<UUID, Long> balances = new ConcurrentHashMap<UUID, Long>();
    private volatile boolean dirty;

    @Override
    public String getName() {
        return "internal";
    }

    @Override
    public boolean supportsOffline() {
        return true;
    }

    @Override
    public long get(UUID player) {
        if (player == null) {
            return 0L;
        }
        Long value = balances.get(player);
        if (value == null) {
            if (EcoConfig.startingBalance != 0L) {
                balances.put(player, EcoConfig.startingBalance);
                dirty = true;
                return EcoConfig.startingBalance;
            }
            return 0L;
        }
        return value.longValue();
    }

    @Override
    public void set(UUID player, long amount) {
        if (player == null) {
            return;
        }
        long clamped = Math.max(0L, Math.min(amount, EcoConfig.maxBalance));
        balances.put(player, clamped);
        dirty = true;
    }

    @Override
    public boolean take(UUID player, long amount) {
        if (player == null || amount < 0L) {
            return false;
        }
        if (amount == 0L) {
            return true;
        }
        long current = get(player);
        if (current < amount) {
            return false;
        }
        set(player, current - amount);
        return true;
    }

    @Override
    public void give(UUID player, long amount) {
        if (player == null || amount <= 0L) {
            return;
        }
        set(player, get(player) + amount);
    }

    @Override
    public List<Map.Entry<UUID, Long>> top(int count) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<Map.Entry<UUID, Long>>(balances.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<UUID, Long>>() {
            @Override
            public int compare(Map.Entry<UUID, Long> a, Map.Entry<UUID, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        if (list.size() > count) {
            return new ArrayList<Map.Entry<UUID, Long>>(list.subList(0, count));
        }
        return list;
    }

    @Override
    public void load() {
        balances.clear();
        File file = DataStore.file("economy.json");
        HashMap<String, Long> raw = JsonUtil.readType(file, MAP_TYPE, new HashMap<String, Long>());
        if (raw != null) {
            for (Map.Entry<String, Long> e : raw.entrySet()) {
                try {
                    balances.put(UUID.fromString(e.getKey()), e.getValue() == null ? 0L : e.getValue());
                } catch (IllegalArgumentException ignored) {
                    EcoServ.LOG.warn("Некорректный UUID в economy.json: {}", e.getKey());
                }
            }
        }
        dirty = false;
        EcoServ.LOG.info("Экономика загружена: {} счетов", balances.size());
    }

    @Override
    public void save() {
        HashMap<String, Long> raw = new HashMap<String, Long>();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            raw.put(e.getKey().toString(), e.getValue());
        }
        if (JsonUtil.write(DataStore.file("economy.json"), raw)) {
            dirty = false;
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public int size() {
        return balances.size();
    }

    public Map<UUID, Long> view() {
        return balances;
    }

    /** Тип для Gson (вынесено, чтобы не тянуть generics в сигнатуру чтения). */
    public static Type mapType() {
        return MAP_TYPE;
    }
}
