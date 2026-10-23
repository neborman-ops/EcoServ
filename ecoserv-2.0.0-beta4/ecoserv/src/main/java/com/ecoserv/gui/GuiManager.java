package com.ecoserv.gui;

import com.ecoserv.content.Actions;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.script.ScriptVars;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.V2Actions;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Свои окна из дизайнера: world/ecoserv/guis.json, открытие игрокам, нажатия, сохранение из дизайнера. */
public final class GuiManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    private static final Type LIST = new TypeToken<ArrayList<CustomGui>>() {
    }.getType();
    private static final Map<String, CustomGui> ALL = new LinkedHashMap<String, CustomGui>();
    /** Кто какое окно сейчас видит. */
    private static final Map<UUID, String> OPEN = new HashMap<UUID, String>();
    private static int tick;

    private GuiManager() {
    }

    public static void init() {
        V2Actions.on("cgui_click", (p, d) -> click(p, d.getString("id"), d.getInteger("i"), d.getCompoundTag("in")));
        V2Actions.on("cgui_closed", (p, d) -> OPEN.remove(p.getUniqueID()));
        V2Actions.on("cgui_save", (p, d) -> {
            if (Perms.isAdmin(p)) {
                try {
                    save(p, d.getString("json"), d.getString("old"));
                    ChatUtil.actionBar(p, "&aОкно сохранено");
                } catch (UiError e) {
                    ChatUtil.actionBar(p, "&c" + e.getMessage());
                }
            }
        });
        V2Actions.on("cgui_steps", (p, d) -> {
            if (Perms.isAdmin(p)) {
                new GuiStepsScreen(p, d.getString("id"), d.getInteger("i")).open();
            }
        });
        V2Actions.on("cgui_list", (p, d) -> {
            if (Perms.isAdmin(p)) {
                openList(p);
            }
        });
        V2Actions.on("cgui_test", (p, d) -> {
            if (Perms.isAdmin(p)) {
                open(p, d.getString("id"));
            }
        });
    }

    public static void load() {
        ALL.clear();
        OPEN.clear();
        List<CustomGui> l = JsonUtil.readType(DataStore.file("guis.json"), LIST, null);
        if (l != null) {
            for (CustomGui g : l) {
                if (g != null && g.id != null && !g.id.isEmpty()) {
                    g.normalize();
                    ALL.put(g.id, g);
                }
            }
        }
    }

    public static void saveAll() {
        JsonUtil.write(DataStore.file("guis.json"), new ArrayList<CustomGui>(ALL.values()));
    }

    public static List<CustomGui> all() {
        return new ArrayList<CustomGui>(ALL.values());
    }

    @Nullable
    public static CustomGui get(String id) {
        return id == null ? null : ALL.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static CustomGui create(String id, @Nullable CustomGui template) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (ALL.containsKey(id)) {
            throw new UiError("Окно «" + id + "» уже есть.");
        }
        CustomGui g = template != null ? JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(template), CustomGui.class) : GuiTemplates.blank();
        g.id = id;
        g.normalize();
        ALL.put(id, g);
        saveAll();
        return g;
    }

    public static void delete(CustomGui g) {
        ALL.remove(g.id);
        saveAll();
    }

    /** Сохранить из дизайнера (old — прежний id, если переименовали). */
    static void save(EntityPlayerMP p, String json, String old) {
        CustomGui g;
        try {
            g = JsonUtil.GSON.fromJson(json, CustomGui.class);
        } catch (Exception e) {
            throw new UiError("Не прочитал окно: " + e.getMessage());
        }
        if (g == null) {
            throw new UiError("Пустое окно.");
        }
        g.id = g.id == null ? "" : g.id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(g.id).matches()) {
            throw new UiError("id окна: латиница, цифры, _ и - (до 32).");
        }
        if (old != null && !old.isEmpty() && !old.equals(g.id)) {
            if (ALL.containsKey(g.id)) {
                throw new UiError("Окно «" + g.id + "» уже есть.");
            }
            ALL.remove(old);
        }
        for (CustomGui.El e : g.elements) {
            e.index = -1;
            e.value = 0;
        }
        g.normalize();
        ALL.put(g.id, g);
        saveAll();
    }

    // --- дизайнер ------------------------------------------------------------------------------------

    public static void openList(EntityPlayerMP p) {
        new GuiListScreen(p).open();
    }

    /** Открыть визуальный дизайнер у админа. */
    public static void design(EntityPlayerMP p, String id) {
        CustomGui g = get(id);
        if (g == null) {
            throw new UiError("Нет окна «" + id + "».");
        }
        com.ecoserv.ui.UiManager.close(p);
        NBTTagCompound d = new NBTTagCompound();
        d.setString("json", JsonUtil.GSON.toJson(g));
        StringBuilder ids = new StringBuilder();
        for (String k : ALL.keySet()) {
            ids.append(k).append(',');
        }
        d.setString("ids", ids.toString());
        V2Actions.send(p, "cgui_design", d);
    }

    // --- игроку --------------------------------------------------------------------------------------

    public static void open(EntityPlayerMP p, String id) {
        CustomGui g = get(id);
        if (g == null) {
            ChatUtil.actionBar(p, "&cНет окна «" + id + "»");
            return;
        }
        if (!g.condition.trim().isEmpty() && !ScriptSteps.condition(p, g.condition, "gui:" + g.id)) {
            return;
        }
        com.ecoserv.ui.UiManager.close(p);
        OPEN.put(p.getUniqueID(), g.id);
        send(p, g, "cgui_open");
        com.ecoserv.dev.DevEvents.fire("gui_open", p, g.id);
    }

    public static void close(EntityPlayerMP p) {
        if (OPEN.remove(p.getUniqueID()) != null) {
            V2Actions.send(p, "cgui_close", new NBTTagCompound());
        }
    }

    /** Копия окна для игрока: подстановки, условия, значения полос. */
    private static void send(EntityPlayerMP p, CustomGui g, String packet) {
        CustomGui c = new CustomGui();
        c.id = g.id;
        c.title = ChatUtil.color(Actions.placeholders(g.title, p, g.id));
        c.width = g.width;
        c.height = g.height;
        c.bg = g.bg;
        c.bgAlpha = g.bgAlpha;
        c.border = g.border;
        c.dim = g.dim;
        c.closeButton = g.closeButton;
        for (int i = 0; i < g.elements.size(); i++) {
            CustomGui.El e = g.elements.get(i);
            if (!e.condition.trim().isEmpty() && !ScriptSteps.condition(p, e.condition, g.id)) {
                continue;
            }
            CustomGui.El x = JsonUtil.GSON.fromJson(JsonUtil.GSON.toJson(e), CustomGui.El.class);
            x.index = i;
            x.steps = new ArrayList<String>();
            x.condition = "";
            x.text = ChatUtil.color(Actions.placeholders(e.text, p, g.id));
            x.tooltip = ChatUtil.color(Actions.placeholders(e.tooltip, p, g.id));
            if ("bar".equals(e.kind)) {
                double v = 0;
                try {
                    v = Double.parseDouble(ScriptVars.get(p.getUniqueID(), e.var));
                } catch (Exception ignored) {
                    // не число
                }
                x.value = Math.max(0, Math.min(1, e.max <= 0 ? 0 : v / e.max));
            } else if ("input".equals(e.kind) && !e.var.isEmpty()) {
                String v = ScriptVars.get(p.getUniqueID(), e.var);
                x.value = 0;
                if (v != null && !v.isEmpty() && x.text.isEmpty()) {
                    x.text = v;
                }
            }
            c.elements.add(x);
        }
        NBTTagCompound d = new NBTTagCompound();
        d.setString("json", JsonUtil.GSON.toJson(c));
        V2Actions.send(p, packet, d);
    }

    static void click(EntityPlayerMP p, String id, int index, NBTTagCompound inputs) {
        CustomGui g = get(id);
        if (g == null || !g.id.equals(OPEN.get(p.getUniqueID())) || index < 0 || index >= g.elements.size()) {
            return;
        }
        // поля ввода → переменные игрока
        for (String k : inputs.getKeySet()) {
            try {
                int i = Integer.parseInt(k);
                if (i >= 0 && i < g.elements.size()) {
                    CustomGui.El in = g.elements.get(i);
                    if ("input".equals(in.kind) && !in.var.isEmpty()) {
                        String v = inputs.getString(k);
                        ScriptVars.set(p.getUniqueID(), in.var, v.length() > 200 ? v.substring(0, 200) : v);
                    }
                }
            } catch (NumberFormatException ignored) {
                // не индекс
            }
        }
        CustomGui.El e = g.elements.get(index);
        if (!"button".equals(e.kind) && !"item".equals(e.kind) && !"image".equals(e.kind)) {
            return;
        }
        if (!e.condition.trim().isEmpty() && !ScriptSteps.condition(p, e.condition, g.id)) {
            return;
        }
        com.ecoserv.dev.DevEvents.fire("gui_click", p, g.id + ":" + index);
        if (!e.steps.isEmpty()) {
            ScriptRunner.startSteps(p, "gui:" + g.id, e.steps, g.id);
        }
        if ("close".equals(e.then)) {
            close(p);
        } else if (e.then.startsWith("gui:")) {
            open(p, e.then.substring(4));
        } else if (g.id.equals(OPEN.get(p.getUniqueID()))) {
            send(p, g, "cgui_update");
        }
    }

    public static void tick() {
        tick++;
        if (OPEN.isEmpty() || tick % 20 != 0) {
            return;
        }
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            String id = OPEN.get(p.getUniqueID());
            CustomGui g = id == null ? null : get(id);
            if (g != null && g.refresh > 0 && (tick / 20) % g.refresh == 0) {
                send(p, g, "cgui_update");
            }
        }
    }

    public static void leave(EntityPlayerMP p) {
        OPEN.remove(p.getUniqueID());
    }
}
