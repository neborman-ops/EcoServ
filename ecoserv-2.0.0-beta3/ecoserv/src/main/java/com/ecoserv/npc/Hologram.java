package com.ecoserv.npc;

import java.util.ArrayList;
import java.util.List;

/** Голограмма (2.0): строки текста в воздухе (невидимые стойки с именами). */
public class Hologram {
    public String id = "";
    public int dim;
    public double x;
    public double y;
    public double z;
    public List<String> lines = new ArrayList<String>();
    /** Расстояние между строками. */
    public double spacing = 0.27D;

    public void normalize() {
        if (lines == null) {
            lines = new ArrayList<String>();
        }
        if (lines.isEmpty()) {
            lines.add("&6Голограмма");
        }
    }
}
