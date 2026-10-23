package com.ecoserv.lighting;

/** Источник света (как PointLight / SpotLight / SurfaceLight в Roblox). */
public class LightObject {
    public String id = "";
    /** point, spot, surface. */
    public String kind = "point";
    public int dim;
    public double x;
    public double y;
    public double z;
    /** Куда светит (spot, surface): поворот и наклон. */
    public float yaw;
    public float pitch = 90.0F;
    public double brightness = 1.0D;
    public int color = 0xFFD9A0;
    public double range = 12.0D;
    /** Угол конуса (spot, surface), градусы. */
    public double angle = 90.0D;
    public boolean shadows;
    public boolean enabled = true;
    /** Ещё и настоящий свет блоков (как факел), чтобы было светло без шейдеров. */
    public boolean blockLight = true;
}
