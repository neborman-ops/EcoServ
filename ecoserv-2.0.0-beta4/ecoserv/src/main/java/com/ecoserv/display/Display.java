package com.ecoserv.display;

/**
 * Блок-дисплей (2.0 beta4) — как block_display / item_display в новых версиях Minecraft: блок или предмет в
 * любой точке мира, со свободным поворотом по трём осям (кватернион) и размером по каждой оси.
 */
public class Display {

    public String id = "";
    public String name = "";
    public int dim;
    /** Центр дисплея. */
    public double x;
    public double y;
    public double z;
    /** Поворот — кватернион (x, y, z, w). */
    public double qx;
    public double qy;
    public double qz;
    public double qw = 1.0D;
    public double sx = 1.0D;
    public double sy = 1.0D;
    public double sz = 1.0D;
    /** Что показывать: «modid:имя@мета». */
    public String item = "minecraft:grass@0";
    /** auto — блок, если это блок; block; item. */
    public String mode = "auto";
    /** Светится в темноте (полная яркость). */
    public boolean glow;
    /** Медленно вращается вокруг вертикали (градусов в секунду, 0 — нет). */
    public double spin;
    /** Видно с расстояния, блоков. */
    public double viewDistance = 96.0D;

    public void normalize() {
        if (name == null) {
            name = "";
        }
        if (item == null || item.isEmpty()) {
            item = "minecraft:stone@0";
        }
        if (mode == null) {
            mode = "auto";
        }
        double l = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (l < 1e-6) {
            qx = qy = qz = 0;
            qw = 1;
        } else {
            qx /= l;
            qy /= l;
            qz /= l;
            qw /= l;
        }
        sx = clamp(sx);
        sy = clamp(sy);
        sz = clamp(sz);
        viewDistance = Math.max(8, Math.min(512, viewDistance));
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 1 : Math.max(0.01D, Math.min(64.0D, v));
    }

    // --- углы Эйлера: рыскание (Y), наклон (X), крен (Z), порядок Y·X·Z ------------------------------

    public static double[] fromEuler(double yawDeg, double pitchDeg, double rollDeg) {
        double[] y = axis(0, 1, 0, Math.toRadians(yawDeg));
        double[] x = axis(1, 0, 0, Math.toRadians(pitchDeg));
        double[] z = axis(0, 0, 1, Math.toRadians(rollDeg));
        return mul(mul(y, x), z);
    }

    public static double[] toEuler(double qx, double qy, double qz, double qw) {
        // матрица поворота
        double m02 = 2 * (qx * qz + qw * qy);
        double m12 = 2 * (qy * qz - qw * qx);
        double m22 = 1 - 2 * (qx * qx + qy * qy);
        double m10 = 2 * (qx * qy + qw * qz);
        double m11 = 1 - 2 * (qx * qx + qz * qz);
        double m00 = 1 - 2 * (qy * qy + qz * qz);
        double m20 = 2 * (qx * qz - qw * qy);
        double pitch = Math.asin(Math.max(-1, Math.min(1, -m12)));
        double yaw;
        double roll;
        if (Math.abs(m12) < 0.9999) {
            yaw = Math.atan2(m02, m22);
            roll = Math.atan2(m10, m11);
        } else {
            yaw = Math.atan2(-m20, m00);
            roll = 0;
        }
        return new double[]{Math.toDegrees(yaw), Math.toDegrees(pitch), Math.toDegrees(roll)};
    }

    public static double[] axis(double ax, double ay, double az, double rad) {
        double l = Math.sqrt(ax * ax + ay * ay + az * az);
        double s = Math.sin(rad / 2) / (l < 1e-9 ? 1 : l);
        return new double[]{ax * s, ay * s, az * s, Math.cos(rad / 2)};
    }

    /** a · b (сначала b, потом a). */
    public static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }
}
