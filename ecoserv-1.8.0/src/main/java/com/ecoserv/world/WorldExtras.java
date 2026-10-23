package com.ecoserv.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Дополнительные правила мира — и для подмиров, и для обычных миров: без голода, полёт, без урона
 * от падения, защита спавна, стройка только для админов, постоянные эффекты.
 */
public class WorldExtras {

    /** Голод не тратится. */
    public boolean noHunger;
    /** Можно летать (как в творческом). */
    public boolean allowFly;
    /** Нет урона от падения. */
    public boolean noFallDamage;
    /** Нет урона вообще (кроме бездны и /kill). */
    public boolean noDamage;
    /** Радиус защиты вокруг точки появления: ломать и ставить могут только админы (0 — нет). */
    public int protectRadius;
    /** Ломать и ставить блоки могут только админы (весь мир). */
    public boolean buildLocked;
    /** Нельзя выбрасывать предметы. */
    public boolean noItemDrop;
    /** Постоянные эффекты: «minecraft:night_vision» или «minecraft:speed:1» (уровень с 0). */
    public List<String> effects = new ArrayList<String>();

    public void normalize() {
        if (effects == null) {
            effects = new ArrayList<String>();
        }
        protectRadius = Math.max(0, Math.min(10000, protectRadius));
    }

    /** Хоть что-то включено (для плашек в списке миров). */
    public boolean any() {
        return noHunger || allowFly || noFallDamage || noDamage || protectRadius > 0 || buildLocked || noItemDrop
                || (effects != null && !effects.isEmpty());
    }

    /** Коротко для списка: «без голода, полёт, …». */
    public String describe() {
        List<String> parts = new ArrayList<String>();
        if (noHunger) {
            parts.add("без голода");
        }
        if (allowFly) {
            parts.add("полёт");
        }
        if (noDamage) {
            parts.add("без урона");
        } else if (noFallDamage) {
            parts.add("без урона от падения");
        }
        if (buildLocked) {
            parts.add("стройка только админам");
        } else if (protectRadius > 0) {
            parts.add("защита спавна " + protectRadius);
        }
        if (noItemDrop) {
            parts.add("нельзя выбрасывать");
        }
        if (effects != null && !effects.isEmpty()) {
            parts.add("эффектов: " + effects.size());
        }
        return String.join(", ", parts);
    }
}
