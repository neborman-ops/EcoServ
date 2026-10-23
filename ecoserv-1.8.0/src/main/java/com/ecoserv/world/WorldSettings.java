package com.ecoserv.world;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки обычных миров (основной, ад, край, миры модов) — то же, что у подмиров: PvP, режим игры,
 * сохранение инвентаря, мобы, приветствие, свой барьер, ГЛИТЧ-бездна и доп. правила.
 * Файл world/ecoserv/world_settings.json.
 */
public final class WorldSettings {

    public static final String DEFAULT = "";
    public static final String ON = "on";
    public static final String OFF = "off";

    /** Барьер обычного мира (основной мир хранит свой в level.dat сам). */
    public static class BorderData {
        public double centerX;
        public double centerZ;
        public double size = 6.0E7D;
        public double damage = 0.2D;
        public double buffer = 5.0D;
        public int warningTime = 15;
        public int warningDistance = 5;
    }

    public static class Settings {
        public int dim;
        /** «» — как на сервере, on — можно, off — нельзя. */
        public String pvp = DEFAULT;
        /** Режим игры при входе: «» — не менять, survival, adventure, creative, spectator. */
        public String gamemode = "";
        /** «» — по правилу игры keepInventory, on — вещи остаются, off — выпадают. */
        public String keepInventory = DEFAULT;
        /** all — все мобы, peaceful — без враждебных, none — никто не появляется сам. */
        public String mobs = "all";
        /** «заголовок|подзаголовок» при входе (пусто — не показывать). */
        public String welcome = "";
        public WorldExtras extras = new WorldExtras();
        public Subworld.Abyss abyss = new Subworld.Abyss();
        @Nullable
        public BorderData border;

        public void normalize() {
            if (pvp == null) {
                pvp = DEFAULT;
            }
            if (gamemode == null) {
                gamemode = "";
            }
            if (keepInventory == null) {
                keepInventory = DEFAULT;
            }
            if (mobs == null || mobs.isEmpty()) {
                mobs = "all";
            }
            if (welcome == null) {
                welcome = "";
            }
            if (extras == null) {
                extras = new WorldExtras();
            }
            extras.normalize();
            if (abyss == null) {
                abyss = new Subworld.Abyss();
            }
            abyss.normalize();
        }

        /** Что-то настроено (для плашки «настроен» в списке). */
        public boolean customized() {
            return !DEFAULT.equals(pvp) || !gamemode.isEmpty() || !DEFAULT.equals(keepInventory) || !"all".equals(mobs)
                    || !welcome.isEmpty() || extras.any() || abyss.enabled;
        }
    }

    private static final Type LIST = new TypeToken<ArrayList<Settings>>() {
    }.getType();
    private static final Map<Integer, Settings> BY_DIM = new LinkedHashMap<Integer, Settings>();
    private static boolean dirty;
    private static boolean loaded;

    private WorldSettings() {
    }

    private static File file() {
        return DataStore.file("world_settings.json");
    }

    public static void load() {
        BY_DIM.clear();
        List<Settings> list = JsonUtil.readType(file(), LIST, new ArrayList<Settings>());
        if (list != null) {
            for (Settings s : list) {
                if (s != null) {
                    s.normalize();
                    BY_DIM.put(s.dim, s);
                }
            }
        }
        dirty = false;
        loaded = true;
    }

    public static void save() {
        if (loaded && JsonUtil.write(file(), new ArrayList<Settings>(BY_DIM.values()))) {
            dirty = false;
        }
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Настройки мира (создаются при первом обращении; пустые не записываются в файл, пока не изменят). */
    public static Settings get(int dim) {
        Settings s = BY_DIM.get(dim);
        if (s == null) {
            s = new Settings();
            s.dim = dim;
            s.normalize();
            BY_DIM.put(dim, s);
        }
        return s;
    }

    /** Без создания (null — мир не настраивали). */
    @Nullable
    public static Settings peek(int dim) {
        return BY_DIM.get(dim);
    }

    /** Изменили в окне: записать сразу. */
    public static void changed() {
        save();
    }
}
