package com.ecoserv.economy;

import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Хранилище профилей игроков: world/ecoserv/players.json */
public final class StatsStore {

    private static final Type TYPE = new TypeToken<HashMap<String, PlayerStats>>() {
    }.getType();

    private static final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<UUID, PlayerStats>();
    private static volatile boolean dirty;

    private StatsStore() {
    }

    public static PlayerStats get(UUID player) {
        PlayerStats s = stats.get(player);
        if (s == null) {
            s = new PlayerStats();
            stats.put(player, s);
            dirty = true;
        }
        return s;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static Map<UUID, PlayerStats> view() {
        return stats;
    }

    public static void load() {
        stats.clear();
        HashMap<String, PlayerStats> raw = JsonUtil.readType(DataStore.file("players.json"), TYPE, new HashMap<String, PlayerStats>());
        for (Map.Entry<String, PlayerStats> e : raw.entrySet()) {
            try {
                if (e.getValue() != null) {
                    stats.put(UUID.fromString(e.getKey()), e.getValue());
                }
            } catch (IllegalArgumentException ignored) {
                // мусорная запись
            }
        }
        dirty = false;
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        HashMap<String, PlayerStats> raw = new HashMap<String, PlayerStats>();
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            raw.put(e.getKey().toString(), e.getValue());
        }
        if (JsonUtil.write(DataStore.file("players.json"), raw)) {
            dirty = false;
        }
    }

    public static void save() {
        dirty = true;
        saveIfDirty();
    }
}
