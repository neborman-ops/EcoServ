package com.ecoserv.content;

import com.ecoserv.util.JsonUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Свой контент (2.0 beta3): блоки, предметы, жидкости, текстуры и 3D-модели. Слоты (64 блока, 64 предмета,
 * 16 жидкостей) зарегистрированы всегда, а что в них — описано здесь. Сервер рассылает набор игрокам, у них
 * он сохраняется и становится ресурс-паком.
 */
public class ContentPack {

    public static final int BLOCKS = 64;
    public static final int ITEMS = 64;
    public static final int FLUIDS = 16;
    /** Слоты 0–11 — как вода, 12–15 — как лава. */
    public static final int WATER_FLUIDS = 12;

    public static class BlockDef {
        public int slot = -1;
        public String name = "Свой блок";
        /** cube, column, cross, glass, model. */
        public String shape = "cube";
        /** Текстуры (ключи из textures): все стороны / верх-низ / бока. */
        public String texture = "";
        public String top = "";
        public String side = "";
        public String model = "";
        public double hardness = 1.5D;
        public double resistance = 6.0D;
        public int light;
        /** stone, wood, metal, glass, grass, sand, cloth, snow. */
        public String sound = "stone";
        public boolean collision = true;
        /** Прозрачные пиксели (листва, стекло). */
        public boolean cutout;
        /** Полупрозрачный (цветное стекло). */
        public boolean translucent;
        public boolean dropsSelf = true;
        public boolean hidden;
    }

    public static class ItemDef {
        public int slot = -1;
        public String name = "Свой предмет";
        public String texture = "";
        /** 3D-модель вместо плоской картинки. */
        public String model = "";
        public int maxStack = 64;
        public List<String> lore = new ArrayList<String>();
        public List<String> steps = new ArrayList<String>();
        public int cooldown;
        public boolean consume;
        public boolean glow;
    }

    public static class FluidDef {
        public int slot = -1;
        public String name = "Своя жидкость";
        public int color = 0x3F76E4;
        public int luminosity;
        public int density = 1000;
        public int viscosity = 1000;
        public int temperature = 300;
    }

    public List<BlockDef> blocks = new ArrayList<BlockDef>();
    public List<ItemDef> items = new ArrayList<ItemDef>();
    public List<FluidDef> fluids = new ArrayList<FluidDef>();
    /** Ключ → PNG (не в json: отдельные файлы / NBT). */
    public transient Map<String, byte[]> textures = new LinkedHashMap<String, byte[]>();
    /** Ключ → JSON модели Minecraft. */
    public transient Map<String, String> models = new LinkedHashMap<String, String>();

    public void normalize() {
        if (blocks == null) {
            blocks = new ArrayList<BlockDef>();
        }
        if (items == null) {
            items = new ArrayList<ItemDef>();
        }
        if (fluids == null) {
            fluids = new ArrayList<FluidDef>();
        }
        for (ItemDef i : items) {
            if (i.lore == null) {
                i.lore = new ArrayList<String>();
            }
            if (i.steps == null) {
                i.steps = new ArrayList<String>();
            }
        }
        if (textures == null) {
            textures = new LinkedHashMap<String, byte[]>();
        }
        if (models == null) {
            models = new LinkedHashMap<String, String>();
        }
    }

    public BlockDef block(int slot) {
        for (BlockDef d : blocks) {
            if (d.slot == slot) {
                return d;
            }
        }
        return null;
    }

    public ItemDef item(int slot) {
        for (ItemDef d : items) {
            if (d.slot == slot) {
                return d;
            }
        }
        return null;
    }

    public FluidDef fluid(int slot) {
        for (FluidDef d : fluids) {
            if (d.slot == slot) {
                return d;
            }
        }
        return null;
    }

    public int freeBlock() {
        for (int i = 0; i < BLOCKS; i++) {
            if (block(i) == null) {
                return i;
            }
        }
        return -1;
    }

    public int freeItem() {
        for (int i = 0; i < ITEMS; i++) {
            if (item(i) == null) {
                return i;
            }
        }
        return -1;
    }

    public int freeFluid(boolean lava) {
        for (int i = lava ? WATER_FLUIDS : 0; i < (lava ? FLUIDS : WATER_FLUIDS); i++) {
            if (fluid(i) == null) {
                return i;
            }
        }
        return -1;
    }

    // --- NBT -----------------------------------------------------------------------------------------

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("defs", JsonUtil.GSON.toJson(this));
        NBTTagCompound tex = new NBTTagCompound();
        for (Map.Entry<String, byte[]> e : textures.entrySet()) {
            tex.setByteArray(e.getKey(), e.getValue());
        }
        t.setTag("tex", tex);
        NBTTagCompound mod = new NBTTagCompound();
        for (Map.Entry<String, String> e : models.entrySet()) {
            mod.setByteArray(e.getKey(), gzip(e.getValue()));
        }
        t.setTag("mod", mod);
        return t;
    }

    public static ContentPack read(NBTTagCompound t) {
        ContentPack p;
        try {
            p = JsonUtil.GSON.fromJson(t.getString("defs"), ContentPack.class);
        } catch (Throwable e) {
            p = null;
        }
        if (p == null) {
            p = new ContentPack();
        }
        p.textures = new LinkedHashMap<String, byte[]>();
        p.models = new LinkedHashMap<String, String>();
        NBTTagCompound tex = t.getCompoundTag("tex");
        for (String k : tex.getKeySet()) {
            p.textures.put(k, tex.getByteArray(k));
        }
        NBTTagCompound mod = t.getCompoundTag("mod");
        for (String k : mod.getKeySet()) {
            p.models.put(k, gunzip(mod.getByteArray(k)));
        }
        p.normalize();
        return p;
    }

    public ContentPack copy() {
        return read(write());
    }

    public String hash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(JsonUtil.GSON.toJson(this).getBytes("UTF-8"));
            for (Map.Entry<String, byte[]> e : textures.entrySet()) {
                md.update(e.getKey().getBytes("UTF-8"));
                md.update(e.getValue());
            }
            for (Map.Entry<String, String> e : models.entrySet()) {
                md.update(e.getKey().getBytes("UTF-8"));
                md.update(e.getValue().getBytes("UTF-8"));
            }
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) {
                b.append(String.format("%02x", x));
            }
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(JsonUtil.GSON.toJson(this).hashCode());
        }
    }

    public static byte[] gzip(String s) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream z = new java.util.zip.GZIPOutputStream(out);
            z.write(s.getBytes("UTF-8"));
            z.close();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            return new byte[0];
        }
    }

    public static String gunzip(byte[] b) {
        try {
            java.util.zip.GZIPInputStream z = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(b));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = z.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (java.io.IOException e) {
            return "";
        }
    }

    public static NBTTagList empty() {
        return new NBTTagList();
    }
}
