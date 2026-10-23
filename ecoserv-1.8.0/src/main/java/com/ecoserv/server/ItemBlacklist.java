package com.ecoserv.server;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Чёрный список предметов: запрет использования, установки и крафта.
 * Файл: config/ecoserv/blacklist.json, правится из GUI или командой.
 */
public final class ItemBlacklist {

    private static final Type TYPE = new TypeToken<ArrayList<String>>() {
    }.getType();

    private static final List<String> keys = new ArrayList<String>();
    private static final List<ItemKey> parsed = new ArrayList<ItemKey>();

    private ItemBlacklist() {
    }

    private static File file() {
        return new File(EcoConfig.getConfigDir(), "blacklist.json");
    }

    public static void load() {
        keys.clear();
        parsed.clear();
        ArrayList<String> loaded = JsonUtil.readType(file(), TYPE, new ArrayList<String>());
        for (int i = 0; i < loaded.size(); i++) {
            String key = loaded.get(i);
            if (key != null && !key.trim().isEmpty()) {
                keys.add(key.trim());
                parsed.add(ItemKey.parse(key.trim()));
            }
        }
        if (!keys.isEmpty()) {
            EcoServ.LOG.info("Чёрный список: {} позиций", keys.size());
        }
    }

    public static void save() {
        JsonUtil.write(file(), keys);
    }

    public static boolean isBlocked(ItemStack stack) {
        if (stack == null || stack.isEmpty() || parsed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < parsed.size(); i++) {
            if (parsed.get(i).matches(stack)) {
                return true;
            }
        }
        return false;
    }

    public static void add(String key) {
        if (key == null || key.trim().isEmpty() || keys.contains(key.trim())) {
            return;
        }
        keys.add(key.trim());
        parsed.add(ItemKey.parse(key.trim()));
        save();
    }

    public static boolean remove(String key) {
        if (key == null) {
            return false;
        }
        int index = keys.indexOf(key.trim());
        if (index < 0) {
            return false;
        }
        keys.remove(index);
        parsed.remove(index);
        save();
        return true;
    }

    public static List<String> list() {
        return Collections.unmodifiableList(keys);
    }
}
