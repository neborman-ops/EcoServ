package com.ecoserv.dev;

import java.util.ArrayList;
import java.util.List;

/**
 * Программируемая зона (2.0): при входе, выходе и пока игрок внутри — шаги сценария.
 * Глобальная (красная) — работает во всех копиях мира (локальных мирах команд подмира), локальная (синяя) —
 * только в этом мире.
 */
public class DevZone {
    public static final String GLOBAL = "global";
    public static final String LOCAL = "local";

    public String name = "";
    public String scope = GLOBAL;
    public int dim;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public boolean enabled = true;
    /** Шаги при входе / выходе / каждые stayTicks внутри. */
    public List<String> onEnter = new ArrayList<String>();
    public List<String> onLeave = new ArrayList<String>();
    public List<String> onStay = new ArrayList<String>();
    public int stayTicks = 20;
    /** Нужна одна из ролей (пусто — всем). */
    public List<String> roles = new ArrayList<String>();
    public String description = "";

    public boolean global() {
        return !LOCAL.equals(scope);
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public void normalize() {
        if (onEnter == null) {
            onEnter = new ArrayList<String>();
        }
        if (onLeave == null) {
            onLeave = new ArrayList<String>();
        }
        if (onStay == null) {
            onStay = new ArrayList<String>();
        }
        if (roles == null) {
            roles = new ArrayList<String>();
        }
        if (description == null) {
            description = "";
        }
        if (scope == null) {
            scope = GLOBAL;
        }
        int x1 = Math.min(minX, maxX);
        int y1 = Math.min(minY, maxY);
        int z1 = Math.min(minZ, maxZ);
        maxX = Math.max(minX, maxX);
        maxY = Math.max(minY, maxY);
        maxZ = Math.max(minZ, maxZ);
        minX = x1;
        minY = y1;
        minZ = z1;
        stayTicks = Math.max(5, stayTicks);
    }
}
