package com.ecoserv.custommenu;

import java.util.ArrayList;
import java.util.List;

/** Своё меню (2.0): окно мода из элементов — заголовки, текст, кнопки, строки, сетка предметов. */
public class CustomMenu {

    public static class Element {
        /** header, text, note, row, button, item, side. */
        public String kind = "row";
        public String text = "";
        public String desc = "";
        public String icon = "minecraft:paper";
        /** Цвет кнопки/заметки: green, blue, yellow, red, grey. */
        public String color = "blue";
        /** Показывать при условии (пусто — всегда). */
        public String condition = "";
        public List<String> steps = new ArrayList<String>();
        /** Что потом: "" — остаться, close — закрыть, menu:id — другое меню. */
        public String then = "";
        /** Спросить подтверждение. */
        public String confirm = "";
    }

    public String id = "";
    public String title = "Меню";
    public String subtitle = "";
    public String icon = "minecraft:book";
    /** Открыть может только при условии (пусто — все). */
    public String condition = "";
    public List<Element> elements = new ArrayList<Element>();
    /** Кнопка в меню паузы (ESC). */
    public boolean escButton;
    public String escLabel = "";
    /** Обновлять раз в N секунд (0 — нет): для %money%, переменных. */
    public int refresh;

    public void normalize() {
        if (elements == null) {
            elements = new ArrayList<Element>();
        }
        for (Element e : elements) {
            if (e.steps == null) {
                e.steps = new ArrayList<String>();
            }
            if (e.then == null) {
                e.then = "";
            }
            if (e.condition == null) {
                e.condition = "";
            }
            if (e.confirm == null) {
                e.confirm = "";
            }
        }
        if (escLabel == null) {
            escLabel = "";
        }
        if (condition == null) {
            condition = "";
        }
    }
}
