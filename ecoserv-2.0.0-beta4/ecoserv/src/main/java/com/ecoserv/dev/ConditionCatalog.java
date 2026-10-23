package com.ecoserv.dev;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Условия для визуального редактора (2.0): вид условия + поля → строка для ScriptSteps.condition. */
public final class ConditionCatalog {

    public static final String[][] OPS = {{">=", "не меньше"}, {">", "больше"}, {"=", "равно"}, {"!=", "не равно"},
            {"<", "меньше"}, {"<=", "не больше"}};

    public static final class Def {
        public final String id;
        public final String title;
        public final String icon;
        /** Поля: {ключ, подпись, вид (word|op|int|item|role|world|currency|quest)}. */
        public final String[][] fields;

        Def(String id, String title, String icon, String[][] fields) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.fields = fields;
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<String, Def>();

    static {
        add(new Def("role", "Есть роль", "minecraft:name_tag", new String[][]{{"role", "Роль", "role"}}));
        add(new Def("var", "Переменная", "minecraft:writable_book",
                new String[][]{{"name", "Переменная", "word"}, {"op", "Сравнение", "op"}, {"value", "Значение", "word"}}));
        add(new Def("money", "Деньги", "minecraft:gold_nugget", new String[][]{{"op", "Сравнение", "op"}, {"value", "Сумма", "int"}}));
        add(new Def("currency", "Валюта", "minecraft:diamond",
                new String[][]{{"cur", "Валюта", "currency"}, {"op", "Сравнение", "op"}, {"value", "Сумма", "int"}}));
        add(new Def("item", "Есть предмет", "minecraft:chest", new String[][]{{"item", "Предмет", "item"}, {"count", "Сколько", "int"}}));
        add(new Def("level", "Уровень опыта", "minecraft:experience_bottle",
                new String[][]{{"op", "Сравнение", "op"}, {"value", "Уровень", "int"}}));
        add(new Def("world", "В мире", "minecraft:grass", new String[][]{{"world", "Мир", "world"}}));
        add(new Def("quest", "Квест выполнен", "minecraft:knowledge_book", new String[][]{{"quest", "Квест", "quest"}}));
        add(new Def("chance", "Случайно (шанс %)", "minecraft:rabbit_foot", new String[][]{{"value", "Шанс, %", "int"}}));
        add(new Def("sneaking", "Крадётся (Shift)", "minecraft:leather_boots", new String[0][]));
    }

    private ConditionCatalog() {
    }

    private static void add(Def d) {
        DEFS.put(d.id, d);
    }

    public static List<Def> all() {
        return new ArrayList<Def>(DEFS.values());
    }

    @Nullable
    public static Def get(String id) {
        return DEFS.get(id);
    }

    /** Разбор строки условия: {not, вид, поля…}. */
    public static Map<String, String> parse(String cond) {
        Map<String, String> v = new LinkedHashMap<String, String>();
        String c = cond == null ? "" : cond.trim();
        if (c.startsWith("not ")) {
            v.put("not", "1");
            c = c.substring(4).trim();
        }
        String[] w = c.isEmpty() ? new String[0] : c.split("\\s+");
        String type = w.length > 0 ? w[0].toLowerCase(Locale.ROOT) : "role";
        v.put("type", type);
        Def d = DEFS.get(type);
        if (d != null) {
            for (int i = 0; i < d.fields.length; i++) {
                v.put(d.fields[i][0], w.length > i + 1 ? w[i + 1] : "");
            }
        }
        return v;
    }

    public static String build(Map<String, String> v) {
        Def d = DEFS.get(v.get("type"));
        if (d == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        if ("1".equals(v.get("not"))) {
            b.append("not ");
        }
        b.append(d.id);
        for (String[] f : d.fields) {
            String x = v.get(f[0]);
            if (x == null || x.trim().isEmpty()) {
                x = "op".equals(f[2]) ? ">=" : "int".equals(f[2]) ? "1" : "?";
            }
            b.append(' ').append(x.trim());
        }
        return b.toString();
    }

    /** По-человечески: «есть роль vip», «деньги ≥ 100». */
    public static String describe(@Nullable String cond) {
        if (cond == null || cond.trim().isEmpty()) {
            return "всегда";
        }
        Map<String, String> v = parse(cond);
        Def d = DEFS.get(v.get("type"));
        if (d == null) {
            return cond;
        }
        StringBuilder b = new StringBuilder();
        if ("1".equals(v.get("not"))) {
            b.append("НЕ ");
        }
        b.append(d.title.toLowerCase(Locale.ROOT));
        for (String[] f : d.fields) {
            String x = v.get(f[0]);
            if ("op".equals(f[2])) {
                for (String[] o : OPS) {
                    if (o[0].equals(x)) {
                        x = o[1];
                    }
                }
            }
            b.append(' ').append(x);
        }
        return b.toString();
    }
}
