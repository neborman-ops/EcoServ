package com.ecoserv.zone;

import net.minecraft.util.math.BlockPos;

/**
 * Защищённая зона — прямоугольный параллелепипед между двумя углами (включительно).
 * Внутри ломать и ставить блоки может только оператор в креативе.
 */
public class Zone {

    public static final String DANGER = "danger";
    public static final String SILENT = "silent";

    public int id;
    /** danger — пишет игроку, что ломать нельзя; silent — молча запрещает. */
    public String type = DANGER;
    public int dim;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public String name = "";
    public String creator = "";
    public long created;

    public Zone() {
    }

    public Zone(int id, String type, int dim, BlockPos a, BlockPos b) {
        this.id = id;
        this.type = type;
        this.dim = dim;
        setCorners(a, b);
        this.created = System.currentTimeMillis();
    }

    public void setCorners(BlockPos a, BlockPos b) {
        minX = Math.min(a.getX(), b.getX());
        minY = Math.min(a.getY(), b.getY());
        minZ = Math.min(a.getZ(), b.getZ());
        maxX = Math.max(a.getX(), b.getX());
        maxY = Math.max(a.getY(), b.getY());
        maxZ = Math.max(a.getZ(), b.getZ());
    }

    public boolean isDanger() {
        return !SILENT.equals(type);
    }

    public boolean contains(int dimension, BlockPos pos) {
        return dimension == dim
                && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    /** Внутри зоны, расширенной на margin блоков во все стороны. */
    public boolean containsNear(int dimension, BlockPos pos, int margin) {
        return dimension == dim
                && pos.getX() >= minX - margin && pos.getX() <= maxX + margin
                && pos.getY() >= minY - margin && pos.getY() <= maxY + margin
                && pos.getZ() >= minZ - margin && pos.getZ() <= maxZ + margin;
    }

    /** Расстояние от точки до зоны по горизонтали (0 — внутри), для поиска ближайших. */
    public double distanceTo(double x, double z) {
        double dx = x < minX ? minX - x : x > maxX + 1 ? x - maxX - 1 : 0.0D;
        double dz = z < minZ ? minZ - z : z > maxZ + 1 ? z - maxZ - 1 : 0.0D;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public String typeTitle() {
        return isDanger() ? "BLOCK DESTROY DANGER" : "BLOCK DESTROY";
    }

    public String describe() {
        return "#" + id + (name.isEmpty() ? "" : " «" + name + "»") + " " + typeTitle()
                + " (" + minX + " " + minY + " " + minZ + ") — (" + maxX + " " + maxY + " " + maxZ + "), "
                + sizeX() + "×" + sizeY() + "×" + sizeZ();
    }
}
