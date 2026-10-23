package com.ecoserv.customitem;

import java.util.ArrayList;
import java.util.List;

/** Свой предмет (2.0): обычный предмет с именем, описанием, свойствами и действием по ПКМ. */
public class CustomItem {
    public String id = "";
    public String base = "minecraft:stick";
    public String name = "&bСвой предмет";
    public List<String> lore = new ArrayList<String>();
    public boolean glow = true;
    public boolean unbreakable;
    /** Урон в руке (0 — как у основы). */
    public double damage;
    /** Скорость атаки (0 — как у основы). */
    public double attackSpeed;
    /** Скорость бега, % (0 — нет). */
    public double moveSpeed;
    /** Доп. здоровье в руке/на теле (0 — нет). */
    public double health;
    public List<String> steps = new ArrayList<String>();
    /** Тратится при использовании. */
    public boolean consume;
    /** Пауза между использованиями, сек. */
    public int cooldown;
    /** Использовать можно при условии (пусто — всегда). */
    public String condition = "";
    public int maxStack = 64;

    public void normalize() {
        if (lore == null) {
            lore = new ArrayList<String>();
        }
        if (steps == null) {
            steps = new ArrayList<String>();
        }
        if (condition == null) {
            condition = "";
        }
    }
}
