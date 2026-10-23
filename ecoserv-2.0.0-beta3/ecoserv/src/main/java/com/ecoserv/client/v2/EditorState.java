package com.ecoserv.client.v2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Редактор сцены: объекты от сервера, выбор, режим инструмента и математика лучей. */
@SideOnly(Side.CLIENT)
public final class EditorState {

    public static final class Obj {
        public String type;
        public String id;
        public String label;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public double sx;
        public double sy;
        public double sz;
        public int color;
        public final Set<String> caps = new HashSet<String>();

        public String key() {
            return type + ":" + id;
        }

        public boolean can(String c) {
            return caps.contains(c);
        }

        public AxisAlignedBB box() {
            switch (type) {
                case "npc":
                    return new AxisAlignedBB(x - 0.35, y, z - 0.35, x + 0.35, y + 1.9, z + 0.35);
                case "holo":
                    return new AxisAlignedBB(x - 0.6, y - 0.2, z - 0.6, x + 0.6, y + Math.max(0.3, sy), z + 0.6);
                case "panel": {
                    double r = Math.max(sx, 0.2) / 2;
                    return new AxisAlignedBB(x - r, y - sy / 2, z - r, x + r, y + sy / 2, z + r);
                }
                case "light":
                    return new AxisAlignedBB(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3);
                case "zone":
                    return new AxisAlignedBB(x - sx / 2, y, z - sz / 2, x + sx / 2, y + sy, z + sz / 2);
                case "block":
                    return new AxisAlignedBB(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5);
                default:
                    return new AxisAlignedBB(x - 0.25, y - 0.25, z - 0.25, x + 0.25, y + 0.25, z + 0.25);
            }
        }

        public Vec3d pivot() {
            AxisAlignedBB b = box();
            return new Vec3d((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2);
        }

        Obj copy() {
            Obj o = new Obj();
            o.type = type;
            o.id = id;
            o.label = label;
            o.x = x;
            o.y = y;
            o.z = z;
            o.yaw = yaw;
            o.pitch = pitch;
            o.sx = sx;
            o.sy = sy;
            o.sz = sz;
            o.color = color;
            o.caps.addAll(caps);
            return o;
        }
    }

    public static final List<Obj> OBJS = new ArrayList<Obj>();
    public static Obj selected;
    /** 0 — перемещение, 1 — поворот, 2 — размер. */
    public static int mode;
    public static boolean snap = true;
    public static double step = 0.5D;
    public static int undo;
    public static int redo;
    public static boolean open;

    // матрицы последнего кадра (для луча мыши)
    public static final float[] P = new float[16];
    public static final float[] M = new float[16];
    public static double camX;
    public static double camY;
    public static double camZ;
    public static boolean haveMatrices;

    private EditorState() {
    }

    public static void receive(NBTTagCompound d) {
        String sel = selected == null ? null : selected.key();
        OBJS.clear();
        selected = null;
        NBTTagList l = d.getTagList("o", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            Obj o = new Obj();
            o.type = t.getString("type");
            o.id = t.getString("id");
            o.label = t.getString("label");
            o.x = t.getDouble("x");
            o.y = t.getDouble("y");
            o.z = t.getDouble("z");
            o.yaw = t.getFloat("yaw");
            o.pitch = t.getFloat("pitch");
            o.sx = t.getDouble("sx");
            o.sy = t.getDouble("sy");
            o.sz = t.getDouble("sz");
            o.color = t.getInteger("c");
            o.caps.addAll(Arrays.asList(t.getString("caps").split(",")));
            OBJS.add(o);
            if (o.key().equals(sel)) {
                selected = o;
            }
        }
        undo = d.getInteger("undo");
        redo = d.getInteger("redo");
    }

    public static NBTTagCompound moveTag(Obj o) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("type", o.type);
        d.setString("id", o.id);
        d.setDouble("x", o.x);
        d.setDouble("y", o.y);
        d.setDouble("z", o.z);
        d.setFloat("yaw", o.yaw);
        d.setFloat("pitch", o.pitch);
        d.setDouble("sx", o.sx);
        d.setDouble("sy", o.sy);
        d.setDouble("sz", o.sz);
        return d;
    }

    // --- луч мыши ----------------------------------------------------------------------------------

    /** Луч из камеры через точку экрана (доли 0..1, y сверху вниз): {origin, dir}. */
    public static Vec3d[] ray(double fx, double fy) {
        double nx = fx * 2 - 1;
        double ny = 1 - fy * 2;
        double vx = nx / P[0];
        double vy = ny / P[5];
        double vz = -1;
        // в мир: транспонированный поворот вида
        double wx = M[0] * vx + M[1] * vy + M[2] * vz;
        double wy = M[4] * vx + M[5] * vy + M[6] * vz;
        double wz = M[8] * vx + M[9] * vy + M[10] * vz;
        double l = Math.sqrt(wx * wx + wy * wy + wz * wz);
        // начало: камера = ноги − Rᵀ·t
        double tx = M[12];
        double ty = M[13];
        double tz = M[14];
        double ox = camX - (M[0] * tx + M[1] * ty + M[2] * tz);
        double oy = camY - (M[4] * tx + M[5] * ty + M[6] * tz);
        double oz = camZ - (M[8] * tx + M[9] * ty + M[10] * tz);
        return new Vec3d[]{new Vec3d(ox, oy, oz), new Vec3d(wx / l, wy / l, wz / l)};
    }

    /** Параметр точки на оси (p + a·s), ближайшей к лучу. */
    public static double closestOnAxis(Vec3d p, Vec3d a, Vec3d o, Vec3d d) {
        Vec3d w = p.subtract(o);
        double aa = a.dotProduct(a);
        double ab = a.dotProduct(d);
        double bb = d.dotProduct(d);
        double aw = a.dotProduct(w);
        double bw = d.dotProduct(w);
        double den = aa * bb - ab * ab;
        if (Math.abs(den) < 1e-9) {
            return 0;
        }
        return (ab * bw - bb * aw) / den;
    }

    /** Пересечение луча с горизонтальной плоскостью y = h. */
    public static Vec3d onPlaneY(Vec3d o, Vec3d d, double h) {
        if (Math.abs(d.y) < 1e-6) {
            return null;
        }
        double t = (h - o.y) / d.y;
        if (t < 0) {
            return null;
        }
        return o.add(d.scale(t));
    }

    public static double snap(double v, double s) {
        return snap && s > 0 ? Math.round(v / s) * s : v;
    }
}
