package com.ecoserv.panel;

import java.util.ArrayList;
import java.util.List;

/** 3D-панель в мире (2.0 beta3): плоское окно с текстом и кнопками; нажимается взглядом + ПКМ. */
public class Panel {

    public static class Element {
        /** title, text, button, bar, line. */
        public String kind = "text";
        public String text = "";
        public int color = 0xFFFFFF;
        /** Кнопка: цвет фона. */
        public int bg = 0x3A6FD8;
        public String condition = "";
        public List<String> steps = new ArrayList<String>();
        /** Полоса: переменная игрока и максимум. */
        public String var = "";
        public double max = 100;
    }

    public String id = "";
    public int dim;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    /** Размер в блоках. */
    public double width = 3.0D;
    public double height = 2.0D;
    /** Размер текста. */
    public double textScale = 1.0D;
    public int bg = 0x10141C;
    public double bgAlpha = 0.85D;
    public int border = 0x4E8CFF;
    /** Всегда смотрит на игрока. */
    public boolean billboard;
    /** Видно с расстояния. */
    public double viewDistance = 48.0D;
    /** Видна при условии (пусто — всем). */
    public String condition = "";
    public List<Element> elements = new ArrayList<Element>();

    public void normalize() {
        if (elements == null) {
            elements = new ArrayList<Element>();
        }
        for (Element e : elements) {
            if (e.steps == null) {
                e.steps = new ArrayList<String>();
            }
            if (e.condition == null) {
                e.condition = "";
            }
            if (e.var == null) {
                e.var = "";
            }
        }
        if (condition == null) {
            condition = "";
        }
    }
}
