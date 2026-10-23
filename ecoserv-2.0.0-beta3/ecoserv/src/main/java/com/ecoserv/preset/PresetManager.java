package com.ecoserv.preset;

import com.ecoserv.EcoServ;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.DataStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Пресеты (2.0): набор контента 2.0 — меню, NPC, голограммы, катсцены, свои предметы, структуры — в одной папке
 * world/ecoserv/presets/имя/. Можно сохранить, перенести на другой сервер и подключить (с заменой по id).
 */
public final class PresetManager {

    /** Файл → что это. */
    public static final Map<String, String> FILES = new LinkedHashMap<String, String>();

    static {
        FILES.put("menus.json", "Свои меню");
        FILES.put("npcs.json", "NPC");
        FILES.put("holograms.json", "Голограммы");
        FILES.put("cutscenes.json", "Катсцены");
        FILES.put("custom_items.json", "Свои предметы");
    }

    private PresetManager() {
    }

    static File root() {
        File d = DataStore.file("presets");
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }

    public static List<String> list() {
        List<String> out = new ArrayList<String>();
        File[] fs = root().listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) {
                    out.add(f.getName());
                }
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** Что внутри пресета: файл → число записей. */
    public static Map<String, Integer> contents(String name) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        File dir = new File(root(), name);
        for (String f : FILES.keySet()) {
            JsonArray a = readArray(new File(dir, f));
            if (a != null && a.size() > 0) {
                out.put(f, a.size());
            }
        }
        File[] st = new File(dir, "structures").listFiles();
        if (st != null && st.length > 0) {
            out.put("structures", st.length);
        }
        return out;
    }

    private static JsonArray readArray(File f) {
        if (!f.isFile()) {
            return null;
        }
        try {
            JsonElement e = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            return e.isJsonArray() ? e.getAsJsonArray() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeArray(File f, JsonArray a) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), com.ecoserv.util.JsonUtil.GSON.toJson(a).getBytes(StandardCharsets.UTF_8));
    }

    /** Сохранить текущий контент как пресет. */
    public static void save(String name, boolean structures) {
        if (!name.matches("^[a-zA-Zа-яА-ЯёЁ0-9_\\-]{1,40}$")) {
            throw new UiError("Имя: буквы, цифры, _ и - (до 40).");
        }
        com.ecoserv.custommenu.MenuManager.save();
        com.ecoserv.npc.NpcManager.save();
        com.ecoserv.cutscene.CutsceneManager.save();
        com.ecoserv.customitem.CustomItems.save();
        File dir = new File(root(), name);
        dir.mkdirs();
        try {
            for (String f : FILES.keySet()) {
                File src = DataStore.file(f);
                if (src.isFile()) {
                    Files.copy(src.toPath(), new File(dir, f).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (structures) {
                File sd = new File(dir, "structures");
                sd.mkdirs();
                File[] fs = DataStore.file("structures").listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        Files.copy(f.toPath(), new File(sd, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new UiError("Не удалось сохранить: " + e.getMessage());
        }
    }

    /** Подключить пресет: записи с тем же id заменяются, новые добавляются. */
    public static int apply(String name) {
        File dir = new File(root(), name);
        if (!dir.isDirectory()) {
            throw new UiError("Нет пресета «" + name + "».");
        }
        int n = 0;
        try {
            for (String f : FILES.keySet()) {
                JsonArray add = readArray(new File(dir, f));
                if (add == null) {
                    continue;
                }
                JsonArray cur = readArray(DataStore.file(f));
                if (cur == null) {
                    cur = new JsonArray();
                }
                Map<String, JsonElement> byId = new LinkedHashMap<String, JsonElement>();
                for (JsonElement e : cur) {
                    byId.put(idOf(e, byId.size()), e);
                }
                for (JsonElement e : add) {
                    byId.put(idOf(e, byId.size() + 100000), e);
                    n++;
                }
                JsonArray out = new JsonArray();
                for (JsonElement e : byId.values()) {
                    out.add(e);
                }
                writeArray(DataStore.file(f), out);
            }
            File[] st = new File(dir, "structures").listFiles();
            if (st != null) {
                File sd = DataStore.file("structures");
                sd.mkdirs();
                for (File f : st) {
                    Files.copy(f.toPath(), new File(sd, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    n++;
                }
            }
        } catch (IOException e) {
            throw new UiError("Не удалось подключить: " + e.getMessage());
        }
        reloadAll();
        return n;
    }

    private static String idOf(JsonElement e, int fallback) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("id")) {
                return o.get("id").getAsString();
            }
        }
        return "#" + fallback;
    }

    public static void reloadAll() {
        try {
            com.ecoserv.custommenu.MenuManager.load();
            com.ecoserv.custommenu.MenuManager.syncEsc();
            com.ecoserv.npc.NpcManager.load();
            com.ecoserv.cutscene.CutsceneManager.load();
            com.ecoserv.customitem.CustomItems.load();
            com.ecoserv.build.BuildManager.load();
        } catch (Throwable t) {
            EcoServ.LOG.error("Пресеты: перезагрузка", t);
        }
    }

    public static void delete(String name) {
        File dir = new File(root(), name);
        deleteTree(dir);
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) {
            for (File c : fs) {
                deleteTree(c);
            }
        }
        f.delete();
    }

    /** Готовый «Стартовый набор» прямо у игрока: гид-NPC, голограмма приветствия, меню. */
    public static void starter(EntityPlayerMP p) {
        com.ecoserv.npc.Npc guide = com.ecoserv.npc.NpcManager.get("guide");
        if (guide == null) {
            guide = com.ecoserv.npc.NpcManager.create(p, "guide", "minecraft:villager");
            guide.name = "&aГид";
            guide.profession = 1;
            guide.dialog.clear();
            com.ecoserv.npc.Npc.Node start = new com.ecoserv.npc.Npc.Node();
            start.id = "start";
            start.text = "Привет, %player%! Я гид этого сервера.\\nЧем помочь?";
            com.ecoserv.npc.Npc.Option rules = new com.ecoserv.npc.Npc.Option();
            rules.label = "Покажи правила";
            rules.steps.add("menu info");
            start.options.add(rules);
            com.ecoserv.npc.Npc.Option gift = new com.ecoserv.npc.Npc.Option();
            gift.label = "Есть подарок для новичка?";
            gift.condition = "not var guide_gift = 1";
            gift.steps.add("set guide_gift 1");
            gift.steps.add("give minecraft:bread 8");
            gift.steps.add("msg &aДержи хлеб в дорогу!");
            gift.next = "bye";
            start.options.add(gift);
            com.ecoserv.npc.Npc.Option bye = new com.ecoserv.npc.Npc.Option();
            bye.label = "Пока!";
            start.options.add(bye);
            com.ecoserv.npc.Npc.Node end = new com.ecoserv.npc.Npc.Node();
            end.id = "bye";
            end.text = "Удачи в игре!";
            com.ecoserv.npc.Npc.Option ok = new com.ecoserv.npc.Npc.Option();
            ok.label = "Спасибо!";
            end.options.add(ok);
            guide.dialog.add(start);
            guide.dialog.add(end);
            com.ecoserv.npc.NpcManager.respawn(guide);
        }
        if (com.ecoserv.npc.NpcManager.hologram("welcome") == null) {
            com.ecoserv.npc.Hologram h = com.ecoserv.npc.NpcManager.createHologram(p, "welcome");
            h.y += 1.0D;
            h.lines.clear();
            h.lines.add("&6&lДобро пожаловать!");
            h.lines.add("&7Онлайн: &f%online%&7/&f%max%");
            h.lines.add("&7Поговори с гидом, он расскажет всё");
            com.ecoserv.npc.NpcManager.respawnHologram(h);
        }
    }
}
