package com.ecoserv.camera;

import com.ecoserv.npc.Npc;
import com.ecoserv.npc.NpcManager;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
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

/** Камеры сцены: world/ecoserv/cameras.json, кто сейчас смотрит через какую камеру. */
public final class CameraManager {

    private static final Type LIST = new TypeToken<ArrayList<SceneCamera>>() {
    }.getType();
    private static final Map<String, SceneCamera> ALL = new LinkedHashMap<String, SceneCamera>();
    /** Игрок → {камера, сколько тиков осталось (-1 — пока не выключат)}. */
    private static final Map<UUID, Object[]> VIEWING = new HashMap<UUID, Object[]>();
    private static int nextId = 1;

    private CameraManager() {
    }

    public static void load() {
        ALL.clear();
        VIEWING.clear();
        List<SceneCamera> l = JsonUtil.readType(DataStore.file("cameras.json"), LIST, null);
        if (l != null) {
            for (SceneCamera c : l) {
                if (c != null && c.id != null && !c.id.isEmpty()) {
                    c.normalize();
                    ALL.put(c.id, c);
                    try {
                        nextId = Math.max(nextId, Integer.parseInt(c.id.replaceAll("\\D", "")) + 1);
                    } catch (NumberFormatException ignored) {
                        // не число
                    }
                }
            }
        }
    }

    public static void save() {
        JsonUtil.write(DataStore.file("cameras.json"), new ArrayList<SceneCamera>(ALL.values()));
    }

    public static List<SceneCamera> all() {
        return new ArrayList<SceneCamera>(ALL.values());
    }

    @Nullable
    public static SceneCamera get(String id) {
        if (id == null) {
            return null;
        }
        SceneCamera c = ALL.get(id);
        if (c != null) {
            return c;
        }
        for (SceneCamera x : ALL.values()) {
            if (x.name.equalsIgnoreCase(id)) {
                return x;
            }
        }
        return null;
    }

    /** Новая камера там, куда смотрит игрок (его глаза). */
    public static SceneCamera create(EntityPlayerMP p) {
        return create(p.dimension, p.posX, p.posY + p.getEyeHeight(), p.posZ, p.rotationYaw, p.rotationPitch);
    }

    public static SceneCamera create(int dim, double x, double y, double z, float yaw, float pitch) {
        SceneCamera c = new SceneCamera();
        c.id = "cam_" + nextId++;
        while (ALL.containsKey(c.id)) {
            c.id = "cam_" + nextId++;
        }
        c.dim = dim;
        c.x = x;
        c.y = y;
        c.z = z;
        c.yaw = yaw;
        c.pitch = pitch;
        c.normalize();
        ALL.put(c.id, c);
        save();
        return c;
    }

    public static void restore(SceneCamera c) {
        c.normalize();
        ALL.put(c.id, c);
        save();
    }

    public static void delete(SceneCamera c) {
        ALL.remove(c.id);
        save();
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            Object[] v = VIEWING.get(p.getUniqueID());
            if (v != null && v[0] == c) {
                stop(p);
            }
        }
    }

    /** Камеру поправили — обновить у тех, кто через неё смотрит. */
    public static void changed(SceneCamera c) {
        c.normalize();
        save();
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            Object[] v = VIEWING.get(p.getUniqueID());
            if (v != null && v[0] == c) {
                sendView(p, c, false);
            }
        }
    }

    @Nullable
    public static SceneCamera viewing(EntityPlayerMP p) {
        Object[] v = VIEWING.get(p.getUniqueID());
        return v == null ? null : (SceneCamera) v[0];
    }

    public static List<EntityPlayerMP> viewers(SceneCamera c) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            Object[] v = VIEWING.get(p.getUniqueID());
            if (v != null && v[0] == c) {
                out.add(p);
            }
        }
        return out;
    }

    /** Переключить игрока на камеру; seconds ≤ 0 — пока не выключат. */
    public static void view(EntityPlayerMP p, SceneCamera c, double seconds) {
        if (c.dim != p.dimension) {
            throw new UiError("Камера в другом мире.");
        }
        VIEWING.put(p.getUniqueID(), new Object[]{c, seconds > 0 ? (int) Math.round(seconds * 20) : -1});
        sendView(p, c, true);
        com.ecoserv.dev.DevEvents.fire("camera_on", p, c.id);
    }

    private static void sendView(EntityPlayerMP p, SceneCamera c, boolean fresh) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("id", c.id);
        d.setString("name", c.title());
        d.setDouble("x", c.x);
        d.setDouble("y", c.y);
        d.setDouble("z", c.z);
        d.setFloat("yaw", c.yaw);
        d.setFloat("pitch", c.pitch);
        d.setFloat("roll", c.roll);
        d.setFloat("fov", c.fov);
        d.setBoolean("hud", c.hideHud);
        d.setBoolean("bars", c.bars);
        d.setBoolean("exit", c.allowExit);
        d.setInteger("tr", fresh ? c.transition : 0);
        int track = -1;
        if ("viewer".equals(c.track)) {
            track = p.getEntityId();
        } else if (c.track.startsWith("npc:")) {
            Npc n = NpcManager.get(c.track.substring(4));
            net.minecraft.entity.Entity e = n == null ? null : NpcManager.entityOf(n);
            if (e != null) {
                track = e.getEntityId();
            }
        }
        d.setInteger("track", track);
        V2Actions.send(p, "camview", d);
    }

    public static void stop(EntityPlayerMP p) {
        Object[] v = VIEWING.remove(p.getUniqueID());
        V2Actions.send(p, "camview_off", new NBTTagCompound());
        if (v != null) {
            com.ecoserv.dev.DevEvents.fire("camera_off", p, ((SceneCamera) v[0]).id);
        }
    }

    public static void tick() {
        if (VIEWING.isEmpty()) {
            return;
        }
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            Object[] v = VIEWING.get(p.getUniqueID());
            if (v == null) {
                continue;
            }
            SceneCamera c = (SceneCamera) v[0];
            int left = (Integer) v[1];
            if (c.dim != p.dimension) {
                stop(p);
                continue;
            }
            if (left > 0) {
                left--;
                v[1] = left;
                if (left == 0) {
                    stop(p);
                }
            }
        }
    }

    public static void leave(EntityPlayerMP p) {
        VIEWING.remove(p.getUniqueID());
    }

    /** Цели для шага/команды: ник, @a (все в мире камеры), @s. */
    public static List<EntityPlayerMP> targets(EntityPlayerMP self, String who, @Nullable SceneCamera c) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        who = who == null ? "" : who.trim();
        if (who.isEmpty() || "@s".equals(who)) {
            if (self != null) {
                out.add(self);
            }
        } else if ("@a".equals(who) || "все".equals(who.toLowerCase(Locale.ROOT))) {
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                if (c == null || p.dimension == c.dim) {
                    out.add(p);
                }
            }
        } else {
            EntityPlayerMP p = PlayerUtil.online(who);
            if (p == null) {
                throw new UiError("Игрок «" + who + "» не в сети.");
            }
            out.add(p);
        }
        return out;
    }

    public static void notifyView(EntityPlayerMP admin, SceneCamera c, List<EntityPlayerMP> who) {
        ChatUtil.actionBar(admin, "&aНа камеру «" + c.title() + "»: " + who.size());
    }
}
