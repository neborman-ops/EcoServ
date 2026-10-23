package com.ecoserv.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Своё окно (2.0 beta4) из визуального дизайнера: элементы стоят по координатам, как в редакторе интерфейсов —
 * текст, кнопки, прямоугольники, предметы, картинки, полосы, поля ввода, линии.
 */
public class CustomGui {

    public static class El {
        /** text, button, rect, item, image, bar, input, line. */
        public String kind = "text";
        public int x;
        public int y;
        public int w = 60;
        public int h = 14;
        public String text = "";
        /** Цвет текста. */
        public int color = 0xFFFFFF;
        /** Цвет фона (кнопка, прямоугольник, полоса). */
        public int bg = 0x2F5BB0;
        public double bgAlpha = 1.0D;
        /** Цвет рамки (-1 — без рамки). */
        public int border = -1;
        /** 0 — слева, 1 — по центру, 2 — справа. */
        public int align;
        public double scale = 1.0D;
        public boolean shadow = true;
        public boolean round = true;
        /** Предмет «modid:имя@мета». */
        public String item = "minecraft:diamond@0";
        /** Картинка из своего контента (ключ текстуры). */
        public String image = "";
        /** Переменная игрока: полоса — откуда значение, поле — куда записать. */
        public String var = "";
        public double max = 100.0D;
        public String tooltip = "";
        /** Показывать при условии (пусто — всегда). */
        public String condition = "";
        public List<String> steps = new ArrayList<String>();
        /** После нажатия: "" — остаться, close — закрыть, gui:id — другое окно. */
        public String then = "";
        // --- только в пакете игроку ---
        public int index = -1;
        public double value;

        public void normalize() {
            if (text == null) {
                text = "";
            }
            if (steps == null) {
                steps = new ArrayList<String>();
            }
            if (then == null) {
                then = "";
            }
            if (condition == null) {
                condition = "";
            }
            if (tooltip == null) {
                tooltip = "";
            }
            if (var == null) {
                var = "";
            }
            if (image == null) {
                image = "";
            }
            if (item == null || item.isEmpty()) {
                item = "minecraft:diamond@0";
            }
            w = Math.max(1, Math.min(1000, w));
            h = Math.max(1, Math.min(1000, h));
            scale = Math.max(0.5D, Math.min(4.0D, scale));
            bgAlpha = Math.max(0.0D, Math.min(1.0D, bgAlpha));
        }
    }

    public String id = "";
    public String title = "";
    public int width = 220;
    public int height = 140;
    public int bg = 0x171920;
    public double bgAlpha = 0.95D;
    public int border = 0x4E8CFF;
    /** Затемнять мир за окном. */
    public boolean dim = true;
    /** Крестик в углу. */
    public boolean closeButton = true;
    /** Открыть может только при условии (пусто — все). */
    public String condition = "";
    /** Обновлять раз в N секунд (0 — нет): для %money%, переменных, полос. */
    public int refresh;
    public List<El> elements = new ArrayList<El>();

    public void normalize() {
        if (title == null) {
            title = "";
        }
        if (condition == null) {
            condition = "";
        }
        if (elements == null) {
            elements = new ArrayList<El>();
        }
        for (El e : elements) {
            e.normalize();
        }
        width = Math.max(40, Math.min(1000, width));
        height = Math.max(30, Math.min(1000, height));
        bgAlpha = Math.max(0.0D, Math.min(1.0D, bgAlpha));
        refresh = Math.max(0, Math.min(600, refresh));
    }
}
