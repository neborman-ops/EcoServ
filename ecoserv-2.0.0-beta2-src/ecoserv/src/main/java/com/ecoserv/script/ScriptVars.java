package com.ecoserv.script;

import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Переменные игроков для сценариев (2.0): world/ecoserv/script_vars.json. %var:имя% — подстановка. */
public final class ScriptVars {

    private static final Type MAP = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, String>>>() {
    }.getType();
    private static Map<String, LinkedHashMap<String, String>> vars = new LinkedHashMap<String, LinkedHashMap<String, String>>();
    private static boolean dirty;

    private ScriptVars() {
    }

    public static void load() {
        Map<String, LinkedHashMap<String, String>> m = JsonUtil.readType(DataStore.file("script_vars.json"), MAP, null);
        vars = m == null ? new LinkedHashMap<String, LinkedHashMap<String, String>>() : m;
        dirty = false;
    }

    public static void saveIfDirty() {
        if (dirty) {
            JsonUtil.write(DataStore.file("script_vars.json"), vars);
            dirty = false;
        }
    }

    public static String get(UUID player, String name) {
        Map<String, String> m = vars.get(player.toString());
        String v = m == null ? null : m.get(name);
        return v == null ? "0" : v;
    }

    public static void set(UUID player, String name, String value) {
        LinkedHashMap<String, String> m = vars.get(player.toString());
        if (m == null) {
            m = new LinkedHashMap<String, String>();
            vars.put(player.toString(), m);
        }
        m.put(name, value == null ? "" : value);
        dirty = true;
    }

    public static void add(UUID player, String name, long amount) {
        long now;
        try {
            now = Math.round(Double.parseDouble(get(player, name)));
        } catch (NumberFormatException e) {
            now = 0;
        }
        set(player, name, Long.toString(now + amount));
    }

    public static Map<String, String> all(UUID player) {
        Map<String, String> m = vars.get(player.toString());
        return m == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(m);
    }

    /** Подставить %var:имя% в текст. */
    public static String expand(UUID player, String text) {
        if (text == null || !text.contains("%var:")) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int s = text.indexOf("%var:", i);
            if (s < 0) {
                out.append(text, i, text.length());
                break;
            }
            int e = text.indexOf('%', s + 5);
            if (e < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, s).append(get(player, text.substring(s + 5, e)));
            i = e + 1;
        }
        return out.toString();
    }
}
