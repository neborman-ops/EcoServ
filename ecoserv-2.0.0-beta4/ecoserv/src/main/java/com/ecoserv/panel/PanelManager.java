package com.ecoserv.panel;

import com.ecoserv.content.Actions;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.script.ScriptVars;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.V2Actions;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 3D-панели: world/ecoserv/panels.json; игрокам — их мир, текст с подстановками для каждого свой. */
public final class PanelManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    private static final Type LIST = new TypeToken<ArrayList<Panel>>() {
    }.getType();
    private static final Map<String, Panel> ALL = new LinkedHashMap<String, Panel>();
    private static int tick;

    private PanelManager() {
    }

    public static void load() {
        ALL.clear();
        List<Panel> l = JsonUtil.readType(DataStore.file("panels.json"), LIST, null);
        if (l != null) {
            for (Panel p : l) {
                if (p != null && p.id != null && !p.id.isEmpty()) {
                    p.normalize();
                    ALL.put(p.id, p);
                }
            }
        }
    }

    public static void save() {
        JsonUtil.write(DataStore.file("panels.json"), new ArrayList<Panel>(ALL.values()));
    }

    public static List<Panel> all() {
        return new ArrayList<Panel>(ALL.values());
    }

    @Nullable
    public static Panel get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static Panel create(EntityPlayerMP p, String id) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            id = "panel_" + (ALL.size() + 1);
            while (ALL.containsKey(id)) {
                id = id + "_";
            }
        }
        if (!ID.matcher(id).matches()) {
            throw new UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (ALL.containsKey(id)) {
            throw new UiError("Панель «" + id + "» уже есть.");
        }
        Panel n = new Panel();
        n.id = id;
        n.dim = p.dimension;
        net.minecraft.util.math.Vec3d look = p.getLookVec();
        n.x = p.posX + look.x * 3;
        n.y = p.posY + 1.6D;
        n.z = p.posZ + look.z * 3;
        n.yaw = p.rotationYaw + 180.0F;
        Panel.Element t = new Panel.Element();
        t.kind = "title";
        t.text = "&6Панель";
        n.elements.add(t);
        Panel.Element tx = new Panel.Element();
        tx.text = "Привет, %player%!";
        n.elements.add(tx);
        Panel.Element b = new Panel.Element();
        b.kind = "button";
        b.text = "Нажми меня";
        b.steps.add("msg &aКнопка нажата!");
        n.elements.add(b);
        n.normalize();
        ALL.put(id, n);
        changed(n);
        return n;
    }

    /** Вернуть панель (отмена в редакторе). */
    public static void restore(Panel p) {
        p.normalize();
        ALL.put(p.id, p);
        changed(p);
    }

    public static void delete(Panel p) {
        ALL.remove(p.id);
        changed(p);
    }

    public static void changed(Panel p) {
        save();
        for (EntityPlayerMP pl : PlayerUtil.allOnline()) {
            if (pl.dimension == p.dim) {
                send(pl);
            }
        }
    }

    public static void send(EntityPlayerMP pl) {
        NBTTagCompound d = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Panel p : ALL.values()) {
            if (p.dim != pl.dimension) {
                continue;
            }
            if (!p.condition.trim().isEmpty() && !ScriptSteps.condition(pl, p.condition, p.id)) {
                continue;
            }
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", p.id);
            t.setDouble("x", p.x);
            t.setDouble("y", p.y);
            t.setDouble("z", p.z);
            t.setFloat("yaw", p.yaw);
            t.setFloat("pitch", p.pitch);
            t.setDouble("w", p.width);
            t.setDouble("h", p.height);
            t.setDouble("s", p.textScale);
            t.setInteger("bg", p.bg);
            t.setDouble("ba", p.bgAlpha);
            t.setInteger("bd", p.border);
            t.setBoolean("bb", p.billboard);
            t.setDouble("vd", p.viewDistance);
            NBTTagList el = new NBTTagList();
            for (int i = 0; i < p.elements.size(); i++) {
                Panel.Element e = p.elements.get(i);
                if (!e.condition.trim().isEmpty() && !ScriptSteps.condition(pl, e.condition, p.id)) {
                    continue;
                }
                NBTTagCompound c = new NBTTagCompound();
                c.setInteger("i", i);
                c.setString("k", e.kind);
                c.setString("t", ChatUtil.color(Actions.placeholders(e.text, pl, p.id)));
                c.setInteger("c", e.color);
                c.setInteger("bg", e.bg);
                if ("bar".equals(e.kind)) {
                    double v = 0;
                    try {
                        v = Double.parseDouble(ScriptVars.get(pl.getUniqueID(), e.var));
                    } catch (Exception ignored) {
                        // не число
                    }
                    c.setDouble("v", Math.max(0, Math.min(1, e.max <= 0 ? 0 : v / e.max)));
                }
                el.appendTag(c);
            }
            t.setTag("el", el);
            list.appendTag(t);
        }
        d.setTag("p", list);
        V2Actions.send(pl, "panels", d);
    }

    /** Нажали кнопку панели. */
    public static void click(EntityPlayerMP pl, String id, int index) {
        Panel p = get(id);
        if (p == null || p.dim != pl.dimension || index < 0 || index >= p.elements.size()) {
            return;
        }
        if (pl.getDistanceSq(p.x, p.y, p.z) > 12 * 12 + p.width * p.width) {
            return;
        }
        Panel.Element e = p.elements.get(index);
        if (!"button".equals(e.kind)) {
            return;
        }
        if (!e.condition.trim().isEmpty() && !ScriptSteps.condition(pl, e.condition, p.id)) {
            return;
        }
        com.ecoserv.dev.DevEvents.fire("panel_click", pl, p.id + ":" + index);
        if (!e.steps.isEmpty()) {
            ScriptRunner.startSteps(pl, "panel:" + p.id, e.steps, p.id);
        }
    }

    /** Подстановки меняются — обновлять раз в 2 секунды. */
    public static void tick() {
        if (++tick % 40 != 0 || ALL.isEmpty()) {
            return;
        }
        boolean any = false;
        for (Panel p : ALL.values()) {
            for (Panel.Element e : p.elements) {
                if (e.text.contains("%") || "bar".equals(e.kind) || !e.condition.isEmpty()) {
                    any = true;
                    break;
                }
            }
        }
        if (any) {
            for (EntityPlayerMP pl : PlayerUtil.allOnline()) {
                send(pl);
            }
        }
    }
}
