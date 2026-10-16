package com.ecoserv.script;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Хранилище сценариев: config/ecoserv/scripts.json. */
public final class ScriptManager {

    private static final Type LIST = new TypeToken<ArrayList<Script>>() {
    }.getType();
    public static final Pattern NAME = Pattern.compile("^[\\p{L}0-9_\\-]{1,48}$");
    private static final Map<String, Script> SCRIPTS = new LinkedHashMap<String, Script>();

    private ScriptManager() {
    }

    private static File file() {
        return new File(EcoConfig.getConfigDir(), "scripts.json");
    }

    public static void load() {
        SCRIPTS.clear();
        File file = file();
        if (!file.exists()) {
            Script example = new Script();
            example.name = "пример";
            example.description = "Пример сценария: заголовок, пауза, звук и подарок";
            example.steps = new ArrayList<String>(Arrays.asList(
                    "title &6Добро пожаловать|&7это пример сценария",
                    "wait 2s",
                    "sound minecraft:entity.player.levelup",
                    "msg &aДержи хлеб, %player%!",
                    "cmd give %player% minecraft:bread 3"));
            List<Script> list = new ArrayList<Script>();
            list.add(example);
            JsonUtil.write(file, list);
        }
        List<Script> loaded = JsonUtil.readType(file, LIST, new ArrayList<Script>());
        if (loaded != null) {
            for (Script s : loaded) {
                if (s != null && s.name != null && !s.name.trim().isEmpty()) {
                    s.name = key(s.name);
                    s.normalize();
                    SCRIPTS.put(s.name, s);
                }
            }
        }
    }

    public static void save() {
        JsonUtil.write(file(), new ArrayList<Script>(SCRIPTS.values()));
    }

    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Nullable
    public static Script get(String name) {
        return SCRIPTS.get(key(name));
    }

    public static List<Script> all() {
        return new ArrayList<Script>(SCRIPTS.values());
    }

    public static List<String> names() {
        return new ArrayList<String>(SCRIPTS.keySet());
    }

    @Nullable
    public static Script create(String name) {
        String k = key(name);
        if (!NAME.matcher(k).matches() || SCRIPTS.containsKey(k)) {
            return null;
        }
        Script s = new Script();
        s.name = k;
        SCRIPTS.put(k, s);
        save();
        return s;
    }

    public static boolean delete(String name) {
        boolean removed = SCRIPTS.remove(key(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** Проверка шага: null — понятен, иначе что не так. */
    @Nullable
    public static String validate(String step) {
        String s = step == null ? "" : step.trim();
        if (s.isEmpty()) {
            return "пустой шаг";
        }
        String cmd = s.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "msg":
            case "title":
            case "actionbar":
            case "cmd":
            case "pcmd":
            case "activate":
            case "run":
            case "sound":
            case "require":
            case "tpworld":
                return s.contains(" ") ? null : "после «" + cmd + "» нужно значение";
            case "wait":
                return ScriptRunner.parseTicks(s.substring(4).trim()) >= 0 ? null : "пауза: число тиков или «2s»";
            case "tp":
                return s.split("\\s+").length >= 4 ? null : "tp x y z";
            case "stop":
                return null;
            default:
                return "неизвестный шаг «" + cmd + "» (msg, title, actionbar, sound, wait, cmd, pcmd, tp, tpworld, activate, require, run, stop)";
        }
    }
}
