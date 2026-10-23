package com.ecoserv.lighting;

import com.ecoserv.ui.UiError;
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
import java.util.Map;

/**
 * Освещение по мирам и источники света: world/ecoserv/lighting.json, lights.json, lighting_presets.json.
 * Клиент получает профиль своего мира и источники рядом.
 */
public final class LightingManager {

    private static final Type PROFILES = new TypeToken<LinkedHashMap<String, LightingProfile>>() {
    }.getType();
    private static final Type LIGHTS = new TypeToken<ArrayList<LightObject>>() {
    }.getType();
    private static Map<String, LightingProfile> profiles = new LinkedHashMap<String, LightingProfile>();
    private static Map<String, LightingProfile> presets = new LinkedHashMap<String, LightingProfile>();
    private static final Map<String, LightObject> LIGHTS_BY_ID = new LinkedHashMap<String, LightObject>();
    private static int nextId = 1;

    private LightingManager() {
    }

    public static void load() {
        Map<String, LightingProfile> p = JsonUtil.readType(DataStore.file("lighting.json"), PROFILES, null);
        profiles = p == null ? new LinkedHashMap<String, LightingProfile>() : p;
        Map<String, LightingProfile> pr = JsonUtil.readType(DataStore.file("lighting_presets.json"), PROFILES, null);
        presets = pr == null ? builtInPresets() : pr;
        LIGHTS_BY_ID.clear();
        List<LightObject> l = JsonUtil.readType(DataStore.file("lights.json"), LIGHTS, null);
        if (l != null) {
            for (LightObject o : l) {
                if (o != null && o.id != null && !o.id.isEmpty()) {
                    LIGHTS_BY_ID.put(o.id, o);
                    try {
                        nextId = Math.max(nextId, Integer.parseInt(o.id.replaceAll("\\D", "")) + 1);
                    } catch (NumberFormatException ignored) {
                        // не число
                    }
                }
            }
        }
        save();
    }

    private static Map<String, LightingProfile> builtInPresets() {
        Map<String, LightingProfile> m = new LinkedHashMap<String, LightingProfile>();
        LightingProfile day = new LightingProfile();
        day.enabled = true;
        day.bloom = true;
        day.bloomIntensity = 0.4D;
        day.bloomThreshold = 1.4D;
        day.sunRays = true;
        day.colorCorrection = true;
        day.ccSaturation = 0.15D;
        day.ccContrast = 0.1D;
        day.atmosphere = true;
        m.put("Солнечный день", day);
        LightingProfile sunset = day.copy();
        sunset.lockTime = true;
        sunset.clockTime = 18.3D;
        sunset.ccTint = 0xFFE2C4;
        sunset.atmColor = 0xFFB27A;
        sunset.atmHaze = 2.0D;
        sunset.atmGlare = 3.0D;
        sunset.sunRaysIntensity = 0.5D;
        m.put("Закат", sunset);
        LightingProfile night = new LightingProfile();
        night.enabled = true;
        night.lockTime = true;
        night.clockTime = 0.0D;
        night.ambient = 0x1A2440;
        night.outdoorAmbient = 0x22305A;
        night.colorCorrection = true;
        night.ccTint = 0xB8C8FF;
        night.ccSaturation = -0.2D;
        night.bloom = true;
        night.bloomThreshold = 0.9D;
        m.put("Лунная ночь", night);
        LightingProfile horror = new LightingProfile();
        horror.enabled = true;
        horror.lockTime = true;
        horror.clockTime = 22.0D;
        horror.customFog = true;
        horror.fogColor = 0x101010;
        horror.fogStart = 0.0D;
        horror.fogEnd = 28.0D;
        horror.colorCorrection = true;
        horror.ccSaturation = -0.7D;
        horror.ccContrast = 0.3D;
        horror.ambient = 0x101010;
        horror.outdoorAmbient = 0x101010;
        horror.brightness = 0.5D;
        m.put("Хоррор", horror);
        LightingProfile dream = new LightingProfile();
        dream.enabled = true;
        dream.bloom = true;
        dream.bloomIntensity = 1.0D;
        dream.bloomThreshold = 0.8D;
        dream.depthOfField = true;
        dream.colorCorrection = true;
        dream.ccTint = 0xFFD8F4;
        dream.ccSaturation = 0.3D;
        dream.atmosphere = true;
        dream.atmColor = 0xF0C8FF;
        dream.atmHaze = 3.0D;
        m.put("Сказка", dream);
        return m;
    }

    public static void save() {
        JsonUtil.write(DataStore.file("lighting.json"), profiles);
        JsonUtil.write(DataStore.file("lighting_presets.json"), presets);
        JsonUtil.write(DataStore.file("lights.json"), new ArrayList<LightObject>(LIGHTS_BY_ID.values()));
    }

    public static LightingProfile profile(int dim) {
        LightingProfile p = profiles.get(Integer.toString(dim));
        if (p == null) {
            p = new LightingProfile();
            profiles.put(Integer.toString(dim), p);
        }
        return p;
    }

    public static void setProfile(int dim, LightingProfile p) {
        profiles.put(Integer.toString(dim), p);
        changed(dim);
    }

    public static Map<String, LightingProfile> presets() {
        return presets;
    }

    public static void savePreset(String name, LightingProfile p) {
        presets.put(name, p.copy());
        save();
    }

    public static void deletePreset(String name) {
        presets.remove(name);
        save();
    }

    // --- источники ----------------------------------------------------------------------------------

    public static List<LightObject> lights() {
        return new ArrayList<LightObject>(LIGHTS_BY_ID.values());
    }

    @Nullable
    public static LightObject light(String id) {
        return LIGHTS_BY_ID.get(id);
    }

    public static LightObject addLight(int dim, double x, double y, double z, String kind) {
        LightObject o = new LightObject();
        o.id = "light_" + nextId++;
        o.kind = kind;
        o.dim = dim;
        o.x = x;
        o.y = y;
        o.z = z;
        if ("spot".equals(kind)) {
            o.angle = 45.0D;
            o.range = 16.0D;
        }
        if ("surface".equals(kind)) {
            o.angle = 90.0D;
            o.range = 8.0D;
        }
        LIGHTS_BY_ID.put(o.id, o);
        changed(dim);
        return o;
    }

    /** Вернуть источник (отмена в редакторе). */
    public static void restoreLight(LightObject o) {
        LIGHTS_BY_ID.put(o.id, o);
        changed(o.dim);
    }

    public static void removeLight(LightObject o) {
        LIGHTS_BY_ID.remove(o.id);
        changed(o.dim);
    }

    public static void lightChanged(LightObject o) {
        changed(o.dim);
    }

    // --- рассылка -----------------------------------------------------------------------------------

    public static void changed(int dim) {
        save();
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            if (p.dimension == dim) {
                send(p);
            }
        }
    }

    public static void send(EntityPlayerMP p) {
        NBTTagCompound d = profile(p.dimension).write();
        NBTTagList l = new NBTTagList();
        for (LightObject o : LIGHTS_BY_ID.values()) {
            if (o.dim != p.dimension || !o.enabled) {
                continue;
            }
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", o.id);
            t.setString("k", o.kind);
            t.setDouble("x", o.x);
            t.setDouble("y", o.y);
            t.setDouble("z", o.z);
            t.setFloat("yaw", o.yaw);
            t.setFloat("pitch", o.pitch);
            t.setDouble("b", o.brightness);
            t.setInteger("c", o.color);
            t.setDouble("r", o.range);
            t.setDouble("a", o.angle);
            t.setBoolean("bl", o.blockLight);
            l.appendTag(t);
        }
        d.setTag("lights", l);
        V2Actions.send(p, "lighting", d);
    }

    public static void need(boolean ok, String msg) {
        if (!ok) {
            throw new UiError(msg);
        }
    }
}
