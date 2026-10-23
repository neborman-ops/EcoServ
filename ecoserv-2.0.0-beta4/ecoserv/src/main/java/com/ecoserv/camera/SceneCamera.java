package com.ecoserv.camera;

/**
 * Камера сцены (2.0 beta4): неподвижная точка обзора, на которую можно переключить игрока — как камера
 * наблюдения или кадр катсцены. Видна в редакторе сцены (F8) как значок камеры с пирамидой обзора.
 */
public class SceneCamera {

    public String id = "";
    public String name = "";
    public int dim;
    /** Где глаз камеры. */
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    /** Крен, градусы. */
    public float roll;
    /** Поле зрения, градусы (70 — как в игре). */
    public float fov = 70.0F;
    /** Прятать интерфейс игрока. */
    public boolean hideHud = true;
    /** Кинополосы сверху и снизу. */
    public boolean bars;
    /** Игрок может выйти сам (Shift). */
    public boolean allowExit = true;
    /** Плавный переход к камере, тиков (0 — сразу). */
    public int transition = 15;
    /** Следить: "" — нет, "viewer" — за тем, кто смотрит, "npc:id" — за NPC. */
    public String track = "";

    public void normalize() {
        if (name == null) {
            name = "";
        }
        if (track == null) {
            track = "";
        }
        fov = Math.max(10.0F, Math.min(150.0F, fov));
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch));
        transition = Math.max(0, Math.min(200, transition));
    }

    public String title() {
        return name.isEmpty() ? id : name;
    }
}
