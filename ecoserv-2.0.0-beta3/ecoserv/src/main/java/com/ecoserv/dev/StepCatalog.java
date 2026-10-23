package com.ecoserv.dev;

import com.ecoserv.ui.UiUtil;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Каталог действий для визуального редактора (2.0): каждое действие — карточка с иконкой, понятным названием
 * и полями (с выбором предмета, звука, мира, сценария…). Из полей собирается строка шага сценария и обратно.
 */
public final class StepCatalog {

    public enum Kind {
        /** Текст до конца строки (может содержать пробелы) — всегда последний. */
        TEXT,
        /** Одно слово. */
        WORD,
        INT,
        NUMBER,
        ITEM,
        BLOCK,
        SOUND,
        PLAYER,
        /** «x y z» (~ — от игрока). */
        COORDS,
        WORLD,
        SCRIPT,
        CURRENCY,
        ROLE,
        EFFECT,
        PARTICLE,
        /** «40» тиков или «2s». */
        TIME,
        /** Варианты из options. */
        CHOICE,
        /** Условие из ConditionCatalog. */
        CONDITION,
        /** Вложенный шаг (текстом). */
        STEP,
        /** Список из источника {@link #source} (NPC, меню, катсцены…): options[0][0] — имя источника. */
        LIST
    }

    /** Источники списков для Kind.LIST: имя → варианты {ключ, название, описание, иконка}. */
    private static final Map<String, java.util.function.Supplier<List<String[]>>> SOURCES =
            new LinkedHashMap<String, java.util.function.Supplier<List<String[]>>>();

    public static void source(String name, java.util.function.Supplier<List<String[]>> s) {
        SOURCES.put(name, s);
    }

    public static List<String[]> options(String source) {
        java.util.function.Supplier<List<String[]>> s = SOURCES.get(source);
        if (s == null) {
            return new ArrayList<String[]>();
        }
        try {
            return s.get();
        } catch (Throwable t) {
            return new ArrayList<String[]>();
        }
    }

    public static final class Param {
        public final String key;
        public final String label;
        public final Kind kind;
        public final String hint;
        public final String def;
        public final boolean optional;
        public final String[][] options;

        Param(String key, String label, Kind kind, String hint, String def, boolean optional, String[][] options) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.hint = hint;
            this.def = def;
            this.optional = optional;
            this.options = options;
        }
    }

    public static final class Def {
        public final String id;
        public final String title;
        public final String desc;
        public final String icon;
        public final String category;
        public final List<Param> params = new ArrayList<Param>();
        /** Особая сборка: if условие then шаг else шаг. */
        boolean ifStep;

        public Def(String id, String title, String desc, String icon, String category) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.icon = icon;
            this.category = category;
        }

        public Def p(String key, String label, Kind kind, String hint, String def) {
            params.add(new Param(key, label, kind, hint, def, false, null));
            return this;
        }

        public Def opt(String key, String label, Kind kind, String hint, String def) {
            params.add(new Param(key, label, kind, hint, def, true, null));
            return this;
        }

        /** Выбор из списка источника (NPC, меню…). */
        public Def pick(String key, String label, String source, String hint, String def) {
            params.add(new Param(key, label, Kind.LIST, hint, def, false, new String[][]{{source}}));
            return this;
        }

        public Def choice(String key, String label, String hint, String def, String[][] options) {
            params.add(new Param(key, label, Kind.CHOICE, hint, def, false, options));
            return this;
        }

        public ItemStack iconStack() {
            return UiUtil.icon(icon);
        }
    }

    public static final String[] CATEGORIES = {"Чат и экран", "Игрок", "Экономика", "Мир", "Логика", "Квесты и события",
            "Системы"};

    private static final Map<String, Def> DEFS = new LinkedHashMap<String, Def>();

    static {
        String c = CATEGORIES[0];
        add(new Def("msg", "Сообщение", "Текст в чат игроку", "minecraft:paper", c)
                .p("text", "Текст", Kind.TEXT, "& — цвета, %player% — ник, %var:имя%", "&aПривет, %player%!"));
        add(new Def("title", "Надпись на экране", "Большой заголовок и подзаголовок", "minecraft:name_tag", c)
                .p("text", "Заголовок|подзаголовок", Kind.TEXT, "через | — подзаголовок", "&6Добро пожаловать|&7удачи!"));
        add(new Def("actionbar", "Текст над хотбаром", "Короткая строка над панелью быстрого доступа", "minecraft:sign", c)
                .p("text", "Текст", Kind.TEXT, "", "&eНайди ключ"));
        add(new Def("broadcast", "Сообщение всем", "В чат всем игрокам сервера", "minecraft:map", c)
                .p("text", "Текст", Kind.TEXT, "", "&6%player% открыл дверь!"));
        add(new Def("sound", "Звук", "Звук только этому игроку", "minecraft:noteblock", c)
                .p("sound", "Звук", Kind.SOUND, "кнопка … — выбрать и послушать", "minecraft:entity.player.levelup")
                .opt("volume", "Громкость", Kind.NUMBER, "1 — обычная", "1")
                .opt("pitch", "Высота", Kind.NUMBER, "1 — обычная, 0.5–2", "1"));
        add(new Def("particle", "Частицы", "Эффект частиц вокруг игрока или в точке", "minecraft:blaze_powder", c)
                .p("type", "Частицы", Kind.PARTICLE, "", "flame")
                .p("count", "Сколько", Kind.INT, "", "20")
                .opt("at", "Где", Kind.COORDS, "пусто — у игрока", ""));

        c = CATEGORIES[1];
        add(new Def("tp", "Телепорт", "Переместить игрока в точку этого мира", "minecraft:ender_pearl", c)
                .p("at", "Куда", Kind.COORDS, "x y z, ~ — от игрока", "~ ~ ~")
                .opt("yaw", "Поворот", Kind.NUMBER, "градусы", "")
                .opt("pitch", "Наклон", Kind.NUMBER, "градусы", ""));
        add(new Def("tpworld", "В другой мир", "На спавн мира или в точку", "minecraft:ender_eye", c)
                .p("world", "Мир", Kind.WORLD, "", "overworld")
                .opt("at", "Точка", Kind.COORDS, "пусто — безопасное место у спавна", ""));
        add(new Def("give", "Дать предмет", "В инвентарь (не влезло — под ноги)", "minecraft:chest", c)
                .p("item", "Предмет", Kind.ITEM, "", "minecraft:diamond")
                .p("count", "Сколько", Kind.INT, "", "1"));
        add(new Def("effect", "Эффект", "Зелье на игрока", "minecraft:potion", c)
                .p("effect", "Эффект", Kind.EFFECT, "", "speed")
                .p("seconds", "Секунд", Kind.INT, "", "10")
                .p("level", "Уровень", Kind.INT, "1 — первый", "1"));
        add(new Def("heal", "Вылечить", "Полное здоровье", "minecraft:golden_apple", c));
        add(new Def("feed", "Накормить", "Полная сытость", "minecraft:bread", c));
        add(new Def("pcmd", "Команда от игрока", "Выполнить команду с правами игрока", "minecraft:command_block", c)
                .p("cmd", "Команда", Kind.TEXT, "без /", "spawn"));

        c = CATEGORIES[2];
        add(new Def("money", "Деньги", "Дать (или забрать — минус) монеты", "minecraft:gold_nugget", c)
                .p("amount", "Сумма", Kind.INT, "минус — забрать", "100"));
        add(new Def("currency", "Валюта", "Дать или забрать валюту", "minecraft:diamond", c)
                .p("currency", "Валюта", Kind.CURRENCY, "", "gems")
                .p("amount", "Сумма", Kind.INT, "минус — забрать", "1"));

        c = CATEGORIES[3];
        add(new Def("setblock", "Поставить блок", "Один блок в точке", "minecraft:grass", c)
                .p("at", "Где", Kind.COORDS, "", "~ ~-1 ~")
                .p("block", "Блок", Kind.BLOCK, "", "minecraft:gold_block"));
        add(new Def("fill", "Заполнить область", "Все блоки от точки до точки (до 32768)", "minecraft:brick_block", c)
                .p("from", "От", Kind.COORDS, "", "~ ~ ~")
                .p("to", "До", Kind.COORDS, "", "~3 ~ ~3")
                .p("block", "Блок", Kind.BLOCK, "minecraft:air — очистить", "minecraft:stone"));
        add(new Def("cmd", "Команда сервера", "Выполнить от имени сервера", "minecraft:repeating_command_block", c)
                .p("cmd", "Команда", Kind.TEXT, "без /, %player% — ник", "give %player% minecraft:apple 3"));

        c = CATEGORIES[4];
        add(new Def("wait", "Пауза", "Подождать перед следующими шагами", "minecraft:clock", c)
                .p("time", "Сколько", Kind.TIME, "40 — тики, 2s — секунды, 1m — минуты", "2s"));
        Def iff = new Def("if", "Если… то…", "Проверить условие и выполнить шаг (или другой — иначе)", "minecraft:comparator", c)
                .p("cond", "Условие", Kind.CONDITION, "", "")
                .p("then", "То — шаг", Kind.STEP, "например msg &aЕсть!", "msg &aУсловие выполнено")
                .opt("else", "Иначе — шаг", Kind.STEP, "пусто — ничего", "");
        iff.ifStep = true;
        add(iff);
        add(new Def("set", "Задать переменную", "Значение переменной игрока", "minecraft:writable_book", c)
                .p("name", "Переменная", Kind.WORD, "латиница/кириллица без пробелов", "очки")
                .p("value", "Значение", Kind.TEXT, "", "0"));
        add(new Def("add", "Прибавить к переменной", "Число (минус — отнять)", "minecraft:book", c)
                .p("name", "Переменная", Kind.WORD, "", "очки")
                .p("amount", "Сколько", Kind.INT, "", "1"));
        add(new Def("run", "Запустить сценарий", "Другой сохранённый сценарий", "minecraft:written_book", c)
                .p("script", "Сценарий", Kind.SCRIPT, "", ""));
        add(new Def("require", "Только если роль", "Нет роли — остальные шаги не выполняются", "minecraft:iron_door", c)
                .choice("what", "Проверка", "", "role", new String[][]{{"role", "Роль"}, {"age", "Возраст"}})
                .p("value", "Значение", Kind.WORD, "id роли или 18, >=16, 14-17", "vip"));
        add(new Def("stop", "Остановить", "Дальше шаги не выполнять", "minecraft:barrier", c));

        c = CATEGORIES[5];
        add(new Def("quest", "Событие для квестов", "Засчитывает условие «Событие сценария»", "minecraft:knowledge_book", c)
                .p("name", "Имя события", Kind.WORD, "", "нашёл_ключ"));
        add(new Def("event", "Своё событие", "Для привязок событий и блоков-программ", "minecraft:observer", c)
                .p("name", "Имя события", Kind.WORD, "", "дверь_открыта"));
        add(new Def("activate", "Активация", "Как нажатие именованной кнопки (FTB Quests и др.)", "minecraft:stone_button", c)
                .p("name", "Имя", Kind.WORD, "", "дверь_шахты"));
    }

    private StepCatalog() {
    }

    /** Системы 2.0 добавляют свои действия (NPC, меню, катсцены…). */
    public static void add(Def d) {
        DEFS.put(d.id, d);
    }

    public static List<Def> all() {
        return new ArrayList<Def>(DEFS.values());
    }

    public static List<Def> inCategory(String category) {
        List<Def> out = new ArrayList<Def>();
        for (Def d : DEFS.values()) {
            if (d.category.equals(category)) {
                out.add(d);
            }
        }
        return out;
    }

    public static List<String> categories() {
        List<String> out = new ArrayList<String>(Arrays.asList(CATEGORIES));
        for (Def d : DEFS.values()) {
            if (!out.contains(d.category)) {
                out.add(d.category);
            }
        }
        return out;
    }

    @Nullable
    public static Def get(String id) {
        return DEFS.get(id);
    }

    @Nullable
    public static Def of(String line) {
        String t = line == null ? "" : line.trim();
        if (t.isEmpty()) {
            return null;
        }
        return DEFS.get(t.split("\\s+", 2)[0].toLowerCase(Locale.ROOT));
    }

    // --- строка <-> поля -----------------------------------------------------------------------------

    /** Сколько слов занимает поле. */
    private static int words(Param p) {
        switch (p.kind) {
            case COORDS:
                return 3;
            case TEXT:
            case STEP:
            case CONDITION:
                return -1;
            default:
                return 1;
        }
    }

    /** Разобрать строку шага в поля (лучшее, что получится). */
    public static Map<String, String> parse(Def d, String line) {
        Map<String, String> v = new LinkedHashMap<String, String>();
        String rest = line.trim();
        int sp = rest.indexOf(' ');
        rest = sp < 0 ? "" : rest.substring(sp + 1).trim();
        if (d.ifStep) {
            int then = rest.indexOf(" then ");
            String cond = then < 0 ? rest : rest.substring(0, then);
            String after = then < 0 ? "" : rest.substring(then + 6);
            int el = after.indexOf(" else ");
            v.put("cond", cond.trim());
            v.put("then", (el < 0 ? after : after.substring(0, el)).trim());
            v.put("else", el < 0 ? "" : after.substring(el + 6).trim());
            return v;
        }
        String[] w = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        int i = 0;
        for (int k = 0; k < d.params.size(); k++) {
            Param p = d.params.get(k);
            int n = words(p);
            if (n < 0) {
                StringBuilder b = new StringBuilder();
                for (; i < w.length; i++) {
                    b.append(b.length() > 0 ? " " : "").append(w[i]);
                }
                v.put(p.key, b.toString());
            } else {
                StringBuilder b = new StringBuilder();
                for (int j = 0; j < n && i < w.length; j++, i++) {
                    b.append(b.length() > 0 ? " " : "").append(w[i]);
                }
                v.put(p.key, b.toString());
            }
        }
        return v;
    }

    /** Собрать строку шага из полей. */
    public static String build(Def d, Map<String, String> v) {
        if (d.ifStep) {
            String s = "if " + val(v, "cond") + " then " + val(v, "then");
            String e = val(v, "else");
            return e.isEmpty() ? s : s + " else " + e;
        }
        StringBuilder b = new StringBuilder(d.id);
        for (Param p : d.params) {
            String x = val(v, p.key).trim();
            if (x.isEmpty()) {
                if (p.optional) {
                    // необязательное пустое — дальше только если есть следующие заполненные
                    boolean later = false;
                    for (int k = d.params.indexOf(p) + 1; k < d.params.size(); k++) {
                        if (!val(v, d.params.get(k).key).trim().isEmpty()) {
                            later = true;
                        }
                    }
                    if (!later) {
                        break;
                    }
                    x = p.kind == Kind.COORDS ? "~ ~ ~" : "~";
                } else {
                    x = p.def;
                }
            }
            b.append(' ').append(x);
        }
        return b.toString();
    }

    private static String val(Map<String, String> v, String k) {
        String s = v.get(k);
        return s == null ? "" : s;
    }

    /** Понятное описание шага для списка. */
    public static String describe(String line) {
        Def d = of(line);
        if (d == null) {
            return line;
        }
        String rest = line.trim().indexOf(' ') < 0 ? "" : line.trim().substring(line.trim().indexOf(' ') + 1);
        if (d.ifStep) {
            Map<String, String> v = parse(d, line);
            return "Если " + ConditionCatalog.describe(v.get("cond")) + " → " + describe(v.get("then"))
                    + (v.get("else").isEmpty() ? "" : " иначе " + describe(v.get("else")));
        }
        return d.title + (rest.isEmpty() ? "" : ": " + UiUtil.cut(rest, 60));
    }
}
