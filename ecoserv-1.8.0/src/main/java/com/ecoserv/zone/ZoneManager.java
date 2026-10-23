package com.ecoserv.zone;

import com.ecoserv.EcoServ;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Все защищённые зоны мира. Файл: world/ecoserv/zones.json */
public final class ZoneManager {

    private static final Type LIST_TYPE = new TypeToken<ArrayList<Zone>>() {
    }.getType();

    private static final List<Zone> ZONES = new ArrayList<Zone>();
    private static int nextId = 1;
    private static boolean dirty;

    /** Незаконченное выделение: игрок кликнул первый угол. */
    public static final class Selection {
        public final int dim;
        public final BlockPos first;
        public final String type;

        Selection(int dim, BlockPos first, String type) {
            this.dim = dim;
            this.first = first;
            this.type = type;
        }
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<UUID, Selection>();

    private ZoneManager() {
    }

    public static List<Zone> all() {
        return Collections.unmodifiableList(ZONES);
    }

    /** Зона в этой точке (если зон несколько — приоритет у DANGER, чтобы игрок увидел предупреждение). */
    @Nullable
    public static Zone at(int dim, BlockPos pos) {
        Zone found = null;
        for (int i = 0; i < ZONES.size(); i++) {
            Zone zone = ZONES.get(i);
            if (zone.contains(dim, pos)) {
                if (zone.isDanger()) {
                    return zone;
                }
                found = zone;
            }
        }
        return found;
    }

    /** Зона, рядом с которой (в пределах margin блоков) находится точка. */
    @Nullable
    public static Zone near(int dim, BlockPos pos, int margin) {
        Zone found = null;
        for (int i = 0; i < ZONES.size(); i++) {
            Zone zone = ZONES.get(i);
            if (zone.containsNear(dim, pos, margin)) {
                if (zone.isDanger()) {
                    return zone;
                }
                found = zone;
            }
        }
        return found;
    }

    public static boolean isEmpty() {
        return ZONES.isEmpty();
    }

    @Nullable
    public static Zone byId(int id) {
        for (int i = 0; i < ZONES.size(); i++) {
            if (ZONES.get(i).id == id) {
                return ZONES.get(i);
            }
        }
        return null;
    }

    public static Zone create(String type, int dim, BlockPos a, BlockPos b, String creator) {
        Zone zone = new Zone(nextId++, type, dim, a, b);
        zone.creator = creator == null ? "" : creator;
        ZONES.add(zone);
        save();
        return zone;
    }

    public static boolean remove(int id) {
        Zone zone = byId(id);
        if (zone == null) {
            return false;
        }
        ZONES.remove(zone);
        save();
        return true;
    }

    /** После правки полей зоны (тип, высота, имя). */
    public static void changed() {
        save();
    }

    // --- выделение палочкой -------------------------------------------------

    @Nullable
    public static Selection selection(UUID player) {
        return SELECTIONS.get(player);
    }

    public static void startSelection(UUID player, int dim, BlockPos first, String type) {
        SELECTIONS.put(player, new Selection(dim, first, type));
    }

    public static void clearSelection(UUID player) {
        SELECTIONS.remove(player);
    }

    // --- хранение -----------------------------------------------------------

    public static void load() {
        ZONES.clear();
        SELECTIONS.clear();
        nextId = 1;
        ArrayList<Zone> loaded = JsonUtil.readType(DataStore.file("zones.json"), LIST_TYPE, new ArrayList<Zone>());
        for (int i = 0; i < loaded.size(); i++) {
            Zone zone = loaded.get(i);
            if (zone == null) {
                continue;
            }
            if (zone.type == null) {
                zone.type = Zone.DANGER;
            }
            if (zone.name == null) {
                zone.name = "";
            }
            ZONES.add(zone);
            nextId = Math.max(nextId, zone.id + 1);
        }
        dirty = false;
        if (!ZONES.isEmpty()) {
            EcoServ.LOG.info("Защищённых зон: {}", ZONES.size());
        }
    }

    public static void save() {
        if (JsonUtil.write(DataStore.file("zones.json"), ZONES)) {
            dirty = false;
        } else {
            dirty = true;
        }
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }
}
