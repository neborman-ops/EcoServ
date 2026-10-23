package com.ecoserv.display;

import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.V2Actions;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Блок-дисплеи: world/ecoserv/displays.json; клиент получает дисплеи своего мира. */
public final class DisplayManager {

    private static final Type LIST = new TypeToken<ArrayList<Display>>() {
    }.getType();
    private static final Map<String, Display> ALL = new LinkedHashMap<String, Display>();
    private static int nextId = 1;

    private DisplayManager() {
    }

    public static void load() {
        ALL.clear();
        List<Display> l = JsonUtil.readType(DataStore.file("displays.json"), LIST, null);
        if (l != null) {
            for (Display d : l) {
                if (d != null && d.id != null && !d.id.isEmpty()) {
                    d.normalize();
                    ALL.put(d.id, d);
                    try {
                        nextId = Math.max(nextId, Integer.parseInt(d.id.replaceAll("\\D", "")) + 1);
                    } catch (NumberFormatException ignored) {
                        // не число
                    }
                }
            }
        }
    }

    public static void save() {
        JsonUtil.write(DataStore.file("displays.json"), new ArrayList<Display>(ALL.values()));
    }

    public static List<Display> all() {
        return new ArrayList<Display>(ALL.values());
    }

    @Nullable
    public static Display get(String id) {
        return id == null ? null : ALL.get(id);
    }

    /** «modid:имя@мета» для предмета. */
    public static String key(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return "minecraft:stone@0";
        }
        ResourceLocation rl = s.getItem().getRegistryName();
        return (rl == null ? "minecraft:stone" : rl.toString()) + "@" + s.getMetadata();
    }

    public static Display create(int dim, double x, double y, double z, String item) {
        Display d = new Display();
        d.id = "display_" + nextId++;
        while (ALL.containsKey(d.id)) {
            d.id = "display_" + nextId++;
        }
        d.dim = dim;
        d.x = x;
        d.y = y;
        d.z = z;
        d.item = item;
        d.normalize();
        ALL.put(d.id, d);
        changed(d);
        return d;
    }

    public static void restore(Display d) {
        d.normalize();
        ALL.put(d.id, d);
        changed(d);
    }

    public static void delete(Display d) {
        ALL.remove(d.id);
        changed(d);
    }

    public static void changed(Display d) {
        save();
        for (EntityPlayerMP pl : PlayerUtil.allOnline()) {
            if (pl.dimension == d.dim) {
                send(pl);
            }
        }
    }

    public static NBTTagCompound write(Display d) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("id", d.id);
        t.setDouble("x", d.x);
        t.setDouble("y", d.y);
        t.setDouble("z", d.z);
        t.setDouble("qx", d.qx);
        t.setDouble("qy", d.qy);
        t.setDouble("qz", d.qz);
        t.setDouble("qw", d.qw);
        t.setDouble("sx", d.sx);
        t.setDouble("sy", d.sy);
        t.setDouble("sz", d.sz);
        t.setString("item", d.item);
        t.setString("mode", d.mode);
        t.setBoolean("glow", d.glow);
        t.setDouble("spin", d.spin);
        t.setDouble("vd", d.viewDistance);
        return t;
    }

    public static void send(EntityPlayerMP pl) {
        NBTTagCompound d = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Display x : ALL.values()) {
            if (x.dim == pl.dimension) {
                l.appendTag(write(x));
            }
        }
        d.setTag("d", l);
        V2Actions.send(pl, "displays", d);
    }
}
