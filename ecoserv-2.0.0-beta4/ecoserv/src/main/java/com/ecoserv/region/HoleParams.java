package com.ecoserv.region;

import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки особых порталов (1.9.0): «Чёрная дыра» и «Монолит».
 * <p>Три уровня: значения по умолчанию в коде → «Настройка для всех» (world/ecoserv/portal_fx.json, отдельно
 * для каждого вида) → своё значение у конкретного портала (Region.fx). Клиенту уходят уже итоговые числа
 * в порядке {@link #ALL}, поэтому этот класс общий для сервера и клиента.</p>
 */
public final class HoleParams {

    public enum Type {
        NUMBER, PERCENT, BOOL, COLOR, CHOICE
    }

    /** Раздел: «эффекты» (есть и в «для всех», и у портала) или «вид» (цвет, диск, размер). */
    public enum Section {
        EFFECTS, LOOK
    }

    public static final class Param {
        public final String key;
        public final String title;
        public final String desc;
        public final Type type;
        public final Section section;
        public final double defHole;
        public final double defMono;
        public final double min;
        public final double max;
        /** Для кого: true — у чёрной дыры / у монолита. */
        public final boolean hole;
        public final boolean mono;
        public final List<String[]> choices = new ArrayList<String[]>();
        /** Номер в массиве, который получает клиент. */
        public final int index;

        Param(int index, String key, String title, String desc, Type type, Section section, double defHole, double defMono,
              double min, double max, boolean hole, boolean mono) {
            this.index = index;
            this.key = key;
            this.title = title;
            this.desc = desc;
            this.type = type;
            this.section = section;
            this.defHole = defHole;
            this.defMono = defMono;
            this.min = min;
            this.max = max;
            this.hole = hole;
            this.mono = mono;
        }

        Param choice(String value, String title) {
            choices.add(new String[]{value, title});
            return this;
        }

        public boolean appliesTo(String style) {
            return Region.STYLE_MONOLITH.equals(style) ? mono : hole;
        }

        public double def(String style) {
            return Region.STYLE_MONOLITH.equals(style) ? defMono : defHole;
        }

        public double clamp(double v) {
            if (type == Type.BOOL) {
                return v != 0.0D ? 1.0D : 0.0D;
            }
            if (type == Type.COLOR) {
                return (double) (((long) v) & 0xFFFFFFL);
            }
            return Math.max(min, Math.min(max, v));
        }

        /** Как показать значение человеку. */
        public String format(double v) {
            switch (type) {
                case BOOL:
                    return v != 0.0D ? "да" : "нет";
                case PERCENT:
                    return Math.round(v) + "%";
                case COLOR:
                    return String.format("#%06X", ((long) v) & 0xFFFFFFL);
                case CHOICE:
                    int i = (int) Math.round(v);
                    return i >= 0 && i < choices.size() ? choices.get(i)[1] : String.valueOf(i);
                default:
                    return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
            }
        }
    }

    public static final List<Param> ALL = new ArrayList<Param>();
    private static final Map<String, Param> BY_KEY = new LinkedHashMap<String, Param>();

    // --- эффекты ----------------------------------------------------------------------------------------
    public static final Param EFFECT_DISTANCE = add("effect_distance", "Искажение экрана — с какого расстояния",
            "Сколько блоков до портала, когда начинается «гиперпрыжок»", Type.NUMBER, Section.EFFECTS, 24, 2, 1, 96, true, true);
    public static final Param WARP = add("warp", "Сила «гиперпрыжка»", "Радиальные полосы и сдвиг цветов при подходе",
            Type.PERCENT, Section.EFFECTS, 100, 35, 0, 200, true, true);
    public static final Param PINCH = add("pinch", "Сила «сжатия» вплотную", "Как у аномалий: экран стягивается к дыре",
            Type.PERCENT, Section.EFFECTS, 100, 0, 0, 200, true, false);
    public static final Param RELEASE = add("release", "Сила «раздутия» после перехода", "", Type.PERCENT, Section.EFFECTS,
            100, 35, 0, 200, true, true);
    public static final Param HUD = add("hud", "Искажать интерфейс", "Хотбар, чат и меню искажаются вместе с миром "
            + "(игрок может выключить у себя в клиентских настройках)", Type.BOOL, Section.EFFECTS, 1, 1, 0, 1, true, true);
    public static final Param LENS = add("lens", "Сила линзы", "Гравитационное искривление света вокруг дыры "
            + "(100% — по Шварцшильду)", Type.PERCENT, Section.EFFECTS, 100, 0, 0, 200, true, false);
    public static final Param RIPPLE = add("ripple", "Рябь у краёв картинки", "Искажение только в полосе ~1 блок вокруг "
            + "картинки", Type.PERCENT, Section.EFFECTS, 0, 100, 0, 200, false, true);
    public static final Param PULL = add("pull", "Притягивать игроков", "Совсем рядом дыра мягко тянет к себе",
            Type.BOOL, Section.EFFECTS, 1, 0, 0, 1, true, false);
    public static final Param PULL_POWER = add("pull_power", "Сила притяжения", "", Type.PERCENT, Section.EFFECTS,
            100, 0, 0, 400, true, false);
    public static final Param VIEW_RADIUS = add("view_radius", "Дальность вида внутри (блоков)", "Сколько блоков "
            + "места назначения видно сквозь портал. Больше — красивее, но тяжелее для сети", Type.NUMBER,
            Section.EFFECTS, 16, 24, 4, 48, true, true);
    public static final Param VIEW_REFRESH = add("view_refresh", "Обновлять вид раз в (секунд)", "Как часто заново "
            + "снимать блоки места назначения", Type.NUMBER, Section.EFFECTS, 30, 15, 5, 600, true, true);
    public static final Param ENTITIES = add("entities", "Показывать игроков и мобов", "Существа в месте назначения "
            + "двигаются в окне вживую", Type.BOOL, Section.EFFECTS, 1, 1, 0, 1, true, true);
    public static final Param SOUNDS = add("sounds", "Звуки", "Гул рядом, свист при переходе", Type.BOOL,
            Section.EFFECTS, 1, 1, 0, 1, true, true);

    // --- вид ----------------------------------------------------------------------------------------------
    public static final Param SIZE = add("size", "Размер окна", "Радиус окна от размера области портала",
            Type.PERCENT, Section.LOOK, 85, 100, 20, 150, true, false);
    public static final Param INSIDE = add("inside", "Что внутри", "", Type.CHOICE, Section.LOOK, 0, 0, 0, 2, true, true)
            .choice("view", "Место назначения (3D)").choice("black", "Чёрная пустота").choice("color", "Свой цвет");
    public static final Param INSIDE_COLOR = add("inside_color", "Цвет внутри", "Если «Что внутри» = свой цвет",
            Type.COLOR, Section.LOOK, 0x101018, 0x0E1A2A, 0, 0xFFFFFF, true, true);
    public static final Param RIM_COLOR = add("rim_color", "Цвет края / свечения", "", Type.COLOR, Section.LOOK,
            0xA6BFFF, 0x8FD3FF, 0, 0xFFFFFF, true, true);
    public static final Param TURN = add("turn", "Поворот выхода", "Если монолиты стоят по-разному — повернуть выход",
            Type.CHOICE, Section.LOOK, 0, 0, 0, 4, false, true)
            .choice("auto", "Автоматически").choice("0", "0°").choice("90", "90°").choice("180", "180°").choice("270", "270°");
    public static final Param DISK = add("disk", "Аккреционный диск", "Светящийся газ вокруг дыры (профиль "
            + "Шакуры–Сюняева, доплер, красное смещение)", Type.BOOL, Section.LOOK, 0, 0, 0, 1, true, false);
    public static final Param DISK_TEMP = add("disk_temp", "Температура диска (K)", "1500 — тёмно-красный, "
            + "4500 — оранжевый, 7000 — белый, 15000+ — голубой", Type.NUMBER, Section.LOOK, 4500, 4500, 1500, 30000, true, false);
    public static final Param DISK_COLOR = add("disk_color", "Оттенок диска", "Умножается на цвет по температуре",
            Type.COLOR, Section.LOOK, 0xFFFFFF, 0xFFFFFF, 0, 0xFFFFFF, true, false);
    public static final Param DISK_SIZE = add("disk_size", "Размер диска", "Внешний радиус в массах M "
            + "(внутренний край — 6M, последняя устойчивая орбита)", Type.NUMBER, Section.LOOK, 22, 22, 8, 60, true, false);
    public static final Param DISK_TILT = add("disk_tilt", "Наклон диска (°)", "0 — лёжа (как пол), 90 — стоя",
            Type.NUMBER, Section.LOOK, 12, 12, 0, 90, true, false);
    public static final Param DISK_YAW = add("disk_yaw", "Поворот диска (°)", "", Type.NUMBER, Section.LOOK, 0, 0, 0, 359,
            true, false);
    public static final Param DISK_BRIGHT = add("disk_bright", "Яркость диска", "", Type.PERCENT, Section.LOOK, 100, 100,
            10, 400, true, false);
    public static final Param DISK_REVERSE = add("disk_reverse", "Вращение в другую сторону", "", Type.BOOL, Section.LOOK,
            0, 0, 0, 1, true, false);

    private static Param add(String key, String title, String desc, Type type, Section section, double defHole,
                             double defMono, double min, double max, boolean hole, boolean mono) {
        Param p = new Param(ALL.size(), key, title, desc, type, section, defHole, defMono, min, max, hole, mono);
        ALL.add(p);
        BY_KEY.put(key, p);
        return p;
    }

    @Nullable
    public static Param get(String key) {
        return BY_KEY.get(key);
    }

    // --- «Настройка для всех» -----------------------------------------------------------------------------

    private static final java.lang.reflect.Type FILE = new TypeToken<HashMap<String, HashMap<String, Double>>>() {
    }.getType();
    private static Map<String, HashMap<String, Double>> global = new HashMap<String, HashMap<String, Double>>();
    /** Растёт при изменении «для всех» (клиентам заново рассылается список). */
    public static volatile int version;

    private HoleParams() {
    }

    public static void load() {
        Map<String, HashMap<String, Double>> m = JsonUtil.readType(DataStore.file("portal_fx.json"), FILE,
                new HashMap<String, HashMap<String, Double>>());
        global = m == null ? new HashMap<String, HashMap<String, Double>>() : m;
        version++;
    }

    public static void save() {
        JsonUtil.write(DataStore.file("portal_fx.json"), global);
        version++;
    }

    /** Значение «для всех» (или по умолчанию). */
    public static double global(String style, Param p) {
        Map<String, Double> m = global.get(style);
        Double v = m == null ? null : m.get(p.key);
        return v == null ? p.def(style) : p.clamp(v);
    }

    public static boolean globalChanged(String style, Param p) {
        Map<String, Double> m = global.get(style);
        return m != null && m.containsKey(p.key);
    }

    public static void setGlobal(String style, Param p, @Nullable Double value) {
        HashMap<String, Double> m = global.get(style);
        if (m == null) {
            m = new HashMap<String, Double>();
            global.put(style, m);
        }
        if (value == null) {
            m.remove(p.key);
        } else {
            m.put(p.key, p.clamp(value));
        }
        save();
    }

    public static void resetGlobal(String style) {
        global.remove(style);
        save();
    }

    // --- итог для портала ---------------------------------------------------------------------------------

    public static double get(Region r, Param p) {
        Map<String, Double> own = r.fx;
        Double v = own == null ? null : own.get(p.key);
        return v == null ? global(r.style, p) : p.clamp(v);
    }

    public static boolean own(Region r, Param p) {
        return r.fx != null && r.fx.containsKey(p.key);
    }

    public static void setOwn(Region r, Param p, @Nullable Double value) {
        if (value == null) {
            if (r.fx != null) {
                r.fx.remove(p.key);
                if (r.fx.isEmpty()) {
                    r.fx = null;
                }
            }
            return;
        }
        if (r.fx == null) {
            r.fx = new HashMap<String, Double>();
        }
        r.fx.put(p.key, p.clamp(value));
    }

    public static boolean flag(Region r, Param p) {
        return get(r, p) != 0.0D;
    }

    /** Все итоговые значения по порядку {@link #ALL} — для клиента. */
    public static float[] resolve(Region r) {
        float[] out = new float[ALL.size()];
        for (Param p : ALL) {
            out[p.index] = (float) get(r, p);
        }
        return out;
    }

    public static List<Param> params(String style, Section section) {
        List<Param> out = new ArrayList<Param>();
        for (Param p : ALL) {
            if (p.section == section && p.appliesTo(style)) {
                out.add(p);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
